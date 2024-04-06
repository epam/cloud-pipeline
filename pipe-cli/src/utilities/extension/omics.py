import json
import os
import subprocess
import sys

import click
import dateutil.parser

from src.config import Config
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.utilities.extension.ext_handler import ExtensionHandler, ExtensionApplicationRule
from src.utilities.printing.storage import print_storage_listing


class OmicsFileOperationHandler(ExtensionHandler):

    def __init__(self, command_group, command, rules):
        ExtensionHandler.__init__(self, command_group, command, rules)

    def _apply(self, arguments):
        pipe_config = Config.instance()
        pipe_omics_bin_path = os.path.join(pipe_config.build_inner_module_path('pipe-omics'), 'pipe-omics')
        cmd_args = [pipe_omics_bin_path, '-g', self.command_group, '-c', self.command, '-i', json.dumps(arguments), '-p']
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
            click.echo("There was a problem of executing the command '{}'".format(cmd_args), file=sys.stderr)
            for stderr_line in iter(process.stderr.readline, ""):
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
            for stdout_line in iter(process.stdout.readline, ""):
                if "uploaded!" in stdout_line or "started!" in stdout_line or "initiated!" in stdout_line:
                    click.echo("\n" + stdout_line)
                else:
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

        output = "".join(process.stdout.readlines())
        if output:
            listing = json.loads(output)
            items = [self.__get_file_object(item) for item in listing]
            print_storage_listing(fields, items, None, show_details, False, False)

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
