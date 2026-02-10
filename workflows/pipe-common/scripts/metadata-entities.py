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
            for field_item in fields:
                updated_entity['data'][field_item] = {
                    'type': 'string',
                    'value': fields[field_item]
                }
        if env_prefix != None:
            for name, value in os.environ.items():
                if not name.startswith(env_prefix) or name.endswith('_PARAM_TYPE'):
                    continue
                field_item = name.replace(env_prefix, '')
                updated_entity['data'][field_item] = {
                    'type': 'string',
                    'value': value
                }

        print(self.api.save_metadata_entity(updated_entity)['externalId'])

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

    # Parse commandline
    args, unknown_args = parser.parse_known_args()
    # For the "update" command - it's list of fields to set
    unknown_args_dict = dict(zip(unknown_args[:-1:2],unknown_args[1::2]))

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

if __name__ == '__main__':
    main()
