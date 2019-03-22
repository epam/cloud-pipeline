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
from internal.synchronization.synchronization import Synchronization
from exceptions import KeyboardInterrupt


def configure(argv):
    opts, args = getopt.getopt(
        argv,
        "a:k:p:u:n:",
        ["api=", "key=", "users-root=", "nfs-root="]
    )
    api = None
    key = None
    users_root = None
    nfs_root = None
    for opt, arg in opts:
        if opt in ("-a", "--api"):
            api = arg
        elif opt in ("-k", "--key"):
            key = arg
        elif opt in ("-u", "--users-root"):
            users_root = arg
        elif opt in ("-n", "--nfs-root"):
            nfs_root = arg
    Config.store(key, api, users_root, nfs_root)
    print 'syncnfs configuration updated'
    exit(0)


def help():
    print 'Use \'configure\' command to setup synchronization properties and settings:'
    print 'python syncnfs.py configure ' \
          '--api=<api path> ' \
          '--key=<api token> ' \
          '--users-root=<root folder for users storages links> ' \
          '--nfs-root=<root folder for nfs mounts>'
    print ''
    print 'Use \'sync\' command to synchronize users nfs permissions.'
    print 'python syncnfs.py sync' \
          '--api=<api path> ' \
          '--key=<api token> ' \
          '--users-root=<root folder for users storages links> ' \
          '--nfs-root=<root folder for nfs mounts>' \
          '-l[use symlinks instead of mounting directories]'
    print ''


def main(argv):
    if len(argv) > 0:
        command = argv[0]
        if command == 'help' or command == '-h' or command == '--help':
            help()
        elif command == 'configure':
            configure(argv[1:])
        elif command == 'sync':
            user = []
            config = Config(safe_initialization=True)
            symlink = False
            try:
                opts, args = getopt.getopt(
                    argv[1:],
                    "a:k:p:u:n:s:l",
                    ["api=", "key=", "users-root=", "nfs-root=", "user="]
                )
                for opt, arg in opts:
                    if opt in ("-a", "--api"):
                        config.api = arg
                    elif opt in ("-k", "--key"):
                        config.access_key = arg
                    elif opt in ("-u", "--users-root"):
                        config.users_root = arg
                    elif opt in ("-n", "--nfs-root"):
                        config.nfs_root = arg
                    elif opt in ("-s", "--user"):
                        user = [arg]
                    elif opt == '-l':
                        symlink = True
                if config.api is None:
                    print 'API path is not configured'
                    help()
                    exit(1)
                elif config.access_key is None:
                    print 'API token is not configured'
                    help()
                    exit(1)
                elif config.users_root is None:
                    print 'Users root is not configured'
                    help()
                    exit(1)
                elif config.nfs_root is None:
                    print 'Nfs root is not configured'
                    help()
                    exit(1)
            except ConfigNotFoundError as error:
                print error.message
                help()
                exit(1)
            start = time.time()
            try:
                Synchronization(config).synchronize(user_ids=user, use_symlinks=symlink)
            except KeyboardInterrupt:
                exit(2)
            print ''
            print 'Synchronization time: {} seconds'.format(time.time() - start)
        else:
            print 'Unknown command {}'.format(command)
            exit(1)
    else:
        help()
        exit(0)

if __name__ == "__main__":
    main(sys.argv[1:])
