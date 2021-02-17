# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import click
import sys


class CustomAbortHandlingGroup(click.Group):
    UNINTERRUPTIBLE_OPERATION_ABORT_MSG_TEMPLATE = 'Operation abortion... Note: the `{}` operation can\'t be stopped ' \
                                                   'and it will continue running in the background.'

    def __init__(self, name=None, commands=None, uninterruptible_cmd_list=None, **attrs):
        click.Group.__init__(self, name, commands, **attrs)
        if uninterruptible_cmd_list is None:
            self.not_standalone_cmd_list = []
        else:
            self.not_standalone_cmd_list = uninterruptible_cmd_list

    def __call__(self, *args, **kwargs):
        try:
            if len(args) > 0 and len(args[0]) > 0:
                command_name = args[0][0]
            else:
                command_name = None
            execute_in_standalone = command_name not in self.not_standalone_cmd_list
            try:
                return self.main(*args, standalone_mode=execute_in_standalone, **kwargs)
            except click.exceptions.Abort:
                click.echo(self.UNINTERRUPTIBLE_OPERATION_ABORT_MSG_TEMPLATE.format(command_name))
                sys.exit(0)
            except click.exceptions.ClickException as click_exception:
                click.echo('Error: {}'.format(str(click_exception)), err=True)
                sys.exit(click_exception.exit_code)
        except KeyboardInterrupt:
            sys.exit(1)
