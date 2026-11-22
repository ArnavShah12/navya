#!/usr/bin/env bash
# Cheap demo: one Lightsail VM running docker-compose.demo.yml (app and Caddy for HTTPS), Lightsail managed PostgreSQL, photos in S3.
#   infra/lightsail-demo.sh up      create the VM, static IP, HTTPS, copy the code, build and start (bills from now: bundle price per month)
#   infra/lightsail-demo.sh push    send new code to the running VM and rebuild
#   infra/lightsail-demo.sh db      Lightsail managed PostgreSQL 16, copies the data from a local container if there is one, points the app at it
#   infra/lightsail-demo.sh https   static IP, open port 443, Caddy gets a Let's Encrypt certificate for <ip>.sslip.io (part of up)
#   infra/lightsail-demo.sh s3      S3 bucket and a bucket-only IAM user, app switched to STORAGE=s3, existing photos copied
#   infra/lightsail-demo.sh down    delete the VM, database, key, static IP, bucket and IAM user. All demo data is lost.
# Needs: aws cli signed in, ssh, tar. No Docker on this machine: the image is built on the VM.
# Limits: the certificate is for an sslip.io name (no domain of our own). One VM, so no backups (take a Lightsail snapshot if the data matters).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f infra/.env.deploy ] && { set -a; . infra/.env.deploy; set +a; }
export AWS_REGION=${AWS_REGION:-ap-southeast-2} AWS_DEFAULT_REGION=${AWS_REGION:-ap-southeast-2}
NAME=navya-demo; BUNDLE=${BUNDLE:-micro_3_2}; KEY=infra/demo-key.pem
IPNAME=$NAME-ip; S3USER=$NAME-s3; DBNAME=$NAME-db; DBBUNDLE=${DBBUNDLE:-micro_2_0}
SSH=(ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5)

vm_ip() { aws lightsail get-instance --instance-name $NAME --query instance.publicIpAddress --output text; }
vm_wait() { until "${SSH[@]}" ubuntu@"$1" 'docker info >/dev/null 2>&1'; do sleep 10; done; }
# stdin: KEY=value lines. Replaces those keys in ~/navya/.env on the VM. Secrets travel on stdin, never on a command line.
setenv() { # $1 = ip
  "${SSH[@]}" ubuntu@"$1" 'mkdir -p navya && cd navya && touch .env && cat > .env.in && grep -v -F -f <(cut -d= -f1 .env.in | sed "s/\$/=/") .env > .env.new || true; cat .env.in >> .env.new; mv .env.new .env; rm .env.in; chmod 600 .env'
}

case "${1:-}" in
up)
  aws lightsail create-key-pair --key-pair-name $NAME-key --query privateKeyBase64 --output text > "$KEY"; chmod 600 "$KEY"
  aws lightsail create-instances --instance-names $NAME --availability-zone "${AWS_REGION}a" --blueprint-id ubuntu_24_04 \
    --bundle-id "$BUNDLE" --key-pair-name $NAME-key --user-data 'fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile; curl -fsSL https://get.docker.com | sh && usermod -aG docker ubuntu' >/dev/null
  until [ "$(aws lightsail get-instance --instance-name $NAME --query instance.state.name --output text)" = running ]; do sleep 10; done
  "$0" db
  "$0" https
  "$0" s3
  ;;
db)     # needs the VM. Private endpoint: only Lightsail resources in this region reach it. TLS is on, the certificate is not verified.
  IP=$(vm_ip); until "${SSH[@]}" ubuntu@"$IP" true 2>/dev/null; do sleep 10; done
  # our own password, hex only: the generated one has shell characters that break .env. It goes from here to the VM's .env on stdin.
  DBPW=$(openssl rand -hex 16)
  if aws lightsail get-relational-database --relational-database-name $DBNAME >/dev/null 2>&1; then
    aws lightsail update-relational-database --relational-database-name $DBNAME --master-user-password "$DBPW" --apply-immediately >/dev/null
    sleep 30
  else
    aws lightsail create-relational-database --relational-database-name $DBNAME --availability-zone "${AWS_REGION}a" \
      --relational-database-blueprint-id postgres_16 --relational-database-bundle-id "$DBBUNDLE" \
      --master-database-name navya --master-username navya --master-user-password "$DBPW" --no-publicly-accessible >/dev/null
  fi
  until [ "$(aws lightsail get-relational-database --relational-database-name $DBNAME --query relationalDatabase.state --output text)" = available ]; do sleep 20; done
  HOST=$(aws lightsail get-relational-database --relational-database-name $DBNAME --query relationalDatabase.masterEndpoint.address --output text)
  printf 'DB_HOST=%s\nDB_PASSWORD=%s\n' "$HOST" "$DBPW" | setenv "$IP"
  # a local PostgreSQL container from an earlier run: copy its data over before the app is switched
  "${SSH[@]}" ubuntu@"$IP" 'cd navya && if docker ps -q -f name=navya-db-1 | grep -q .; then H=$(grep ^DB_HOST= .env | cut -d= -f2); P=$(grep ^DB_PASSWORD= .env | cut -d= -f2); docker exec navya-db-1 pg_dump -U navya --no-owner --no-acl navya | docker run --rm -i -e PGPASSWORD="$P" postgres:16 psql -q -v ON_ERROR_STOP=1 -h "$H" -U navya -d navya >/dev/null && echo "copied the local database"; fi'
  "$0" push
  ;;
https)  # a static IP keeps the sslip.io name valid across stop and start. Attaching it changes the VM's address.
  aws lightsail allocate-static-ip --static-ip-name $IPNAME >/dev/null 2>&1 || true
  aws lightsail attach-static-ip --static-ip-name $IPNAME --instance-name $NAME >/dev/null
  aws lightsail open-instance-public-ports --instance-name $NAME --port-info fromPort=443,toPort=443,protocol=tcp >/dev/null
  IP=$(aws lightsail get-static-ip --static-ip-name $IPNAME --query staticIp.ipAddress --output text)
  until [ "$(vm_ip)" = "$IP" ]; do sleep 5; done
  until "${SSH[@]}" ubuntu@"$IP" true 2>/dev/null; do sleep 10; done
  printf 'DOMAIN=%s\n' "${IP//./-}.sslip.io" | setenv "$IP"
  "$0" push
  ;;
push)   # copy the code to the VM, build and start (also the way to update a running demo)
  IP=$(vm_ip)
  echo "VM at $IP, waiting for Docker"
  vm_wait "$IP"
  tar czf - --exclude=node_modules --exclude=target --exclude=e2e --exclude=.git --exclude=data --exclude='.env*' --exclude='*.pem' . \
    | "${SSH[@]}" ubuntu@"$IP" 'mkdir -p navya && tar xzf - -C navya'
  # secrets are made on the VM and stay there, except the demo password, printed once below. Existing values are kept.
  "${SSH[@]}" ubuntu@"$IP" 'cd navya && touch .env && { grep -q ^JWT_SECRET= .env || printf "JWT_SECRET=%s\nSEED_PASSWORD=%s\n" "$(openssl rand -hex 32)" "$(openssl rand -hex 6)" >> .env; } && chmod 600 .env && docker compose -f docker-compose.demo.yml up -d --build --remove-orphans'
  HOST=$("${SSH[@]}" ubuntu@"$IP" 'grep ^DOMAIN= navya/.env | cut -d= -f2')
  echo "Demo: https://$HOST   (members aanya@navya.test, rohan@navya.test, meera@navya.test, admin@navya.test)"
  echo "Demo password: $("${SSH[@]}" ubuntu@"$IP" 'grep SEED_PASSWORD navya/.env | cut -d= -f2')"
  echo "Smoke test:  BASE_URL=https://$HOST node load/smoke.mjs"
  ;;
s3)     # bucket is private and encrypted. The IAM user can only read, write and delete objects in this one bucket.
  ACC=$(aws sts get-caller-identity --query Account --output text)
  BUCKET=$NAME-$ACC-$AWS_REGION
  aws s3api create-bucket --bucket "$BUCKET" --create-bucket-configuration LocationConstraint="$AWS_REGION" >/dev/null
  aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  aws s3api put-bucket-encryption --bucket "$BUCKET" --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws iam create-user --user-name $S3USER >/dev/null
  aws iam put-user-policy --user-name $S3USER --policy-name bucket-only --policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:GetObject\",\"s3:PutObject\",\"s3:DeleteObject\"],\"Resource\":\"arn:aws:s3:::$BUCKET/*\"},{\"Effect\":\"Allow\",\"Action\":\"s3:ListBucket\",\"Resource\":\"arn:aws:s3:::$BUCKET\"}]}"
  IP=$(vm_ip)
  # the key goes straight from AWS to the VM's .env: not printed, not written on this machine
  aws iam create-access-key --user-name $S3USER --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text \
    | awk -v b="$BUCKET" -v r="$AWS_REGION" '{print "AWS_ACCESS_KEY_ID="$1; print "AWS_SECRET_ACCESS_KEY="$2; print "S3_BUCKET="b; print "S3_REGION="r}' | setenv "$IP"
  # copy photos made so far (the seed data), then switch the app over
  "${SSH[@]}" ubuntu@"$IP" 'cd navya && set -a && . ./.env && set +a && docker run --rm -v navya_appdata:/data:ro -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION="$S3_REGION" amazon/aws-cli s3 sync /data/files "s3://$S3_BUCKET/"'
  echo STORAGE=s3 | setenv "$IP"
  "${SSH[@]}" ubuntu@"$IP" 'cd navya && docker compose -f docker-compose.demo.yml up -d'
  echo "Photos now in s3://$BUCKET"
  ;;
down)
  ACC=$(aws sts get-caller-identity --query Account --output text); BUCKET=$NAME-$ACC-$AWS_REGION
  aws lightsail delete-instance --instance-name $NAME --force-delete-add-ons >/dev/null || true
  aws lightsail delete-relational-database --relational-database-name $DBNAME --skip-final-snapshot >/dev/null 2>&1 || true
  aws lightsail release-static-ip --static-ip-name $IPNAME >/dev/null 2>&1 || true
  aws lightsail delete-key-pair --key-pair-name $NAME-key >/dev/null || true
  aws s3 rm "s3://$BUCKET" --recursive >/dev/null 2>&1 && aws s3api delete-bucket --bucket "$BUCKET" || true
  for k in $(aws iam list-access-keys --user-name $S3USER --query 'AccessKeyMetadata[].AccessKeyId' --output text 2>/dev/null); do aws iam delete-access-key --user-name $S3USER --access-key-id "$k"; done
  aws iam delete-user-policy --user-name $S3USER --policy-name bucket-only >/dev/null 2>&1 || true
  aws iam delete-user --user-name $S3USER >/dev/null 2>&1 || true
  rm -f "$KEY"; echo "Deleted the VM, database, key, static IP, bucket and S3 user."
  ;;
*) echo "usage: $0 up|push|db|https|s3|down" >&2; exit 2 ;;
esac
