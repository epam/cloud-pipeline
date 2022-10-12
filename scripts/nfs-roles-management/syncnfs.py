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
import logging
import os
import sys
import time
from logging.handlers import TimedRotatingFileHandler

from internal.config import Config, ConfigNotFoundError
from internal.model.mask import FullMask
from internal.synchronization.synchronization import Synchronization


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
    logging.info('syncnfs configuration updated')
    exit(0)


def help():
    logging.info('Use \'configure\' command to setup synchronization properties and settings:')
    logging.info('python syncnfs.py configure '
                 '--api=<api path> '
                 '--key=<api token> '
                 '--users-root=<root folder for users storages links> '
                 '--nfs-root=<root folder for nfs mounts>')
    logging.info('')
    logging.info('Use \'sync\' command to synchronize users nfs permissions.')
    logging.info('python syncnfs.py sync'
                 '--api=<api path> '
                 '--key=<api token> '
                 '--users-root=<root folder for users storages links> '
                 '--nfs-root=<root folder for nfs mounts>'
                 '-l[use symlinks instead of mounting directories]')
    logging.info('')


def main(argv):
    logging_format = os.getenv('CP_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_level = os.getenv('CP_LOGGING_LEVEL', 'INFO')
    logging_file = os.getenv('CP_LOGGING_FILE', 'sync-nfs.log')
    logging_history = int(os.getenv('CP_LOGGING_HISTORY', default='30'))

    logging_formatter = logging.Formatter(logging_format)

    logging.getLogger().setLevel(logging_level)

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(console_handler)

    file_handler = logging.handlers.TimedRotatingFileHandler(logging_file, when='D', interval=1,
                                                             backupCount=logging_history)
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(file_handler)

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
                    logging.info('API path is not configured')
                    help()
                    exit(1)
                elif config.access_key is None:
                    logging.info('API token is not configured')
                    help()
                    exit(1)
                elif config.users_root is None:
                    logging.info('Users root is not configured')
                    help()
                    exit(1)
                elif config.nfs_root is None:
                    logging.info('Nfs root is not configured')
                    help()
                    exit(1)
            except ConfigNotFoundError:
                logging.exception('Configuration has not been found.')
                help()
                exit(1)
            filter_mask = int(os.getenv('CP_DAV_FILTER_MASK', FullMask.READ))
            logging.info('Storages with {} permissions will be synchronized...'
                         .format(FullMask.as_string(filter_mask) or 'any'))
            start = time.time()
            try:
                Synchronization(config).synchronize(user_ids=user, use_symlinks=symlink, filter_mask=filter_mask)
            except KeyboardInterrupt:
                exit(2)
            logging.info('')
            logging.info('Synchronization time: {:.1f} seconds'.format(time.time() - start))
        else:
            logging.error('Unknown command {}'.format(command))
            exit(1)
    else:
        help()
        exit(0)


if __name__ == "__main__":
    main(sys.argv[1:])
