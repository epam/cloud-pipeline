import json
import os
import subprocess
import sys

import click

from src.utilities.extension.handler import ExtensionHandler, ExtensionApplicationRule, ExtensionApplicationRuleType


class OmicsFileOperationHandler(ExtensionHandler):

    def __init__(self, command_group, command, rules):
        ExtensionHandler.__init__(self, command_group, command, rules)

    def _apply(self, arguments):
        cmd_args = ['pipe-omics', '-g', self.command_group, '-c', self.command, '-i', json.dumps(arguments)]
        process = subprocess.Popen(
            cmd_args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=os.environ.copy()
        )
        output, err = process.communicate()
        rc = process.returncode
        click.echo(output)
        if rc != 0:
            click.echo("There was a problem of executing the command '{}'".format(cmd_args), file=sys.stderr)
        click.echo(err, file=sys.stderr)


class OmicsCopyFileHandler(OmicsFileOperationHandler):

    def __init__(self):
        OmicsFileOperationHandler.__init__(
            self, "storage", "cp",
            [
                ExtensionApplicationRule("source", "omics://.*", ExtensionApplicationRuleType.REGEXP),
                ExtensionApplicationRule("destination", "omics://.*", ExtensionApplicationRuleType.REGEXP)
            ]
        )


class OmicsListFilesHandler(OmicsFileOperationHandler):

    def __init__(self):
        OmicsFileOperationHandler.__init__(
            self, "storage", "ls",
            [ExtensionApplicationRule("path", "omics://.*", ExtensionApplicationRuleType.REGEXP)]
        )

