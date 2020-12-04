# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import sys

import click
import os
from src.api.cluster import Cluster
from src.utilities import date_utilities

from src.api.pipeline_run import PipelineRun


OUTPUT_FILE_DATE_FORMAT = '%Y-%m-%dT%H:%M:%S'


class ClusterMonitoringManager:

    def __init__(self):
        pass

    @classmethod
    def generate_report(cls, instance_id, run_id, output, raw_from_date, raw_to_date, interval):
        if not instance_id and not run_id:
            click.echo("One of '--instance-id' or '--run-id' options shall be specified", err=True)
            sys.exit(1)
        if instance_id and run_id:
            click.echo("Both options '--instance-id' and '--run-id' cannot be set together", err=True)
            sys.exit(1)
        if not instance_id:
            run = PipelineRun.get(run_id)
            if not run:
                click.echo("Pipeline run '%d' cannot be found" % run_id, err=True)
                sys.exit(1)
            instance_id = dict(run.instance)['nodeName']
            if not instance_id:
                click.echo("Instance ID cannot be found for run '%d'" % run_id, err=True)
                sys.exit(1)
        if not interval:
            interval = '1m'
        date_to = date_utilities.now() if not raw_to_date else date_utilities.format_date(raw_to_date)
        date_from = date_utilities.minus_day(date_to) if not raw_from_date \
            else date_utilities.format_date(raw_from_date)
        output_path = cls._build_output_path(output, run_id, instance_id, date_from, date_to, interval)
        Cluster.download_usage_report(instance_id, date_from, date_to,
                                      cls._convert_to_duration_format(interval), output_path)
        click.echo("Usage report downloaded to '%s'" % output_path)

    @staticmethod
    def _build_output_path(output, run_id, instance_id, date_from, date_to, interval):
        if not output:
            output_name = ClusterMonitoringManager._build_output_file_name(run_id, instance_id, date_from, date_to,
                                                                           interval)
            return os.path.abspath(output_name)
        output_path = os.path.abspath(output)
        if output.endswith(os.path.sep) and not os.path.exists(output_path):
            os.makedirs(output_path)
        if output.endswith(os.path.sep) or os.path.isdir(output_path):
            output_name = ClusterMonitoringManager._build_output_file_name(run_id, instance_id, date_from, date_to,
                                                                           interval)
            return os.path.join(output_path, output_name)
        output_name = os.path.basename(output_path)
        folder_path = output_path.rstrip(output_name)
        if not os.path.exists(folder_path):
            os.makedirs(folder_path)
        return output_path

    @staticmethod
    def _build_output_file_name(run_id, instance_id, date_from, date_to, interval):
        instance_indicator = str(run_id or instance_id)
        return "cluster_monitor_%s_%s_%s_%s.csv" % (
            instance_indicator,
            date_utilities.format_date(date_from, OUTPUT_FILE_DATE_FORMAT),
            date_utilities.format_date(date_to, OUTPUT_FILE_DATE_FORMAT),
            interval)

    @staticmethod
    def _convert_to_duration_format(raw_interval):
        return "PT{}".format(raw_interval).upper()
