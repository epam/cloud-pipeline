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

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger


DATE_FORMAT = "%Y-%m-%d"


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

STAT_PATTERN = '''
<p>
<b>Overall</b>
</p>
<p>
Compute hours: {compute_hours}<br>
Runs count: {runs_count}<br>
Usage costs($): {usage_costs}<br>
Login time(hours): {login_time}<br>
</p>
<p>
<b>Most-used</b>
</p>
<p>
Top 3 instance types:
{instance_types}
Top 3 pipelines:
{pipelines}
Top 3 tools:
{tools}
Top 3 owned buckets (by volume):
{owned_buckets}
Top 3 used buckets (by audit based requests):
{used_buckets}
</p>
'''


EMAIL_SUBJECT = '[%s]: Platform usage statistics'


def get_api_link(url):
    return url.rstrip('/').replace('/restapi', '')


class Stat(object):

    def __init__(self, compute_hours, runs_count, usage_costs, login_time, instance_types,
                 pipelines, tools, owned_buckets, used_buckets):
        self.compute_hours = compute_hours
        self.runs_count = runs_count
        self.usage_costs = usage_costs
        self.login_time = login_time
        self.instance_types = instance_types
        self.pipelines = pipelines
        self.tools = tools
        self.owned_buckets = owned_buckets
        self.used_buckets = used_buckets

    @staticmethod
    def format_top3(top):
        top3_str = ''
        for t in top:
            top3_str += '<li>{}</li>'.format(t)
        return '<ul>{}</ul>'.format(top3_str)

    def get_object_str(self):
        return STAT_PATTERN.format(**{'compute_hours': self.compute_hours,
                                      'runs_count': self.runs_count,
                                      'usage_costs': self.usage_costs,
                                      'login_time': self.login_time,
                                      'instance_types': self.format_top3(self.instance_types),
                                      'pipelines': self.format_top3(self.pipelines),
                                      'tools': self.format_top3(self.tools),
                                      'owned_buckets': self.format_top3(self.owned_buckets),
                                      'used_buckets': self.format_top3(self.used_buckets)})


class Notifier(object):

    def __init__(self, api, start, end, stat, deploy_name, notify_users):
        self.api = api
        self.start = start
        self.end = end
        self.stat = stat
        self.deploy_name = deploy_name
        self.notify_users = notify_users

    def send_notifications(self):
        if not self.notify_users:
            return
        self.api.create_notification(EMAIL_SUBJECT % self.deploy_name,
                                     self.build_text(),
                                     self.notify_users[0],
                                     copy_users=self.notify_users[1:] if len(
                                         self.notify_users) > 0 else None,
                                     )

    def build_text(self):
        stat_str = self.stat.get_object_str()
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
    parser.add_argument('--from_date', required=True)
    parser.add_argument('--to_date', required=True)
    parser.add_argument('--users', nargs='+', help='List of users separated by spaces', required=False)
    parser.add_argument('--roles', nargs='+', help='List of roles separated by spaces', required=False)
    args = parser.parse_args()

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
            send_users_stat(api, logger, from_date, to_date, users)
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


def send_users_stat(api, logger, from_date, to_date, users):
    pipeline_data = api.billing_export(from_date, to_date, users, ["PIPELINE"])
    pipeline_dict = _read_content(pipeline_data)

    tool_data = api.billing_export(from_date, to_date, users, ["TOOL"])
    tool_dict = _read_content(tool_data)

    storage_data = api.billing_export(from_date, to_date, users, ["STORAGE"])
    storage_dict = _read_content(storage_data)

    run_data = api.billing_export(from_date, to_date, users, ["RUN"])
    run_dict = _read_content(run_data, False)

    start_date_time = "{} 00:00:00.000".format(from_date)
    end_date_time = "{} 00:00:00.000".format(to_date)
    report_users = api.report_users(start_date_time, end_date_time, users)
    log_filter = api.log_filter(start_date_time, end_date_time, users)

    for user in users:
        logger.info('User: {}'.format(user))
        active_hours_list = [1 for d in report_users if \
                             d.get('activeUsers') is not None and user in d.get('activeUsers')]
        login_time = len(active_hours_list)
        logger.info('Login time: {}.'.format(login_time))
        compute_hours = _get_compute_hours(pipeline_dict, tool_dict, user)
        logger.info('Compute hours: {}.'.format(compute_hours))
        runs_count = _get_runs_count(pipeline_dict, tool_dict, user)
        logger.info('Runs count: {}.'.format(runs_count))
        usage_costs = _get_usage_costs(pipeline_dict, tool_dict, storage_dict, user)
        logger.info('Usage costs: {}.'.format(usage_costs))
        instance_types = _get_top3_instances(run_dict, user)
        logger.info('Top 3 Instance types: {}.'.format(instance_types))
        pipelines = _get_pipelines(pipeline_dict, user)
        logger.info('Top 3 Pipelines: {}.'.format(pipelines))
        tools = _get_tools(tool_dict, user)
        logger.info('Top 3 Tools: {}.'.format(tools))
        owned_buckets = _get_owned_buckets(storage_dict, user)
        logger.info('Top 3 Owned buckets: {}.'.format(owned_buckets))
        used_buckets = _get_used_buckets(log_filter, user)
        logger.info('Top 3 Used buckets: {}.'.format(used_buckets))
        stat = Stat(compute_hours, runs_count, usage_costs, login_time, instance_types, pipelines, tools,
                    owned_buckets, used_buckets)
        notifier = Notifier(api, from_date, to_date, stat, os.getenv('CP_DEPLOY_NAME', 'Cloud Pipeline'), [user])
        notifier.send_notifications()


def _read_content(content, skip_first_row=True):
    csv_content = io.BytesIO(content)
    if skip_first_row:
        next(csv_content)
    reader = csv.DictReader(csv_content)
    return list(reader)


def _get_usage_costs(pipeline_dict, tool_dict, storage_dict, user):
    pipeline_usage_costs = sum([float(pc.get('Cost ($)')) for pc in pipeline_dict if pc.get('Owner') == user])
    tool_usage_costs = sum([float(pc.get('Cost ($)')) for pc in tool_dict])
    storage_usage_costs = sum([float(pc.get('Cost ($)')) for pc in storage_dict])
    return pipeline_usage_costs + tool_usage_costs + storage_usage_costs


def _get_runs_count(pipeline_dict, tool_dict, user):
    pipeline_runs_count = sum([int(pc.get('Runs (count)')) for pc in pipeline_dict if pc.get('Owner') == user])
    tool_runs_count = sum([int(pc.get('Runs (count)')) for pc in tool_dict])
    return pipeline_runs_count + tool_runs_count


def _get_compute_hours(pipeline_dict, tool_dict, user):
    pipeline_compute_hours = sum([float(pc.get('Duration (hours)')) for pc in pipeline_dict if pc.get('Owner') == user])
    tool_compute_hours = sum([float(pc.get('Duration (hours)')) for pc in tool_dict])
    return pipeline_compute_hours + tool_compute_hours


def _get_top3_instances(run_dict, user):
    def key_func(k):
        return k['Instance']
    user_run_dict = [pc for pc in run_dict if pc.get('Owner') == user]
    run_dict_sorted = sorted(user_run_dict, key=key_func)
    instances = list()
    for key, group in itertools.groupby(run_dict_sorted, key_func):
        instances.append({"instance": key, "count": len(list(group))})
    instances_sorted = sorted(instances, key=lambda d: int(d['count']), reverse=True)
    return [pc.get("instance") for pc in instances_sorted[:3]]


def _get_used_buckets(log_filter, user):
    def key_func(k):
        return str(k['message'].split(' ')[1].split('://')[1].split('/')[0])
    user_log_filter = [pc for pc in log_filter if pc.get('user') is not None and pc.get('user') == user]
    log_dict_sorted = sorted(user_log_filter, key=key_func)
    storages = list()
    for key, group in itertools.groupby(log_dict_sorted, key_func):
        storages.append({"storage": key, "count": len(list(group))})
    storages_sorted = sorted(storages, key=lambda d: int(d['count']), reverse=True)
    return [pc.get("storage") for pc in storages_sorted[:3]]


def _get_pipelines(pipeline_dict, user):
    user_run_dict = [pc for pc in pipeline_dict if pc.get('Owner') == user]
    pipelines = sorted(user_run_dict, key=lambda d: int(d['Runs (count)']), reverse=True)
    return [pc.get("Pipeline") for pc in pipelines[:3]]


def _get_tools(tool_dict, user):
    user_tool_dict = [pc for pc in tool_dict if pc.get('Owner') == user]
    tools = sorted(user_tool_dict, key=lambda d: int(d['Runs (count)']), reverse=True)
    return [pc.get("Tool") for pc in tools[:3]]


def _get_owned_buckets(storage_dict, user):
    user_storage_dict = [pc for pc in storage_dict if pc.get('Owner') == user]
    used_buckets = sorted(user_storage_dict, key=lambda d: float(d['Current Volume (GB)']), reverse=True)
    return [pc.get("Storage") for pc in used_buckets[:3]]


if __name__ == '__main__':
    send_statistics()
