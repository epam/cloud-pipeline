# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Where the process starts: arguments, logging, signals.

Every setting can be given either as a command line option or as an environment variable, and the
option wins. That is not duplication for its own sake - the container passes everything through the
environment, as the rest of the platform's services do, while a developer running the service by hand
wants options and `--help`. The two names of a setting are listed together in the help text, so there
is one place to look.

The two that have no default are the API url and the token: there is nothing sensible to guess, and
guessing would mean a service that starts, polls nothing and looks healthy.
"""

from __future__ import annotations

import argparse
import os
import signal
import sys
from pathlib import Path

from .api import CloudPipelineApi
from .config import ServiceConfig
from .log import configure_logging, get_logger
from .orchestrator import Orchestrator
from .version import __version__

LOG = get_logger(__name__)

# Exit code for a missing or unusable setting. 22 is `EINVAL`, and it is what the platform's other
# service entry points use for the same situation - see `deploy/docker/cp-storage-lifecycle-service`.
EXIT_BAD_CONFIG = 22


def build_parser() -> argparse.ArgumentParser:
    """
    Declares the command line.

    `argparse` is the standard library's parser. Each `add_argument` call declares one option: the
    flag, where its value is stored, its type, its default and its help text. `--help` is generated
    from exactly this, so the help text is the documentation of the flag rather than a copy of it.

    `type=int` makes argparse convert and validate: a non-numeric value is rejected with a message
    naming the option, instead of failing much later with a type error.

    The two booleans use `argparse.BooleanOptionalAction`, which declares `--once` and `--no-once`
    together, rather than `action='store_true'`. With `store_true` a setting turned on through the
    environment could not be turned off on the command line - the flag would already be true and there
    would be no spelling for "no" - so `CP_SAS_ONCE=true` left behind in a container environment would
    silently win over every later run.

    Defaults are read from the environment here rather than in `ServiceConfig`, so that a value given
    on the command line simply overwrites the environment one with no precedence logic anywhere.
    """
    parser = argparse.ArgumentParser(
        prog='storage-archive-service',
        description='Builds the archives users ask for, and cleans up after itself.')
    parser.add_argument('--version', action='version', version=__version__)

    parser.add_argument(
        '--cp-api-url', default=os.getenv('API'),
        help='Base url of the platform API, including /restapi. Environment: API')
    parser.add_argument(
        '--cp-api-token', default=os.getenv('API_TOKEN'),
        help='JWT of a system administrator. Environment: API_TOKEN')
    parser.add_argument(
        '--verify-ssl', action=argparse.BooleanOptionalAction,
        default=_env_flag('CP_SAS_VERIFY_SSL', False),
        help='Verify the API certificate. Off by default, as a platform normally signs its own. '
             'Environment: CP_SAS_VERIFY_SSL')

    parser.add_argument(
        '--work-dir', type=Path, default=os.getenv('CP_SAS_WORK_DIR', '/tmp/sas'),
        help='Scratch space, one subdirectory per running task. Environment: CP_SAS_WORK_DIR')
    parser.add_argument(
        '--slots', type=int, default=_env_int('CP_SAS_SLOTS', 2),
        help='How many tasks may run at once. Environment: CP_SAS_SLOTS')
    parser.add_argument(
        '--poll-interval-seconds', type=int, default=_env_int('CP_SAS_POLL_INTERVAL', 20),
        help='How long to sleep between rounds. Environment: CP_SAS_POLL_INTERVAL')
    parser.add_argument(
        '--status-poll-seconds', type=int, default=_env_int('CP_SAS_STATUS_POLL', 15),
        help='How often a running worker checks whether its task was cancelled. '
             'Environment: CP_SAS_STATUS_POLL')
    parser.add_argument(
        '--part-size-mb', type=int, default=_env_int('CP_SAS_PART_SIZE_MB', 8),
        help='Multipart upload part size, in mebibytes. Environment: CP_SAS_PART_SIZE_MB')
    parser.add_argument(
        '--compress-level', type=int, default=_env_int('CP_SAS_COMPRESS_LEVEL', 1),
        choices=range(0, 10),
        help='ZIP deflate level, 0 to 9. Low on purpose: research data is usually already compressed. '
             'Environment: CP_SAS_COMPRESS_LEVEL')

    parser.add_argument(
        '--nfs-mount-root', type=Path, default=os.getenv('CP_SAS_NFS_MOUNT_ROOT', '/mnt/sas-shares'),
        help='Where NFS shares are mounted, one subdirectory per share. '
             'Environment: CP_SAS_NFS_MOUNT_ROOT')
    parser.add_argument(
        '--nfs-mount-timeout-seconds', type=int, default=_env_int('CP_SAS_NFS_MOUNT_TIMEOUT', 30),
        help='Hard limit on a mount call and on the liveness check of a mount point. '
             'Environment: CP_SAS_NFS_MOUNT_TIMEOUT')

    parser.add_argument(
        '--once', action=argparse.BooleanOptionalAction, default=_env_flag('CP_SAS_ONCE', False),
        help='Run one round and exit, after the workers it started have finished. '
             'Environment: CP_SAS_ONCE')
    parser.add_argument(
        '--log-level', default=os.getenv('CP_SAS_LOG_LEVEL', 'INFO'),
        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR'],
        help='Environment: CP_SAS_LOG_LEVEL')
    return parser


def to_config(args: argparse.Namespace) -> ServiceConfig:
    """
    Turns the parsed arguments into the settings object the rest of the service uses.

    A separate step so that nothing downstream has to know about argparse, and so that a test can build
    a `ServiceConfig` directly. The only conversion of substance is the part size, which is configured
    in mebibytes because that is how people talk about it and stored in bytes because that is what the
    S3 calls want.
    """
    return ServiceConfig(
        api_url=args.cp_api_url,
        api_token=args.cp_api_token,
        work_dir=args.work_dir,
        slots=args.slots,
        poll_interval_seconds=args.poll_interval_seconds,
        status_poll_seconds=args.status_poll_seconds,
        part_size_bytes=args.part_size_mb * 1024 * 1024,
        compress_level=args.compress_level,
        nfs_mount_root=args.nfs_mount_root,
        nfs_mount_timeout_seconds=args.nfs_mount_timeout_seconds,
        verify_ssl=args.verify_ssl,
        run_once=args.once,
    )


def main(argv: list[str] | None = None) -> int:
    """
    Starts the service and returns the process exit code.

    Returns rather than calls `sys.exit`, so that a test can call `main([...])` and look at the result
    instead of catching `SystemExit`. The conversion from a return value to an exit code happens in the
    one line at the bottom of the file.

    The signal handlers are what make a container stop cleanly. `SIGTERM` is what kubernetes sends
    before it kills a pod, and `SIGINT` is Ctrl-C; both ask the orchestrator to finish its round and
    wait for the workers, instead of the process dying mid-upload with a multipart upload left behind.
    A second signal is not special-cased: the handler only sets a flag, so the usual way out of a
    shutdown that is taking too long is the container runtime's own kill after the grace period.
    """
    args = build_parser().parse_args(argv)
    configure_logging(args.log_level)
    if not args.cp_api_url or not args.cp_api_token:
        LOG.error('Both the API url and the API token are required. Pass --cp-api-url and '
                  '--cp-api-token, or set API and API_TOKEN.')
        return EXIT_BAD_CONFIG
    if args.slots < 1:
        LOG.error('--slots must be at least 1, got %s', args.slots)
        return EXIT_BAD_CONFIG
    # The numeric settings are checked here and not only by argparse. `choices` and `type` apply to a
    # value *parsed from the command line*; a default taken from the environment goes through neither.
    # So `CP_SAS_COMPRESS_LEVEL=10` would reach `zipfile` untouched and fail every task with an
    # "Internal error of the archive service", which tells an operator nothing about the cause.
    if not 0 <= args.compress_level <= 9:
        LOG.error('--compress-level must be between 0 and 9, got %s', args.compress_level)
        return EXIT_BAD_CONFIG
    if args.part_size_mb < 5:
        # S3 requires every part but the last to be at least 5 MiB.
        LOG.error('--part-size-mb must be at least 5, got %s', args.part_size_mb)
        return EXIT_BAD_CONFIG
    if args.poll_interval_seconds < 1 or args.status_poll_seconds < 1:
        LOG.error('--poll-interval-seconds and --status-poll-seconds must be at least 1, got %s '
                  'and %s', args.poll_interval_seconds, args.status_poll_seconds)
        return EXIT_BAD_CONFIG

    config = to_config(args)
    LOG.info('Storage archive service %s against %s', __version__, config.api_url)
    api = CloudPipelineApi(config.api_url, config.api_token, verify_ssl=config.verify_ssl)
    orchestrator = Orchestrator(api, config)

    def handle_signal(number, _frame):
        # The two arguments a signal handler receives are the signal number and the stack frame that
        # was interrupted. The frame is not needed, and the leading underscore in its name is the
        # convention for saying so.
        LOG.info('Received signal %s', signal.Signals(number).name)
        orchestrator.stop()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)
    orchestrator.run()
    return 0


def _env_int(name: str, default: int) -> int:
    """
    Reads an integer from the environment, falling back to the default on anything unusable.

    An empty variable is treated as unset. That matters here: a container template that writes
    `CP_SAS_SLOTS=` for a value nobody configured is a very normal thing to happen, and
    `int('')` raises.
    """
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError:
        # Logging is not configured yet when this runs - defaults are computed while the parser is
        # built - so the complaint goes straight to stderr.
        print(f'{name}={raw!r} is not a number, using {default}', file=sys.stderr)
        return default


def _env_flag(name: str, default: bool) -> bool:
    """
    Reads a boolean from the environment.

    `!r` in the message above and the set below are both about the same thing: an environment variable
    is always a string, and `bool('false')` is `True`, because every non-empty string is truthy. A
    boolean read with `bool(os.getenv(...))` is a bug waiting for the first person who sets the
    variable to `false` to turn the feature on.
    """
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ('1', 'true', 'yes', 'on')


# This runs only when the file is executed as a program - `python -m sas.app` or
# `python sas/app.py` - and not when it is imported. Python has no `public static void main`: a module
# is executed from top to bottom when imported, so without this guard the service would start up inside
# any test that imported it. `__name__` is the module's own name, and it is the literal string
# `'__main__'` exactly in the module the interpreter was given to run.
if __name__ == '__main__':
    sys.exit(main())
