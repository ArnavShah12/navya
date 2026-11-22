#!/usr/bin/env bash
# Deletes everything Navya created, so nothing keeps billing after the demo.
#   infra/teardown.sh            asks first
#   infra/teardown.sh --yes      no question
# The database is snapshotted before it is deleted (the template says so). Delete that snapshot by hand when you no longer need it.
set -euo pipefail
ENV_NAME=${ENV_NAME:-demo}
export AWS_REGION=${AWS_REGION:-ap-southeast-2} AWS_DEFAULT_REGION=${AWS_REGION:-ap-southeast-2}
if [ "${1:-}" != "--yes" ]; then
  read -r -p "Delete the navya-$ENV_NAME stack (app, database, photos, logs) in $AWS_REGION? Type the environment name to confirm: " a
  [ "$a" = "$ENV_NAME" ] || { echo "Cancelled"; exit 1; }
fi
BUCKET=$(aws cloudformation describe-stacks --stack-name "navya-$ENV_NAME" --query "Stacks[0].Outputs[?OutputKey=='BucketName'].OutputValue" --output text 2>/dev/null || true)
[ -n "$BUCKET" ] && aws s3 rm "s3://$BUCKET" --recursive || true   # a bucket must be empty before it can be deleted
aws cloudformation delete-stack --stack-name "navya-$ENV_NAME"
aws cloudformation wait stack-delete-complete --stack-name "navya-$ENV_NAME"
for p in db-password jwt-secret admin-password origin-secret; do aws ssm delete-parameter --name "/navya/$ENV_NAME/$p" 2>/dev/null || true; done
SNAP=$(aws rds describe-db-snapshots --db-instance-identifier "navya-$ENV_NAME" --query 'sort_by(DBSnapshots,&SnapshotCreateTime)[-1].DBSnapshotIdentifier' --output text 2>/dev/null || true)
echo "Deleted. Left in place on purpose: the budget stack (navya-budget), the OIDC stack, and the final database snapshot."
echo "To bring the data back:  DB_SNAPSHOT=$SNAP ALERT_EMAIL=... infra/deploy.sh"
echo "Snapshots cost money while they exist. Delete this one when you are done: aws rds delete-db-snapshot --db-snapshot-identifier $SNAP"
