# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import getopt
import sys
import time
from internal.config import Config, ConfigNotFoundError
from internal.synchronization.pipeline_server import PipelineServer
from exceptions import KeyboardInterrupt


def configure(argv):
    opts, args = getopt.getopt(
        argv,
        "a:k:p:",
        ["api=", "key=", "proxy=", "email-attribute=", "name-attribute=", "admins-group=", "git-group-prefix="]
    )
    api = None
    key = None
    proxy = None
    email = None
    name = None
    admins_group = None
    git_group_prefix = None
    for opt, arg in opts:
        if opt in ("-a", "--api"):
            api = arg
        elif opt in ("-k", "--key"):
            key = arg
        elif opt in ("-p", "--proxy"):
            proxy = arg
        elif opt == "--email-attribute":
            email = arg
        elif opt == "--name-attribute":
            name = arg
        elif opt == "--admins-group":
            admins_group = arg
        elif opt == "--git-group-prefix":
            git_group_prefix = arg
    Config.store(key, api, proxy, email, name, admins_group, git_group_prefix)
    print 'syncgit configuration updated'
    exit(0)


def help():
    print 'Use \'configure\' command to setup synchronization properties and settings:'
    print 'python syncgit.py configure ' \
          '--api=<api path> ' \
          '--key=<api token> ' \
          '--email-attribute=<attribute name for \'email\' field, case sensitive, default - \'Email\'> ' \
          '--name-attribute=<attribute name for \'name\' field, case sensitive, default - \'Name\'> ' \
          '--admins-group=<administrators group name, defualt - \'ROLE_ADMIN\'> ' \
          '--git-group-prefix=<prefix for group names, default - \'PIPELINE-\'>'
    print ''
    print 'Use \'sync\' command to synchronize git users, groups and project members.'
    print 'python syncgit.py sync'
    print ''
    print 'Use \'purge\' command to remove git users, groups and project members.'
    print 'python syncgit.py purge'
    print ''


def main(argv):
    if len(argv) > 0:
        command = argv[0]
        if command == 'help' or command == '-h' or command == '--help':
            help()
        elif command == 'configure':
            configure(argv[1:])
        elif command == 'sync':
            try:
                config = Config.instance()
                if config.api is None:
                    print 'API path is not configured'
                    help()
                    exit(1)
                elif config.access_key is None:
                    print 'API token is not configured'
                    help()
                    exit(1)
            except ConfigNotFoundError as error:
                print error.message
                help()
                exit(1)
            start = time.time()
            pipeline_server = PipelineServer()
            try:
                pipeline_server.synchronize(map(lambda pipeline_id: int(pipeline_id), argv[1:]))
            except KeyboardInterrupt:
                exit(2)
            print ''
            print 'Synchronization time: {} seconds'.format(time.time() - start)
        elif command == 'purge':
            try:
                config = Config.instance()
                if config.api is None:
                    print 'API path is not configured'
                    help()
                    exit(1)
                elif config.access_key is None:
                    print 'API token is not configured'
                    help()
                    exit(1)
            except ConfigNotFoundError as error:
                print error.message
                help()
                exit(1)
            choice = ''
            while choice not in ('y', 'n'):
                sys.stdout.write('This command will remove all users (except root) and generated groups for git server.'
                                 ' Are you sure? y/n: ')
                choice = raw_input().lower()
                if choice == 'n':
                    sys.exit()
            pipeline_server = PipelineServer()
            for server in pipeline_server.get_distinct_git_servers():
                server.clear_users_and_groups()
            print ''
        else:
            print 'Unknown command {}'.format(command)
            exit(1)

if __name__ == "__main__":
    main(sys.argv[1:])
