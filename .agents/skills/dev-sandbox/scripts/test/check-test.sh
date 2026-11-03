#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks of the preflight check: a case for each item it refuses and each it warns about, with AWS
# replaced by the stubs in stubs.sh. Runs in the tools image, started by sandbox-test.sh with a
# throw-away state directory; it makes no AWS call.
set -euo pipefail
# As in-tools.sh, which the commands run under.
shopt -s inherit_errexit

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=stubs.sh
. "$TEST_DIR/stubs.sh"

# expect_check <description> <status> <line>...: runs the check, and expects its exit status and
# each of the lines in its output.
expect_check() {
  local description=$1 status=$2 line missing=''
  shift 2
  run_command run_check
  for line in "$@"; do
    [[ $(logged "$line") != 0 ]] || missing+=" [$line]"
  done
  check "$description" "$RUN_STATUS${missing:+, output lacks$missing}" "$status"
  if [[ -n $missing ]]; then
    sed 's/^/        | /' "$RUN_LOG"
  fi
}

# routes <route>...: describe-route-tables output with the VPC's main table holding the routes.
routes() {
  jq -cn '{RouteTables: [{RouteTableId: "rtb-main", Associations: [{Main: true}],
    Routes: $ARGS.positional}]}' --jsonargs "$@"
}

subnet() {   # subnet <jq update>: the default subnet, changed
  jq -cn --arg vpc "$CP_SANDBOX_VPC_ID" "{SubnetId: \"subnet-0fake\", VpcId: \$vpc,
    State: \"available\", AvailableIpAddressCount: 100, AvailabilityZone: \"eu-west-1a\",
    MapPublicIpOnLaunch: false} | $1"
}

test_a_good_config_passes() {
  reset_stubs
  expect_check "a config that is right passes, with no warning" 0 \
    'ok    credentials: arn:aws:iam::123456789012:user/dev' \
    'ok    credentials: temporary, with a session token' \
    'ok    CP_SANDBOX_VPC_ID vpc-0fake exists' \
    'ok    CP_SANDBOX_SUBNET_ID subnet-0fake: available, in vpc-0fake and eu-west-1a, 100 free addresses' \
    'ok    egress: route table rtb-main sends 0.0.0.0/0 to NAT gateway nat-0fake, available' \
    'ok    CP_SANDBOX_SECURITY_GROUP_IDS: sg-0fake is in vpc-0fake' \
    'ok    traffic between the master and the workers: all of it allowed by sg-0fake' \
    'ok    outbound HTTPS to 0.0.0.0/0: allowed by sg-0fake' \
    'ok    CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile: role cp-sandbox-role has AmazonSSMManagedInstanceCore' \
    'ok    CP_SANDBOX_MASTER_INSTANCE_TYPE t3.medium is offered in eu-west-1a' \
    'ok    CP_SANDBOX_WORKER_INSTANCE_TYPE t3.medium is offered in eu-west-1a' \
    '0 failed, 0 warning(s).'
  check "the check only reads" \
    "$(grep -vE '^(sts get-caller-identity|configure export-credentials|ec2 describe-|iam get-|iam list-)' "$CALLS" || true)" ''
  check "the check asks for the subnet's route tables, the NAT gateway and the zone's offerings" \
    "$(called 'ec2 describe-route-tables'; called 'ec2 describe-nat-gateways' | sed 's/ --query.*//'; called 'ec2 describe-instance-type-offerings' | sed 's/ --query.*//')" \
    "$(printf '%s\n' 'ec2 describe-route-tables --filters Name=vpc-id,Values=vpc-0fake --output json' \
      'ec2 describe-nat-gateways --nat-gateway-ids nat-0fake' \
      'ec2 describe-instance-type-offerings --location-type availability-zone --filters Name=location,Values=eu-west-1a Name=instance-type,Values=t3.medium,t3.medium')"
  run_command cmd_check
  check "check succeeds on a good config" "$RUN_STATUS" 0
  run_command cmd_check --force
  check "check takes no option" "$RUN_STATUS" 1
}

test_credentials() {
  reset_stubs
  STUB_ERROR["sts get-caller-identity"]=InvalidClientTokenId
  expect_check "check fails on credentials AWS refuses, and checks nothing else" 1 \
    'FAIL  credentials (AWS_PROFILE, or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY): InvalidClientTokenId' \
    '-     the rest: not checked without working credentials' '1 failed, 0 warning(s).'
  check "check makes no other call without credentials" "$(grep -cv '^sts ' "$CALLS" || true)" 0

  reset_stubs
  STUB_ERROR["sts get-caller-identity"]='raw:Unable to locate credentials. You can configure credentials by running "aws configure".'
  expect_check "check fails on missing credentials, quoting the CLI" 1 \
    'FAIL  credentials (AWS_PROFILE, or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY): Unable to locate credentials.'
  run_command cmd_check
  check "check exits non-zero when an item fails" "$RUN_STATUS" 1
}

# exported <key ID> <session token>: the credentials as the AWS CLI exports them.
exported() {
  jq -cn --arg id "$1" --arg secret "$FAKE_SECRET_KEY" --arg token "$2" \
    '{Version: 1, AccessKeyId: $id, SecretAccessKey: $secret} + (if $token == "" then {} else {SessionToken: $token} end)'
}

test_long_term_keys() {
  local warning='warn  credentials: long-term AWS keys (an access key ID starting AKIA, or no session token). Every run'"'"'s pod and the API'"'"'s log carry the credentials the API runs with'
  reset_stubs
  STUB_OUTPUT["configure export-credentials"]=$(exported "$LONG_TERM_KEY_ID" '')
  expect_check "check warns of an IAM user's access key, and passes" 0 "$warning" \
    'use a profile with short-lived credentials' '0 failed, 1 warning(s).'
  check "and prints none of the credentials" \
    "$(grep -c -e FAKEFAKE -e "$FAKE_SECRET_KEY" "$RUN_LOG" || true)" 0
  check "it asks the AWS CLI to export the credentials it resolves" \
    "$(called 'configure export-credentials')" 'configure export-credentials --format process'

  reset_stubs
  STUB_OUTPUT["configure export-credentials"]=$(exported "$LONG_TERM_KEY_ID" "$FAKE_SESSION_TOKEN")
  expect_check "check warns of an access key ID starting AKIA, whatever comes with it" 0 "$warning"

  reset_stubs
  STUB_OUTPUT["configure export-credentials"]=$(exported "$TEMPORARY_KEY_ID" '')
  expect_check "check warns of credentials without a session token" 0 "$warning"
  check "and prints none of them" "$(grep -c -e FAKEFAKE -e "$FAKE_SECRET_KEY" "$RUN_LOG" || true)" 0

  reset_stubs
  STUB_ERROR["configure export-credentials"]=raw:'Unable to export credentials'
  expect_check "check passes, and says so, when the AWS CLI cannot export the credentials" 0 \
    '-     credentials: not told long-term from temporary, as the AWS CLI could not export them' \
    '0 failed, 0 warning(s).'

  reset_stubs
  STUB_OUTPUT["configure export-credentials"]='not JSON'
  expect_check "or when what it exports is not JSON" 0 '-     credentials: not told long-term from temporary'

  reset_stubs
  STUB_ERROR["sts get-caller-identity"]=InvalidClientTokenId
  expect_check "check does not look at credentials that do not work" 1 'FAIL  credentials'
  check "so it exports none" "$(called 'configure export-credentials')" ''
}

test_vpc_and_subnet() {
  reset_stubs
  STUB_ERROR["ec2 describe-vpcs"]=InvalidVpcID.NotFound
  expect_check "check fails on a VPC that does not exist" 1 \
    'FAIL  CP_SANDBOX_VPC_ID vpc-0fake does not exist (InvalidVpcID.NotFound)' '1 failed'

  reset_stubs
  STUB_ERROR["ec2 describe-subnets"]=InvalidSubnetID.NotFound
  expect_check "check fails on a subnet that does not exist, and skips what needs it" 1 \
    'FAIL  CP_SANDBOX_SUBNET_ID subnet-0fake does not exist (InvalidSubnetID.NotFound)' \
    '-     egress: not checked without the subnet' \
    "-     instance types: not checked without the subnet's availability zone" '1 failed'

  reset_stubs
  STUB_OUTPUT["ec2 describe-subnets"]=$(subnet '.VpcId = "vpc-0other"')
  expect_check "check fails on a subnet in another VPC" 1 \
    'FAIL  CP_SANDBOX_SUBNET_ID subnet-0fake is in vpc-0other, not in CP_SANDBOX_VPC_ID vpc-0fake'

  reset_stubs
  STUB_OUTPUT["ec2 describe-subnets"]=$(subnet '.State = "pending"')
  expect_check "check fails on a subnet that is not available" 1 \
    'FAIL  CP_SANDBOX_SUBNET_ID subnet-0fake is pending, not available'

  reset_stubs
  STUB_OUTPUT["ec2 describe-subnets"]=$(subnet '.AvailableIpAddressCount = 0')
  expect_check "check fails on a subnet with no free address" 1 \
    'FAIL  CP_SANDBOX_SUBNET_ID subnet-0fake has no free IP addresses'

  reset_stubs
  STUB_OUTPUT["ec2 describe-subnets"]=$(subnet '.MapPublicIpOnLaunch = true')
  expect_check "check warns about a subnet that assigns public IPs" 0 \
    "warn  CP_SANDBOX_SUBNET_ID subnet-0fake assigns public IPs on launch: the sandbox's own instances ask for none" \
    '0 failed, 1 warning(s).'
}

test_egress() {
  local local_route='{"DestinationCidrBlock": "10.0.0.0/16", "GatewayId": "local", "State": "active"}'

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]=$(routes "$local_route")
  expect_check "check fails on a subnet with no route to the internet" 1 \
    'FAIL  egress: route table rtb-main of CP_SANDBOX_SUBNET_ID subnet-0fake has no active 0.0.0.0/0 route'

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]=$(routes "$local_route" \
    '{"DestinationCidrBlock": "0.0.0.0/0", "NatGatewayId": "nat-0gone", "State": "blackhole"}')
  expect_check "check fails on a 0.0.0.0/0 route that is a blackhole" 1 \
    'FAIL  egress: route table rtb-main of CP_SANDBOX_SUBNET_ID subnet-0fake has no active 0.0.0.0/0 route'

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]=$(routes "$local_route" \
    '{"DestinationCidrBlock": "0.0.0.0/0", "GatewayId": "igw-0fake", "State": "active"}')
  expect_check "check refuses a route through an internet gateway, which needs a public IP" 1 \
    'FAIL  egress: route table rtb-main of CP_SANDBOX_SUBNET_ID subnet-0fake sends 0.0.0.0/0 to internet gateway igw-0fake'

  reset_stubs
  STUB_OUTPUT["ec2 describe-nat-gateways"]=pending
  expect_check "check fails on a NAT gateway that is not available" 1 \
    'FAIL  egress: NAT gateway nat-0fake, the 0.0.0.0/0 route of rtb-main, is pending, not available'

  reset_stubs
  STUB_ERROR["ec2 describe-nat-gateways"]=NatGatewayNotFound
  expect_check "check fails on a NAT gateway that does not exist" 1 \
    'FAIL  egress: NAT gateway nat-0fake, the 0.0.0.0/0 route of rtb-main, does not exist (NatGatewayNotFound)'

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]=$(jq -cn '{RouteTables: [
    {RouteTableId: "rtb-main", Associations: [{Main: true}],
     Routes: [{DestinationCidrBlock: "0.0.0.0/0", NatGatewayId: "nat-0fake", State: "active"}]},
    {RouteTableId: "rtb-other", Associations: [{Main: false, SubnetId: "subnet-0other"}],
     Routes: [{DestinationCidrBlock: "0.0.0.0/0", NatGatewayId: "nat-0fake", State: "active"}]},
    {RouteTableId: "rtb-own", Associations: [{Main: false, SubnetId: "subnet-0fake"}],
     Routes: [{DestinationCidrBlock: "0.0.0.0/0", GatewayId: "igw-0fake", State: "active"}]}]}')
  expect_check "check reads the subnet's own route table before the VPC's main one" 1 \
    'FAIL  egress: route table rtb-own of CP_SANDBOX_SUBNET_ID subnet-0fake sends 0.0.0.0/0 to internet gateway igw-0fake'

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]=$(routes "$local_route" \
    '{"DestinationCidrBlock": "0.0.0.0/0", "TransitGatewayId": "tgw-0fake", "State": "active"}')
  expect_check "check takes a route through a transit gateway on trust, with a warning" 0 \
    'warn  egress: route table rtb-main sends 0.0.0.0/0 to tgw-0fake, taken on trust' \
    '0 failed, 1 warning(s).'
  check "check looks up no NAT gateway for another target" "$(called 'ec2 describe-nat-gateways')" ''

  reset_stubs
  STUB_OUTPUT["ec2 describe-route-tables"]='{"RouteTables": []}'
  expect_check "check fails when no route table applies to the subnet" 1 \
    'FAIL  egress: no route table applies to CP_SANDBOX_SUBNET_ID subnet-0fake'
}

test_security_groups() {
  reset_stubs
  STUB_ERROR["ec2 describe-security-groups sg-0fake"]=InvalidGroup.NotFound
  expect_check "check fails on a security group that does not exist" 1 \
    'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS: sg-0fake does not exist (InvalidGroup.NotFound)' \
    '-     security group rules: not checked without a group in the VPC' '1 failed'

  reset_stubs
  STUB_OUTPUT["ec2 describe-security-groups sg-0fake"]=$(SG_VPC=vpc-0other security_group sg-0fake all-from-self https-out)
  expect_check "check fails on a security group in another VPC" 1 \
    'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS: sg-0fake is in vpc-0other, not in CP_SANDBOX_VPC_ID vpc-0fake'

  reset_stubs
  CP_SANDBOX_SECURITY_GROUP_IDS='sg-a, sg-b'
  STUB_OUTPUT["ec2 describe-security-groups sg-a"]=$(security_group sg-a https-out)
  STUB_OUTPUT["ec2 describe-security-groups sg-b"]=$(security_group sg-b)
  expect_check "check fails when no group allows all traffic from itself" 1 \
    'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS: no group allows all traffic from itself' \
    'ok    outbound HTTPS to 0.0.0.0/0: allowed by sg-a' '1 failed'

  reset_stubs
  STUB_OUTPUT["ec2 describe-security-groups sg-0fake"]=$(security_group sg-0fake all-from-self)
  expect_check "check fails when no group allows outbound HTTPS" 1 \
    'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS: no group allows outbound TCP 443 to 0.0.0.0/0' '1 failed'

  reset_stubs
  CP_SANDBOX_SECURITY_GROUP_IDS='sg-a,sg-b'
  STUB_OUTPUT["ec2 describe-security-groups sg-a"]=$(security_group sg-a all-from-self)
  STUB_OUTPUT["ec2 describe-security-groups sg-b"]=$(jq -cn '{GroupId: "sg-b", VpcId: "vpc-0fake",
    IpPermissions: [], IpPermissionsEgress: [{IpProtocol: "tcp", FromPort: 443, ToPort: 443,
    IpRanges: [{CidrIp: "0.0.0.0/0"}]}]}')
  expect_check "check takes the rules of all the groups together, and a TCP 443 rule for HTTPS" 0 \
    'all of it allowed by sg-a' 'outbound HTTPS to 0.0.0.0/0: allowed by sg-b' '0 failed'

  reset_stubs
  CP_SANDBOX_SECURITY_GROUP_IDS=' , '
  expect_check "check fails on a list that names no security group" 1 \
    'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS names no security group'
}

test_instance_profiles() {
  reset_stubs
  STUB_ERROR["iam get-instance-profile cp-sandbox-profile"]=NoSuchEntity
  expect_check "check fails on an instance profile that does not exist" 1 \
    'FAIL  CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile does not exist (NoSuchEntity)' '1 failed'

  reset_stubs
  CP_SANDBOX_INSTANCE_PROFILE=arn:aws:iam::123456789012:instance-profile/team/cp-sandbox
  CP_SANDBOX_WORKER_INSTANCE_PROFILE=$CP_SANDBOX_INSTANCE_PROFILE
  expect_check "check reads a profile given as an ARN by its name" 0 \
    'ok    CP_SANDBOX_INSTANCE_PROFILE arn:aws:iam::123456789012:instance-profile/team/cp-sandbox: role cp-sandbox-role has'
  check "check looks the ARN's profile up by name" \
    "$(called 'iam get-instance-profile' | sed 's/ --query.*//')" \
    'iam get-instance-profile --instance-profile-name cp-sandbox'

  reset_stubs
  STUB_OUTPUT["iam get-instance-profile cp-sandbox-profile"]=None
  expect_check "check fails on an instance profile with no role" 1 \
    'FAIL  CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile has no role'

  reset_stubs
  STUB_OUTPUT["iam list-attached-role-policies cp-sandbox-role"]=$'arn:aws:iam::aws:policy/ReadOnlyAccess\tarn:aws:iam::123456789012:policy/AmazonSSMManagedInstanceCore'
  expect_check "check fails when the master's role lacks the AWS-managed SSM policy" 1 \
    'FAIL  CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile: role cp-sandbox-role does not have AmazonSSMManagedInstanceCore attached'

  reset_stubs
  STUB_ERROR["iam get-instance-profile"]=AccessDenied
  expect_check "check warns, rather than failing, when IAM reads are denied" 0 \
    'warn  CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile: not checked, IAM read denied (AccessDenied)' \
    '0 failed, 1 warning(s).'

  reset_stubs
  STUB_ERROR["iam list-attached-role-policies"]=AccessDenied
  expect_check "check warns when it may read the profile but not its role's policies" 0 \
    'warn  CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile: role cp-sandbox-role not checked, IAM read denied (AccessDenied)'

  reset_stubs
  CP_SANDBOX_WORKER_INSTANCE_PROFILE=cp-worker
  STUB_OUTPUT["iam get-instance-profile cp-worker"]=cp-worker-role
  STUB_OUTPUT["iam list-attached-role-policies cp-worker-role"]=arn:aws:iam::aws:policy/ReadOnlyAccess
  expect_check "check warns when the workers' role lacks the SSM policy" 0 \
    'warn  CP_SANDBOX_WORKER_INSTANCE_PROFILE cp-worker: role cp-worker-role does not have AmazonSSMManagedInstanceCore attached, so the workers cannot be reached through SSM' \
    'ok    CP_SANDBOX_INSTANCE_PROFILE cp-sandbox-profile: role cp-sandbox-role has' '0 failed, 1 warning(s).'

  reset_stubs
  CP_SANDBOX_WORKER_INSTANCE_PROFILE=cp-worker
  STUB_ERROR["iam get-instance-profile cp-worker"]=NoSuchEntity
  expect_check "check fails on a workers' profile that does not exist" 1 \
    'FAIL  CP_SANDBOX_WORKER_INSTANCE_PROFILE cp-worker does not exist (NoSuchEntity)'
}

test_instance_types() {
  reset_stubs
  CP_SANDBOX_WORKER_INSTANCE_TYPE=p5.48xlarge
  expect_check "check fails on an instance type the subnet's zone does not offer" 1 \
    'FAIL  CP_SANDBOX_WORKER_INSTANCE_TYPE p5.48xlarge is not offered in eu-west-1a' \
    'ok    CP_SANDBOX_MASTER_INSTANCE_TYPE t3.medium is offered in eu-west-1a' '1 failed'
}

test_every_failure_at_once() {
  reset_stubs
  STUB_ERROR["ec2 describe-vpcs"]=InvalidVpcID.NotFound
  STUB_ERROR["ec2 describe-security-groups sg-0fake"]=InvalidGroup.NotFound
  CP_SANDBOX_MASTER_INSTANCE_TYPE=x9.huge
  expect_check "check reports every failed item, not only the first" 1 \
    'FAIL  CP_SANDBOX_VPC_ID vpc-0fake' 'FAIL  CP_SANDBOX_SECURITY_GROUP_IDS: sg-0fake' \
    'FAIL  CP_SANDBOX_MASTER_INSTANCE_TYPE x9.huge' '3 failed, 0 warning(s).'
}

test_a_good_config_passes
test_credentials
test_long_term_keys
test_vpc_and_subnet
test_egress
test_security_groups
test_instance_profiles
test_instance_types
test_every_failure_at_once
finish
