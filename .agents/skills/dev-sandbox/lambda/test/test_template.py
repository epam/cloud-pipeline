"""Tests of template.yaml: what its role may do, and that the function, the schedule and reaper.py
agree. cfn-lint checks the rest of it.
"""

import pathlib

import pytest
import yaml

import reaper

LAMBDA_DIR = pathlib.Path(reaper.__file__).resolve().parent


class TemplateLoader(yaml.SafeLoader):
    """Reads CloudFormation's short forms, !Ref and !Sub say, as their long ones."""


def _intrinsic(loader, suffix, node):
    if isinstance(node, yaml.ScalarNode):
        value = loader.construct_scalar(node)
    elif isinstance(node, yaml.SequenceNode):
        value = loader.construct_sequence(node, deep=True)
    else:
        value = loader.construct_mapping(node, deep=True)
    if suffix == "Ref":
        return {"Ref": value}
    if suffix == "GetAtt" and isinstance(value, str):
        value = value.split(".", 1)
    return {f"Fn::{suffix}": value}


TemplateLoader.add_multi_constructor("!", _intrinsic)


@pytest.fixture(scope="module")
def template():
    with open(LAMBDA_DIR / "template.yaml", encoding="utf-8") as file:
        return yaml.load(file, Loader=TemplateLoader)  # noqa: S506 - a SafeLoader subclass


def of_type(template, resource_type):
    found = {name: resource for name, resource in template["Resources"].items()
             if resource["Type"] == resource_type}
    assert len(found) == 1, f"one {resource_type} in the template, not {len(found)}"
    return next(iter(found.items()))


def as_list(value):
    return value if isinstance(value, list) else [value]


def test_the_role_may_list_instances_terminate_tagged_ones_and_write_its_log(template):
    _, role = of_type(template, "AWS::IAM::Role")
    statements = [statement
                  for policy in role["Properties"]["Policies"]
                  for statement in policy["PolicyDocument"]["Statement"]]
    assert "ManagedPolicyArns" not in role["Properties"]
    assert all(statement["Effect"] == "Allow" for statement in statements)
    actions = sorted(action for statement in statements for action in as_list(statement["Action"]))
    assert actions == ["ec2:DescribeInstances", "ec2:TerminateInstances",
                       "logs:CreateLogStream", "logs:PutLogEvents"]


def test_the_role_terminates_only_instances_with_a_cp_sandbox_tag(template):
    _, role = of_type(template, "AWS::IAM::Role")
    terminate = [statement
                 for policy in role["Properties"]["Policies"]
                 for statement in policy["PolicyDocument"]["Statement"]
                 if "ec2:TerminateInstances" in as_list(statement["Action"])]
    assert len(terminate) == 1
    assert as_list(terminate[0]["Action"]) == ["ec2:TerminateInstances"]
    assert terminate[0]["Condition"] == {
        "StringLike": {f"aws:ResourceTag/{reaper.TAG_SANDBOX}": "?*"}}
    assert terminate[0]["Resource"] == {
        "Fn::Sub": "arn:${AWS::Partition}:ec2:${AWS::Region}:${AWS::AccountId}:instance/*"}


def test_the_role_writes_only_the_functions_own_log(template):
    log_group_name, log_group = of_type(template, "AWS::Logs::LogGroup")
    _, function = of_type(template, "AWS::Lambda::Function")
    _, role = of_type(template, "AWS::IAM::Role")
    logs = [statement
            for policy in role["Properties"]["Policies"]
            for statement in policy["PolicyDocument"]["Statement"]
            if any(action.startswith("logs:") for action in as_list(statement["Action"]))]
    assert [statement["Resource"] for statement in logs] == [
        {"Fn::GetAtt": [log_group_name, "Arn"]}]
    # Lambda writes to /aws/lambda/<function name>.
    assert function["Properties"]["FunctionName"] == {"Ref": "AWS::StackName"}
    assert log_group["Properties"]["LogGroupName"] == {"Fn::Sub": "/aws/lambda/${AWS::StackName}"}


def test_the_function_runs_reaper_py(template):
    _, function = of_type(template, "AWS::Lambda::Function")
    properties = function["Properties"]
    module, attribute = properties["Handler"].split(".")
    assert (LAMBDA_DIR / properties["Code"]).is_file()
    assert properties["Code"] == f"{module}.py" == pathlib.Path(reaper.__file__).name
    assert callable(getattr(reaper, attribute))
    assert properties["Runtime"] == "python3.12"


def test_dry_run_is_a_parameter_that_defaults_to_true(template):
    _, function = of_type(template, "AWS::Lambda::Function")
    assert function["Properties"]["Environment"]["Variables"] == {"DRY_RUN": {"Ref": "DryRun"}}
    parameter = template["Parameters"]["DryRun"]
    assert parameter["Default"] == "true"
    for value in parameter["AllowedValues"]:
        reaper.dry_run_setting(value)


def test_the_schedule_runs_the_function_every_hour(template):
    function_name, _ = of_type(template, "AWS::Lambda::Function")
    schedule_name, schedule = of_type(template, "AWS::Events::Rule")
    _, permission = of_type(template, "AWS::Lambda::Permission")
    assert schedule["Properties"]["ScheduleExpression"] == "rate(1 hour)"
    assert schedule["Properties"]["State"] == "ENABLED"
    assert [target["Arn"] for target in schedule["Properties"]["Targets"]] == [
        {"Fn::GetAtt": [function_name, "Arn"]}]
    assert permission["Properties"] == {
        "FunctionName": {"Ref": function_name},
        "Action": "lambda:InvokeFunction",
        "Principal": "events.amazonaws.com",
        "SourceArn": {"Fn::GetAtt": [schedule_name, "Arn"]},
    }
