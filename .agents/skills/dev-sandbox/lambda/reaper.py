"""Terminates the dev sandbox instances that have outlived their time.

A Lambda function, run every hour by template.yaml in the region it is deployed to. Of the EC2
instances there that carry a cp-sandbox tag, whoever owns them, it terminates:

- every one whose cp-sandbox-expires tag, a UTC ISO-8601 time, has passed;
- every worker (cp-sandbox-role=worker) whose sandbox has no running master: no instance with the
  same cp-sandbox and cp-sandbox-owner tags, cp-sandbox-role=master, that is pending or running
  and is not itself being terminated.

It touches nothing else: no instance without a cp-sandbox tag, and no image. It terminates only
with DRY_RUN=false: with DRY_RUN=true, or with none, it logs what it would have terminated. Every
decision is logged, one line per instance.
"""

import datetime
import logging
import os

import boto3
from botocore.exceptions import BotoCoreError, ClientError

TAG_SANDBOX = "cp-sandbox"
TAG_OWNER = "cp-sandbox-owner"
TAG_ROLE = "cp-sandbox-role"
TAG_EXPIRES = "cp-sandbox-expires"
ROLE_MASTER = "master"
ROLE_WORKER = "worker"

# The states an instance can be terminated from. One shutting down or terminated is on its way out.
CANDIDATE_STATES = ("pending", "running", "stopping", "stopped")
# A master in one of these keeps its workers: one that is pending is starting up.
MASTER_UP_STATES = ("pending", "running")

DRY_RUN_VALUES = {
    "0": False, "false": False, "no": False,
    "": True, "1": True, "true": True, "yes": True,
}

logger = logging.getLogger("cp-sandbox-reaper")
logger.setLevel(logging.INFO)


def dry_run_setting(value):
    """Whether the DRY_RUN value asks for a dry run. Unset or empty means yes: a function deployed
    without the variable terminates nothing."""
    key = (value or "").strip().lower()
    if key not in DRY_RUN_VALUES:
        raise ValueError(f"DRY_RUN={value!r}: use true or false")
    return DRY_RUN_VALUES[key]


def parse_expiry(value):
    """The time a cp-sandbox-expires value names, as an aware datetime, or None if it names none.

    The scripts write UTC, as 2026-01-31T12:00:00Z. A time without a zone is taken as UTC.
    """
    if not value:
        return None
    try:
        parsed = datetime.datetime.fromisoformat(value)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=datetime.timezone.utc)
    return parsed


def describe(instance):
    """How the log names an instance: its ID, its sandbox tags and its state."""
    return (f"{instance['id']} ({TAG_SANDBOX}={instance['sandbox']}, "
            f"{TAG_OWNER}={instance['owner'] or '-'}, {TAG_ROLE}={instance['role'] or '-'}, "
            f"{TAG_EXPIRES}={instance['expires'] or '-'}, {instance['state']})")


def list_instances(ec2):
    """The region's instances tagged cp-sandbox that can still be terminated.

    The filter asks EC2 for those only; each instance is checked again here, so that nothing
    without the tag, or in another state, is ever acted on, whatever the filter returned.
    """
    paginator = ec2.get_paginator("describe_instances")
    pages = paginator.paginate(Filters=[
        {"Name": "tag-key", "Values": [TAG_SANDBOX]},
        {"Name": "instance-state-name", "Values": list(CANDIDATE_STATES)},
    ])
    instances = []
    for page in pages:
        for reservation in page.get("Reservations", []):
            for item in reservation.get("Instances", []):
                tags = {tag["Key"]: tag["Value"] for tag in item.get("Tags", [])}
                instance_id = item["InstanceId"]
                state = item.get("State", {}).get("Name", "")
                if not tags.get(TAG_SANDBOX):
                    logger.warning("skip %s: no %s tag", instance_id, TAG_SANDBOX)
                    continue
                if state not in CANDIDATE_STATES:
                    logger.info("skip %s: %s", instance_id, state or "no state")
                    continue
                instances.append({
                    "id": instance_id,
                    "state": state,
                    "sandbox": tags[TAG_SANDBOX],
                    "owner": tags.get(TAG_OWNER, ""),
                    "role": tags.get(TAG_ROLE, ""),
                    "expires": tags.get(TAG_EXPIRES, ""),
                })
    return instances


def decide(instances, now):
    """What to do with each instance: a list of (instance, terminate, reason)."""
    expired = {}
    for instance in instances:
        expiry = parse_expiry(instance["expires"])
        expired[instance["id"]] = expiry is not None and expiry <= now

    # A sandbox is its name and its owner: two developers may pick the same name.
    masters_up = {
        (instance["sandbox"], instance["owner"])
        for instance in instances
        if instance["role"] == ROLE_MASTER
        and instance["state"] in MASTER_UP_STATES
        and not expired[instance["id"]]
    }

    decisions = []
    for instance in instances:
        if expired[instance["id"]]:
            decisions.append((instance, True, "expired"))
        elif (instance["role"] == ROLE_WORKER
              and (instance["sandbox"], instance["owner"]) not in masters_up):
            decisions.append((instance, True, "its sandbox has no running master"))
        elif parse_expiry(instance["expires"]) is None:
            decisions.append((instance, False, f"{TAG_EXPIRES} is missing or not a time"))
        else:
            decisions.append((instance, False, "not expired"))
    return decisions


def reap(ec2, now, dry_run):
    """Terminates what decide() picks, one instance per call, and returns what happened.

    Raises RuntimeError at the end if any termination failed, after trying every one.
    """
    instances = list_instances(ec2)
    logger.info("%d instance(s) tagged %s; dry run: %s",
                len(instances), TAG_SANDBOX, "yes" if dry_run else "no")
    result = {"dry_run": dry_run, "terminated": [], "kept": [], "failed": []}
    for instance, terminate, reason in decide(instances, now):
        if not terminate:
            logger.info("keep %s: %s", describe(instance), reason)
            result["kept"].append(instance["id"])
        elif dry_run:
            logger.info("would terminate %s: %s", describe(instance), reason)
            result["terminated"].append(instance["id"])
        else:
            try:
                ec2.terminate_instances(InstanceIds=[instance["id"]])
            except (ClientError, BotoCoreError) as error:
                logger.error("could not terminate %s: %s: %s", describe(instance), reason, error)
                result["failed"].append(instance["id"])
                continue
            logger.info("terminate %s: %s", describe(instance), reason)
            result["terminated"].append(instance["id"])
    logger.info("%s %d, kept %d, failed %d",
                "would terminate" if dry_run else "terminated",
                len(result["terminated"]), len(result["kept"]), len(result["failed"]))
    if result["failed"]:
        raise RuntimeError(f"could not terminate {', '.join(result['failed'])}")
    return result


def handler(event, context):
    """The Lambda entry point. The event is not read: every run looks at the whole region."""
    dry_run = dry_run_setting(os.environ.get("DRY_RUN"))
    return reap(boto3.client("ec2"), datetime.datetime.now(datetime.timezone.utc), dry_run)
