# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import os
import subprocess
import sys

import click
import dateutil.parser

from src.config import Config
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.utilities.extension.ext_handler import ExtensionHandler, ExtensionApplicationRule
from src.utilities.printing.storage import print_storage_items, init_items_table

PIPE_OMICS_JUST_PRINT_MESSAGE_ERROR_CODE = 15


class OmicsFileOperationHandler(ExtensionHandler):

    def __init__(self, command_group, command, rules):
        ExtensionHandler.__init__(self, command_group, command, rules)

    def _apply(self, arguments):
        pipe_config = Config.instance()
        pipe_omics_bin_path = os.path.join(pipe_config.build_inner_module_path('pipe-omics'), 'pipe-omics')
        cmd_args = [
            pipe_omics_bin_path, '-g', self.command_group, '-c', self.command, '-i', json.dumps(arguments), '-p'
        ]
        process = subprocess.Popen(
            cmd_args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=self._configure_envs(pipe_config)
        )
        quiet = arguments.get("quiet", False)
        self._process_output(process, quiet, arguments, cmd_args)
        return_code = process.wait()
        if return_code != 0 and not quiet:
            if return_code != PIPE_OMICS_JUST_PRINT_MESSAGE_ERROR_CODE:
                click.echo("There was a problem of executing the command '{}'".format(cmd_args), file=sys.stderr)
            while True:
                stderr_line = process.stderr.readline()
                if not stderr_line:
                    break
                click.echo(stderr_line, file=sys.stderr)
            process.stderr.close()

    def _process_output(self, process, quiet, arguments, cmd):
        pass

    def _configure_envs(self, pipe_config):
        envs = os.environ.copy()
        if "API" not in envs:
            envs["API"] = pipe_config.api
        if "API_TOKEN" not in envs:
            envs["API_TOKEN"] = pipe_config.get_token()
        return envs


class OmicsCopyFileHandler(OmicsFileOperationHandler):

    def __init__(self):
        OmicsFileOperationHandler.__init__(
            self, "storage", "cp",
            [
                ExtensionApplicationRule("source", "omics://.*", ExtensionApplicationRule.REGEXP),
                ExtensionApplicationRule("destination", "omics://.*", ExtensionApplicationRule.REGEXP)
            ]
        )

    def _process_output(self, process, quiet, arguments, cmd):
        if not quiet:
            while True:
                stdout_line = process.stdout.readline()
                if not stdout_line:
                    break
                if not isinstance(stdout_line, str):
                    stdout_line = stdout_line.decode("utf-8")
                if "uploaded!" in stdout_line or "downloaded!" in stdout_line or "started!" in stdout_line or "initiated!" in stdout_line:
                    click.echo('\n' + stdout_line.strip())
                else:
                    # prints line and returns carriage to the start of the line
                    # in order to correctly show the progressBar in the terminal
                    click.echo(stdout_line.strip() + '\r', nl=False)
        else:
            dev_null = open(os.devnull, 'w')
            dev_null.writelines(process.stdout.readlines())
        process.stdout.close()


class OmicsListFilesHandler(OmicsFileOperationHandler):

    def __init__(self):
        OmicsFileOperationHandler.__init__(
            self, "storage", "ls",
            [ExtensionApplicationRule("path", "omics://.*", ExtensionApplicationRule.REGEXP)]
        )

    def _process_output(self, process, quiet, arguments, cmd):
        show_details = arguments.get('show_details', False)

        if show_details:
            fields = ["Type", "Labels", "Modified", "Size", "Name"]
        else:
            fields = []

        output = "".join([o if isinstance(o, str) else o.decode("utf-8") for o in process.stdout.readlines()])
        if output:
            listing = json.loads(output)
            items_table = init_items_table(fields)
            items = [self.__get_file_object(item) for item in listing]
            print_storage_items(None, items, show_details, items_table, False, False)

    def __get_file_object(self, file):
        item = DataStorageItemModel()
        item.type = file['type']
        item.name = file['path']
        if 'size' in file:
            item.size = file['size']
        item.path = file['path']
        item.changed = dateutil.parser.parse(file['changed']).astimezone(Config.instance().timezone())
        item.labels = [DataStorageItemLabelModel(label, value) for label, value in file.get('labels', {}).items()]
        if 'name' in file:
            item.labels.append(DataStorageItemLabelModel("name", file['name']))
        return item
