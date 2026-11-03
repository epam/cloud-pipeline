"""Tests of reaper.py, with EC2 stubbed by botocore's Stubber: nothing here reaches AWS.

A call the test has not queued fails the test, so a case that queues no terminate_instances
proves that nothing is terminated.
"""

import collections
import datetime
import logging
import re

import boto3
import pytest
from botocore.exceptions import EndpointConnectionError
from botocore.stub import Stubber

import reaper

NOW = datetime.datetime(2026, 10, 14, 12, 0, 0, tzinfo=datetime.timezone.utc)
PAST = "2026-10-14T11:59:59Z"
FUTURE = "2026-10-15T12:00:00Z"
OWNER = "dev@example.com"
OTHER_OWNER = "other@example.com"

FILTERS = [
    {"Name": "tag-key", "Values": ["cp-sandbox"]},
    {"Name": "instance-state-name", "Values": ["pending", "running", "stopping", "stopped"]},
]
STATE_CODES = {"pending": 0, "running": 16, "shutting-down": 32, "terminated": 48,
               "stopping": 64, "stopped": 80}
DECISION = re.compile(r"^(?:terminate|would terminate|keep|could not terminate) (\S+) \(")

Ec2 = collections.namedtuple("Ec2", "client stubber log")


def instance(instance_id, state="running", sandbox="dev-abcd", owner=OWNER, role="master",
             expires=FUTURE):
    """An instance as describe_instances returns it. A tag given as None is left out."""
    tags = [{"Key": key, "Value": value} for key, value in (
        ("Name", f"cp-sandbox-{sandbox}-{role}"),
        ("cp-sandbox", sandbox),
        ("cp-sandbox-owner", owner),
        ("cp-sandbox-role", role),
        ("cp-sandbox-expires", expires),
    ) if value is not None]
    return {"InstanceId": instance_id, "State": {"Name": state, "Code": STATE_CODES[state]},
            "Tags": tags}


def logged(instance_id, sandbox="dev-abcd", role="master", expires=FUTURE, state="running",
           owner=OWNER):
    """How the log names an instance."""
    return (f"{instance_id} (cp-sandbox={sandbox}, cp-sandbox-owner={owner}, "
            f"cp-sandbox-role={role}, cp-sandbox-expires={expires}, {state})")


@pytest.fixture
def ec2(caplog):
    caplog.set_level(logging.INFO, logger="cp-sandbox-reaper")
    client = boto3.client("ec2", region_name="eu-west-1", aws_access_key_id="testing",
                          aws_secret_access_key="testing")
    with Stubber(client) as stubber:
        yield Ec2(client, stubber, caplog)


def expect_describe(ec2, *pages):
    """Queues describe_instances, one page per argument, as the paginator asks for them."""
    for number, page in enumerate(pages):
        params = {"Filters": FILTERS}
        if number:
            params["NextToken"] = f"page-{number}"
        response = {"Reservations": [{"ReservationId": f"r-{number}", "Instances": page}]}
        if number < len(pages) - 1:
            response["NextToken"] = f"page-{number + 1}"
        ec2.stubber.add_response("describe_instances", response, params)


def expect_terminate(ec2, *instance_ids):
    """Queues one terminate_instances per instance, in that order."""
    for instance_id in instance_ids:
        ec2.stubber.add_response(
            "terminate_instances",
            {"TerminatingInstances": [{
                "InstanceId": instance_id,
                "CurrentState": {"Name": "shutting-down", "Code": 32},
                "PreviousState": {"Name": "running", "Code": 16},
            }]},
            {"InstanceIds": [instance_id]})


def assert_one_decision_each(ec2, *instance_ids):
    """The log has one decision line for each of the instances, and none for any other."""
    decided = [match.group(1) for match in map(DECISION.match, ec2.log.messages) if match]
    assert sorted(decided) == sorted(instance_ids)


def reap(ec2, dry_run=False):
    result = reaper.reap(ec2.client, NOW, dry_run)
    ec2.stubber.assert_no_pending_responses()
    assert_one_decision_each(ec2, *result["terminated"], *result["kept"], *result["failed"])
    return result


def test_terminates_an_expired_instance_and_keeps_one_that_has_not_expired(ec2):
    expect_describe(ec2, [
        instance("i-expired", sandbox="dev-old", expires=PAST),
        instance("i-current", sandbox="dev-new", expires=FUTURE),
    ])
    expect_terminate(ec2, "i-expired")
    result = reap(ec2)
    assert result == {"dry_run": False, "terminated": ["i-expired"], "kept": ["i-current"],
                      "failed": []}
    assert (f"terminate {logged('i-expired', sandbox='dev-old', expires=PAST)}: expired"
            in ec2.log.messages)
    assert f"keep {logged('i-current', sandbox='dev-new')}: not expired" in ec2.log.messages
    assert "2 instance(s) tagged cp-sandbox; dry run: no" in ec2.log.messages
    assert "terminated 1, kept 1, failed 0" in ec2.log.messages


def test_an_expiry_of_exactly_now_has_passed(ec2):
    expect_describe(ec2, [instance("i-now", expires="2026-10-14T12:00:00Z")])
    expect_terminate(ec2, "i-now")
    assert reap(ec2)["terminated"] == ["i-now"]


def test_terminates_expired_instances_of_every_role_and_state(ec2):
    expect_describe(ec2, [
        instance("i-stopped-master", state="stopped", expires=PAST),
        instance("i-builder", sandbox="ami-0123456789ab", role="ami-builder", expires=PAST),
        instance("i-pending", state="pending", sandbox="dev-efgh", expires=PAST),
        instance("i-no-role", sandbox="dev-ijkl", role=None, expires=PAST),
    ])
    expect_terminate(ec2, "i-stopped-master", "i-builder", "i-pending", "i-no-role")
    assert reap(ec2)["terminated"] == ["i-stopped-master", "i-builder", "i-pending", "i-no-role"]
    assert (f"terminate {logged('i-no-role', sandbox='dev-ijkl', role='-', expires=PAST)}: "
            "expired") in ec2.log.messages


def test_an_expired_worker_goes_even_while_its_master_runs(ec2):
    expect_describe(ec2, [
        instance("i-master"),
        instance("i-worker", role="worker", expires=PAST),
    ])
    expect_terminate(ec2, "i-worker")
    result = reap(ec2)
    assert result["terminated"] == ["i-worker"]
    assert result["kept"] == ["i-master"]


def test_terminates_a_worker_whose_sandbox_has_no_master(ec2):
    expect_describe(ec2, [instance("i-worker", role="worker")])
    expect_terminate(ec2, "i-worker")
    assert reap(ec2)["terminated"] == ["i-worker"]
    assert (f"terminate {logged('i-worker', role='worker')}: its sandbox has no running master"
            in ec2.log.messages)


@pytest.mark.parametrize("master_state", ["stopping", "stopped"])
def test_terminates_a_worker_whose_master_is_not_running(ec2, master_state):
    expect_describe(ec2, [
        instance("i-master", state=master_state),
        instance("i-worker", role="worker"),
    ])
    expect_terminate(ec2, "i-worker")
    result = reap(ec2)
    assert result["terminated"] == ["i-worker"]
    assert result["kept"] == ["i-master"]


@pytest.mark.parametrize("master_state", ["pending", "running"])
def test_keeps_a_worker_whose_master_is_up(ec2, master_state):
    expect_describe(ec2, [
        instance("i-worker", role="worker"),
        instance("i-master", state=master_state),
    ])
    assert reap(ec2)["kept"] == ["i-worker", "i-master"]


def test_finds_the_master_on_another_page(ec2):
    expect_describe(ec2, [instance("i-worker", role="worker")], [instance("i-master")])
    assert reap(ec2)["kept"] == ["i-worker", "i-master"]


def test_a_worker_goes_with_its_expired_master(ec2):
    expect_describe(ec2, [
        instance("i-master", expires=PAST),
        instance("i-worker", role="worker", expires=FUTURE),
    ])
    expect_terminate(ec2, "i-master", "i-worker")
    assert reap(ec2)["terminated"] == ["i-master", "i-worker"]


def test_a_master_of_another_owner_keeps_no_worker(ec2):
    expect_describe(ec2, [
        instance("i-other-master", owner=OTHER_OWNER),
        instance("i-worker", role="worker"),
        instance("i-other-worker", role="worker", owner=OTHER_OWNER),
    ])
    expect_terminate(ec2, "i-worker")
    result = reap(ec2)
    assert result["terminated"] == ["i-worker"]
    assert result["kept"] == ["i-other-master", "i-other-worker"]


def test_a_master_of_another_sandbox_keeps_no_worker(ec2):
    expect_describe(ec2, [
        instance("i-master", sandbox="dev-efgh"),
        instance("i-worker", role="worker"),
    ])
    expect_terminate(ec2, "i-worker")
    assert reap(ec2)["terminated"] == ["i-worker"]


@pytest.mark.parametrize("expires, shown", [(None, "-"), ("", "-"), ("next week", "next week")])
def test_keeps_an_instance_without_a_valid_expiry(ec2, expires, shown):
    expect_describe(ec2, [
        instance("i-master", expires=expires),
        instance("i-worker", role="worker", expires=expires),
    ])
    assert reap(ec2)["kept"] == ["i-master", "i-worker"]
    assert (f"keep {logged('i-master', expires=shown)}: cp-sandbox-expires is missing or not a "
            "time") in ec2.log.messages


def test_an_orphaned_worker_goes_whatever_its_expiry(ec2):
    expect_describe(ec2, [instance("i-worker", role="worker", expires="next week")])
    expect_terminate(ec2, "i-worker")
    assert reap(ec2)["terminated"] == ["i-worker"]


def test_never_acts_on_an_instance_the_filter_should_not_have_returned(ec2):
    expect_describe(ec2, [
        instance("i-untagged", sandbox=None, role="worker", expires=PAST),
        instance("i-empty-tag", sandbox="", role="worker", expires=PAST),
        instance("i-shutting-down", state="shutting-down", expires=PAST),
        instance("i-terminated", state="terminated", role="worker", expires=PAST),
    ])
    result = reap(ec2)
    assert result == {"dry_run": False, "terminated": [], "kept": [], "failed": []}
    assert "skip i-untagged: no cp-sandbox tag" in ec2.log.messages
    assert "skip i-empty-tag: no cp-sandbox tag" in ec2.log.messages
    assert "skip i-shutting-down: shutting-down" in ec2.log.messages
    assert "skip i-terminated: terminated" in ec2.log.messages


def test_a_dry_run_terminates_nothing(ec2):
    expect_describe(ec2, [
        instance("i-expired", expires=PAST),
        instance("i-orphan", sandbox="dev-efgh", role="worker"),
        instance("i-current", sandbox="dev-ijkl"),
    ])
    result = reap(ec2, dry_run=True)
    assert result == {"dry_run": True, "terminated": ["i-expired", "i-orphan"],
                      "kept": ["i-current"], "failed": []}
    assert f"would terminate {logged('i-expired', expires=PAST)}: expired" in ec2.log.messages
    assert "3 instance(s) tagged cp-sandbox; dry run: yes" in ec2.log.messages
    assert "would terminate 2, kept 1, failed 0" in ec2.log.messages


def test_terminates_on_every_page(ec2):
    expect_describe(ec2,
                    [instance("i-first", expires=PAST)],
                    [instance("i-second", sandbox="dev-efgh", expires=PAST)],
                    [instance("i-third", sandbox="dev-ijkl")])
    expect_terminate(ec2, "i-first", "i-second")
    result = reap(ec2)
    assert result["terminated"] == ["i-first", "i-second"]
    assert result["kept"] == ["i-third"]


def test_a_refused_termination_stops_none_of_the_others(ec2):
    expect_describe(ec2, [
        instance("i-protected", expires=PAST),
        instance("i-expired", sandbox="dev-efgh", expires=PAST),
    ])
    ec2.stubber.add_client_error("terminate_instances", service_error_code="OperationNotPermitted",
                                 service_message="termination protection",
                                 expected_params={"InstanceIds": ["i-protected"]})
    expect_terminate(ec2, "i-expired")
    with pytest.raises(RuntimeError, match="could not terminate i-protected$"):
        reaper.reap(ec2.client, NOW, False)
    ec2.stubber.assert_no_pending_responses()
    assert_one_decision_each(ec2, "i-protected", "i-expired")
    assert any(message.startswith(f"could not terminate {logged('i-protected', expires=PAST)}: "
                                  "expired: ")
               and "OperationNotPermitted" in message for message in ec2.log.messages)
    assert "terminated 1, kept 0, failed 1" in ec2.log.messages


def test_a_connection_error_stops_none_of_the_others(ec2, monkeypatch):
    expect_describe(ec2, [
        instance("i-unreachable", expires=PAST),
        instance("i-expired", sandbox="dev-efgh", expires=PAST),
    ])
    expect_terminate(ec2, "i-expired")
    stubbed = ec2.client.terminate_instances

    def terminate(**params):
        if params == {"InstanceIds": ["i-unreachable"]}:
            raise EndpointConnectionError(endpoint_url="https://ec2.eu-west-1.amazonaws.com/")
        return stubbed(**params)

    monkeypatch.setattr(ec2.client, "terminate_instances", terminate)
    with pytest.raises(RuntimeError, match="could not terminate i-unreachable$"):
        reaper.reap(ec2.client, NOW, False)
    ec2.stubber.assert_no_pending_responses()
    assert_one_decision_each(ec2, "i-unreachable", "i-expired")
    assert "terminated 1, kept 0, failed 1" in ec2.log.messages


def test_no_instances(ec2):
    expect_describe(ec2, [])
    assert reap(ec2) == {"dry_run": False, "terminated": [], "kept": [], "failed": []}
    assert "0 instance(s) tagged cp-sandbox; dry run: no" in ec2.log.messages


@pytest.mark.parametrize("value, expected", [
    ("false", False), ("False", False), (" false ", False), ("0", False), ("no", False),
    (None, True), ("", True), ("true", True), ("TRUE", True), ("1", True), ("yes", True),
])
def test_dry_run_setting(value, expected):
    assert reaper.dry_run_setting(value) is expected


@pytest.mark.parametrize("value", ["maybe", "2", "on"])
def test_dry_run_setting_refuses_anything_else(value):
    with pytest.raises(ValueError, match="DRY_RUN"):
        reaper.dry_run_setting(value)


@pytest.mark.parametrize("value, expected", [
    ("2026-10-14T12:00:00Z", NOW),
    ("2026-10-14T12:00:00", NOW),
    ("2026-10-14T14:00:00+02:00", NOW),
    ("", None),
    (None, None),
    ("tomorrow", None),
])
def test_parse_expiry(value, expected):
    assert reaper.parse_expiry(value) == expected


def handler_with(monkeypatch, ec2, dry_run):
    if dry_run is None:
        monkeypatch.delenv("DRY_RUN", raising=False)
    else:
        monkeypatch.setenv("DRY_RUN", dry_run)
    monkeypatch.setattr(reaper.boto3, "client", lambda service: {"ec2": ec2.client}[service])
    result = reaper.handler({}, None)
    ec2.stubber.assert_no_pending_responses()
    return result


def test_the_handler_terminates_against_the_current_time(monkeypatch, ec2):
    expect_describe(ec2, [
        instance("i-long-gone", expires="2000-01-01T00:00:00Z"),
        instance("i-far-ahead", sandbox="dev-efgh", expires="2100-01-01T00:00:00Z"),
    ])
    expect_terminate(ec2, "i-long-gone")
    result = handler_with(monkeypatch, ec2, "false")
    assert result == {"dry_run": False, "terminated": ["i-long-gone"], "kept": ["i-far-ahead"],
                      "failed": []}


@pytest.mark.parametrize("dry_run", [None, "", "true"])
def test_the_handler_terminates_nothing_unless_told_to(monkeypatch, ec2, dry_run):
    expect_describe(ec2, [instance("i-long-gone", expires="2000-01-01T00:00:00Z")])
    result = handler_with(monkeypatch, ec2, dry_run)
    assert result == {"dry_run": True, "terminated": ["i-long-gone"], "kept": [], "failed": []}


def test_the_handler_makes_no_client_on_a_bad_dry_run(monkeypatch):
    def no_client(service):
        raise AssertionError(f"made a {service} client")

    monkeypatch.setenv("DRY_RUN", "maybe")
    monkeypatch.setattr(reaper.boto3, "client", no_client)
    with pytest.raises(ValueError, match="DRY_RUN='maybe'"):
        reaper.handler({}, None)
