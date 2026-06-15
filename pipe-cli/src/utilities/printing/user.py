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

import json
from abc import ABCMeta, abstractmethod

import click
from prettytable import prettytable


class UserInstancesPrintService:

    __metaclass__ = ABCMeta

    @abstractmethod
    def print_no_limits(self, username, active_runs_count):
        """Report that no instance limits are configured for the user.

        :param username: the name of the logged user.
        :param active_runs_count: number of runs in RUNNING or RESUMING state.
        """
        pass

    @abstractmethod
    def print_single_limits(self, username, active_runs_count, source, value):
        """Report aa single active instance limits applied to the user.

        :param username: the name of the logged user.
        :param active_runs_count: number of runs in RUNNING or RESUMING state.
        :param source: source to display.
        :param value: value to display.
        """
        pass

    @abstractmethod
    def print_limits(self, username, active_runs_count, limits_dict):
        """Report active instance limits applied to the user.

        :param username: the name of the logged user.
        :param active_runs_count: number of runs in RUNNING or RESUMING state.
        :param limits_dict: ordered mapping of ``source -> value`` to display.
        """
        pass


class PrettyTableUserInstancesPrintService(UserInstancesPrintService):

    @staticmethod
    def _print_active_runs(username, active_runs_count):
        click.echo('Active runs detected for a user: [{}: {}]'.format(username, active_runs_count))

    def print_no_limits(self, username, active_runs_count):
        self._print_active_runs(username, active_runs_count)
        click.echo('No restrictions on runs launching configured')

    def print_single_limits(self, username, active_runs_count, source, value):
        self._print_active_runs(username, active_runs_count)
        click.echo('The following restriction applied on runs launching: [{}: {}]'.format(source, value))

    def print_limits(self, username, active_runs_count, limits_dict):
        self._print_active_runs(username, active_runs_count)
        click.echo('The following restrictions applied on runs launching:\n')
        table = prettytable.PrettyTable()
        table.field_names = ['Source', 'Value']
        table.align = 'l'
        for source, value in limits_dict.items():
            table.add_row([source, value])
        click.echo(table)


class JsonUserInstancesPrintService(UserInstancesPrintService):
    """JSON implementation of :class:`UserInstancesPrintService`.

    All methods emit a single JSON object to stdout. Schema::

        {
          "userName":       string,          // the name of the logged user
          "activeRunsCount": integer,        // number of currently active runs
          "instanceLimits": [                // empty array when no limits are configured
            {
              "source": string,             // limit origin, e.g. "USER" or group name
              "value":  integer             // maximum allowed concurrent runs
            }
          ]
        }

    Note: without ``-v`` / ``--verbose`` only the single most restrictive limit
    is included in ``instanceLimits``. Pass ``-v`` to receive all configured limits.
    """

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    @staticmethod
    def _to_dict(username, runs_count, limits):
        return {
            'userName': username,
            'activeRunsCount': runs_count,
            'instanceLimits': limits
        }

    def print_no_limits(self, username, active_runs_count):
        click.echo(self._to_json(self._to_dict(
            username=username,
            runs_count=active_runs_count,
            limits=[]
        )))

    def print_single_limits(self, username, active_runs_count, source, value):
        click.echo(self._to_json(self._to_dict(
            username=username,
            runs_count=active_runs_count,
            limits=[{'source': source, 'value': value}]
        )))

    def print_limits(self, username, active_runs_count, limits_dict):
        click.echo(self._to_json(self._to_dict(
            username=username,
            runs_count=active_runs_count,
            limits=[{'source': s, 'value': v} for s, v in limits_dict.items()]
        )))


def create_user_instances_print_service(output_format):
    if output_format == 'json':
        return JsonUserInstancesPrintService()
    return PrettyTableUserInstancesPrintService()
