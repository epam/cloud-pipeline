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

**This service assumes a role of the platform rather than using the host's own access.** That is what
makes it deployable beyond a single-region, single-account installation: the host it runs on may be able
to reach nothing at all, while the role recorded against a storage or its region can reach that storage
by definition - it is the role the platform itself uses for it.

`storage-lifecycle-service`'s `S3StorageOperations._build_s3_client` does exactly this, and this module
is deliberately its twin: same role resolution, same assume-role session, same per-region client. The
rule is the Java provider's too - `S3StorageProvider.getS3Helper`, which is the path every read of an S3
storage takes:

    1. the storage says `useAssumedCredentials`  -> its own `tempCredentialsRole`, else the region's
    2. otherwise                                 -> the region's `iamRole`
    3. otherwise                                 -> the region's `tempCredentialsRole`
    4. otherwise                                 -> no role: the region's named profile, or whatever
                                                    `boto3` finds by itself

Step 3 is this service's own, and `resolve_role_arn` explains why: where a deployment records no
`iamRole`, the Java provider and the lifecycle service fall through to the credentials of the host they
run on, and this service assumes the region's storage role from the node's instance profile instead.
Step 4 is the single-account installation, where the host's own access is the right answer anyway.

**Credentials of the host are therefore still needed - to assume with, not to read with.** On a platform
node that is the node's instance profile, which is trusted by the roles the platform records; it is also
how the API server and the lifecycle service get theirs.

**Why not ask the API for temporary credentials.** `POST /datastorage/tempCredentials/` exists and
resolves the same role by the same rule - `AbstractAWSTemporaryCredentialsGenerator` - but it hands back
a key, a secret and a token that expire in an hour, and this service packs archives that take longer. So
using it would mean watching the clock and re-asking: our own refresh logic, for credentials the cloud
already knows how to refresh. Assuming the role directly gives botocore's own machinery
(`DeferredRefreshableCredentials`) the job instead: it re-assumes when the credentials are close to
expiring, inside the signing path, with nothing in this service involved.

One consequence is worth naming: a role assumed this way carries the role's own permissions, not a
session policy narrowed to one storage, which is what the credentials endpoint would have returned. The
same is true of the lifecycle service, and of the API when it reads a storage for a user.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import boto3
import botocore.session
from botocore.config import Config
from botocore.credentials import AssumeRoleCredentialFetcher, DeferredRefreshableCredentials
from botocore.exceptions import ProfileNotFound

from ..errors import ApiError, CredentialsUnavailable
from ..log import get_logger
from ..model import CloudRegion, Storage

if TYPE_CHECKING:
    # Read only by a type checker, so naming the API client in an annotation costs no import at
    # runtime - and breaks no cycle, which this one would otherwise be: `api.py` must stay unaware of
    # this module.
    from ..api import CloudPipelineApi

LOG = get_logger(__name__)

# What an assumed session is called in the cloud's own audit log. The same string the Java side uses -
# `AWSUtils.ROLE_SESSION_NAME` - so that a CloudTrail entry made by this service is recognisable as the
# platform's rather than as `botocore-session-1760000000`, which is what botocore invents when nobody
# names the session.
ROLE_SESSION_NAME = 'CLOUD_PIPELINE_SESSION'


def build_s3_client(api: CloudPipelineApi, storage: Storage):
    """
    Makes an S3 client that can reach the storage, in the storage's own region.

    The region is read from the API and not guessed. A client created without one talks to whatever
    region the environment names, and S3 answers a request for a bucket elsewhere with a redirect that
    botocore follows - except that a SigV4 signature is computed for a region, so the retry can fail
    with a signature error instead. Reading the region also means a task archiving a storage in
    `eu-central-1` and writing the archive to a storage in `us-east-1` is the ordinary case: each client
    is built from its own storage's region, and nothing in the process has to agree on one.

    No return type is annotated because botocore builds its client classes at runtime, so there is no
    name to annotate. That is normal for boto3 code and the reason type checkers need a stub package
    for it.
    """
    region = _load_region(api, storage)
    session = _build_session(resolve_role_arn(storage, region), region.profile, region.code)
    if region.s3_endpoint:
        # Three choices, and all three are what the rest of the platform makes against such an endpoint.
        # A path-style request (`https://host/bucket/key`) is what an S3-compatible service answers,
        # while botocore defaults to the virtual-hosted style (`https://bucket.host/key`) that needs a
        # wildcard DNS entry per bucket - `RegionAwareS3Helper.getDefaultS3Client` sets the same pair.
        # `verify=False` because such a deployment normally signs its own certificate, and the service
        # it points at is inside the installation: `pipe` makes the same exception in
        # `S3Client._get_client`, and for the same reason the API url is not verified either.
        LOG.debug('Reaching storage %s through the custom endpoint %s of region %s', storage.name,
                  region.s3_endpoint, region.code)
        return session.client('s3', region_name=region.code, endpoint_url=region.s3_endpoint,
                              verify=False, config=Config(s3={'addressing_style': 'path'}))
    LOG.debug('Reaching storage %s in region %s', storage.name, region.code)
    return session.client('s3', region_name=region.code)


def resolve_role_arn(storage: Storage, region: CloudRegion) -> str | None:
    """
    The role to assume for a storage, or `None` to use the host's own credentials.

    `useAssumedCredentials` on the storage is the switch, and it is not optional detail: with it set,
    the storage names the role it has to be reached through - it usually lives in another account - and
    `tempCredentialsRole` is that role, the storage's own or its region's. That branch is
    `S3StorageProvider.getS3Helper` and the lifecycle service's `_fetch_role_arn_for_storage` line for
    line.

    Without the switch the region decides, and `iamRole` comes first because it is the field added for
    exactly this - issue 2235, "assume aws role" - so wherever a deployment sets it, this service
    assumes what the API assumes and no question arises.

    **The last step is this service's own decision rather than a copy of anything.** A region with no
    `iamRole` leaves the Java provider and the lifecycle service falling back to the credentials of
    whatever host they run on, and for them that is reasonable: the API server and the lifecycle node
    are given S3 access directly. This service then takes the region's `tempCredentialsRole` instead -
    the role a deployment records as *the* role for reaching its storages - and assumes it from the
    node's own instance profile, which is what that profile is trusted for. The effect is that the
    service's reach is decided by the platform's own configuration rather than by how much S3 access
    somebody granted the node it happens to run on.

    The cost of that last step, stated plainly: the API assumes `tempCredentialsRole` **with a session
    policy** that narrows it to one storage (`AWSUtils.generate`), and an assumed role here carries no
    such policy. So on this path the service holds that role whole, which is wider than what a user
    gets through `pipe` - and still exactly the role the deployment chose for its storages.
    """
    if storage.use_assumed_credentials:
        return storage.temp_credentials_role or region.temp_credentials_role
    return region.iam_role or region.temp_credentials_role


def _load_region(api: CloudPipelineApi, storage: Storage) -> CloudRegion:
    """
    The region of a storage. Fails the task rather than guessing when it cannot be had.

    Without the region there is no role to assume and no region to sign for, and the only fallback
    would be the environment's - which is the guessing this module exists to remove. A storage with no
    region at all is a storage no cloud client can be built for, and both cases are a configuration
    problem rather than anything the user who asked for the archive can act on, so the message says
    which storage and what went wrong.
    """
    if not storage.region_id:
        raise CredentialsUnavailable(
            f'Storage {storage.name} belongs to no cloud region, so there is no way to tell how to '
            f'reach it')
    try:
        return api.load_region(storage.region_id)
    except (ApiError, KeyError, ValueError) as error:
        # `ApiRejected` is an `ApiError`, so both the refusal and the transport failure land here. The
        # refusal means the region was deleted or this service's user cannot read it; neither is worth
        # separating for the person reading the message.
        raise CredentialsUnavailable(
            f'Cannot read cloud region {storage.region_id} of storage {storage.name}, so there is no '
            f'way to tell how to reach it: {error}') from error


def _build_session(role_arn: str | None, profile: str | None, region_code: str) -> boto3.Session:
    """
    The session a client is made from: an assumed role, a profile of the host, or neither.

    The assumed-role half is the point of the module, and the obvious way to write it is wrong. A single
    `sts.assume_role` call hands back credentials that expire - an hour by default - and an archive of a
    large selection takes longer than that. `DeferredRefreshableCredentials` is botocore's own answer: it
    calls the fetcher the first time a request needs signing, and again whenever the credentials are
    close to expiring, so the re-assume happens inside the signing path and nothing here watches a clock.
    The lifecycle service builds its session the same way, down to the assignment to `_credentials` - a
    private attribute, and the only way botocore offers to install credentials of one's own.

    A profile is used only when there is no role to assume. Assuming needs credentials to call STS with,
    and both the lifecycle service and `AssumedCredentialsS3Helper` let the default chain provide them -
    on a platform node that is the node's own role, which is the one thing a deployment can rely on
    having.
    """
    if not role_arn:
        if profile:
            LOG.debug('Using the AWS profile %s of the region', profile)
            try:
                return boto3.Session(profile_name=profile)
            except ProfileNotFound as missing:
                # The region names a profile that this host's AWS configuration does not define. A
                # deployment problem, and one worth its own message: botocore's own error arrives out
                # of a constructor and would be reported to the user as an internal error of this
                # service.
                raise CredentialsUnavailable(
                    f'The cloud region names the AWS profile {profile!r}, which this host does not '
                    f'have: {missing}') from missing
        LOG.debug('No role and no profile on the region: using the credentials of this host')
        return boto3.Session()
    LOG.debug('Assuming the role %s', role_arn)
    source = boto3.Session()
    source_credentials = source.get_credentials()
    if source_credentials is None:
        # Nothing to call STS with. Checked here because botocore does not: its fetcher dereferences
        # these credentials when it finally makes the call, so the failure would arrive as an
        # `AttributeError` on `NoneType` from inside the signer - reported to the user as an internal
        # error of this service, with nothing in it about credentials.
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

    The region is pinned to the storage's own. Left out, botocore resolves the global STS endpoint,
    which exists on the main partition and does not in a China or GovCloud one - so a deployment there
    would fail to assume any role at all. The lifecycle service leaves it out; there is no reason to
    copy that, and the region is already in hand.

    botocore passes the source credentials as keyword arguments and never a region, so adding one here
    cannot collide with what it sends.
    """
    def client_creator(service_name, **kwargs):
        return source.client(service_name, region_name=region_code, **kwargs)
    return client_creator
