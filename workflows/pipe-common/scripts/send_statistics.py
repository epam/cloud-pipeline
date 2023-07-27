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
from enum import Enum
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger


DATE_FORMAT = "%Y-%m-%d"
DATE_TIME_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
CAPABILITY_TEMPLATE = "CP_CAP_CUSTOM_{}=true=boolean"

STATISTICS_TEMPLATE = "statistics-template.html"
TABLE_TEMPLATE = "group-3-table.html"
TABLE_CENTER_TEMPLATE = "group-3-table-center.html"

TYPE = 'Type'
TYPE_GPU = 'GPU'
TYPE_CPU = 'CPU'
COST = 'Cost ($)'
DURATION_HOURS = 'Duration (hours)'
ROUND = 4

EMAIL_TEMPLATE = '{text}'


EMAIL_SUBJECT = '[%s]: Platform usage statistics'


class Top3(Enum):
    INSTANCES = ("Top-3 instances", "instance")
    PIPELINES = ("Top-3 pipelines", "pipeline")
    TOOLS = ("Top-3 tools", "tool")
    BUCKETS = ("Top-3 used buckets", "bucket")
    CAPABILITIES = ("Top-3 run capabilities", "capability")


class Stat(object):

    def __init__(self, compute_hours, runs_count, storage_read, storage_write, usage_weight, login_time, cpu_hours,
                 gpu_hours, clusters_compute_secs, worker_nodes_count,
                 top3_instance_types, top3_pipelines, top3_tools, top3_used_buckets, top3_run_capabilities):
        self.compute_hours = compute_hours
        self.runs_count = runs_count
        self.storage_read = storage_read
        self.storage_write = storage_write
        self.usage_weight = usage_weight
        self.login_time = login_time
        self.cpu_hours = cpu_hours
        self.gpu_hours = gpu_hours
        self.clusters_compute_secs = clusters_compute_secs
        self.worker_nodes_count = worker_nodes_count
        self.top3_instance_types = top3_instance_types
        self.top3_pipelines = top3_pipelines
        self.top3_tools = top3_tools
        self.top3_used_buckets = top3_used_buckets
        self.top3_run_capabilities = top3_run_capabilities

    @staticmethod
    def format_count(count):
        return "<b>{}</b>".format(count)

    @staticmethod
    def format_weight(weight):
        return "<b>{}</b><span>%</span>".format(round(weight, ROUND))

    @staticmethod
    def format_hours(float_hours):
        return Stat.format_seconds(int(float_hours * 3600))

    @staticmethod
    def format_seconds(seconds):
        hours, seconds = divmod(seconds, 3600)
        result = "<b>{}</b><span>&nbsp;h</span>".format(int(hours))
        minutes, seconds = divmod(seconds, 60)
        if minutes > 0:
            return "{}&nbsp;<b>{}</b><span>&nbsp;m</span>".format(result, int(minutes))
        return result

    @staticmethod
    def format_items(top):
        items = ''
        for key, value in top:
            items += '<tr><td class="group3-left">{}</td><td class="group3-right">{}</td></tr>'.format(key, value)
        return items

    @staticmethod
    def format_tables(tables, table_center_templ, table_templ):
        table_html = ''
        row_templ = '<tr>{}</tr>'
        while len(tables) > 0:
            if len(tables) == 1:
                column_html = Stat.format_column(table_center_templ, tables)
                table_html += row_templ.format(column_html)
            else:
                column_html = Stat.format_column(table_templ, tables)
                column_html += Stat.format_column(table_templ, tables)
                table_html += row_templ.format(column_html)
        return table_html

    @staticmethod
    def format_column(table_center_templ, tables):
        column = tables.popitem()
        return table_center_templ.format(**{"TABLE_TITLE": column[0][0],
                                            "TABLE_ICON": column[0][1],
                                            "ITEMS": column[1]})

    def get_tables(self):
        tables = dict()
        if len(self.top3_instance_types) > 0:
            tables[Top3.INSTANCES.value] = Stat.format_items(self.top3_instance_types)
        if len(self.top3_pipelines) > 0:
            tables[Top3.PIPELINES.value] = Stat.format_items(self.top3_pipelines)
        if len(self.top3_tools) > 0:
            tables[Top3.TOOLS.value] = Stat.format_items(self.top3_tools)
        if len(self.top3_used_buckets) > 0:
            tables[Top3.BUCKETS.value] = Stat.format_items(self.top3_used_buckets)
        if len(self.top3_run_capabilities) > 0:
            tables[Top3.CAPABILITIES.value] = Stat.format_items(self.top3_run_capabilities)
        return tables

    def get_object_str(self, start, end, deploy_name, template, table_templ, table_center_templ):
        return template.format(**{'PLATFORM_NAME': deploy_name,
                                  'PERIOD': "{} - {}".format(start, end),
                                  'COMPUTE_HOURS': Stat.format_hours(self.compute_hours),
                                  'RUNS_COUNT': Stat.format_count(self.runs_count),
                                  'LOGIN_TIME': Stat.format_hours(self.login_time),
                                  'USAGE_WEIGHT': Stat.format_weight(self.usage_weight),
                                  'READ_REQUESTS': self.storage_read,
                                  'WRITE_REQUESTS': self.storage_write,
                                  'CPU': Stat.format_hours(self.cpu_hours),
                                  'GPU': Stat.format_hours(self.gpu_hours),
                                  'COMPUTE': Stat.format_seconds(self.clusters_compute_secs),
                                  'WORKER_NODES': self.worker_nodes_count,
                                  'TABLE': Stat.format_tables(self.get_tables(), table_center_templ, table_templ)})


class Notifier(object):
    def __init__(self, api, start, end, stat, deploy_name, notify_users):
        self.api = api
        self.start = start
        self.end = end
        self.stat = stat
        self.deploy_name = deploy_name
        self.notify_users = notify_users

    def send_notifications(self, template, table_templ, table_center_templ):
        if not self.notify_users:
            return
        self.api.create_notification(EMAIL_SUBJECT % self.deploy_name,
                                     self.build_text(template, table_templ, table_center_templ),
                                     self.notify_users[0],
                                     copy_users=self.notify_users[1:] if len(self.notify_users) > 0 else None)

    def build_text(self, template, table_templ, table_center_templ):
        stat_str = self.stat.get_object_str(self.start, self.end, self.deploy_name,
                                            template, table_templ, table_center_templ)
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

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory, connection_timeout=120)
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

        users = [u for u in all_users if not u.get('blocked')]
        if len(users) > 0:
            logger.info('{} User(s) collected.'.format(len(users)))
            logger.info('Collecting and sending statistics...')
            _send_users_stat(api, logger, from_date, to_date, users, template_path)
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


def _send_users_stat(api, logger, from_date, to_date, users, template_path):
    capabilities = _get_capabilities(api)

    platform_usage_costs = _get_platform_usage_cost(api, from_date, to_date)
    logger.info('Platform Usage costs: {}.'.format(platform_usage_costs))

    # template, table_templ, table_center_templ = _get_templates(template_path)

    for user in users:
        _user_name = user.get('userName')
        logger.info('User: {}'.format(_user_name))
        stat = _get_statistics(api, capabilities, logger, platform_usage_costs, from_date, to_date,
                               _user_name, user.get('id'))
        notifier = Notifier(api, from_date, to_date, stat, os.getenv('CP_DEPLOY_NAME', 'Cloud Pipeline'), [_user_name])
        notifier.send_notifications(template, table_templ, table_center_templ)


def _get_statistics(api, capabilities, logger, platform_usage_costs, from_date, to_date, user, user_id):
    from_date_time = "{} 00:00:00.000".format(from_date)
    to_date_time = "{} 23:59:59.999".format(to_date)
    runs = _get_runs(api, from_date, to_date, user)
    storage_requests = _get_storage_requests(api, from_date_time, to_date_time, user_id)
    storage_write = storage_requests.get('writeRequests')
    logger.info('Storage write requests count: {}.'.format(storage_write))
    storage_read = storage_requests.get('readRequests')
    logger.info('Storage read requests count: {}.'.format(storage_read))
    compute_hours = _get_compute_hours(runs)
    logger.info('Compute hours: {}.'.format(compute_hours))
    cpu_hours = _get_compute_hours_by_type(runs, TYPE_CPU)
    logger.info('CPU Compute hours: {}.'.format(cpu_hours))
    gpu_hours = _get_compute_hours_by_type(runs, TYPE_GPU)
    logger.info('GPU Compute hours: {}.'.format(gpu_hours))
    runs_count = _get_runs_count(runs)
    logger.info('Runs count: {}.'.format(runs_count))
    usage_costs = _get_usage_costs(runs)
    logger.info('Usage costs: {}.'.format(usage_costs))
    usage_weight = usage_costs / platform_usage_costs
    logger.info('Usage weight: {}.'.format(usage_weight))
    clusters_compute_secs = _get_cluster_compute_secs(api, from_date_time, to_date_time, user)
    logger.info('Clusters compute seconds: {}.'.format(clusters_compute_secs))
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
    top3_used_buckets = _get_used_buckets(api, from_date_time, to_date_time, user_id)
    logger.info('Top 3 Used buckets: {}.'.format(top3_used_buckets))
    top3_run_capabilities = _get_top3_run_capabilities(api, from_date_time, to_date_time, user, capabilities)
    logger.info('Top 3 Capabilities: {}.'.format(top3_run_capabilities))
    return Stat(compute_hours, runs_count, storage_read, storage_write, usage_weight, login_time, cpu_hours,
                gpu_hours, clusters_compute_secs, worker_nodes_count,
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


def _get_storage_requests(api, from_date_time, to_date_time, user_id):
    body = {
        "fromDate": from_date_time,
        "toDate": to_date_time,
        "groupBy": "user_id",
        "userId": int(user_id)
    }
    result = api.load_storage_requests(body)
    if not result:
        return {}
    else:
        return result


def _get_storage_usage(api, from_date_time, to_date_time, user, usage):
    filter = {'messageTimestampFrom': from_date_time,
              'messageTimestampTo': to_date_time,
              'types': ['audit'],
              'users': [user],
              'message': usage}
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
            capabilities_dict[c] = total_time
    return sorted(capabilities_dict.items(), key=lambda item: float(item[1]), reverse=True)[:3]


def _get_cluster_compute_secs(api, from_date, to_date, user):
    runs = api.filter_runs_all(from_date, to_date, user, {'masterRun': True})
    total_time = 0
    for r in runs:
        start_date = datetime.strptime(r.get('startDate'), DATE_TIME_FORMAT)
        end_date = datetime.strptime(r.get('endDate'), DATE_TIME_FORMAT)
        total_time += (end_date - start_date).total_seconds()
    return total_time


def _get_worker_nodes_count(api, from_date, to_date, user):
    return api.run_count(from_date, to_date, user, {'workerRun': True})


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
    return sum([float(r.get(COST)) for r in runs])


def _get_runs_count(runs):
    return len(runs)


def _get_compute_hours(runs):
    durations = [float(r.get(DURATION_HOURS)) for r in runs]
    return sum(durations)


def _get_compute_hours_by_type(runs, type):
    durations = [float(r.get(DURATION_HOURS)) for r in runs if r.get(TYPE) == type]
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


def _get_used_buckets(api, from_date_time, to_date_time, user_id):
    body = {
        "fromDate": from_date_time,
        "toDate": to_date_time,
        "maxEntries": 3,
        "sorting": {
            "field": "total_requests",
            "order": "DESC"
        },
        "userId": int(user_id)
    }

    requests_by_storage = api.load_storage_requests(body)
    top3_storages = list()
    if 'statistics' not in requests_by_storage:
        return top3_storages
    for storage in requests_by_storage.get('statistics'):
        storage_name = storage.get('storageName') if 'storageName' in storage else 'Deleted (%d)' % storage.get('storageId')
        top3_storages.append((storage_name, storage.get('totalRequests')))
    return top3_storages


def _get_templates(templ_path):
    with open(os.path.join(templ_path, STATISTICS_TEMPLATE), 'r') as file:
        template = file.read()
    with open(os.path.join(templ_path, TABLE_TEMPLATE), 'r') as file:
        table_templ = file.read()
    with open(os.path.join(templ_path, TABLE_CENTER_TEMPLATE), 'r') as file:
        table_center_templ = file.read()
    return template, table_templ, table_center_templ


if __name__ == '__main__':
    send_statistics()
