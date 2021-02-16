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


class GroupWithConfigurableStandalone(click.Group):
    NOT_STANDALONE_COMMANDS = ['pause', 'resume']
    SYNC_OPERATION_DESCRIPTION_TEMPLATE = 'Operation abortion... Note: the {} operation can\'t be stopped ' \
                                          'and it will continue to run in the background.'

    def __call__(self, *args, **kwargs):
        command_name = args[0][0]
        execute_in_standalone = command_name not in self.NOT_STANDALONE_COMMANDS
        try:
            return self.main(*args, standalone_mode=execute_in_standalone, **kwargs)
        except click.exceptions.Abort:
            click.echo(self.SYNC_OPERATION_DESCRIPTION_TEMPLATE.format(command_name))
            sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(1)
