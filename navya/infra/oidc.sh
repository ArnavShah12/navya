#!/usr/bin/env bash
# One-off: lets the GitHub repository deploy through OIDC (no stored keys). Prints the role ARN to save as a repository variable.
#   GITHUB_REPO=owner/repo infra/oidc.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export AWS_REGION=${AWS_REGION:-ap-southeast-2} AWS_DEFAULT_REGION=${AWS_REGION:-ap-southeast-2}
REPO=${GITHUB_REPO:?set GITHUB_REPO to owner/repo}
EXISTING=$(aws iam list-open-id-connect-providers --query "OpenIDConnectProviderList[?ends_with(Arn, 'token.actions.githubusercontent.com')].Arn" --output text)
CREATE=true; [ -n "$EXISTING" ] && CREATE=false
aws cloudformation deploy --stack-name "navya-${ENV_NAME:-demo}-oidc" --template-file infra/oidc.yaml --capabilities CAPABILITY_NAMED_IAM --no-fail-on-empty-changeset \
  --parameter-overrides GitHubRepo="$REPO" Env="${ENV_NAME:-demo}" CreateProvider="$CREATE"
aws cloudformation describe-stacks --stack-name "navya-${ENV_NAME:-demo}-oidc" --query "Stacks[0].Outputs[?OutputKey=='RoleArn'].OutputValue" --output text
