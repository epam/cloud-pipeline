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

Every setting is both a command line option and an environment variable, and the option wins. A
container passes everything through the environment; a developer wants `--help`.

The url and the token have no default, and their names are the platform's own - `CP_API_JWT_ADMIN`,
and `API` or the pair `CP_API_SRV_INTERNAL_HOST`/`CP_API_SRV_INTERNAL_PORT` - because that is what
`cp-config-global` puts into every service container.
"""

from __future__ import annotations

import argparse
import os
import signal
import sys
from pathlib import Path

from .api import CloudPipelineApi
from .config import ServiceConfig
from .util.log import DEFAULT_BACKUP_DAYS, configure_logging, get_logger
from .orchestrator import Orchestrator
from .util.version import __version__

LOG = get_logger(__name__)

# 22 is `EINVAL`, which the platform's other service entry points use for a bad setting too.
EXIT_BAD_CONFIG = 22


def build_parser() -> argparse.ArgumentParser:
    """
    Declares the command line.

    The booleans use `BooleanOptionalAction` so that `--no-once` exists: with `store_true`, a
    `CP_SATS_ONCE=true` left in a container environment could not be turned off on the command line.

    Environment defaults are read here, so an option simply overwrites one and no precedence logic is
    needed anywhere else.
    """
    parser = argparse.ArgumentParser(
        prog='storage-async-task-service',
        description='Builds the archives users ask for, and cleans up after itself.')
    parser.add_argument('--version', action='version', version=__version__)

    parser.add_argument(
        '--cp-api-url', default=_env_api_url(),
        help='Base url of the platform API, including /restapi. Environment: API, or '
             'CP_API_SRV_INTERNAL_HOST and CP_API_SRV_INTERNAL_PORT together')
    parser.add_argument(
        '--cp-api-token', default=os.getenv('CP_API_JWT_ADMIN'),
        help='JWT of a system administrator. Environment: CP_API_JWT_ADMIN')
    parser.add_argument(
        '--verify-ssl', action=argparse.BooleanOptionalAction,
        default=_env_flag('CP_SATS_VERIFY_SSL', False),
        help='Verify the API certificate. Off by default, as a platform normally signs its own. '
             'Environment: CP_SATS_VERIFY_SSL')

    parser.add_argument(
        '--work-dir', type=Path, default=os.getenv('CP_SATS_WORK_DIR', '/tmp/sats'),
        help='Scratch space, one subdirectory per running task. Environment: CP_SATS_WORK_DIR')
    parser.add_argument(
        '--slots', type=int, default=_env_int('CP_SATS_SLOTS', 2),
        help='How many tasks may run at once. Environment: CP_SATS_SLOTS')
    parser.add_argument(
        '--poll-interval-seconds', type=int, default=_env_int('CP_SATS_POLL_INTERVAL', 20),
        help='How long to sleep between rounds. Environment: CP_SATS_POLL_INTERVAL')
    parser.add_argument(
        '--cleanup-interval-seconds', type=int, default=_env_int('CP_SATS_CLEANUP_INTERVAL', 300),
        help='How often to run the cleanups that finish tasks whose workers are gone and delete '
             'expired archives. 0 turns them off. Environment: CP_SATS_CLEANUP_INTERVAL')
    parser.add_argument(
        '--status-poll-seconds', type=int, default=_env_int('CP_SATS_STATUS_POLL', 15),
        help='How often a running worker checks whether its task was cancelled. '
             'Environment: CP_SATS_STATUS_POLL')
    parser.add_argument(
        '--part-size-mb', type=int, default=_env_int('CP_SATS_PART_SIZE_MB', 8),
        help='Multipart upload part size, in mebibytes. Environment: CP_SATS_PART_SIZE_MB')
    parser.add_argument(
        '--compress-level', type=int, default=_env_int('CP_SATS_COMPRESS_LEVEL', 1),
        choices=range(0, 10),
        help='ZIP deflate level, 0 to 9. Low on purpose: research data is usually already compressed. '
             'Environment: CP_SATS_COMPRESS_LEVEL')

    parser.add_argument(
        '--nfs-mount-root', type=Path, default=os.getenv('CP_SATS_NFS_MOUNT_ROOT', '/mnt/sats-shares'),
        help='Where NFS shares are mounted, one subdirectory per share. '
             'Environment: CP_SATS_NFS_MOUNT_ROOT')
    parser.add_argument(
        '--nfs-mount-timeout-seconds', type=int, default=_env_int('CP_SATS_NFS_MOUNT_TIMEOUT', 30),
        help='Hard limit on a mount call and on the liveness check of a mount point. '
             'Environment: CP_SATS_NFS_MOUNT_TIMEOUT')

    parser.add_argument(
        '--once', action=argparse.BooleanOptionalAction, default=_env_flag('CP_SATS_ONCE', False),
        help='Run one round and exit, after the workers it started have finished. '
             'Environment: CP_SATS_ONCE')
    parser.add_argument(
        '--log-level', default=os.getenv('CP_SATS_LOG_LEVEL', 'INFO'),
        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR'],
        help='Environment: CP_SATS_LOG_LEVEL')
    parser.add_argument(
        '--log-dir', default=os.getenv('CP_SATS_LOG_DIR', ''),
        help='Directory for the rotating log file, which is written in addition to stdout. Empty means '
             'stdout alone. Environment: CP_SATS_LOG_DIR')
    parser.add_argument(
        '--log-backup-days', type=int, default=_env_int('CP_SATS_LOG_BACKUP_DAYS',
                                                        DEFAULT_BACKUP_DAYS),
        help='How many rotated log files to keep. Only applies with a log directory. '
             'Environment: CP_SATS_LOG_BACKUP_DAYS')
    return parser


def to_config(args: argparse.Namespace) -> ServiceConfig:
    """
    Turns the parsed arguments into the settings the rest of the service uses.

    A separate step, so nothing downstream knows about argparse. The part size is configured in
    mebibytes and stored in bytes, which is what the S3 calls want.
    """
    return ServiceConfig(
        api_url=args.cp_api_url,
        api_token=args.cp_api_token,
        work_dir=args.work_dir,
        slots=args.slots,
        poll_interval_seconds=args.poll_interval_seconds,
        cleanup_interval_seconds=args.cleanup_interval_seconds,
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

    Returns rather than calling `sys.exit`, so a test can read the result.

    The signal handlers are what make a container stop cleanly: both ask the orchestrator to finish
    its round and wait for the workers instead of dying mid-upload. A second signal is not
    special-cased - the container runtime's kill after the grace period is the way out.
    """
    args = build_parser().parse_args(argv)
    # Two steps, and the order matters: stdout first, so every complaint below has somewhere to go;
    # the log file only once its settings are checked, since creating it can fail.
    configure_logging(args.log_level)
    if not args.cp_api_url or not args.cp_api_token:
        LOG.error('Both the API url and the API token are required. Pass --cp-api-url and '
                  '--cp-api-token, or set CP_API_JWT_ADMIN and either API or both '
                  'CP_API_SRV_INTERNAL_HOST and CP_API_SRV_INTERNAL_PORT.')
        return EXIT_BAD_CONFIG
    if args.log_backup_days < 1:
        # The handler accepts zero and then loses everything at midnight.
        LOG.error('--log-backup-days must be at least 1, got %s', args.log_backup_days)
        return EXIT_BAD_CONFIG
    if args.log_dir:
        try:
            configure_logging(args.log_level, log_dir=args.log_dir,
                              backup_days=args.log_backup_days)
        except OSError as error:
            # A read-only mount, or a path whose parent is a file. Without this the container
            # crash-loops on a traceback instead of saying what is wrong.
            LOG.error('Cannot use %s as the log directory (%s)', args.log_dir, error)
            return EXIT_BAD_CONFIG
    if args.slots < 1:
        LOG.error('--slots must be at least 1, got %s', args.slots)
        return EXIT_BAD_CONFIG
    # Checked here and not only by argparse: `choices` and `type` apply to a value parsed from the
    # command line, while a default taken from the environment goes through neither.
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
    if args.cleanup_interval_seconds < 0:
        # Zero is a meaning - "never" - and negative is not.
        LOG.error('--cleanup-interval-seconds must be 0 or more, got %s',
                  args.cleanup_interval_seconds)
        return EXIT_BAD_CONFIG

    config = to_config(args)
    LOG.info('Storage archive service %s against %s', __version__, config.api_url)
    api = CloudPipelineApi(config.api_url, config.api_token, verify_ssl=config.verify_ssl)
    orchestrator = Orchestrator(api, config)

    def handle_signal(number, _frame):
        LOG.info('Received signal %s', signal.Signals(number).name)
        orchestrator.stop()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)
    orchestrator.run()
    return 0


def _env_api_url() -> str | None:
    """
    The API url from the environment: `API`, or one built from the API service's internal host and port.

    The second form is what a platform container gets, and it is assembled here rather than in the
    entry script so that a test can reach the rule.

    Both halves are required: a url without the port would point at 443, where the API does not
    listen, and the service would start and fail every call. `None` is what `argparse` reads as "no
    default".
    """
    configured = (os.getenv('API') or '').strip()
    if configured:
        return configured
    host = (os.getenv('CP_API_SRV_INTERNAL_HOST') or '').strip()
    port = (os.getenv('CP_API_SRV_INTERNAL_PORT') or '').strip()
    if not host or not port:
        return None
    return f'https://{host}:{port}/pipeline/restapi/'


def _env_int(name: str, default: int) -> int:
    """
    Reads an integer from the environment, falling back to the default on anything unusable.

    An empty variable counts as unset: a container template writing `CP_SATS_SLOTS=` is normal, and
    `int('')` raises.
    """
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError:
        # Logging is not configured yet - defaults are computed while the parser is built.
        print(f'{name}={raw!r} is not a number, using {default}', file=sys.stderr)
        return default


def _env_flag(name: str, default: bool) -> bool:
    """
    Reads a boolean from the environment.

    Spelled out because `bool('false')` is `True`: every non-empty string is truthy.
    """
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ('1', 'true', 'yes', 'on')


# Runs only when this module is executed as a program, not when it is imported.
if __name__ == '__main__':
    sys.exit(main())
