# What the reaper's checks run in: Python 3.12, as the Lambda runtime template.yaml names, with
# pytest and boto3 for lambda/test/, and cfn-lint for lambda/template.yaml.
#
# Built by scripts/test/sandbox-test.sh, which tags the image with a hash of this file, so an edit
# here is picked up on the next run.

FROM python:3.12

ARG BOTO3_VERSION=1.43.113
ARG PYTEST_VERSION=9.1.1
ARG PYYAML_VERSION=6.0.3
ARG CFN_LINT_VERSION=1.57.2

RUN pip install --no-cache-dir \
      "boto3==${BOTO3_VERSION}" \
      "pytest==${PYTEST_VERSION}" \
      "pyyaml==${PYYAML_VERSION}" \
      "cfn-lint==${CFN_LINT_VERSION}"
