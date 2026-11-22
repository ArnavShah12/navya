#!/usr/bin/env bash
# Deploys Navya to AWS by hand (the documented manual process; the same steps CI runs).
#   ALERT_EMAIL=you@example.com infra/deploy.sh            first deploy and every later one
#   DESIRED=2 infra/deploy.sh                              run two tasks
# Needs: aws cli v2 signed in (aws sso login, or AWS_PROFILE), docker, openssl.
# Secrets are made here with openssl and stored in SSM Parameter Store. They are never printed or written to a file.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f infra/.env.deploy ] && { set -a; . infra/.env.deploy; set +a; }   # local settings, gitignored

ENV_NAME=${ENV_NAME:-demo}
REGION=${AWS_REGION:-ap-southeast-2}
ALERT_EMAIL=${ALERT_EMAIL:?set ALERT_EMAIL (alarm and budget emails go here)}
DESIRED=${DESIRED:-1}
BUDGET_USD=${BUDGET_USD:-100}
ADMIN_EMAIL=${ADMIN_EMAIL:-}
DB_SNAPSHOT=${DB_SNAPSHOT:-}   # snapshot name printed by teardown.sh, to bring the old data back
STACK=navya-$ENV_NAME
export AWS_REGION=$REGION AWS_DEFAULT_REGION=$REGION

ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo "Account $ACCOUNT, region $REGION, environment $ENV_NAME"

# 1. budget first, so a cost alert exists before anything that costs money
aws cloudformation deploy --stack-name navya-budget --template-file infra/budget.yaml --no-fail-on-empty-changeset \
  --parameter-overrides LimitUsd="$BUDGET_USD" AlertEmail="$ALERT_EMAIL"

# 2. secrets, created once. An existing value is never overwritten, so redeploys keep working logins.
put_secret() { # name, length in bytes
  if ! aws ssm get-parameter --name "/navya/$ENV_NAME/$1" >/dev/null 2>&1; then
    aws ssm put-parameter --name "/navya/$ENV_NAME/$1" --type SecureString --value "$(openssl rand -hex "$2")" >/dev/null
    echo "created /navya/$ENV_NAME/$1"
  fi
}
put_secret db-password 24
put_secret jwt-secret 32
put_secret admin-password 16   # only used when ADMIN_EMAIL is set
ORIGIN_SECRET=$(aws ssm get-parameter --name "/navya/$ENV_NAME/origin-secret" --with-decryption --query Parameter.Value --output text 2>/dev/null || true)
if [ -z "$ORIGIN_SECRET" ]; then
  aws ssm put-parameter --name "/navya/$ENV_NAME/origin-secret" --type SecureString --value "$(openssl rand -hex 24)" >/dev/null
  ORIGIN_SECRET=$(aws ssm get-parameter --name "/navya/$ENV_NAME/origin-secret" --with-decryption --query Parameter.Value --output text)
fi

# newest default PostgreSQL version AWS offers here today (older ones stop being allowed for new instances)
DB_VERSION=$(aws rds describe-db-engine-versions --engine postgres --default-only --query 'DBEngineVersions[0].EngineVersion' --output text)

PREFIX_LIST=$(aws ec2 describe-managed-prefix-lists --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
  --query 'PrefixLists[0].PrefixListId' --output text)

deploy_stack() { # image tag, desired count
  aws cloudformation deploy --stack-name "$STACK" --template-file infra/main.yaml --capabilities CAPABILITY_IAM --no-fail-on-empty-changeset \
    --parameter-overrides Env="$ENV_NAME" ImageTag="$1" DesiredCount="$2" CloudFrontPrefixListId="$PREFIX_LIST" \
    OriginSecret="$ORIGIN_SECRET" DbEngineVersion="$DB_VERSION" DbSnapshotId="$DB_SNAPSHOT" AlertEmail="$ALERT_EMAIL" AdminEmail="$ADMIN_EMAIL"
}
out() { aws cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }

# 3. infrastructure (zero tasks on the very first run, because the image repository is empty)
if ! aws cloudformation describe-stacks --stack-name "$STACK" >/dev/null 2>&1; then deploy_stack latest 0; fi

if [ "${INFRA_ONLY:-}" = 1 ]; then   # stack exists with zero tasks: no image built, nothing running
  echo "Infrastructure only: no image built, no task started. Run again without INFRA_ONLY to build, push and start."
  exit 0
fi

# 4. build and push the image, tagged with the git commit (or a timestamp outside git)
TAG=${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}
REPO=$(out RepositoryUri)
aws ecr get-login-password | docker login --username AWS --password-stdin "${REPO%%/*}"
docker build -t "$REPO:$TAG" .
docker push "$REPO:$TAG"

# 5. start (or update) the service on that image
deploy_stack "$TAG" "$DESIRED"
aws ecs wait services-stable --cluster "$(out ClusterName)" --services "$(out ServiceName)"

URL=$(out Url)
echo
echo "Deployed $TAG at $URL"
echo "Smoke test:  BASE_URL=$URL node load/smoke.mjs"
echo "Dashboard:   $(out DashboardUrl)"
echo "Confirm the SNS email sent to $ALERT_EMAIL, or alarms will not reach you."
