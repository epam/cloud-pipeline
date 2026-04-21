# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

from abc import abstractmethod, ABCMeta
import click
from prettytable import prettytable

from src.model.pipeline_run_model import PriceType
from src.utilities import state_utilities


def _to_int_value(value):
    if value is None:
        return None
    if str(value).isdigit():
        return int(value)
    return value


class PrintService(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def flush(self):
        pass

    @abstractmethod
    def error(self, message, err=False, buf=False):
        pass

    @abstractmethod
    def empty_runs(self, run_filter=None):
        pass

    @abstractmethod
    def runs(self, run_filter):
        pass

    @abstractmethod
    def run(self, run_model, run_model_price):
        pass

    @abstractmethod
    def node_details(self, run_model):
        pass

    @abstractmethod
    def run_parameters(self, run_model):
        pass

    @abstractmethod
    def run_tasks(self, run_model):
        pass

    @abstractmethod
    def run_tags(self, run_model):
        pass

    @abstractmethod
    def launch_run_id(self, run_id):
        pass

    @abstractmethod
    def launch_run_status(self, run_status):
        pass

    @abstractmethod
    def launch_run_parameters(self, parameters):
        pass


class PrettyTablePrintService(PrintService):

    @staticmethod
    def _echo_title(title, line=True):
        click.echo(title)
        if line:
            for i in title:
                click.echo('-', nl=False)
            click.echo('')

    def flush(self):
        # not supported for text table
        pass

    def error(self, message, err=False, buf=False):
        click.echo(message, err=err)

    def empty_runs(self, run_filter=None):
        click.echo('No data is available for the request')

    def runs(self, run_filter):
        if run_filter.total_count > run_filter.page_size:
            click.echo('Showing {} results from {}:'.format(run_filter.page_size, run_filter.total_count))
        runs_table = prettytable.PrettyTable()
        runs_table.field_names = ["RunID", "Parent RunID", "Pipeline", "Version", "Status", "Started", "Owner"]
        runs_table.align = "r"
        for run_model in run_filter.elements:
            runs_table.add_row([run_model.identifier,
                                run_model.parent_id,
                                run_model.pipeline,
                                run_model.version,
                                state_utilities.color_state(run_model.status),
                                run_model.scheduled_date,
                                run_model.owner])
        click.echo(runs_table)
        click.echo()

    def run(self, run_model, run_model_price):
        run_main_info_table = prettytable.PrettyTable()
        run_main_info_table.field_names = ["key", "value"]
        run_main_info_table.align = "l"
        run_main_info_table.set_style(12)
        run_main_info_table.header = False
        run_main_info_table.add_row(['ID:', run_model.identifier])
        run_main_info_table.add_row(['Pipeline:', run_model.pipeline])
        run_main_info_table.add_row(['Version:', run_model.version])
        if run_model.owner is not None:
            run_main_info_table.add_row(['Owner:', run_model.owner])
        if run_model.endpoints is not None and len(run_model.endpoints) > 0:
            endpoint_index = 0
            for endpoint in run_model.endpoints:
                if endpoint_index == 0:
                    run_main_info_table.add_row(['Endpoints:', endpoint])
                else:
                    run_main_info_table.add_row(['', endpoint])
                endpoint_index = endpoint_index + 1
        if not run_model.scheduled_date:
            run_main_info_table.add_row(['Scheduled', 'N/A'])
        else:
            run_main_info_table.add_row(['Scheduled:', run_model.scheduled_date])
        if not run_model.start_date:
            run_main_info_table.add_row(['Started', 'N/A'])
        else:
            run_main_info_table.add_row(['Started:', run_model.start_date])
        if not run_model.end_date:
            run_main_info_table.add_row(['Completed', 'N/A'])
        else:
            run_main_info_table.add_row(['Completed:', run_model.end_date])
        run_main_info_table.add_row(['Status:', state_utilities.color_state(run_model.status)])
        run_main_info_table.add_row(['ParentID:', run_model.parent_id])
        if run_model_price.total_price > 0:
            run_main_info_table.add_row(['Estimated price:', '{} $'.format(round(run_model_price.total_price, 2))])
        else:
            run_main_info_table.add_row(['Estimated price:', 'N/A'])

        run_main_info_table.add_row(['Tags:', run_model.tags_str])

        click.echo(run_main_info_table)
        click.echo()

    def node_details(self, run_model):
        node_details_table = prettytable.PrettyTable()
        node_details_table.field_names = ["key", "value"]
        node_details_table.align = "l"
        node_details_table.set_style(12)
        node_details_table.header = False

        for key, value in run_model.instance:
            if key == PriceType.SPOT:
                node_details_table.add_row(['price-type', PriceType.SPOT if value else PriceType.ON_DEMAND])
            else:
                node_details_table.add_row([key, value])
        self._echo_title('Node details:')
        click.echo(node_details_table)
        click.echo()

    def run_parameters(self, run_model):
        self._echo_title('Parameters:')
        if len(run_model.parameters) > 0:
            for parameter in run_model.parameters:
                click.echo('{}={}'.format(parameter.name, parameter.value))
        else:
            click.echo('No parameters are configured')
        click.echo()

    def run_tasks(self, run_model):
        self._echo_title('Tasks:', line=False)
        if len(run_model.tasks) > 0:
            tasks_table = prettytable.PrettyTable()
            tasks_table.field_names = ['Task', 'State', 'Scheduled', 'Started', 'Finished']
            tasks_table.align = "r"
            for task in run_model.tasks:
                scheduled = 'N/A'
                started = 'N/A'
                finished = 'N/A'
                if task.created is not None:
                    scheduled = task.created
                if task.started is not None:
                    started = task.started
                if task.finished is not None:
                    finished = task.finished
                tasks_table.add_row(
                    [task.name, state_utilities.color_state(task.status), scheduled, started, finished])
            click.echo(tasks_table)
        else:
            click.echo('No tasks are available for the run')
        click.echo()

    def run_tags(self, run_model):
        self._echo_title('Tags:')
        if len(run_model.tags) > 0:
            for tag_name in run_model.tags:
                click.echo('{}={}'.format(tag_name, run_model.tags[tag_name]))
        else:
            click.echo('No tags are configured')
        click.echo()

    def launch_run_id(self, run_id):
        click.echo(run_id)

    def launch_run_status(self, run_status):
        click.echo(run_status)

    def launch_run_parameters(self, parameters):
        for parameter in parameters:
            if parameter.required:
                click.echo('* --{}'.format(parameter.name))
            else:
                click.echo('  --{} ({})'.format(parameter.name, parameter.parameter_type))
            if parameter.value is not None:
                click.echo('    Default: {}'.format(parameter.value))
            click.echo()


class JsonPrintService(PrintService):

    def __init__(self):
        self.__buffer = {}

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    def flush(self):
        if self.__buffer:
            click.echo(self._to_json(self.__buffer))

    def launch_run_id(self, run_id):
        self.__buffer['runId'] = _to_int_value(run_id)

    def launch_run_status(self, status):
        self.__buffer['status'] = status

    def launch_run_parameters(self, parameters):
        run_params = []
        for parameter in parameters:
            run_param = {
                'name': parameter.name,
                'type': parameter.parameter_type,
                'required': parameter.required
            }
            if parameter.value is not None:
                run_param['value'] = parameter.value
            run_params.append(run_param)
        click.echo(self._to_json(run_params))

    def error(self, message, err=False, buf=False):
        if buf:
            if not 'error' in self.__buffer:
                self.__buffer['error'] = [message]
            else:
                self.__buffer['error'].append(message)
        click.echo(self._to_json({'error': message}), err=err)

    def empty_runs(self, run_filter=None):
        payload = {
            'totalCount': 0,
            'runs': [],
        }
        if run_filter is not None:
            payload['page'] = run_filter.page
            payload['pageSize'] = run_filter.page_size
        click.echo(self._to_json(payload))

    def runs(self, run_filter):
        runs = []
        for run_model in run_filter.elements:
            runs.append({
                'runId': run_model.identifier,
                'parentRunId': _to_int_value(run_model.parent_id),
                'pipeline': run_model.pipeline,
                'version': run_model.version,
                'status': run_model.status,
                'scheduled': run_model.scheduled_date,
                'owner': run_model.owner,
            })
        payload = {
            'totalCount': run_filter.total_count,
            'page': run_filter.page,
            'pageSize': run_filter.page_size,
            'truncated': run_filter.total_count > run_filter.page_size,
            'runs': runs,
        }
        click.echo(self._to_json(payload))

    def _node_instance_dict(self, run_model):
        node = {}
        instance = run_model.instance
        if not instance:
            return node
        for key, value in instance:
            if key == PriceType.SPOT:
                node['price-type'] = PriceType.SPOT if value else PriceType.ON_DEMAND
            else:
                node[key] = value
        return node

    def run(self, run_model, run_model_price):
        total_price = run_model_price.total_price
        estimated = None
        if total_price is not None and total_price > 0:
            estimated = round(total_price, 2)
        self.__buffer = {
            'id': run_model.identifier,
            'pipeline': run_model.pipeline,
            'version': run_model.version,
            'owner': run_model.owner,
            'endpoints': list(run_model.endpoints) if run_model.endpoints else [],
            'scheduled': run_model.scheduled_date,
            'started': run_model.start_date,
            'completed': run_model.end_date,
            'status': run_model.status,
            'parentId': _to_int_value(run_model.parent_id),
            'estimatedPrice': estimated,
            'tagsSummary': run_model.tags_str,
        }

    def node_details(self, run_model):
        self.__buffer['nodeDetails'] = self._node_instance_dict(run_model)

    def run_parameters(self, run_model):
        self.__buffer['parameters'] = [
            {'name': parameter.name, 'value': parameter.value}
            for parameter in run_model.parameters
        ]

    def run_tasks(self, run_model):
        tasks = []
        for task in run_model.tasks:
            tasks.append({
                'name': task.name,
                'status': task.status,
                'scheduled': task.created,
                'started': task.started,
                'finished': task.finished,
            })
        self.__buffer['tasks'] = tasks

    def run_tags(self, run_model):
        self.__buffer['tags'] = dict(run_model.tags)


def create_print_service(output):
    if output == 'json':
        return JsonPrintService()
    return PrettyTablePrintService()
