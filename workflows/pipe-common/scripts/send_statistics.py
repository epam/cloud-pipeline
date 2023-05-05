# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
import os
import io
import csv
import itertools
import argparse
from datetime import datetime
import json

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger


DATE_FORMAT = "%Y-%m-%d"
DATE_TIME_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
CAPABILITY_TEMPLATE = "CP_CAP_CUSTOM_{}=true=boolean"


EMAIL_TEMPLATE = '''
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <style>
        table,
        td {{
            border: 1px solid black;
            border-collapse: collapse;
            padding: 5px;
        }}
    </style>
</head>

<body>
<p>Dear user,</p>
<p>*** This is a system generated email, do not reply to this email ***</p>
<p>Please find usage statistics for period {from} - {to}. </p>
<p>
{text}
</p>
<p>Best regards,</p>
<p>{deploy_name} Platform</p>
</body>

</html>
'''


EMAIL_SUBJECT = '[%s]: Platform usage statistics'


class Stat(object):

    def __init__(self, compute_hours, runs_count, storage_read, storage_write, usage_weight, login_time, cpu_hours,
                 gpu_hours, clusters_compute_hours, worker_nodes_count,
                 top3_instance_types, top3_pipelines, top3_tools, top3_used_buckets, top3_run_capabilities):
        self.compute_hours = compute_hours
        self.runs_count = runs_count
        self.storage_read = storage_read
        self.storage_write = storage_write
        self.usage_weight = usage_weight
        self.login_time = login_time
        self.cpu_hours = cpu_hours
        self.gpu_hours = gpu_hours
        self.clusters_compute_hours = clusters_compute_hours
        self.worker_nodes_count = worker_nodes_count
        self.top3_instance_types = top3_instance_types
        self.top3_pipelines = top3_pipelines
        self.top3_tools = top3_tools
        self.top3_used_buckets = top3_used_buckets
        self.top3_run_capabilities = top3_run_capabilities

    @staticmethod
    def format_top3(top):
        top3_str = ''
        for key, value in top:
            top3_str += '<li>{}: {}</li>'.format(key, value)
        return '<ul>{}</ul>'.format(top3_str)

    def get_object_str(self, template):
        return template.format(**{'compute_hours': self.compute_hours,
                                  'runs_count': self.runs_count,
                                  'storage_read': self.storage_read,
                                  'storage_write': self.storage_write,
                                  'usage_weight': self.usage_weight,
                                  'login_time': self.login_time,
                                  'cpu_hours': self.cpu_hours,
                                  'gpu_hours': self.gpu_hours,
                                  'clusters_compute_hours': self.clusters_compute_hours,
                                  'worker_nodes_count': self.worker_nodes_count,
                                  'top3_instance_types': self.format_top3(self.top3_instance_types),
                                  'top3_pipelines': self.format_top3(self.top3_pipelines),
                                  'top3_tools': self.format_top3(self.top3_tools),
                                  'top3_used_buckets': self.format_top3(self.top3_used_buckets),
                                  'top3_run_capabilities': self.format_top3(self.top3_run_capabilities)})


class Notifier(object):

    def __init__(self, api, start, end, stat, deploy_name, notify_users):
        self.api = api
        self.start = start
        self.end = end
        self.stat = stat
        self.deploy_name = deploy_name
        self.notify_users = notify_users

    def send_notifications(self, template):
        if not self.notify_users:
            return
        self.api.create_notification(EMAIL_SUBJECT % self.deploy_name,
                                     self.build_text(template),
                                     self.notify_users[0],
                                     copy_users=self.notify_users[1:] if len(self.notify_users) > 0 else None
                                     )

    def build_text(self, template):
        stat_str = self.stat.get_object_str(template)
        return EMAIL_TEMPLATE.format(**{'text': stat_str,
                                        'from': self.start,
                                        'to': self.end,
                                        'deploy_name': self.deploy_name})


def send_statistics():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']

    logging_directory = os.getenv('CP_SEND_STAT_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_SEND_STAT_LEVEL', 'INFO')
    logging_level_local = os.getenv('CP_SEND_STAT_LEVEL_LOCAL', 'DEBUG')
    logging_format = os.getenv('CP_SEND_STAT_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')

    logging.basicConfig(level=logging_level_local, format=logging_format,
                        filename=os.path.join(logging_directory, 'send_statistics.log'))

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task='SendStat', inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    parser = argparse.ArgumentParser()
    parser.add_argument('--template_path', required=True)
    parser.add_argument('--from_date', required=True)
    parser.add_argument('--to_date', required=True)
    parser.add_argument('--users', nargs='+', help='List of users separated by spaces', required=False)
    parser.add_argument('--roles', nargs='+', help='List of roles separated by spaces', required=False)
    args = parser.parse_args()

    template_path = args.template_path
    from_date = args.from_date
    try:
        datetime.strptime(from_date, DATE_FORMAT)
    except ValueError:
        raise RuntimeError("Invalid from_date format. Date format should be {}.".format(DATE_FORMAT))
    to_date = args.to_date
    try:
        datetime.strptime(to_date, DATE_FORMAT)
    except ValueError:
        raise RuntimeError("Invalid to_date format. Date format should be {}.".format(DATE_FORMAT))

    if not args.users and not args.roles:
        parser.error('Either --users or --roles should be specified.')

    users = [] if args.users is None else args.users
    roles = [] if args.roles is None else args.roles

    try:
        all_users = list()
        logger.debug('Collecting users...')
        for user in users:
            user_details = api.load_user_by_name(user)
            if user_details is not None:
                all_users.append(user_details)

        logger.debug('Collecting roles...')
        for role in roles:
            details = api.load_role_by_name(role)
            if 'users' in details:
                users = details['users']
                all_users.extend(users)

        users = [u.get('userName') for u in all_users if not u.get('blocked')]
        users = list(set(users))
        if len(users) > 0:
            logger.info('{} User(s) collected({}).'.format(len(users), ','.join(users)))
            logger.info('Collecting and sending statistics...')
            with open(template_path, 'r') as file:
                template = file.read()
                _send_users_stat(api, logger, from_date, to_date, users, template)
        else:
            logger.info('No users found to collect and send statistics.')
    except KeyboardInterrupt:
        logger.warning('Interrupted.')
        # break
    except Exception:
        logger.warning('Platform usage notification has failed.', trace=True)
    except BaseException:
        logger.error('Platform usage notification  has failed completely.', trace=True)
        raise


def _send_users_stat(api, logger, from_date, to_date, users, template):

    capabilities = _get_capabilities(api)
    storages = api.data_storage_load_all()

    platform_usage_costs = _get_platform_usage_cost(api, from_date, to_date)
    logger.info('Platform Usage costs: {}.'.format(platform_usage_costs))

    for user in users:
        logger.info('User: {}'.format(user))
        stat = _get_statistics(api, capabilities, logger, platform_usage_costs, from_date, to_date,
                               storages, user)
        notifier = Notifier(api, from_date, to_date, stat, os.getenv('CP_DEPLOY_NAME', 'Cloud Pipeline'), [user])
        notifier.send_notifications(template)


def _get_statistics(api, capabilities, logger, platform_usage_costs, from_date, to_date, storages, user):
    from_date_time = "{} 00:00:00.000".format(from_date)
    to_date_time = "{} 00:00:00.000".format(to_date)
    runs = _get_runs(api, from_date, to_date, user)
    storage_write = _get_storage_usage(api, from_date_time, to_date_time, user, 'WRITE')
    logger.info('Storage write requests count: {}.'.format(storage_write))
    storage_read = _get_storage_usage(api, from_date_time, to_date_time, user, 'READ')
    logger.info('Storage read requests count: {}.'.format(storage_read))
    compute_hours = _get_compute_hours(runs)
    logger.info('Compute hours: {}.'.format(compute_hours))
    cpu_hours = _get_cpu_compute_hours(runs)
    logger.info('CPU Compute hours: {}.'.format(cpu_hours))
    gpu_hours = _get_gpu_compute_hours(runs)
    logger.info('GPU Compute hours: {}.'.format(gpu_hours))
    runs_count = _get_runs_count(runs)
    logger.info('Runs count: {}.'.format(runs_count))
    usage_costs = _get_usage_costs(runs)
    logger.info('Usage costs: {}.'.format(usage_costs))
    usage_weight = usage_costs / platform_usage_costs
    logger.info('Usage weight: {}.'.format(usage_weight))
    clusters_compute_hours = _get_cluster_compute_hours(api, from_date_time, to_date_time, user)
    logger.info('Clusters compute hours: {}.'.format(clusters_compute_hours))
    worker_nodes_count = _get_worker_nodes_count(api, from_date_time, to_date_time, user)
    logger.info('Clusters worker nodes count: {}.'.format(worker_nodes_count))
    login_time = _get_login_time(api, from_date_time, to_date_time, user)
    logger.info('Login time: {}.'.format(login_time))
    top3_instance_types = _get_top3(runs, "Instance")
    logger.info('Top 3 Instance types: {}.'.format(top3_instance_types))
    top3_pipelines = _get_top3(runs, "Pipeline")
    logger.info('Top 3 Pipelines: {}.'.format(top3_pipelines))
    top3_tools = _get_top3(runs, "Tool")
    logger.info('Top 3 Tools: {}.'.format(top3_tools))
    top3_used_buckets = _get_used_buckets(api, from_date_time, to_date_time, user, storages)
    logger.info('Top 3 Used buckets: {}.'.format(top3_used_buckets))
    top3_run_capabilities = _get_top3_run_capabilities(api, from_date_time, to_date_time, user, capabilities)
    logger.info('Top 3 Capabilities: {}.'.format(top3_run_capabilities))
    return Stat(compute_hours, runs_count, storage_read, storage_write, usage_weight, login_time, cpu_hours,
                gpu_hours, clusters_compute_hours, worker_nodes_count,
                top3_instance_types, top3_pipelines, top3_tools, top3_used_buckets, top3_run_capabilities)


def _get_login_time(api, from_date_time, to_date_time, user):
    users_report = api.report_users(from_date_time, to_date_time, [user])
    active_hours_list = [1 for d in users_report if d.get('activeUsers') is not None]
    return len(active_hours_list)


def _get_platform_usage_cost(api, from_date, to_date):
    all_run_dict = _get_runs(api, from_date, to_date, None)
    return _get_usage_costs(all_run_dict)


def _get_runs(api, from_date, to_date, user):
    filters = {} if user is None else {"owner": [user]}
    run_data = api.billing_export(from_date, to_date, filters, ["RUN"])
    return _read_content(run_data, False)


def _get_storage_usage(api, from_date_time, to_date_time, user, usage):
    filter = {'messageTimestampFrom': from_date_time, 'messageTimestampTo': to_date_time, 'types': ['audit'],
              'users': [user], 'message': usage}
    storage_usage = api.log_group(filter, "user")
    if storage_usage is None or storage_usage.get(user) is None:
        return 0
    return storage_usage.get(user)


def _get_top3_run_capabilities(api, from_date, to_date, user, capabilities):
    capabilities_dict = {}
    for c in capabilities:
        runs = api.filter_runs_all(from_date, to_date, user, {'partialParameters': CAPABILITY_TEMPLATE.format(c)})
        if len(runs) > 0:
            total_time = 0
            for r in runs:
                start_date = datetime.strptime(r.get('startDate'), DATE_TIME_FORMAT)
                end_date = datetime.strptime(r.get('endDate'), DATE_TIME_FORMAT)
                total_time += (end_date - start_date).total_seconds()
            capabilities_dict[c] = total_time / 3600
    return sorted(capabilities_dict.items(), key=lambda item: float(item[1]), reverse=True)[:3]


def _get_cluster_compute_hours(api, from_date, to_date, user):
    runs = api.filter_runs_all(from_date, to_date, user, {'master': True})
    total_time = 0
    for r in runs:
        start_date = datetime.strptime(r.get('startDate'), DATE_TIME_FORMAT)
        end_date = datetime.strptime(r.get('endDate'), DATE_TIME_FORMAT)
        total_time += (end_date - start_date).total_seconds()
    return total_time / 3600


def _get_worker_nodes_count(api, from_date, to_date, user):
    return api.run_count(from_date, to_date, user, {'worker': True})


def _get_capabilities(api):
    cap_str = api.get_preference('launch.capabilities')['value']
    cap_json = json.loads(cap_str)
    return list(cap_json.keys())


def _read_content(content, skip_first_row=True):
    csv_content = io.BytesIO(content)
    if skip_first_row:
        next(csv_content)
    reader = csv.DictReader(csv_content)
    return list(reader)


def _get_usage_costs(runs):
    return sum([float(r.get('Cost ($)')) for r in runs])


def _get_runs_count(runs):
    return len(runs)


def _get_compute_hours(runs):
    durations = [float(r.get('Duration (hours)')) for r in runs]
    return sum(durations)


def _get_cpu_compute_hours(runs):
    durations = [float(r.get('Duration (hours)')) for r in runs if r.get('Type') == 'CPU']
    return sum(durations)


def _get_gpu_compute_hours(runs):
    durations = [float(r.get('Duration (hours)')) for r in runs if r.get('Type') == 'GPU']
    return sum(durations)


def _get_top3(runs, entity):
    def key_func(k):
        return k[entity]
    runs_sorted = sorted(runs, key=key_func)
    runs_grouped = {}
    for key, group in itertools.groupby(runs_sorted, key_func):
        if len(key.strip()) > 0:
            runs_grouped[key] = len(list(group))
    return sorted(runs_grouped.items(), key=lambda item: item[1], reverse=True)[:3]


def _get_used_buckets(api, from_date_time, to_date_time, user, storages):
    filter = {'messageTimestampFrom': from_date_time, 'messageTimestampTo': to_date_time, 'types': ['audit'],
              'users': [user]}
    logs_by_storage = api.log_group(filter, "storageId")
    top3_storages = list()
    if logs_by_storage is not None:
        user_log_group_sorted = sorted(logs_by_storage.items(), key=lambda x: x[1], reverse=True)[:3]
        for key, value in user_log_group_sorted:
            storage_name = [pc.get('name') for pc in storages if pc.get('id') == int(key)]
            if len(storage_name) > 0:
                top3_storages.append((storage_name, value))
    return top3_storages


if __name__ == '__main__':
    send_statistics()
