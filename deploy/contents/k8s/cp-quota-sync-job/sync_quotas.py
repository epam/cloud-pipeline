# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
Makes sure every platform user has a personal compute spending quota.

A user who already has a personal compute quota, of any period, is left as it is:
the script only creates the missing quotas and never updates or deletes one.

Only the Python standard library is used, so the script runs on a stock python image.
"""

import json
import logging
import os
import ssl
import sys
import urllib.request

QUOTA_GROUP = 'COMPUTE_INSTANCE'
QUOTA_TYPE = 'USER'
NOTIFY_ACTION = 'NOTIFY'

DEFAULT_VALUE = 1000.0
DEFAULT_PERIOD = 'MONTH'
DEFAULT_NOTIFY_THRESHOLDS = '100,600'
PERIODS = ('MONTH', 'QUARTER', 'YEAR')
USER_RECIPIENT = 'USER'
ROLE_RECIPIENT = 'ROLE'
ROLE_PREFIX = 'ROLE_'


class ApiError(Exception):
    pass


class ConfigError(Exception):
    pass


class CloudPipelineApi(object):

    def __init__(self, api_url, token, verify_ssl=True, timeout=60):
        self.api_url = api_url.rstrip('/') + '/'
        self.token = token
        self.timeout = timeout
        self.ssl_context = ssl.create_default_context() if verify_ssl else ssl._create_unverified_context()

    def load_users(self):
        return self._request('GET', 'users') or []

    def load_quotas(self):
        return self._request('GET', 'quotas') or []

    def create_quota(self, quota):
        return self._request('POST', 'quotas', quota)

    def delete_quota(self, quota_id):
        return self._request('DELETE', 'quotas/{}'.format(quota_id))

    def _request(self, method, endpoint, body=None):
        data = json.dumps(body).encode('utf-8') if body is not None else None
        request = urllib.request.Request(self.api_url + endpoint, data=data, method=method, headers={
            'Authorization': 'Bearer ' + self.token,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })
        with urllib.request.urlopen(request, context=self.ssl_context, timeout=self.timeout) as response:
            raw = response.read().decode('utf-8')
        # Some endpoints, the quota delete among them, answer a success with an empty body. An error is still
        # a status ERROR result, as the API's exception handler builds it for every endpoint.
        if not raw.strip():
            return None
        result = json.loads(raw)
        if result.get('status') != 'OK':
            raise ApiError('{} {} failed: {}'.format(method, endpoint, result.get('message')))
        return result.get('payload')


class SyncConfig(object):

    def __init__(self, api_url, token, value, period, notify_thresholds, dry_run, verify_ssl=True,
                 extra_recipients=()):
        self.api_url = api_url
        self.token = token
        self.value = value
        self.period = period
        self.notify_thresholds = notify_thresholds
        self.dry_run = dry_run
        self.verify_ssl = verify_ssl
        self.extra_recipients = list(extra_recipients)

    @classmethod
    def from_env(cls, env):
        api_url, token, verify_ssl = api_settings_from_env(env)
        period = env.get('CP_QUOTA_SYNC_PERIOD', DEFAULT_PERIOD).upper()
        if period not in PERIODS:
            raise ConfigError('CP_QUOTA_SYNC_PERIOD shall be one of {}, got {}'.format(', '.join(PERIODS), period))
        notify_thresholds = [_parse_positive(threshold, 'CP_QUOTA_SYNC_NOTIFY_THRESHOLDS')
                             for threshold in env.get('CP_QUOTA_SYNC_NOTIFY_THRESHOLDS',
                                                      DEFAULT_NOTIFY_THRESHOLDS).split(',')
                             if threshold.strip()]
        # A quota with no actions would never notify, and the job never updates a quota it has created
        if not notify_thresholds:
            raise ConfigError('CP_QUOTA_SYNC_NOTIFY_THRESHOLDS shall name at least one threshold')
        return cls(api_url=api_url,
                   token=token,
                   value=_parse_positive(env.get('CP_QUOTA_SYNC_VALUE', str(DEFAULT_VALUE)), 'CP_QUOTA_SYNC_VALUE'),
                   period=period,
                   notify_thresholds=notify_thresholds,
                   dry_run=_parse_bool(env.get('CP_QUOTA_SYNC_DRY_RUN', 'false')),
                   verify_ssl=verify_ssl,
                   extra_recipients=_parse_recipients(env.get('CP_QUOTA_SYNC_EXTRA_RECIPIENTS', '')))


def api_settings_from_env(env):
    """Returns the API address, the token and whether to check the API's certificate."""
    api_url = env.get('API')
    # An explicit API address may be anywhere, so its certificate is checked by default. The one built
    # from the cluster config is the API service inside the cluster, which serves a self-signed certificate.
    verify_ssl = bool(api_url)
    if not api_url:
        host = env.get('CP_API_SRV_INTERNAL_HOST')
        port = env.get('CP_API_SRV_INTERNAL_PORT')
        if not host or not port:
            raise ConfigError('Neither API nor CP_API_SRV_INTERNAL_HOST and CP_API_SRV_INTERNAL_PORT are set')
        api_url = 'https://{}:{}/pipeline/restapi/'.format(host, port)
    if env.get('CP_QUOTA_SYNC_VERIFY_SSL'):
        verify_ssl = _parse_bool(env['CP_QUOTA_SYNC_VERIFY_SSL'])
    token = env.get('API_TOKEN') or env.get('CP_API_JWT_ADMIN')
    if not token:
        raise ConfigError('Neither API_TOKEN nor CP_API_JWT_ADMIN is set')
    return api_url, token, verify_ssl


def _parse_bool(raw):
    return raw.strip().lower() == 'true'


def _parse_recipients(raw):
    """
    Parses a comma-separated list of USER:<name> and ROLE:<name> entries into quota recipients.

    ROLE covers both a role (ROLE_ADMIN) and a user group, as the API resolves either name into its members.
    An entry without a prefix is a role if it starts with ROLE_, and a user otherwise.
    A role or group name is upper-cased: the platform stores both in upper case, and the API matches them exactly.
    """
    recipients = []
    for entry in raw.split(','):
        entry = entry.strip()
        if not entry:
            continue
        kind, separator, name = entry.partition(':')
        if separator:
            kind = kind.strip().upper()
            if kind not in (USER_RECIPIENT, ROLE_RECIPIENT):
                raise ConfigError('CP_QUOTA_SYNC_EXTRA_RECIPIENTS entry {} shall start with {}: or {}:'
                                  .format(entry, USER_RECIPIENT, ROLE_RECIPIENT))
            principal = kind == USER_RECIPIENT
            name = name.strip()
        else:
            principal = not entry.upper().startswith(ROLE_PREFIX)
            name = entry
        if not name:
            raise ConfigError('CP_QUOTA_SYNC_EXTRA_RECIPIENTS entry {} has no name'.format(entry))
        recipients.append({'name': name if principal else name.upper(), 'principal': principal})
    return recipients


def _parse_positive(raw, name):
    try:
        value = float(raw)
    except ValueError:
        raise ConfigError('{} shall be a number, got {}'.format(name, raw))
    if value <= 0:
        raise ConfigError('{} shall be positive, got {}'.format(name, raw))
    return value


def find_users_without_quota(users, quotas):
    covered = set(quota['subject'].lower() for quota in quotas
                  if quota.get('quotaGroup') == QUOTA_GROUP
                  and quota.get('type') == QUOTA_TYPE
                  and quota.get('subject'))
    return [user['userName'] for user in users
            if user.get('userName') and user['userName'].lower() not in covered]


def resolve_extra_recipients(users, extra_recipients):
    """
    Replaces each extra user recipient's name with the user's own name, matched ignoring case.

    An unknown user is rejected rather than skipped: the job never updates a quota it has created, so a misspelt
    recipient would stay on every quota created with it. Roles and groups are passed as they are, as a group
    is only an attribute of its users and has no list of its own to check against.
    """
    user_names = dict((user['userName'].lower(), user['userName']) for user in users if user.get('userName'))
    resolved = []
    for recipient in extra_recipients:
        if not recipient['principal']:
            resolved.append(recipient)
            continue
        user_name = user_names.get(recipient['name'].lower())
        if not user_name:
            raise ConfigError('CP_QUOTA_SYNC_EXTRA_RECIPIENTS names an unknown user {}'.format(recipient['name']))
        resolved.append({'name': user_name, 'principal': True})
    return resolved


def build_quota(user_name, value, period, notify_thresholds, extra_recipients=()):
    # Quota notifications go to the recipients only, so the user has to be one of them
    recipients = []
    seen = set()
    for recipient in [{'name': user_name, 'principal': True}] + list(extra_recipients):
        # The GUI tells recipients apart by kind and name, so a repeated one could not be edited there
        key = (recipient['principal'], recipient['name'].lower())
        if key not in seen:
            seen.add(key)
            recipients.append(recipient)
    return {
        'quotaGroup': QUOTA_GROUP,
        'type': QUOTA_TYPE,
        'subject': user_name,
        'period': period,
        'value': value,
        'recipients': recipients,
        'actions': [{'threshold': threshold, 'actions': [NOTIFY_ACTION]} for threshold in notify_thresholds]
    }


def sync(api, config):
    """
    Creates the missing quotas and returns the number of users a quota could not be created for.

    Raises ConfigError, before any quota is created, if an extra recipient is an unknown user.
    """
    users = api.load_users()
    quotas = api.load_quotas()
    extra_recipients = resolve_extra_recipients(users, config.extra_recipients)
    missing = find_users_without_quota(users, quotas)
    logging.info('%d user(s) found, %d of them without a personal compute quota', len(users), len(missing))
    created = 0
    failures = 0
    for user_name in missing:
        quota = build_quota(user_name, config.value, config.period, config.notify_thresholds, extra_recipients)
        if config.dry_run:
            logging.info('[dry run] Would create quota for %s: %s', user_name, json.dumps(quota))
            continue
        try:
            api.create_quota(quota)
            created += 1
            logging.info('Created %s compute quota of %s for %s', config.period, config.value, user_name)
        except Exception as e:
            failures += 1
            logging.error('Failed to create quota for %s: %s', user_name, e)
    logging.info('%d quota(s) created, %d failed', created, failures)
    return failures


def main():
    logging.basicConfig(level=os.environ.get('CP_QUOTA_SYNC_LOG_LEVEL', 'INFO').strip().upper(),
                        format='%(asctime)s [%(levelname)s] %(message)s')
    try:
        config = SyncConfig.from_env(os.environ)
        failures = sync(CloudPipelineApi(config.api_url, config.token, verify_ssl=config.verify_ssl), config)
    except ConfigError as e:
        logging.error(str(e))
        return 2
    if failures:
        logging.error('Quota was not created for %d user(s)', failures)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
