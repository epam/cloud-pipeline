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

import argparse
import os
import json
from pipeline import PipelineAPI
import sys

class Metadata:
    def __init__(self, folder_id, class_id, class_name, external_id):
        self.folder_id = folder_id
        self.class_id = class_id
        self.class_name = class_name
        self.external_id = external_id
        self.api = PipelineAPI(os.environ['API'], 'logs')

    def get_dataset_details(self):
        return self.api.find_metadata_entity(self.folder_id,
                                             self.external_id,
                                             self.class_name)

    def update_dataset(self, fields=None, env_prefix=None):
        updated_entity = {
            'classId': self.class_id,
            'parentId': self.folder_id,
            'data': {}
        }

        if self.external_id:
            entity = self.get_dataset_details()
            updated_entity['entityId'] = entity['id']
            updated_entity['externalId'] = self.external_id
            updated_entity['data'] = entity['data'].copy()

        if fields != None:
            for field_item, (value, field_type) in fields.items():
                updated_entity['data'][field_item] = {
                    'type': field_type,
                    'value': value
                }
        if env_prefix != None:
            for name, value in os.environ.items():
                if not name.startswith(env_prefix) or name.endswith('_PARAM_TYPE'):
                    continue
                field_item = name.replace(env_prefix, '')
                updated_entity['data'][field_item] = {
                    'type': os.environ.get(name + '_PARAM_TYPE', 'string'),
                    'value': value
                }

        print(self.api.save_metadata_entity(updated_entity)['externalId'])

def parse_file_fields(file_path):
    with open(file_path, 'r') as f:
        data = json.load(f)
    fields = {}
    for key, val in data.items():
        if isinstance(val, list):
            fields[key] = (json.dumps(val), 'array')
        elif isinstance(val, dict) and 'value' in val and 'type' in val:
            fields[key] = (val['value'], val['type'])
        else:
            fields[key] = (val if isinstance(val, str) else json.dumps(val), 'string')
    return fields

def main():
    parser = argparse.ArgumentParser()
    # Base options
    parser.add_argument('--folder-id',
                        help='Metadata entity folder ID',
                        type=int,
                        required=False,
                        default=int(os.getenv('CP_METADATA_FOLDER_ID', 0)))
    parser.add_argument('--class-id',
                        help='Metadata class ID',
                        type=int,
                        required=False,
                        default=int(os.getenv('CP_METADATA_CLASS_ID', 0)))
    parser.add_argument('--class-name',
                        help='Metadata class name',
                        type=str,
                        required=False,
                        default=os.getenv('CP_METADATA_CLASS'))
    parser.add_argument('--entity-ext-id',
                        help='Metadata entity external UUID',
                        required=False,
                        type=str,
                        default=os.getenv('CP_METADATA_ENTITY_EXT_ID', 0))
    subparsers = parser.add_subparsers(dest='command')

    # 'show' command
    show_parser = subparsers.add_parser('show')
    show_parser.add_argument('field', nargs='?')

    # 'update' command
    update_parser = subparsers.add_parser('update')
    update_parser.add_argument('--metadata-from-env-prefix',
                                help='All environment variables starting with this prefix will be added as fields',
                                required=False,
                                type=str)

    # 'update-from-file' command
    update_file_parser = subparsers.add_parser('update-from-file')
    update_file_parser.add_argument('file',
                                    help='Path to a JSON file with fields to update',
                                    type=str)

    # Parse commandline
    args, unknown_args = parser.parse_known_args()
    # For the "update" command - parse "field value [--type type] ..." triples
    unknown_args_dict = {}
    i = 0
    while i < len(unknown_args):
        if unknown_args[i].startswith('--'):
            print('[ERROR] Unexpected option "%s" before field name' % unknown_args[i])
            sys.exit(1)
        if i + 1 >= len(unknown_args):
            print('[ERROR] Missing value for field "%s"' % unknown_args[i])
            sys.exit(1)
        key = unknown_args[i]
        value = unknown_args[i + 1]
        i += 2
        if i < len(unknown_args) and unknown_args[i] == '--type':
            if i + 1 >= len(unknown_args):
                print('[ERROR] Missing argument for --type')
                sys.exit(1)
            field_type = unknown_args[i + 1]
            i += 2
        else:
            field_type = 'string'
        unknown_args_dict[key] = (value, field_type)

    metadata = Metadata(args.folder_id,
                        args.class_id,
                        args.class_name,
                        args.entity_ext_id)
    if args.command == 'show':
        entity = metadata.get_dataset_details()
        if args.field:
            print(entity['data'][args.field]['value'])
        else:
            print(json.dumps(entity))
    elif args.command == 'update':
        if len(unknown_args_dict) == 0:
            print('[ERROR] No fields are specified for update')
            sys.exit(1)
        metadata.update_dataset(fields=unknown_args_dict,
                                env_prefix=args.metadata_from_env_prefix)
    elif args.command == 'update-from-file':
        metadata.update_dataset(fields=parse_file_fields(args.file))

if __name__ == '__main__':
    main()
