# shellcheck shell=bash
# check: the preflight check of the config against the AWS account. Read-only. Reports every item
# that fails, each naming its setting or resource, and fails when any did. up and ami run it before
# they launch anything. Runs in the tools image.

SSM_POLICY=AmazonSSMManagedInstanceCore
ANYWHERE=0.0.0.0/0
HTTPS_PORT=443

# What --dry-run takes the describe calls to answer: a network that passes.
DRY_RUN_SUBNET='{"SubnetId": "subnet-0dryrun", "VpcId": "<the VpcId>", "State": "available",
  "AvailableIpAddressCount": 250, "AvailabilityZone": "<the zone>", "MapPublicIpOnLaunch": false}'
DRY_RUN_ROUTE_TABLES='{"RouteTables": [{"RouteTableId": "rtb-0dryrun", "Associations": [{"Main": true}],
  "Routes": [{"DestinationCidrBlock": "0.0.0.0/0", "NatGatewayId": "nat-0dryrun", "State": "active"}]}]}'

CHECK_FAILURES=0
CHECK_WARNINGS=0
# What the subnet's own check found, for the checks that depend on it.
CHECK_SUBNET_VPC=''
CHECK_SUBNET_ZONE=''
# Set by check_call.
CALL_OUTPUT=''
CALL_ERROR=''

cmd_check() {
  (($# == 0)) || die "check: unknown option $1"
  run_check || exit 1
}

# run_check: checks every item, writing a line for each; fails when an item failed.
run_check() {
  CHECK_FAILURES=0
  CHECK_WARNINGS=0
  printf 'Preflight check of %s, in %s:\n' "$SANDBOX_ENV_FILE" "$SANDBOX_REGION"
  if check_credentials; then
    check_credential_kind
    check_vpc
    check_subnet
    check_egress
    check_security_groups
    check_instance_profiles
    check_instance_types
  fi
  printf '%s failed, %s warning(s).\n' "$CHECK_FAILURES" "$CHECK_WARNINGS"
  ((CHECK_FAILURES == 0))
}

item_ok() {
  printf '  ok    %s\n' "$*"
}

item_warn() {
  printf '  warn  %s\n' "$*"
  CHECK_WARNINGS=$((CHECK_WARNINGS + 1))
}

item_fail() {
  printf '  FAIL  %s\n' "$*"
  CHECK_FAILURES=$((CHECK_FAILURES + 1))
}

item_skip() {
  printf '  -     %s\n' "$*"
}

# check_call <dry-run output> <aws args...>: a read-only AWS call. Sets CALL_OUTPUT to what it
# wrote and, when it fails, CALL_ERROR to the AWS error code, or else the CLI's last error line.
check_call() {
  local error_file status=0
  CALL_OUTPUT=''
  CALL_ERROR=''
  if dry_run; then
    CALL_OUTPUT=$(aws_call "$@")
    return 0
  fi
  error_file=$(mktemp)
  CALL_OUTPUT=$(aws_call "$@" 2>"$error_file") || status=$?
  if ((status)); then
    CALL_ERROR=$(sed -n 's/.*An error occurred (\([^)]*\)).*/\1/p' "$error_file" | head -n 1)
    [[ -n $CALL_ERROR ]] || CALL_ERROR=$(sed '/^[[:space:]]*$/d' "$error_file" | tail -n 1)
    CALL_ERROR=${CALL_ERROR:-exit status $status}
  fi
  rm -f "$error_file"
  return "$status"
}

# call_problem: what the failed call says about the resource it asked for.
call_problem() {
  case $CALL_ERROR in
    *NotFound* | *Malformed* | NoSuchEntity) printf 'does not exist (%s)' "$CALL_ERROR" ;;
    *) printf 'could not be read: %s' "$CALL_ERROR" ;;
  esac
}

access_denied() {
  case $CALL_ERROR in
    AccessDenied* | *UnauthorizedOperation*) return 0 ;;
  esac
  return 1
}

check_credentials() {
  if check_call arn:aws:iam::000000000000:user/dry-run sts get-caller-identity \
    --query Arn --output text; then
    item_ok "credentials: $CALL_OUTPUT"
    return 0
  fi
  item_fail "credentials (AWS_PROFILE, or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY): $CALL_ERROR"
  item_skip "the rest: not checked without working credentials"
  return 1
}

# The API that services runs hands its credentials, the config's, to every run's pod, and its log
# holds them: long-term keys stay good there until someone deletes the key.
check_credential_kind() {
  case $(credential_kind) in
    temporary) item_ok "credentials: temporary, with a session token" ;;
    long-term)
      item_warn "credentials: long-term AWS keys (an access key ID starting AKIA, or no session \
token). Every run's pod and the API's log carry the credentials the API runs with: use a profile \
with short-lived credentials, such as SSO or an assumed role, or an IAM user of their own, with \
only the permissions README.md lists"
      ;;
    *) item_skip "credentials: not told long-term from temporary, as the AWS CLI could not export them" ;;
  esac
}

# credential_kind: "temporary" or "long-term", for the credentials the AWS CLI resolves, or
# nothing when it cannot export them. They go from the CLI to jq, and no further.
credential_kind() {
  if dry_run; then
    aws_call '' configure export-credentials --format process
    printf 'temporary\n'
    return 0
  fi
  aws_call '' configure export-credentials --format process 2>/dev/null | jq -r '
    if ((.AccessKeyId // "") | startswith("AKIA")) or ((.SessionToken // "") == "")
    then "long-term" else "temporary" end' 2>/dev/null || true
}

check_vpc() {
  if check_call "$CP_SANDBOX_VPC_ID" ec2 describe-vpcs --vpc-ids "$CP_SANDBOX_VPC_ID" \
    --query 'Vpcs[0].VpcId' --output text; then
    item_ok "CP_SANDBOX_VPC_ID $CP_SANDBOX_VPC_ID exists"
  else
    item_fail "CP_SANDBOX_VPC_ID $CP_SANDBOX_VPC_ID $(call_problem)"
  fi
}

check_subnet() {
  local subnet="CP_SANDBOX_SUBNET_ID $CP_SANDBOX_SUBNET_ID" details state free public failed=0
  CHECK_SUBNET_VPC=''
  CHECK_SUBNET_ZONE=''
  if ! check_call "$(dry_run_subnet)" ec2 describe-subnets --subnet-ids "$CP_SANDBOX_SUBNET_ID" \
    --query 'Subnets[0]' --output json; then
    item_fail "$subnet $(call_problem)"
    return 0
  fi
  details=$CALL_OUTPUT
  CHECK_SUBNET_VPC=$(jq -r '.VpcId // ""' <<<"$details")
  CHECK_SUBNET_ZONE=$(jq -r '.AvailabilityZone // ""' <<<"$details")
  state=$(jq -r '.State // ""' <<<"$details")
  free=$(jq -r '.AvailableIpAddressCount // 0' <<<"$details")
  public=$(jq -r '.MapPublicIpOnLaunch // false' <<<"$details")

  if [[ $CHECK_SUBNET_VPC != "$CP_SANDBOX_VPC_ID" ]]; then
    item_fail "$subnet is in $CHECK_SUBNET_VPC, not in CP_SANDBOX_VPC_ID $CP_SANDBOX_VPC_ID"
    failed=1
  fi
  if [[ $state != available ]]; then
    item_fail "$subnet is $state, not available"
    failed=1
  fi
  if ((free == 0)); then
    item_fail "$subnet has no free IP addresses"
    failed=1
  fi
  if ((failed == 0)); then
    item_ok "$subnet: available, in $CHECK_SUBNET_VPC and $CHECK_SUBNET_ZONE, $free free addresses"
  fi
  if [[ $public == true ]]; then
    item_warn "$subnet assigns public IPs on launch: the sandbox's own instances ask for none, \
but the platform's workers take the subnet's default"
  fi
}

# Packages, Docker, the Kubernetes RPMs and the system images all come from the internet, and the
# instances get no public IP, so only a NAT gateway, or a route taken on trust, leads there.
check_egress() {
  local route table target
  if [[ -z $CHECK_SUBNET_VPC ]]; then
    item_skip "egress: not checked without the subnet"
    return 0
  fi
  if ! check_call "$DRY_RUN_ROUTE_TABLES" ec2 describe-route-tables \
    --filters "Name=vpc-id,Values=$CHECK_SUBNET_VPC" --output json; then
    item_fail "egress: the route tables of $CHECK_SUBNET_VPC $(call_problem)"
    return 0
  fi
  route=$(default_route "$CP_SANDBOX_SUBNET_ID" <<<"$CALL_OUTPUT")
  table=$(cut -f1 <<<"$route")
  target=$(cut -s -f2 <<<"$route")
  if [[ -z $table ]]; then
    item_fail "egress: no route table applies to CP_SANDBOX_SUBNET_ID $CP_SANDBOX_SUBNET_ID"
    return 0
  fi

  case $target in
    '')
      item_fail "egress: route table $table of CP_SANDBOX_SUBNET_ID $CP_SANDBOX_SUBNET_ID has no \
active $ANYWHERE route. The instances download from the internet: route $ANYWHERE through a NAT \
gateway"
      ;;
    igw-*)
      item_fail "egress: route table $table of CP_SANDBOX_SUBNET_ID $CP_SANDBOX_SUBNET_ID sends \
$ANYWHERE to internet gateway $target, which instances without a public IP cannot use: route it \
through a NAT gateway"
      ;;
    nat-*) check_nat_gateway "$table" "$target" ;;
    *)
      item_warn "egress: route table $table sends $ANYWHERE to $target, taken on trust: it must \
lead to the internet"
      ;;
  esac
}

check_nat_gateway() {
  local table=$1 nat=$2
  if ! check_call available ec2 describe-nat-gateways --nat-gateway-ids "$nat" \
    --query 'NatGateways[0].State' --output text; then
    item_fail "egress: NAT gateway $nat, the $ANYWHERE route of $table, $(call_problem)"
  elif [[ $CALL_OUTPUT != available ]]; then
    item_fail "egress: NAT gateway $nat, the $ANYWHERE route of $table, is $CALL_OUTPUT, not available"
  else
    item_ok "egress: route table $table sends $ANYWHERE to NAT gateway $nat, available"
  fi
}

# default_route <subnet-id>: reads describe-route-tables output for the subnet's VPC, and writes the
# ID of the route table the subnet uses — its own, or else the VPC's main one — and, after a tab,
# the target of that table's active route to 0.0.0.0/0, if it has one. Nothing when no table
# applies.
default_route() {
  jq -r --arg subnet "$1" --arg anywhere "$ANYWHERE" '
    (.RouteTables // []) as $tables
    | ([$tables[] | select(any(.Associations[]?; .SubnetId == $subnet))]
       + [$tables[] | select(any(.Associations[]?; .Main == true))])[0]
    | select(. != null)
    | ([(.Routes // [])[]
        | select(.DestinationCidrBlock == $anywhere and (.State // "active") == "active")][0]) as $route
    | [.RouteTableId,
       ($route | if . == null then empty else
         .GatewayId // .NatGatewayId // .TransitGatewayId // .VpcPeeringConnectionId
         // .NetworkInterfaceId // .InstanceId // .EgressOnlyInternetGatewayId // .CarrierGatewayId
         // .LocalGatewayId // .CoreNetworkArn // "unknown" end)]
    | join("\t")'
}

check_security_groups() {
  local group details vpc self_traffic='' https='' named=0 found=0
  local -a groups=()
  IFS=',' read -r -a groups <<<"$CP_SANDBOX_SECURITY_GROUP_IDS"
  for group in "${groups[@]}"; do
    group=${group//[[:space:]]/}
    [[ -n $group ]] || continue
    named=$((named + 1))
    if ! check_call "$(dry_run_security_group "$group")" ec2 describe-security-groups \
      --group-ids "$group" --query 'SecurityGroups[0]' --output json; then
      item_fail "CP_SANDBOX_SECURITY_GROUP_IDS: $group $(call_problem)"
      continue
    fi
    details=$CALL_OUTPUT
    vpc=$(jq -r '.VpcId // ""' <<<"$details")
    if [[ $vpc != "$CP_SANDBOX_VPC_ID" ]]; then
      item_fail "CP_SANDBOX_SECURITY_GROUP_IDS: $group is in $vpc, not in CP_SANDBOX_VPC_ID \
$CP_SANDBOX_VPC_ID"
      continue
    fi
    found=$((found + 1))
    item_ok "CP_SANDBOX_SECURITY_GROUP_IDS: $group is in $vpc"
    if allows_all_from_itself <<<"$details"; then
      self_traffic+=" $group"
    fi
    if allows_https_out <<<"$details"; then
      https+=" $group"
    fi
  done

  if ((named == 0)); then
    item_fail "CP_SANDBOX_SECURITY_GROUP_IDS names no security group"
    return 0
  fi
  if ((found == 0)); then
    item_skip "security group rules: not checked without a group in the VPC"
    return 0
  fi
  if [[ -n $self_traffic ]]; then
    item_ok "traffic between the master and the workers: all of it allowed by$self_traffic"
  else
    item_fail "CP_SANDBOX_SECURITY_GROUP_IDS: no group allows all traffic from itself, which the \
master and the workers need between them"
  fi
  if [[ -n $https ]]; then
    item_ok "outbound HTTPS to $ANYWHERE: allowed by$https"
  else
    item_fail "CP_SANDBOX_SECURITY_GROUP_IDS: no group allows outbound TCP $HTTPS_PORT to $ANYWHERE"
  fi
}

# allows_all_from_itself, allows_https_out: read a security group, as describe-security-groups
# writes it, and succeed when it has such a rule.
allows_all_from_itself() {
  jq -e '.GroupId as $id
    | any(.IpPermissions[]?; .IpProtocol == "-1" and any(.UserIdGroupPairs[]?; .GroupId == $id))' \
    >/dev/null
}

allows_https_out() {
  jq -e --arg anywhere "$ANYWHERE" --argjson port "$HTTPS_PORT" '
    any(.IpPermissionsEgress[]?;
      any(.IpRanges[]?; .CidrIp == $anywhere)
      and (.IpProtocol == "-1"
        or ((.IpProtocol == "tcp" or .IpProtocol == "6")
          and (.FromPort // 0) <= $port and (.ToPort // 65535) >= $port)))' >/dev/null
}

# The master's and the AMI builder's profile must let them reach SSM, which every step on them goes
# through. The workers' may not, which only costs a way to look into them.
check_instance_profiles() {
  check_instance_profile CP_SANDBOX_INSTANCE_PROFILE "$CP_SANDBOX_INSTANCE_PROFILE" required
  if [[ $CP_SANDBOX_WORKER_INSTANCE_PROFILE != "$CP_SANDBOX_INSTANCE_PROFILE" ]]; then
    check_instance_profile CP_SANDBOX_WORKER_INSTANCE_PROFILE \
      "$CP_SANDBOX_WORKER_INSTANCE_PROFILE" optional
  fi
}

# check_instance_profile <setting> <name-or-arn> <required|optional>: the profile exists, and its
# role has the SSM policy attached; without it, required fails and optional warns.
check_instance_profile() {
  local setting="$1 $2" profile=${2##*/} need=$3 role
  if ! check_call cp-sandbox-dry-run-role iam get-instance-profile \
    --instance-profile-name "$profile" --query 'InstanceProfile.Roles[0].RoleName' --output text; then
    if access_denied; then
      item_warn "$setting: not checked, IAM read denied ($CALL_ERROR). It must exist, and its \
role have $SSM_POLICY attached"
    else
      item_fail "$setting $(call_problem)"
    fi
    return 0
  fi
  role=$CALL_OUTPUT
  if [[ -z $role || $role == None ]]; then
    item_fail "$setting has no role"
    return 0
  fi

  if ! check_call "arn:aws:iam::aws:policy/$SSM_POLICY" iam list-attached-role-policies \
    --role-name "$role" --query 'AttachedPolicies[].PolicyArn' --output text; then
    if access_denied; then
      item_warn "$setting: role $role not checked, IAM read denied ($CALL_ERROR). It must have \
$SSM_POLICY attached"
    else
      item_fail "$setting: role $role $(call_problem)"
    fi
    return 0
  fi
  if [[ $CALL_OUTPUT =~ (^|[[:space:]])arn:[a-z-]+:iam::aws:policy/$SSM_POLICY($|[[:space:]]) ]]; then
    item_ok "$setting: role $role has $SSM_POLICY"
  elif [[ $need == required ]]; then
    item_fail "$setting: role $role does not have $SSM_POLICY attached, and the instances cannot \
reach SSM without it"
  else
    item_warn "$setting: role $role does not have $SSM_POLICY attached, so the workers cannot be \
reached through SSM"
  fi
}

check_instance_types() {
  local setting type offered
  if [[ -z $CHECK_SUBNET_ZONE ]]; then
    item_skip "instance types: not checked without the subnet's availability zone"
    return 0
  fi
  if ! check_call "$CP_SANDBOX_MASTER_INSTANCE_TYPE $CP_SANDBOX_WORKER_INSTANCE_TYPE" \
    ec2 describe-instance-type-offerings --location-type availability-zone \
    --filters "Name=location,Values=$CHECK_SUBNET_ZONE" \
    "Name=instance-type,Values=$CP_SANDBOX_MASTER_INSTANCE_TYPE,$CP_SANDBOX_WORKER_INSTANCE_TYPE" \
    --query 'InstanceTypeOfferings[].InstanceType' --output text; then
    item_fail "instance types: the offerings of $CHECK_SUBNET_ZONE $(call_problem)"
    return 0
  fi
  offered=" $(tr -s '[:space:]' ' ' <<<"$CALL_OUTPUT") "
  for setting in CP_SANDBOX_MASTER_INSTANCE_TYPE CP_SANDBOX_WORKER_INSTANCE_TYPE; do
    type=${!setting}
    if [[ $offered == *" $type "* ]]; then
      item_ok "$setting $type is offered in $CHECK_SUBNET_ZONE"
    else
      item_fail "$setting $type is not offered in $CHECK_SUBNET_ZONE, the subnet's zone"
    fi
  done
}

dry_run_subnet() {
  jq -c --arg vpc "$CP_SANDBOX_VPC_ID" --arg subnet "$CP_SANDBOX_SUBNET_ID" \
    --arg zone "${SANDBOX_REGION}a" '.VpcId = $vpc | .SubnetId = $subnet | .AvailabilityZone = $zone' \
    <<<"$DRY_RUN_SUBNET"
}

dry_run_security_group() {
  jq -cn --arg group "$1" --arg vpc "$CP_SANDBOX_VPC_ID" --arg anywhere "$ANYWHERE" '{
    GroupId: $group, VpcId: $vpc,
    IpPermissions: [{IpProtocol: "-1", UserIdGroupPairs: [{GroupId: $group}]}],
    IpPermissionsEgress: [{IpProtocol: "-1", IpRanges: [{CidrIp: $anywhere}]}]}'
}
