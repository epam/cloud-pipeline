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
Deletes spending quotas, those of one quota group and/or type, or all of them if CP_QUOTA_DELETE_ALL is true.

It is run by hand, not by the job. It only lists the quotas it selects unless CP_QUOTA_DELETE_DRY_RUN is false.
Deleting a quota also deletes its applied actions: the restrictions they put in place are lifted at once, and
a quota recreated for a user who is already over a threshold notifies them again.
"""

import logging
import os
import sys

from sync_quotas import CloudPipelineApi, ConfigError, api_settings_from_env

QUOTA_GROUPS = ('GLOBAL', 'COMPUTE_INSTANCE', 'STORAGE')
QUOTA_TYPES = ('OVERALL', 'BILLING_CENTER', 'USER', 'GROUP')
SETTINGS_PREFIX = 'CP_QUOTA_DELETE_'
SETTINGS = ('CP_QUOTA_DELETE_GROUP', 'CP_QUOTA_DELETE_TYPE', 'CP_QUOTA_DELETE_ALL', 'CP_QUOTA_DELETE_DRY_RUN')


class DeleteConfig(object):

    def __init__(self, api_url, token, quota_group, quota_type, dry_run, verify_ssl=True):
        self.api_url = api_url
        self.token = token
        self.quota_group = quota_group
        self.quota_type = quota_type
        self.dry_run = dry_run
        self.verify_ssl = verify_ssl

    @classmethod
    def from_env(cls, env):
        # A misspelt filter would otherwise be ignored, and select every quota
        unknown = sorted(name for name in env if name.startswith(SETTINGS_PREFIX) and name not in SETTINGS)
        if unknown:
            raise ConfigError('Unknown setting(s) {}, expected one of {}'.format(', '.join(unknown),
                                                                                 ', '.join(SETTINGS)))
        api_url, token, verify_ssl = api_settings_from_env(env)
        quota_group = _parse_choice(env, 'CP_QUOTA_DELETE_GROUP', QUOTA_GROUPS)
        quota_type = _parse_choice(env, 'CP_QUOTA_DELETE_TYPE', QUOTA_TYPES)
        select_all = env.get('CP_QUOTA_DELETE_ALL', '').strip().lower() == 'true'
        # Every quota is selected only when asked for, never because no filter was given
        if select_all and (quota_group or quota_type):
            raise ConfigError('CP_QUOTA_DELETE_ALL=true cannot be combined with CP_QUOTA_DELETE_GROUP '
                              'or CP_QUOTA_DELETE_TYPE')
        if not select_all and not quota_group and not quota_type:
            raise ConfigError('Set CP_QUOTA_DELETE_GROUP and/or CP_QUOTA_DELETE_TYPE, '
                              'or CP_QUOTA_DELETE_ALL=true to select every quota')
        return cls(api_url=api_url,
                   token=token,
                   quota_group=quota_group,
                   quota_type=quota_type,
                   # Only an explicit false deletes: a quota's applied actions are lost with it
                   dry_run=env.get('CP_QUOTA_DELETE_DRY_RUN', 'true').strip().lower() != 'false',
                   verify_ssl=verify_ssl)


def _parse_choice(env, name, choices):
    value = env.get(name, '').strip().upper()
    if value and value not in choices:
        raise ConfigError('{} shall be one of {}, got {}'.format(name, ', '.join(choices), value))
    return value or None


def select_quotas(quotas, quota_group=None, quota_type=None):
    return [quota for quota in quotas
            if (not quota_group or quota.get('quotaGroup') == quota_group)
            and (not quota_type or quota.get('type') == quota_type)]


def describe(quota):
    return '#{} {} {} {} {} {}'.format(quota.get('id'), quota.get('quotaGroup'), quota.get('type'),
                                       quota.get('subject') or '-', quota.get('period'), quota.get('value'))


def delete(api, config):
    """Deletes the selected quotas and returns the number of quotas that could not be deleted."""
    quotas = select_quotas(api.load_quotas(), config.quota_group, config.quota_type)
    logging.info('%d quota(s) selected (group: %s, type: %s)', len(quotas),
                 config.quota_group or 'any', config.quota_type or 'any')
    deleted = 0
    failures = 0
    for quota in quotas:
        if config.dry_run:
            logging.info('[dry run] Would delete quota %s', describe(quota))
            continue
        try:
            api.delete_quota(quota['id'])
            deleted += 1
            logging.info('Deleted quota %s', describe(quota))
        except Exception as e:
            failures += 1
            logging.error('Failed to delete quota %s: %s', describe(quota), e)
    if config.dry_run:
        logging.info('Dry run: nothing was deleted. Set CP_QUOTA_DELETE_DRY_RUN=false to delete.')
    else:
        logging.info('%d quota(s) deleted, %d failed', deleted, failures)
    return failures


def main():
    logging.basicConfig(level=os.environ.get('CP_QUOTA_SYNC_LOG_LEVEL', 'INFO').strip().upper(),
                        format='%(asctime)s [%(levelname)s] %(message)s')
    try:
        config = DeleteConfig.from_env(os.environ)
    except ConfigError as e:
        logging.error(str(e))
        return 2
    failures = delete(CloudPipelineApi(config.api_url, config.token, verify_ssl=config.verify_ssl), config)
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
