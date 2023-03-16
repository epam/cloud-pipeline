# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import glob
import os
import urllib
import xml.etree.ElementTree as ET

from pipeline.api import PipelineAPI, TaskStatus
from .utils import HcsParsingUtils, log_run_info, get_list_run_param

HCS_PARSING_TAGS_MAPPING_LIST = get_list_run_param('HCS_PARSING_TAG_MAPPING')
HCS_PARSING_SKIP_DICTIONARY_CHECK_TAGS = get_list_run_param('HCS_PARSING_SKIP_DICTIONARY_CHECK_TAGS')
MAGNIFICATION_CAT_ATTR_NAME = os.getenv('HCS_PARSING_MAGNIFICATION_CAT_ATTR_NAME', 'Magnification')

HCS_KEYWORD_FILE_SUFFIX = '.kw.txt'
EXTENDED_TAGS_OWNER_TAG_KEY = 'OWNER'
EXTENDED_TAGS_CHANNEL_TYPE_TAG_KEY = 'CHANNELTYPE'
EXTENDED_TAGS_MULTIPLANE_FLAG_TAG_KEY = 'PLANES'
EXTENDED_TAGS_TIMECOURSE_FLAG_TAG_KEY = 'TIMEPOINTS'
HYPHEN = '-'
PATH_DELIMITER = '/'
COMMA = ','
TAGS_MAPPING_KEYS_DELIMITER = '='
TAG_DELIMITER = os.getenv('HCS_PARSING_TAG_DELIMITER', ';')


class HcsFileTagProcessor:

    CATEGORICAL_ATTRIBUTE = '/categoricalAttribute'

    def __init__(self, hcs_root_dir, hcs_img_path, xml_info_tree):
        self.hcs_root_dir = hcs_root_dir
        self.hcs_img_path = hcs_img_path
        self.xml_info_tree = xml_info_tree
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.system_dictionaries_url = self.api.api_url + self.CATEGORICAL_ATTRIBUTE
        self.cloud_path = HcsParsingUtils.extract_cloud_path(self.hcs_img_path)

    @staticmethod
    def build_magnification_from_numeric_string(magnification):
        return '{}x'.format(int(float(magnification)))

    @staticmethod
    def map_to_metadata_dict(schema, metadata):
        metadata_entries = metadata.findall(schema + 'XMLAnnotation')
        metadata_dict = dict()
        for entry in metadata_entries:
            entry_value = entry.find(schema + 'Value')
            if entry_value is not None:
                metadata_record = entry_value.find(schema + 'OriginalMetadata')
                if metadata_record is not None:
                    key = metadata_record.find(schema + 'Key').text
                    value = metadata_record.find(schema + 'Value').text
                    if key and value:
                        metadata_dict[key] = value
        return metadata_dict

    @staticmethod
    def read_well_tags(hcs_root_path):
        result = {}
        tags_folder = os.path.join(hcs_root_path, 'assaylayout')
        if not os.path.isdir(tags_folder):
            return result
        for xml in glob.glob(os.path.join(tags_folder, '*.xml')):
            xml_tags = ET.parse(xml).getroot()
            ome_schema = HcsParsingUtils.extract_xml_schema(xml_tags)
            layers = HcsParsingUtils.find_all_in_xml(xml_tags, ome_schema + 'Layer')
            if not layers:
                entry = HcsParsingUtils.find_in_xml(xml_tags, ome_schema + 'AssayLayoutEntry')
                layout = HcsParsingUtils.find_in_xml(entry, ome_schema + 'AssayLayout')
                layers = HcsParsingUtils.find_all_in_xml(layout, ome_schema + 'Layer')
                if not layers:
                    log_run_info('[{}] {}'.format(
                        hcs_root_path, 'Failed to extract tags from %s, unexpected XML schema.' % xml),
                        status=TaskStatus.RUNNING)
                    return result
            for layer in layers:
                tag_key = layer.find(ome_schema + 'Name').text
                if not tag_key:
                    continue
                tag_key = tag_key.encode('utf-8').strip()
                for well in layer.findall(ome_schema + 'Well'):
                    well_key = (well.find(ome_schema + 'Col').text, well.find(ome_schema + 'Row').text)
                    tag_value = well.find(ome_schema + 'Value').text
                    if not tag_value or tag_value == '0':
                        continue
                    if well_key not in result:
                        result[well_key] = {}
                    if tag_key not in result[well_key]:
                        result[well_key][tag_key] = []
                    tag_value = tag_value.encode('utf-8').strip()
                    for val in tag_value.split(TAG_DELIMITER):
                        result[well_key][tag_key].append(val.strip())
        return result

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        log_run_info('[{}] {}'.format(self.hcs_root_dir, message), status=status)

    def process_tags(self, wells_tags):
        existing_attributes_dictionary = self.load_existing_attributes()
        ome_schema = HcsParsingUtils.extract_xml_schema(self.xml_info_tree)
        metadata = self.xml_info_tree.find(ome_schema + 'StructuredAnnotations')
        if not metadata:
            self.log_processing_info('No metadata found for file, skipping tags processing...')
            return 0
        metadata_dict = self.map_to_metadata_dict(ome_schema, metadata)
        self.add_metadata_from_keyword_file(metadata_dict)
        if wells_tags:
            for well_tags in wells_tags.values():
                for key, val in well_tags.items():
                    if key not in metadata_dict:
                        metadata_dict[key] = set()
                    metadata_dict[key].update(val)
        tags_to_push = self.build_tags_dictionary(metadata_dict, existing_attributes_dictionary)
        if not tags_to_push:
            self.log_processing_info('No matching tags found')
            return 0
        pipe_tags = self.prepare_tags(existing_attributes_dictionary, tags_to_push)
        tags_to_push_str = ' '.join(pipe_tags)
        self.log_processing_info('Following tags will be assigned to the file: {}'.format(tags_to_push_str))
        url_encoded_cloud_file_path = HcsParsingUtils.extract_cloud_path(urllib.quote(self.hcs_img_path))
        return os.system('pipe storage set-object-tags "{}" {}'.format(url_encoded_cloud_file_path, tags_to_push_str))

    def add_metadata_from_keyword_file(self, metadata_dict):
        experiment_id = os.path.basename(self.hcs_root_dir)
        keyword_file_name = experiment_id[:experiment_id.rfind(HYPHEN)] + HCS_KEYWORD_FILE_SUFFIX
        keyword_file_path = os.path.join(self.hcs_root_dir, keyword_file_name)
        keyword_file_content = self.try_extract_keyword_file_content(keyword_file_path)
        if keyword_file_content is None:
            return
        self.add_owner_info_if_present(keyword_file_content, metadata_dict)
        self.add_channel_type_info_if_present(keyword_file_content, metadata_dict)
        self.add_timepoints_info_if_present(keyword_file_content, metadata_dict)
        self.add_plane_info_if_present(keyword_file_content, metadata_dict)

    @staticmethod
    def try_extract_keyword_file_content(keyword_file_path):
        if os.path.isfile(keyword_file_path):
            with open(keyword_file_path, "r") as keyword_file:
                data = keyword_file.read()
                json_data = data[data.find('{'):data.rfind('}') + 1]
                return json.loads(json_data)
        return None

    @staticmethod
    def add_plane_info_if_present(keyword_file_content, metadata_dict):
        if EXTENDED_TAGS_MULTIPLANE_FLAG_TAG_KEY in keyword_file_content:
            planes_count = int(keyword_file_content[EXTENDED_TAGS_MULTIPLANE_FLAG_TAG_KEY])
            metadata_dict[EXTENDED_TAGS_MULTIPLANE_FLAG_TAG_KEY] = 'Yes' if planes_count > 1 else 'No'

    @staticmethod
    def add_timepoints_info_if_present(keyword_file_content, metadata_dict):
        if EXTENDED_TAGS_TIMECOURSE_FLAG_TAG_KEY in keyword_file_content:
            timepoints_count = int(keyword_file_content[EXTENDED_TAGS_TIMECOURSE_FLAG_TAG_KEY])
            metadata_dict[EXTENDED_TAGS_TIMECOURSE_FLAG_TAG_KEY] = 'Yes' if timepoints_count > 1 else 'No'

    @staticmethod
    def add_channel_type_info_if_present(keyword_file_content, metadata_dict):
        if EXTENDED_TAGS_CHANNEL_TYPE_TAG_KEY in keyword_file_content:
            channel_type = keyword_file_content[EXTENDED_TAGS_CHANNEL_TYPE_TAG_KEY]
            if isinstance(channel_type, list):
                channel_type = PATH_DELIMITER.join(channel_type.sort())
            metadata_dict[EXTENDED_TAGS_CHANNEL_TYPE_TAG_KEY] = channel_type

    @staticmethod
    def add_owner_info_if_present(keyword_file_content, metadata_dict):
        if EXTENDED_TAGS_OWNER_TAG_KEY in keyword_file_content:
            metadata_dict[EXTENDED_TAGS_OWNER_TAG_KEY] = keyword_file_content[EXTENDED_TAGS_OWNER_TAG_KEY].upper()

    def build_tags_dictionary(self, metadata_dict, existing_attributes_dictionary):
        tags_dictionary = dict()
        common_tags_mapping = self.map_tags(existing_attributes_dictionary)
        if common_tags_mapping:
            tags_dictionary.update(self.extract_matching_tags_from_metadata(metadata_dict, common_tags_mapping))
        magnification_value = self._get_magnification_attribute_value()
        if magnification_value:
            tags_dictionary[MAGNIFICATION_CAT_ATTR_NAME] = {magnification_value}
        return tags_dictionary

    def _get_magnification_attribute_value(self):
        ome_schema = HcsParsingUtils.extract_xml_schema(self.xml_info_tree)
        instrument_details = self.xml_info_tree.find(ome_schema + 'Instrument')
        if len(instrument_details) > 0:
            objectives_details = instrument_details.find(ome_schema + 'Objective')
            if objectives_details is not None:
                magnification_value = objectives_details.get('NominalMagnification')
                if magnification_value:
                    return self.build_magnification_from_numeric_string(magnification_value)
        return None

    def prepare_tags(self, existing_attributes_dictionary, tags_to_push):
        attribute_updates = list()
        pipe_tags = list()
        for attribute_name, values_to_push in tags_to_push.items():
            if len(values_to_push) > 1:
                self.log_processing_info('Multiple tags matches occurred for "{}": [{}]'
                                         .format(attribute_name, values_to_push))
                value = TAG_DELIMITER.join(values_to_push)
            else:
                value = list(values_to_push)[0]
            if attribute_name not in existing_attributes_dictionary:
                self.log_processing_info('No categorical attribute [{}] exists'.format(attribute_name))
                continue
            existing_values = existing_attributes_dictionary[attribute_name]
            existing_attribute_id = existing_values[0]['attributeId']
            existing_values_names = [existing_value['value'].encode('utf-8') for existing_value in existing_values]
            for val in values_to_push:
                if val not in existing_values_names:
                    existing_values.append({'key': attribute_name, 'value': val})
                    attribute_updates.append({'id': int(existing_attribute_id),
                                              'key': attribute_name, 'values': existing_values})
            pipe_tags.append('\'{}\'=\'{}\''.format(attribute_name, value))
        if attribute_updates:
            self.log_processing_info('Updating following categorical attributes before tagging: {}'
                                     .format(attribute_updates))
            self.update_categorical_attributes(attribute_updates)
        return pipe_tags

    def update_categorical_attributes(self, attribute_updates):
        for attribute in attribute_updates:
            self.api.execute_request(self.system_dictionaries_url, method='post', data=json.dumps(attribute))

    def extract_matching_tags_from_metadata(self, metadata_dict, tags_mapping):
        tags_to_push = dict()
        for key in tags_mapping.keys():
            if key in metadata_dict:
                value = metadata_dict[key]
                if isinstance(value, basestring) and value.startswith('[') and value.endswith(']'):
                    self.log_processing_info('Processing array value')
                    value = value[1:-1]
                    values = list(set(value.split(COMMA)))
                    if not values:
                        self.log_processing_info('Empty metadata values, skipping [{}]'.format(key))
                        continue
                    value = values

                target_tag = tags_mapping[key]
                if target_tag not in tags_to_push:
                    tags_to_push[target_tag] = set()
                if isinstance(value, basestring):
                    tags_to_push[target_tag].add(value)
                else:
                    tags_to_push[target_tag].update(value)
        return tags_to_push

    def map_tags(self, existing_attributes_dictionary):
        skip_dictionary_check_tags = set(element.strip() for element in HCS_PARSING_SKIP_DICTIONARY_CHECK_TAGS)
        tags_mapping = dict()
        for rule in HCS_PARSING_TAGS_MAPPING_LIST:
            rule_mapping = rule.split(TAGS_MAPPING_KEYS_DELIMITER, 1)
            if len(rule_mapping) != 2:
                self.log_processing_info('Error [{}]: mapping rule declaration should contain a delimiter!'.format(
                    rule_mapping))
                continue
            else:
                key = rule_mapping[0]
                value = rule_mapping[1]
                if value not in existing_attributes_dictionary and value not in skip_dictionary_check_tags:
                    self.log_processing_info('No dictionary [{}] is registered, the rule "{}" will be skipped!'
                                             .format(value, rule_mapping))
                    continue
                else:
                    tags_mapping[key] = value
        return tags_mapping

    def load_existing_attributes(self):
        existing_attributes = self.api.execute_request(self.system_dictionaries_url)
        existing_attributes_dictionary = {attribute['key']: attribute['values'] for attribute in existing_attributes}
        return existing_attributes_dictionary
