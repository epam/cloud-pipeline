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

import argparse
import json
import os
import xml.etree.ElementTree as ET

from pipeline import PipelineAPI

TAGS_MAPPING_RULE_DELIMITER = ','
TAGS_MAPPING_KEYS_DELIMITER = '='
SCHEMA_PREFIX = '{http://www.openmicroscopy.org/Schemas/OME/2016-06}'
SYSTEM_IMAGE_NAMES = {'overview', 'label', 'thumbnail', 'macro', 'macro image', 'macro mask image', 'label image',
                      'overview image', 'thumbnail image'}
DEEP_ZOOM_CREATION_SCRIPT = os.path.join(os.getenv('WSI_PARSER_HOME', '/opt/local/wsi-parser'), 'create_deepzoom.sh')
IMAGE_AREA_LIMIT = int(os.getenv('WSI_PARSER_IMAGE_LIMIT', 15000 * 25000))


def get_file_without_extension(file_path):
    return os.path.splitext(file_path)[0]


def generate_xml_info(file_path):
    xml_info_file = get_file_without_extension(file_path) + '_info.xml'
    os.system('showinf -nopix -omexml-only {} > {}'.format(file_path, xml_info_file))
    return xml_info_file


def extract_path_for_pipe_cp(file_path):
    path_chunks = file_path.split('/cloud-data/', 1)
    if len(path_chunks) != 2:
        return None
    return path_chunks[1]


def process_tags(cloud_path, xml_attributes, tags_mapping_str):
    api_url = os.environ['API']
    api = PipelineAPI(api_url, 'logs')
    system_dictionaries_url = api_url + '/categoricalAttribute'
    existing_attributes = api.execute_request(system_dictionaries_url)
    existing_attributes_dictionary = {attribute['key']: attribute['values'] for attribute in existing_attributes}
    tags_mapping = dict()
    for rule in tags_mapping_str.split(TAGS_MAPPING_RULE_DELIMITER):
        rule_mapping = rule.split(TAGS_MAPPING_KEYS_DELIMITER, 1)
        if len(rule_mapping) != 2:
            print('Error [{}]: mapping rule declaration should contain a delimiter!'.format(rule_mapping))
            continue
        else:
            key = rule_mapping[0]
            value = rule_mapping[1]
            if value not in existing_attributes_dictionary:
                print('No dictionary [{}] is registered, the rule "{}" will be skipped!'.format(value, rule_mapping))
                continue
            else:
                tags_mapping[key] = value
    if not tags_mapping:
        print('No tags to map found, skipping...')
    metadata = xml_attributes.find(SCHEMA_PREFIX + 'StructuredAnnotations')
    if len(metadata) == 0:
        print('No metadata found for file, skipping tags processing')
    metadata_entries = metadata.findall(SCHEMA_PREFIX + 'XMLAnnotation')
    tags_to_push = dict()
    for entry in metadata_entries:
        entry_value = entry.find(SCHEMA_PREFIX + 'Value')
        if entry_value is not None:
            metadata_record = entry_value.find(SCHEMA_PREFIX + 'OriginalMetadata')
            if metadata_record is not None:
                key = metadata_record.find(SCHEMA_PREFIX + 'Key').text
                if key and key in tags_mapping:
                    value = metadata_record.find(SCHEMA_PREFIX + 'Value').text[1:-1]
                    if value:
                        if value.startswith('[') and value.endswith(']'):
                            print('Processing array value')
                            value = value[1:-1]
                            values = list(set(value.split(',')))
                            if len(values) != 1:
                                print('Empty or multiple metadata values, skipping [{}]'.format(key))
                            value = values[0]
                        target_tag = tags_mapping[key]
                        if target_tag in tags_to_push:
                            tags_to_push[target_tag].add(value)
                        else:
                            tags_to_push[target_tag] = {value}
    if not tags_to_push:
        print('No matching tags found, skipping...')
        return
    attribute_updates = list()
    pipe_tags = list()
    for attribute_name, values_to_push in tags_to_push.items():
        if len(values_to_push) > 1:
            print('Multiple tags matches occurred for "{}": [{}]'.format(attribute_name, values_to_push))
        value = list(values_to_push)[0]
        existing_values = existing_attributes_dictionary[attribute_name]
        existing_values_names = [existing_value['value'] for existing_value in existing_values]
        if value not in existing_values_names:
            existing_values.append({'key': attribute_name, 'value': value})
            attribute_updates.append({'key': attribute_name, 'values': existing_values})
        pipe_tags.append(attribute_name + '=' + value)
    if attribute_updates:
        print('Updating following categorical attributes: {}'.format(attribute_updates))
        api.execute_request(system_dictionaries_url, method='post', data=json.dumps(attribute_updates))
    tags_to_assign = ','.join(pipe_tags)
    print('Following tags will be assigned to the file: {}'.format(tags_to_assign))
    os.system('pipe storage set-object-tags "cp://{}" "{}"'.format(cloud_path, tags_to_assign))


def calculate_series(file_path, xml_info_tree):
    series_mapping = {}
    base_name = os.path.basename(file_path)
    current_group_name = ''
    current_group_details_list = []
    images = xml_info_tree.findall(SCHEMA_PREFIX + 'Image')
    for i in range(0, len(images)):
        image = images[i]
        name = image.get('Name')
        details = create_details(i, image, name)
        current_group_details_list.append(details)
        if not name.startswith(base_name):
            if not current_group_name:
                current_group_name = name
            series_mapping[current_group_name] = current_group_details_list
            current_group_name = name
            current_group_details_list = []
        elif not current_group_name:
            series_mapping[name] = [details]
    if current_group_name:
        series_mapping[current_group_name] = current_group_details_list
    print('Following groups are found in a file {}'.format(series_mapping.keys()))
    target_group = None
    target_series = None
    for group_name in series_mapping.keys():
        if group_name not in SYSTEM_IMAGE_NAMES:
            target_group = group_name
            break
    print('Target group is: {}'.format(target_group))
    if target_group:
        for image in series_mapping[target_group]:
            image_area_size = image.width * image.height
            if image_area_size < IMAGE_AREA_LIMIT:
                target_series = image.id
                break
        if not target_series:
            target_series = series_mapping[target_group][-1].id
    return target_series


def create_details(i, image, name):
    resolution_details = image.find(SCHEMA_PREFIX + 'Pixels')
    width = resolution_details.get('SizeX')
    height = resolution_details.get('SizeY')
    details = ImageDetails(i, name, width, height)
    return details


def process_file(file_path):
    print('Generating XML description of a file')
    xml_info_file = generate_xml_info(file_path)
    tree = ET.parse(xml_info_file)
    xml_info_tree = tree.getroot()

    cloud_path = extract_path_for_pipe_cp(file_path)
    if cloud_path:
        tags_mapping_rules = os.getenv("WSI_PARSING_TAG_MAPPING")
        if tags_mapping_rules:
            print('Start processing tags')
            process_tags(cloud_path, xml_info_tree, tags_mapping_rules)
        else:
            print('No tags mapping rules specified')
    else:
        print('File path is not on the cloud, skipping tags processing')
    target_series = calculate_series(file_path, xml_info_tree)
    if target_series is None:
        print('Unable to determine target series, skipping DZ creation ')
        return
    print('Series #{} selected for DZ creation'.format(target_series))
    os.system('bash {} {} {} {}'.format(DEEP_ZOOM_CREATION_SCRIPT, file_path, xml_info_file, target_series))


class ImageDetails(object):

    def __init__(self, series_id, name, width, height):
        self.id = series_id
        self.name = name
        self.width = int(width)
        self.height = int(height)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--file', type=str, required=True)
    args = parser.parse_args()
    file_path = args.file
    process_file(file_path)


if __name__ == '__main__':
    main()
