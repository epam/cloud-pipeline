# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
The S3 clients of this service, and where their credentials come from.

The service assumes a role of the platform rather than using the host's own access, so it can reach a
storage the host cannot. The rule follows `S3StorageProvider.getS3Helper` and the lifecycle service's
`S3StorageOperations._build_s3_client`:

    1. the storage says `useAssumedCredentials`  -> its own `tempCredentialsRole`, else the region's
    2. otherwise                                 -> the region's `iamRole`
    3. otherwise                                 -> the region's `tempCredentialsRole`
    4. otherwise                                 -> no role: the region's profile, or whatever boto3
                                                    finds by itself

Step 3 is this service's own - see `resolve_role_arn`. Host credentials are still needed, to assume
with; on a platform node that is the node's instance profile.

`POST /datastorage/tempCredentials/` is not used: it hands back credentials that expire in an hour,
and an archive can take longer, so the clock would become ours to watch. Assuming the role directly
leaves the refresh to botocore.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import boto3
import botocore.session
from botocore.config import Config
from botocore.credentials import AssumeRoleCredentialFetcher, DeferredRefreshableCredentials
from botocore.exceptions import ProfileNotFound

from ..util.errors import ApiError, CredentialsUnavailable
from ..util.log import get_logger
from ..model import CloudRegion, Storage

if TYPE_CHECKING:
    # Read only by a type checker, so the annotation costs no import at runtime. `api.py` must stay
    # unaware of this module.
    from ..api import CloudPipelineApi

LOG = get_logger(__name__)

# The same session name the Java side uses (`AWSUtils.ROLE_SESSION_NAME`), so a CloudTrail entry is
# recognisable as the platform's.
ROLE_SESSION_NAME = 'CLOUD_PIPELINE_SESSION'


def build_s3_client(api: CloudPipelineApi, storage: Storage):
    """
    Makes an S3 client that can reach the storage, in the storage's own region.

    The region is read from the API, not guessed: a SigV4 signature is computed for one region, so a
    client pointed elsewhere can fail with a signature error. It also means archiving a storage in one
    region into a bucket in another is the ordinary case.

    No return type: botocore builds its client classes at runtime.
    """
    region = _load_region(api, storage)
    session = _build_session(resolve_role_arn(storage, region), region.profile, region.code)
    if region.s3_endpoint:
        # Path-style addressing is what an S3-compatible service answers, while botocore defaults to
        # the virtual-hosted style, which needs a wildcard DNS entry per bucket. `verify=False`
        # because such a deployment normally signs its own certificate. Both match
        # `RegionAwareS3Helper.getDefaultS3Client`.
        LOG.debug('Reaching storage %s through the custom endpoint %s of region %s', storage.name,
                  region.s3_endpoint, region.code)
        return session.client('s3', region_name=region.code, endpoint_url=region.s3_endpoint,
                              verify=False, config=Config(s3={'addressing_style': 'path'}))
    LOG.debug('Reaching storage %s in region %s', storage.name, region.code)
    return session.client('s3', region_name=region.code)


def resolve_role_arn(storage: Storage, region: CloudRegion) -> str | None:
    """
    The role to assume for a storage, or `None` to use the host's own credentials.

    With `useAssumedCredentials` the storage names the role it must be reached through, usually
    because it lives in another account. Otherwise the region decides, `iamRole` first.

    The last step is this service's own: where a region records no `iamRole`, the Java provider and
    the lifecycle service fall back to host credentials, and this service assumes the region's
    `tempCredentialsRole` instead. That role is held whole here, without the session policy the API
    narrows it with in `AWSUtils.generate`.
    """
    if storage.use_assumed_credentials:
        return storage.temp_credentials_role or region.temp_credentials_role
    return region.iam_role or region.temp_credentials_role


def _load_region(api: CloudPipelineApi, storage: Storage) -> CloudRegion:
    """
    The region of a storage. Fails the task rather than guessing when it cannot be had.

    Both cases are a configuration problem, so the message names the storage.
    """
    if not storage.region_id:
        raise CredentialsUnavailable(
            f'Storage {storage.name} belongs to no cloud region, so there is no way to tell how to '
            f'reach it')
    try:
        return api.load_region(storage.region_id)
    except (ApiError, KeyError, ValueError) as error:
        # `ApiRejected` is an `ApiError`, so a refusal lands here too.
        raise CredentialsUnavailable(
            f'Cannot read cloud region {storage.region_id} of storage {storage.name}, so there is no '
            f'way to tell how to reach it: {error}') from error


def _build_session(role_arn: str | None, profile: str | None, region_code: str) -> boto3.Session:
    """
    The session a client is made from: an assumed role, a profile of the host, or neither.

    A plain `sts.assume_role` would hand back credentials that expire within the hour, and an archive
    can take longer. `DeferredRefreshableCredentials` re-assumes inside the signing path instead. The
    assignment to the private `_credentials` is the only way botocore offers to install credentials of
    one's own, and the lifecycle service does the same.

    A profile is used only when there is no role: assuming needs credentials to call STS with, and
    those come from the default chain.
    """
    if not role_arn:
        if profile:
            LOG.debug('Using the AWS profile %s of the region', profile)
            try:
                return boto3.Session(profile_name=profile)
            except ProfileNotFound as missing:
                # Otherwise botocore's own error arrives out of a constructor and is reported to the
                # user as an internal error.
                raise CredentialsUnavailable(
                    f'The cloud region names the AWS profile {profile!r}, which this host does not '
                    f'have: {missing}') from missing
        LOG.debug('No role and no profile on the region: using the credentials of this host')
        return boto3.Session()
    LOG.debug('Assuming the role %s', role_arn)
    source = boto3.Session()
    source_credentials = source.get_credentials()
    if source_credentials is None:
        # botocore does not check this: without the guard the failure arrives much later as an
        # `AttributeError` from inside the signer.
        raise CredentialsUnavailable(
            f'This host has no credentials of its own, so the role {role_arn} cannot be assumed. On a '
            f'platform node that means its instance profile is missing')
    fetcher = AssumeRoleCredentialFetcher(
        client_creator=_sts_client_creator(source, region_code),
        source_credentials=source_credentials,
        role_arn=role_arn,
        extra_args={'RoleSessionName': ROLE_SESSION_NAME},
    )
    botocore_session = botocore.session.Session()
    botocore_session._credentials = DeferredRefreshableCredentials(
        method='assume-role', refresh_using=fetcher.fetch_credentials)
    return boto3.Session(botocore_session=botocore_session)


def _sts_client_creator(source: boto3.Session, region_code: str):
    """
    Builds the function botocore calls to make the STS client that assumes the role.

    The region is pinned to the storage's own: left out, botocore resolves the global STS endpoint,
    which does not exist in a China or GovCloud partition.
    """
    def client_creator(service_name, **kwargs):
        return source.client(service_name, region_name=region_code, **kwargs)
    return client_creator
