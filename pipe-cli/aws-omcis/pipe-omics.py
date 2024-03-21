import argparse
import json
import logging
import os
import traceback

from omics.cpapi import CloudPipelineClient
from omics import storage_operations
import sys

_default_logging_level = 'ERROR'


def configure_logging(args):
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=args.logging_level)
    logging.getLogger('botocore').setLevel(logging.ERROR)


def perform_command(group, command, parsed_args):
    api = CloudPipelineClient(os.getenv('API', ''), os.getenv('API_TOKEN', ''))
    match group:
        case 'storage':
            storage_operations.perform_storage_command(api, command, parsed_args)
        case _:
            raise RuntimeError()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-g", "--group", type=str, choices=["storage"], required=True, help="")
    parser.add_argument("-c", "--command", type=str, required=False, help="")
    parser.add_argument("-i", "--raw-input", type=str, help="")
    parser.add_argument("-l", "--logging-level", type=str, required=False, default=_default_logging_level,
                        help="Logging level.")

    args = parser.parse_args()
    configure_logging(args)

    try:
        parsed_args = json.loads(args.raw_input)
        perform_command(args.group, args.command, parsed_args)
    except Exception:
        logging.exception('Unhandled error')
        traceback.print_exc()
        sys.exit(1)
