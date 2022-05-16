# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import errno
import json
import os
import multiprocessing
import datetime
import math
import shutil
import sys
import tempfile
import time
import traceback
import urllib
import xml.etree.ElementTree as ET

from collections import OrderedDict
from functools import cmp_to_key
from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger
from pipeline.common import get_path_with_trailing_delimiter, get_path_without_trailing_delimiter
from PIL import Image


MAGNIFICATION_CAT_ATTR_NAME = os.getenv('HCS_PARSING_MAGNIFICATION_CAT_ATTR_NAME', 'Magnification')
HCS_ACTIVE_PROCESSING_TIMEOUT_MIN = int(os.getenv('HCS_PARSING_ACTIVE_PROCESSING_TIMEOUT_MIN', 360))
TAGS_PROCESSING_ONLY = os.getenv('HCS_PARSING_TAGS_ONLY', 'false') == 'true'
FORCE_PROCESSING = os.getenv('HCS_PARSING_FORCE_PROCESSING', 'false') == 'true'
HCS_CLOUD_FILES_SCHEMA = os.getenv('HCS_PARSING_CLOUD_FILES_SCHEMA', 's3')
PLANE_COORDINATES_DELIMITER = os.getenv('HCS_PARSING_PLANE_COORDINATES_DELIMITER', '_')
DEFAULT_CHANNEL_WIDTH = os.getenv('HCS_PARSING_DEFAULT_CHANNEL_WIDTH', 1080)
DEFAULT_CHANNEL_HEIGHT = os.getenv('HCS_PARSING_DEFAULT_CHANNEL_HEIGHT', 1080)

HCS_PROCESSING_OUTPUT_FOLDER = os.getenv('HCS_PARSING_OUTPUT_FOLDER')
HCS_PROCESSING_TASK_NAME = 'HCS processing'
HCS_OME_COMPATIBLE_INDEX_FILE_NAME = 'Index.xml'
OVERVIEW_DIR_NAME = 'overview'
HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')
MEASUREMENT_INDEX_FILE_PATH = '/{}/{}'.format(HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)
UNKNOWN_ATTRIBUTE_VALUE = 'NA'
HYPHEN = '-'
TAGS_MAPPING_RULE_DELIMITER = ','
TAGS_MAPPING_KEYS_DELIMITER = '='


def log_success(message):
    log_info(message, status=TaskStatus.SUCCESS)


def log_info(message, status=TaskStatus.RUNNING):
    Logger.log_task_event(HCS_PROCESSING_TASK_NAME, message, status)


class HcsParsingUtils:

    @staticmethod
    def extract_xml_schema(xml_info_root):
        full_schema = xml_info_root.tag
        return full_schema[:full_schema.rindex('}') + 1]

    @staticmethod
    def get_file_without_extension(file_path):
        return os.path.splitext(file_path)[0]

    @staticmethod
    def get_basename_without_extension(file_path):
        return HcsParsingUtils.get_file_without_extension(os.path.basename(file_path))

    @staticmethod
    def get_file_last_modification_time(file_path):
        return int(os.stat(file_path).st_mtime)

    @staticmethod
    def build_preview_file_path(hcs_root_folder_path):
        index_file_abs_path = os.path.join(HcsParsingUtils.get_file_without_extension(hcs_root_folder_path),
                                           HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)
        hcs_xml_info_root = ET.parse(index_file_abs_path).getroot()
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        file_name = HcsParsingUtils.get_file_without_extension(hcs_root_folder_path)
        name_xml_element = HcsFileParser.extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix)\
            .find(hcs_schema_prefix + 'Name')
        if name_xml_element is not None:
            file_pretty_name = name_xml_element.text
            if file_pretty_name is not None:
                file_name = file_pretty_name
        preview_file_basename = HcsParsingUtils.replace_special_chars(file_name) + '.hcs'
        parent_folder = HCS_PROCESSING_OUTPUT_FOLDER \
            if HCS_PROCESSING_OUTPUT_FOLDER is not None \
            else os.path.dirname(hcs_root_folder_path)
        return os.path.join(parent_folder, preview_file_basename)

    @staticmethod
    def get_stat_active_file_name(hcs_img_path):
        return HcsParsingUtils._get_service_file_name(hcs_img_path, 'hcsparser.inprog')

    @staticmethod
    def get_stat_file_name(hcs_img_path):
        return HcsParsingUtils._get_service_file_name(hcs_img_path, 'hcsparser')

    @staticmethod
    def get_service_directory(hcs_img_path):
        name_without_extension = HcsParsingUtils.get_basename_without_extension(hcs_img_path)
        parent_dir = HCS_PROCESSING_OUTPUT_FOLDER \
            if HCS_PROCESSING_OUTPUT_FOLDER is not None \
            else os.path.dirname(hcs_img_path)
        return os.path.join(parent_dir, '.hcsparser', name_without_extension)

    @staticmethod
    def generate_local_service_directory(hcs_img_path):
        name_without_extension = HcsParsingUtils.get_basename_without_extension(hcs_img_path)
        return tempfile.mkdtemp(prefix=name_without_extension + '.hcsparser.')

    @staticmethod
    def create_service_dir_if_not_exist(hcs_img_path):
        directory = HcsParsingUtils.get_service_directory(hcs_img_path)
        if not os.path.exists(directory):
            os.makedirs(directory)

    @staticmethod
    def _get_service_file_name(hcs_img_path, suffix):
        parent_dir = HcsParsingUtils.get_service_directory(hcs_img_path)
        parser_flag_file = '.stat.{}'.format(suffix)
        return os.path.join(parent_dir, parser_flag_file)

    @staticmethod
    def active_processing_exceed_timeout(active_stat_file):
        processing_stat_file_modification_date = HcsParsingUtils.get_file_last_modification_time(active_stat_file)
        processing_deadline = datetime.datetime.now() - datetime.timedelta(minutes=HCS_ACTIVE_PROCESSING_TIMEOUT_MIN)
        return (processing_stat_file_modification_date - time.mktime(processing_deadline.timetuple())) < 0

    @staticmethod
    def extract_cloud_path(file_path, cloud_scheme=HCS_CLOUD_FILES_SCHEMA):
        path_chunks = file_path.split('/cloud-data/', 1)
        if len(path_chunks) != 2:
            raise RuntimeError('Unable to determine cloud path of [{}]'.format(file_path))
        return '{}://{}'.format(cloud_scheme, path_chunks[1])

    @staticmethod
    def replace_special_chars(file_path):
        return file_path.replace('/', '|')

    @staticmethod
    def quote_string(string):
        return '"{}"'.format(string)


class HcsProcessingDirsGenerator:

    def __init__(self, lookup_paths):
        self.lookup_paths = lookup_paths

    @staticmethod
    def is_folder_content_modified_after(dir_path, modification_date):
        dir_root = os.walk(dir_path)
        for dir_root, directories, files in dir_root:
            for file in files:
                if HcsParsingUtils.get_file_last_modification_time(os.path.join(dir_root, file)) > modification_date:
                    return True
        return False

    def generate_paths(self):
        hcs_roots = self.find_all_hcs_roots()
        log_info('Found {} HCS files'.format(len(hcs_roots)))
        return filter(lambda p: self.is_processing_required(p), hcs_roots)

    def find_all_hcs_roots(self):
        hcs_roots = set()
        for lookup_path in self.lookup_paths:
            dir_walk_root = os.walk(lookup_path)
            for dir_root, directories, files in dir_walk_root:
                for file in files:
                    full_file_path = os.path.join(dir_root, file)
                    if full_file_path.endswith(MEASUREMENT_INDEX_FILE_PATH):
                        hcs_roots.add(full_file_path[:-len(MEASUREMENT_INDEX_FILE_PATH)])
        return hcs_roots

    def is_processing_required(self, hcs_folder_root_path):
        if TAGS_PROCESSING_ONLY or FORCE_PROCESSING:
            return True
        hcs_img_path = HcsParsingUtils.build_preview_file_path(hcs_folder_root_path)
        if not os.path.exists(hcs_img_path):
            return True
        active_stat_file = HcsParsingUtils.get_stat_active_file_name(hcs_img_path)
        if os.path.exists(active_stat_file):
            return HcsParsingUtils.active_processing_exceed_timeout(active_stat_file)
        stat_file = HcsParsingUtils.get_stat_file_name(hcs_img_path)
        if not os.path.isfile(stat_file):
            return True
        stat_file_modification_date = HcsParsingUtils.get_file_last_modification_time(stat_file)
        return self.is_folder_content_modified_after(hcs_folder_root_path, stat_file_modification_date)


class FieldDetails:
    def __init__(self, well_column, well_row, ome_image_id, x, y):
        self.well_column = int(well_column)
        self.well_row = int(well_row)
        self.ome_image_id = ome_image_id
        self.x = float(x)
        self.y = float(y)


class WellGrid:
    def __init__(self):
        self.__x_coords = set()
        self.__y_coords = set()

    def add_x_coord(self, value):
        self.__x_coords.add(value)

    def add_y_coord(self, value):
        self.__y_coords.add(value)

    def get_width(self):
        return len(self.__x_coords)

    def get_height(self):
        return len(self.__y_coords)

    def get_values_dict(self):
        return dict({y_coord: set(self.__x_coords) for y_coord in self.__y_coords})


class HcsFileTagProcessor:

    CATEGORICAL_ATTRIBUTE = '/categoricalAttribute'

    def __init__(self, hcs_img_path, xml_info_tree):
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

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        log_info('[{}] {}'.format(self.hcs_img_path, message), status=status)

    def process_tags(self):
        existing_attributes_dictionary = self.load_existing_attributes()
        ome_schema = HcsParsingUtils.extract_xml_schema(self.xml_info_tree)
        metadata = self.xml_info_tree.find(ome_schema + 'StructuredAnnotations')
        if not metadata:
            self.log_processing_info('No metadata found for file, skipping tags processing...')
            return 0
        metadata_dict = self.map_to_metadata_dict(ome_schema, metadata)
        tags_to_push = self.build_tags_dictionary(metadata_dict, existing_attributes_dictionary)
        if not tags_to_push:
            self.log_processing_info('No matching tags found')
            return 0
        pipe_tags = self.prepare_tags(existing_attributes_dictionary, tags_to_push)
        tags_to_push_str = ' '.join(pipe_tags)
        self.log_processing_info('Following tags will be assigned to the file: {}'.format(tags_to_push_str))
        url_encoded_cloud_file_path = HcsParsingUtils.extract_cloud_path(urllib.quote(self.hcs_img_path))
        return os.system('pipe storage set-object-tags "{}" {}'.format(url_encoded_cloud_file_path, tags_to_push_str))

    def build_tags_dictionary(self, metadata_dict, existing_attributes_dictionary):
        tags_dictionary = dict()
        common_tags_mapping = self.map_tags('HCS_PARSING_TAG_MAPPING', existing_attributes_dictionary)
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
                continue
            value = list(values_to_push)[0]
            if attribute_name not in existing_attributes_dictionary:
                self.log_processing_info('No categorical attribute [{}] exists'.format(attribute_name))
                continue
            existing_values = existing_attributes_dictionary[attribute_name]
            existing_attribute_id = existing_values[0]['attributeId']
            existing_values_names = [existing_value['value'] for existing_value in existing_values]
            if value not in existing_values_names:
                existing_values.append({'key': attribute_name, 'value': value})
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
                if value.startswith('[') and value.endswith(']'):
                    self.log_processing_info('Processing array value')
                    value = value[1:-1]
                    values = list(set(value.split(',')))
                    if len(values) != 1:
                        self.log_processing_info('Empty or multiple metadata values, skipping [{}]'
                                                 .format(key))
                        continue
                    value = values[0]
                target_tag = tags_mapping[key]
                if target_tag in tags_to_push:
                    tags_to_push[target_tag].add(value)
                else:
                    tags_to_push[target_tag] = {value}
        return tags_to_push

    def map_tags(self, tags_mapping_env_var_name, existing_attributes_dictionary):
        tags_mapping_rules_str = os.getenv(tags_mapping_env_var_name, '')
        tags_mapping_rules = tags_mapping_rules_str.split(TAGS_MAPPING_RULE_DELIMITER) if tags_mapping_rules_str else []
        tags_mapping = dict()
        for rule in tags_mapping_rules:
            rule_mapping = rule.split(TAGS_MAPPING_KEYS_DELIMITER, 1)
            if len(rule_mapping) != 2:
                self.log_processing_info('Error [{}]: mapping rule declaration should contain a delimiter!'.format(
                    rule_mapping))
                continue
            else:
                key = rule_mapping[0]
                value = rule_mapping[1]
                if value not in existing_attributes_dictionary:
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


class HcsFileParser:

    _OME_TIFF_TIMEPOINT_CREATION_SCRIPT = os.path.join(os.getenv('HCS_PARSER_HOME', '/opt/local/hcs-tools'),
                                                       'scripts/convert_to_ome_tiff.sh')

    def __init__(self, hcs_root_dir):
        self.hcs_root_dir = get_path_without_trailing_delimiter(hcs_root_dir)
        self.hcs_img_path = HcsParsingUtils.build_preview_file_path(hcs_root_dir)
        self.hcs_img_service_dir = HcsParsingUtils.get_service_directory(self.hcs_img_path)
        self.ome_xml_info_file_path = os.path.join(self.hcs_img_service_dir, 'info.ome.xml')
        self.stat_file_path = HcsParsingUtils.get_stat_file_name(self.hcs_img_path)
        self.tmp_stat_file_path = HcsParsingUtils.get_stat_active_file_name(self.hcs_img_path)
        self.tmp_local_dir = HcsParsingUtils.generate_local_service_directory(self.hcs_img_path)
        self.well_size_dict = json.loads(os.getenv('HCS_PARSING_PLATE_DETAILS_DICT', '{}'))
        self.parsing_start_time = None

    @staticmethod
    def generate_bioformats_ome_xml(input_file, output_file):
        exit_code = os.system('showinf -nopix -omexml-only "{}" > "{}"'.format(input_file, output_file))
        if exit_code != 0:
            raise RuntimeError('An error occurred during OME-XML generation [{}]'.format(input_file))

    @staticmethod
    def _extract_time_series_details(hcs_index_file_path):
        hcs_xml_info_tree = ET.parse(hcs_index_file_path).getroot()
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_tree)
        images_list = hcs_xml_info_tree.find(hcs_schema_prefix + 'Images')
        time_series_details = dict()
        for image in images_list.findall(hcs_schema_prefix + 'Image'):
            sequence_id = image.find(hcs_schema_prefix + 'SequenceID').text
            sequence_timepoints = time_series_details[sequence_id] if sequence_id in time_series_details else list()
            timepoint_id = image.find(hcs_schema_prefix + 'TimepointID').text
            if timepoint_id not in sequence_timepoints:
                sequence_timepoints.append(timepoint_id)
            time_series_details[sequence_id] = sequence_timepoints
        if len(time_series_details) == 0:
            time_series_details['1'] = ['1']
        return time_series_details

    @staticmethod
    def _get_plate_configuration(xml_info_tree):
        plate_height = 1
        plate_width = 1
        plate_details = HcsFileParser.extract_plate_from_ome_xml(xml_info_tree)
        if plate_details:
            plate_width = plate_details.get('Columns')
            plate_height = plate_details.get('Rows')
        return plate_width, plate_height

    @staticmethod
    def build_cartesian_coords_key(x_coord, y_coord):
        return PLANE_COORDINATES_DELIMITER.join([str(x_coord), str(y_coord)])

    @staticmethod
    def extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix=None):
        if not hcs_schema_prefix:
            hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        plates_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Plates')
        plate = plates_list.find(hcs_schema_prefix + 'Plate')
        return plate

    @staticmethod
    def extract_plate_from_ome_xml(ome_xml_info_root):
        ome_schema_prefix = HcsParsingUtils.extract_xml_schema(ome_xml_info_root)
        ome_plate = ome_xml_info_root.find(ome_schema_prefix + 'Plate')
        return ome_plate

    @staticmethod
    def calculate_wells_padding_for_ome(hcs_xml_info_root, ome_xml_info_root):
        wells_x_padding_hcs, \
            wells_y_padding_hcs = HcsFileParser.extract_first_well_coordinates_hcs_xml(hcs_xml_info_root)
        wells_x_padding_ome, \
            wells_y_padding_ome = HcsFileParser.extract_first_well_coordinates_ome_xml(ome_xml_info_root)
        return wells_x_padding_hcs - wells_x_padding_ome, wells_y_padding_hcs - wells_y_padding_ome

    @staticmethod
    def extract_first_well_coordinates_hcs_xml(hcs_xml_info_root):
        well_x = sys.maxint
        well_y = sys.maxint
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        hcs_wells = hcs_xml_info_root.find(hcs_schema_prefix + 'Wells')
        for well in hcs_wells.findall(hcs_schema_prefix + 'Well'):
            well_x_coord = int(well.find(hcs_schema_prefix + 'Col').text)
            well_y_coord = int(well.find(hcs_schema_prefix + 'Row').text)
            if well_y_coord < well_y:
                well_y = well_y_coord
            if well_x_coord < well_x:
                well_x = well_x_coord
        return well_x, well_y

    @staticmethod
    def extract_first_well_coordinates_ome_xml(ome_xml_info_root):
        well_x = sys.maxint
        well_y = sys.maxint
        ome_plate = HcsFileParser.extract_plate_from_ome_xml(ome_xml_info_root)
        ome_schema_prefix = HcsParsingUtils.extract_xml_schema(ome_xml_info_root)
        for well in ome_plate.findall(ome_schema_prefix + 'Well'):
            well_x_coord = int(well.get('Column'))
            well_y_coord = int(well.get('Row'))
            if well_y_coord < well_y:
                well_y = well_y_coord
            if well_x_coord < well_x:
                well_x = well_x_coord
        return well_x, well_y

    @staticmethod
    def compare_planar_2d_coords_key(coord_1, coord_2):
        coord_dims_1 = coord_1[0].split(PLANE_COORDINATES_DELIMITER)
        coord_dims_2 = coord_2[0].split(PLANE_COORDINATES_DELIMITER)
        x_1 = int(coord_dims_1[0])
        y_1 = int(coord_dims_1[1])
        x_2 = int(coord_dims_2[0])
        y_2 = int(coord_dims_2[1])
        if x_1 < x_2:
            return -1
        elif x_1 > x_2:
            return 1
        elif y_1 < y_2:
            return -1
        elif y_1 > y_2:
            return 1
        else:
            return 0

    @staticmethod
    def ordered_by_coords(dictionary):
        return OrderedDict(sorted(dictionary.items(),
                                  key=cmp_to_key(lambda c1, c2: HcsFileParser.compare_planar_2d_coords_key(c1, c2))))

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(HCS_PROCESSING_TASK_NAME, '[{}] {}'.format(self.hcs_root_dir, message), status=status)

    def generate_ome_xml_info_file(self):
        self.log_processing_info('Generating XML description')
        HcsParsingUtils.create_service_dir_if_not_exist(self.hcs_img_path)
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_PATH
        if HCS_INDEX_FILE_NAME != HCS_OME_COMPATIBLE_INDEX_FILE_NAME:
            compatible_index_path = os.path.join(self.hcs_img_service_dir, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
            shutil.copy(hcs_index_file_path, compatible_index_path)
            hcs_index_file_path = compatible_index_path
        self.generate_bioformats_ome_xml(hcs_index_file_path, self.ome_xml_info_file_path)

    def build_parsing_details(self):
        return {
            "bioformats2raw_extra_flags": os.getenv('BIOFORMATS2RAW_EXTRA_FLAGS'),
            "raw2ometiff_extra_flags": os.getenv('RAW2OMETIFF_EXTRA_FLAGS'),
            "start_time": self.parsing_start_time.strftime('%Y-%m-%d %H:%M:%S.%f')
        }

    def create_tmp_stat_file(self):
        self.write_dict_to_file(self.tmp_stat_file_path, self.build_parsing_details())

    def create_stat_file(self):
        self.write_dict_to_file(self.stat_file_path, self.build_parsing_details())

    @staticmethod
    def write_dict_to_file(file_path, dictionary):
        HcsFileParser._mkdir(os.path.dirname(file_path))
        with open(file_path, 'w') as output_file:
            output_file.write(json.dumps(dictionary, indent=4))

    def clear_tmp_stat_file(self):
        if os.path.exists(self.tmp_stat_file_path):
            self.log_processing_info('Cleaning up temporary processing file: [{}]'.format(self.tmp_stat_file_path))
            os.remove(self.tmp_stat_file_path)

    def clear_tmp_local_dir(self):
        if os.path.exists(self.tmp_local_dir):
            self.log_processing_info('Cleaning up temporary dir: [{}]'.format(self.tmp_local_dir))
            shutil.rmtree(self.tmp_local_dir)

    def _write_hcs_file(self, time_series_details, plate_width, plate_height, comment=None):
        if os.getenv('HCS_PARSING_PREVIEW_FIELDS_USE_ABSOLUTE_PATHS') == 'true':
            source_dir = HcsParsingUtils.extract_cloud_path(self.hcs_root_dir)
            preview_dir = HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)
        else:
            hcs_file_root_dir = os.path.dirname(self.hcs_img_path)
            source_dir = os.path.relpath(self.hcs_root_dir, hcs_file_root_dir)
            preview_dir = os.path.relpath(self.hcs_img_service_dir, hcs_file_root_dir)
        ome_data_file_name = os.getenv('HCS_PARSING_OME_TIFF_FILE_NAME', 'data.ome.tiff')
        ome_offsets_file_name = ome_data_file_name[:ome_data_file_name.find('.')] + '.offsets.json'
        details = {
            'sourceDir': source_dir,
            'previewDir': preview_dir,
            'time_series_details': time_series_details,
            'plate_height': plate_height,
            'plate_width': plate_width,
            'comment': comment,
            'ome_data_file_name': ome_data_file_name,
            'ome_offsets_file_name': ome_offsets_file_name
        }
        self.log_processing_info('Saving preview info [source={}; preview={}] to [{}]'
                                 .format(self.hcs_root_dir, self.hcs_img_service_dir, self.hcs_img_path))
        self.write_dict_to_file(self.hcs_img_path, details)

    @staticmethod
    def _mkdir(path):
        try:
            os.makedirs(path)
        except OSError as e:
            if e.errno == errno.EEXIST and os.path.isdir(path):
                pass
            else:
                return False
        return True

    def _localize_related_files(self):
        hcs_root_cloud_path = HcsParsingUtils.extract_cloud_path(self.hcs_root_dir)
        local_tmp_dir_trailing = get_path_with_trailing_delimiter(self.tmp_local_dir)
        self.log_processing_info('Localizing data files...')
        localization_result = os.system('pipe storage cp -f -r "{}" "{}"'.format(hcs_root_cloud_path,
                                                                                 local_tmp_dir_trailing))
        return localization_result == 0

    def process_file(self):
        """Process the specified HCS file

        Returns
        -------
        exit_code
            an integer, describing whether operation was successful or not:
            0 - processed successfully
            1 - some errors occurred during the processing
            2 - processing is skipped
        """
        self.log_processing_info('Start processing')
        self.parsing_start_time = datetime.datetime.now()
        if os.path.exists(self.tmp_stat_file_path) \
                and not HcsParsingUtils.active_processing_exceed_timeout(self.tmp_stat_file_path):
            self.log_processing_info('This file is processed by another parser, skipping...')
            return 2
        self.create_tmp_stat_file()
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_PATH
        time_series_details = self._extract_time_series_details(hcs_index_file_path)
        self.generate_ome_xml_info_file()
        xml_info_tree = ET.parse(self.ome_xml_info_file_path).getroot()
        plate_width, plate_height = self._get_plate_configuration(xml_info_tree)
        if not TAGS_PROCESSING_ONLY:
            if not self._localize_related_files():
                self.log_processing_info('Some errors occurred during copying files from the bucket, exiting...')
                return 1
            else:
                self.log_processing_info('Localization is finished.')
            local_preview_dir = os.path.join(self.tmp_local_dir, 'preview')
            hcs_local_index_file_path = get_path_without_trailing_delimiter(self.tmp_local_dir) \
                                        + MEASUREMENT_INDEX_FILE_PATH
            for sequence_id, timepoints in time_series_details.items():
                self.log_processing_info('Processing sequence with id={}'.format(sequence_id))
                sequence_index_file_path = self.extract_sequence_data(sequence_id, hcs_local_index_file_path)
                conversion_result = os.system('bash "{}" "{}" "{}" {}'.format(
                    self._OME_TIFF_TIMEPOINT_CREATION_SCRIPT, sequence_index_file_path, local_preview_dir, sequence_id))
                if conversion_result != 0:
                    self.log_processing_info('File processing was not successful...')
                    return 1
                sequence_overview_index_file_path = self.build_sequence_overview_index(sequence_index_file_path)
                conversion_result = os.system('bash "{}" "{}" "{}" {} "{}"'.format(
                    self._OME_TIFF_TIMEPOINT_CREATION_SCRIPT, sequence_overview_index_file_path, local_preview_dir,
                    sequence_id, 'overview_data.ome.tiff'))
                if conversion_result != 0:
                    self.log_processing_info('File processing was not successful: well preview generation failure')
                    return 1
                self.write_dict_to_file(os.path.join(local_preview_dir, sequence_id, 'wells_map.json'),
                                        self.build_wells_map(sequence_id))
            cloud_transfer_result = os.system('pipe storage cp -f -r "{}" "{}"'
                                              .format(local_preview_dir,
                                                      HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)))
            if cloud_transfer_result != 0:
                self.log_processing_info('Results transfer was not successful...')
                return 1
        self._write_hcs_file(time_series_details, plate_width, plate_height)
        tags_processing_result = self.try_process_tags(xml_info_tree)
        if TAGS_PROCESSING_ONLY:
            return tags_processing_result
        self.create_stat_file()
        return 0

    def extract_sequence_data(self, target_sequence_id, hcs_local_index_file_path):
        hcs_xml_info_tree = ET.parse(hcs_local_index_file_path)
        hcs_xml_info_root = hcs_xml_info_tree.getroot()
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        images_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Images')
        sequence_data_local_dir = os.path.join(self.tmp_local_dir, target_sequence_id)
        self._mkdir(sequence_data_local_dir)
        src_images_dir = os.path.dirname(hcs_local_index_file_path)
        sequence_image_ids = set()
        images = images_list.findall(hcs_schema_prefix + 'Image')
        image_subfolders = set()
        for image in images:
            file_name = image.find(hcs_schema_prefix + 'URL').text
            last_delim_index = file_name.rfind('/')
            if last_delim_index > 0:
                image_subfolders.add(file_name[:last_delim_index])
        for path in image_subfolders:
            self._mkdir(os.path.join(sequence_data_local_dir, path))
            self._mkdir(os.path.join(sequence_data_local_dir, OVERVIEW_DIR_NAME, path))
        for image in images:
            sequence_id = image.find(hcs_schema_prefix + 'SequenceID').text
            if sequence_id != target_sequence_id:
                images_list.remove(image)
            else:
                sequence_image_ids.add(image.find(hcs_schema_prefix + 'id').text)
                file_name = image.find(hcs_schema_prefix + 'URL').text
                src_file_path = os.path.join(src_images_dir, file_name)
                dest_file_path = os.path.join(sequence_data_local_dir, file_name)
                shutil.move(src_file_path, dest_file_path)
        sequence_wells = set()
        wells_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Wells')
        for well in wells_list.findall(hcs_schema_prefix + 'Well'):
            well_images = well.findall(hcs_schema_prefix + 'Image')
            exists_in_sequence = False
            for image in well_images:
                if image.get('id') in sequence_image_ids:
                    exists_in_sequence = True
                    sequence_wells.add(well.find(hcs_schema_prefix + 'id').text)
                else:
                    well.remove(image)
            if not exists_in_sequence:
                wells_list.remove(well)
        plate = self.extract_plate_from_hcs_xml(hcs_xml_info_root)
        for well in plate.findall(hcs_schema_prefix + 'Well'):
            if well.get('id') not in sequence_wells:
                plate.remove(well)
        sequence_index_file_path = os.path.join(sequence_data_local_dir, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        ET.register_namespace('', hcs_schema_prefix[1:-1])
        hcs_xml_info_tree.write(sequence_index_file_path)
        return sequence_index_file_path

    def build_wells_map(self, sequence_id):
        hcs_index_file_path = os.path.join(self.tmp_local_dir, sequence_id, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        ome_xml_file_path = os.path.join(os.path.dirname(hcs_index_file_path), 'Index.ome.xml')
        self.generate_bioformats_ome_xml(hcs_index_file_path, ome_xml_file_path)
        preview_hcs_index_file_path = os.path.join(self.tmp_local_dir, sequence_id, OVERVIEW_DIR_NAME,
                                                   HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        preview_ome_xml_file_path = os.path.join(os.path.dirname(preview_hcs_index_file_path), 'Index.ome.xml')
        self.generate_bioformats_ome_xml(preview_hcs_index_file_path, preview_ome_xml_file_path)
        hcs_xml_info_root = ET.parse(hcs_index_file_path).getroot()
        ome_xml_info_root = ET.parse(ome_xml_file_path).getroot()
        wells_x_padding, wells_y_padding = self.calculate_wells_padding_for_ome(hcs_xml_info_root, ome_xml_info_root)

        ome_plate = self.extract_plate_from_ome_xml(ome_xml_info_root)
        ome_schema_prefix = HcsParsingUtils.extract_xml_schema(ome_xml_info_root)
        measured_wells = self.find_measured_wells(ome_plate, ome_schema_prefix, wells_x_padding, wells_y_padding)

        wells_mapping = dict()
        is_well_round, well_size = self.extract_well_configuration(hcs_xml_info_root)
        for well_key, fields_list in measured_wells.items():
            wells_mapping[well_key] = self.build_well_details(fields_list, well_size, is_well_round)
        preview_ome_xml_info_root = ET.parse(preview_ome_xml_file_path).getroot()
        preview_ome_plate = self.extract_plate_from_ome_xml(preview_ome_xml_info_root)
        for well in preview_ome_plate.findall(ome_schema_prefix + 'Well'):
            well_x_coord = int(well.get('Column')) + wells_x_padding
            well_y_coord = int(well.get('Row')) + wells_y_padding
            coords_key = self.build_cartesian_coords_key(well_x_coord, well_y_coord)
            if coords_key in wells_mapping:
                well_sample = well.find(ome_schema_prefix + 'WellSample')
                well_image_id = well_sample.find(ome_schema_prefix + 'ImageRef').get('ID')
                well_details = wells_mapping[coords_key]
                well_details['well_overview'] = well_image_id
                wells_mapping[coords_key] = well_details
        return HcsFileParser.ordered_by_coords(wells_mapping)

    def build_well_details(self, fields_list, well_size, is_well_round):
        x_coords = set()
        y_coords = set()
        for field in fields_list:
            x_coords.add(field.x)
            y_coords.add(field.y)
        x_coords = list(x_coords)
        x_coords.sort()
        y_coords = list(y_coords)
        y_coords.sort()
        if len(x_coords) > 1:
            well_radius = well_size / 2
            field_cell_width = x_coords[1] - x_coords[0]
            well_viewer_radius = well_radius / field_cell_width
            well_view_height = int(math.ceil(well_viewer_radius) * 2)
            well_view_width = int(math.ceil(well_viewer_radius) * 2)
            radius_rounding_padding = 1 if int(round(well_viewer_radius % 1)) == 0 else 0
            x_coord_padding = int(round((well_radius + x_coords[0]) / field_cell_width)) + radius_rounding_padding
            y_coord_padding = int(round((well_radius + y_coords[0]) / field_cell_width)) + radius_rounding_padding
        else:
            well_viewer_radius = None
            x_coord_padding = 0
            y_coord_padding = 0
            well_view_width = len(x_coords)
            well_view_height = len(y_coords)
        to_ome_mapping = dict()
        for field in fields_list:
            field_x_coord = x_coords.index(field.x) + 1 + x_coord_padding
            field_y_coord = y_coords.index(field.y) + 1 + y_coord_padding
            to_ome_mapping[self.build_cartesian_coords_key(field_x_coord, field_y_coord)] = field.ome_image_id
        well_details = {
            'width': well_view_width,
            'height': well_view_height,
            'round_radius': round(well_viewer_radius, 2) if is_well_round else None,
            'to_ome_wells_mapping': HcsFileParser.ordered_by_coords(to_ome_mapping)
        }
        return well_details

    def find_measured_wells(self, ome_plate, ome_schema_prefix, wells_x_padding, wells_y_padding):
        measured_wells = {}
        for well in ome_plate.findall(ome_schema_prefix + 'Well'):
            well_x_coord = int(well.get('Column')) + wells_x_padding
            well_y_coord = int(well.get('Row')) + wells_y_padding
            well_fields = set()
            for field in well.findall(ome_schema_prefix + 'WellSample'):
                field_x_coord = field.get('PositionX')
                field_y_coord = field.get('PositionY')
                if field_x_coord is not None and field_y_coord is not None:
                    ome_image_id = field.find(ome_schema_prefix + 'ImageRef').get('ID')
                    well_fields.add(
                        FieldDetails(well_x_coord, well_y_coord, ome_image_id, field_x_coord, field_y_coord))
            if len(well_fields) > 0:
                measured_wells[self.build_cartesian_coords_key(well_x_coord, well_y_coord)] = well_fields
        return measured_wells

    def extract_well_configuration(self, hcs_xml_info_root):
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        plate = self.extract_plate_from_hcs_xml(hcs_xml_info_root)
        plate_type = plate.find(hcs_schema_prefix + 'PlateTypeName').text
        well_configuration = self.well_size_dict[plate_type]
        well_size = well_configuration['size']
        is_well_round = True if 'is_round' not in well_configuration else well_configuration['is_round'] == 'false'
        return is_well_round, well_size

    def try_process_tags(self, xml_info_tree):
        tags_processing_result = 0
        try:
            if HcsFileTagProcessor(self.hcs_img_path, xml_info_tree).process_tags() != 0:
                self.log_processing_info('Some errors occurred during file tagging')
                tags_processing_result = 1
        except Exception as e:
            log_info('An error occurred during tags processing: {}'.format(str(e)))
            print(traceback.format_exc())
            tags_processing_result = 1
        return tags_processing_result

    def build_sequence_overview_index(self, sequence_index_file_path):
        hcs_xml_info_tree = ET.parse(sequence_index_file_path)
        hcs_xml_info_root = hcs_xml_info_tree.getroot()
        sequence_data_root_path = os.path.dirname(sequence_index_file_path)
        sequence_preview_dir_path = os.path.join(sequence_data_root_path, OVERVIEW_DIR_NAME)
        self._mkdir(sequence_preview_dir_path)
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        original_images_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Images')
        hcs_xml_info_root.remove(original_images_list)
        wells_grid_mapping = self.get_wells_grid_mapping(hcs_schema_prefix, original_images_list)
        channel_dimensions = self.get_channel_dimensions(hcs_xml_info_root, wells_grid_mapping)
        self.log_processing_info('Scaling overview TIFF files...')
        well_layers, well_grid_missing_values = self.build_well_layers(original_images_list, sequence_data_root_path,
                                                                       channel_dimensions, sequence_preview_dir_path,
                                                                       wells_grid_mapping)
        wells_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Wells')
        self.log_processing_info('Merging overview TIFF files...')
        for well in wells_list.findall(hcs_schema_prefix + 'Well'):
            self.merge_well_layers(original_images_list, sequence_preview_dir_path, well, well_layers,
                                   well_grid_missing_values)
        hcs_xml_info_root.append(original_images_list)
        preview_sequence_index_file_path = os.path.join(sequence_preview_dir_path, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        ET.register_namespace('', hcs_schema_prefix[1:-1])
        hcs_xml_info_tree.write(preview_sequence_index_file_path)
        return preview_sequence_index_file_path

    def merge_well_layers(self, original_images_list, sequence_preview_dir_path, well, well_layers,
                          well_grid_missing_values):
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(original_images_list)
        [well.remove(image) for image in well.findall(hcs_schema_prefix + 'Image')]
        row_id = well.find(hcs_schema_prefix + 'Row').text
        column_id = well.find(hcs_schema_prefix + 'Col').text
        well_id = tuple([column_id, row_id])
        well_details = well_layers[well_id]
        for timepoint_id, timepoint_images in well_details.items():
            for plane_id, plane_channels in timepoint_images.items():
                for channel_id, channel_images in plane_channels.items():
                    well_preview_full_name = 'r{}c{}p{}-ch{}sk{}.tiff' \
                        .format(row_id, column_id, plane_id, channel_id, timepoint_id)
                    vertically_sorted_mapping = OrderedDict(
                        sorted(channel_images.items(), key=cmp_to_key(lambda y1, y2: float(y2[0]) - float(y1[0]))))
                    well_rows_data = vertically_sorted_mapping.items()
                    well_grid_missing_channel_planes = {}
                    if channel_id in well_grid_missing_values:
                        channel_missing_values = well_grid_missing_values[channel_id]
                        if well_id in channel_missing_values:
                            well_grid_missing_channel_planes = channel_missing_values[well_id]
                    rows_image_paths = self.merge_images_by_row(hcs_schema_prefix, sequence_preview_dir_path,
                                                                well_preview_full_name, well_rows_data,
                                                                well_grid_missing_channel_planes)
                    well_preview_full_path = HcsParsingUtils.quote_string(
                        os.path.join(sequence_preview_dir_path, well_preview_full_name))
                    exit_code = os.system('convert -append {} {}'
                                          .format(' '.join(rows_image_paths), well_preview_full_path))
                    if exit_code != 0:
                        raise RuntimeError('Error during overview rows'' merging')
                    bottom_row_details = well_rows_data[0]
                    well_preview_details = bottom_row_details[1][0]
                    well_preview_details.find(hcs_schema_prefix + 'FieldID').text = '1'
                    well_preview_details.find(hcs_schema_prefix + 'URL').text = well_preview_full_name
                    image_id = '{}K{}F1P{}R{}'.format(well.find(hcs_schema_prefix + 'id').text,
                                                      timepoint_id, plane_id, channel_id)
                    well_preview_details.find(hcs_schema_prefix + 'id').text = image_id
                    well_preview_details.find(hcs_schema_prefix + 'PositionY').text = \
                        vertically_sorted_mapping.keys()[-1]
                    original_images_list.append(well_preview_details)
                    well.append(ET.fromstring('<Image id="{}" />'.format(image_id)))

    def merge_images_by_row(self, hcs_schema_prefix, sequence_preview_dir_path, well_preview_full_name, well_rows_data,
                            well_grid_missing_channel_planes):
        rows_image_paths = list()
        for y_coord, images_list in well_rows_data:
            row_image_chunks_names = {}
            cropped_width, cropped_height = self.get_cropped_size(sequence_preview_dir_path, images_list)
            for image in images_list:
                x_coord = image.find(hcs_schema_prefix + 'PositionX').text
                file_path = HcsParsingUtils.quote_string(os.path.join(sequence_preview_dir_path,
                                                                      image.find(hcs_schema_prefix + 'URL').text))
                row_image_chunks_names[x_coord] = file_path
            if y_coord in well_grid_missing_channel_planes:
                for x_coord in well_grid_missing_channel_planes[y_coord]:
                    empty_file_name = HcsParsingUtils.quote_string(
                        tempfile.mkstemp(dir=sequence_preview_dir_path, suffix='.tiff',
                                         prefix='empty-{}-{}'.format(y_coord, x_coord))[1])
                    os.system('convert -size {}x{} xc:#000000 {}'
                              .format(cropped_width, cropped_height, empty_file_name))
                    row_image_chunks_names[x_coord] = empty_file_name
            sorted_images = OrderedDict(sorted(row_image_chunks_names.items(),
                                               key=cmp_to_key(lambda x1, x2: float(x1[0]) - float(x2[0]))))
            row_image_name = HcsParsingUtils.quote_string(
                os.path.join(sequence_preview_dir_path, '{}y-{}'.format(y_coord, well_preview_full_name)))
            exit_code = os.system('convert +append {} {}'.format(' '.join(sorted_images.values()), row_image_name))
            if exit_code != 0:
                raise RuntimeError('Error during y={} row overview merging'.format(y_coord))
            rows_image_paths.append(row_image_name)
        return rows_image_paths

    def get_cropped_size(self, sequence_preview_dir_path, images_list):
        image = images_list[0]
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(image)
        full_image_path = os.path.join(sequence_preview_dir_path, image.find(hcs_schema_prefix + 'URL').text)
        row_image_descriptor = Image.open(full_image_path)
        return row_image_descriptor.width, row_image_descriptor.height

    def build_well_layers(self, original_images_root, sequence_data_root_path, channel_dimensions,
                          sequence_preview_dir_path, wells_grid_mapping):
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(original_images_root)
        original_images_list = original_images_root.findall(hcs_schema_prefix + 'Image')
        well_grouping = dict()
        well_grid_missing_values = dict()
        for well_id, grid_details in wells_grid_mapping.items():
            for channel_id in channel_dimensions.keys():
                if channel_id not in well_grid_missing_values:
                    well_grid_missing_values[channel_id] = {well_id: grid_details.get_values_dict()}
                else:
                    well_grid_missing_values[channel_id][well_id] = grid_details.get_values_dict()
        print(well_grid_missing_values)
        for image in original_images_list:
            well_id = self.add_image_summary_to_mapping(hcs_schema_prefix, image, well_grouping)
            original_images_root.remove(image)
            file_name = image.find(hcs_schema_prefix + 'URL').text
            src_file = os.path.join(sequence_data_root_path, file_name)
            resized_file = os.path.join(sequence_preview_dir_path, file_name)
            well_grid_details = wells_grid_mapping[well_id]
            channel_id = image.find(hcs_schema_prefix + 'ChannelID').text
            y_coord = image.find(hcs_schema_prefix + 'PositionY').text
            x_coord = image.find(hcs_schema_prefix + 'PositionX').text
            channel_width, channel_height = self.find_channel_dimensions(channel_dimensions, channel_id)
            resize_width = str(channel_width / well_grid_details.get_width())
            resize_height = str(channel_height / well_grid_details.get_height())
            exit_code = os.system('convert "{}" -resize {}x{} "{}"'
                                  .format(src_file, resize_width, resize_height, resized_file))
            x_coords = well_grid_missing_values[channel_id][well_id][y_coord]
            if x_coords and x_coord in x_coords:
                x_coords.remove(x_coord)
                well_grid_missing_values[channel_id][well_id][y_coord] = x_coords
            if exit_code != 0:
                raise RuntimeError(
                    'An error occurred during [{}] resizing: {}x{} to {}x{}, exit code {}'
                        .format(file_name, channel_width, channel_height, resize_width, resize_height, exit_code))
        for channel_id, channel_map in well_grid_missing_values.items():
            for well_id, well_map in channel_map.items():
                for y_coord, x_coords in well_map.items():
                    if len(x_coords) == 0:
                        well_map.pop(y_coord)
                if len(well_map) == 0:
                    channel_map.pop(well_id)
        return well_grouping, well_grid_missing_values

    def find_channel_dimensions(self, channel_dimensions, channel_id):
        if channel_id in channel_dimensions:
            channel_dimension = channel_dimensions[channel_id]
            channel_width = channel_dimension[0]
            channel_height = channel_dimension[1]
        else:
            channel_width = DEFAULT_CHANNEL_WIDTH
            channel_height = DEFAULT_CHANNEL_HEIGHT
        return channel_width, channel_height

    def add_image_summary_to_mapping(self, hcs_schema_prefix, image, well_grouping):
        row_id = image.find(hcs_schema_prefix + 'Row').text
        column_id = image.find(hcs_schema_prefix + 'Col').text
        timepoint_id = image.find(hcs_schema_prefix + 'TimepointID').text
        plane_id = image.find(hcs_schema_prefix + 'PlaneID').text
        channel_id = image.find(hcs_schema_prefix + 'ChannelID').text
        y_coord_value = image.find(hcs_schema_prefix + 'PositionY').text
        well_id = tuple([column_id, row_id])
        if well_id in well_grouping:
            well_timepoints_mapping = well_grouping[well_id]
            if timepoint_id in well_timepoints_mapping:
                well_timepoint_planes_mapping = well_timepoints_mapping[timepoint_id]
                if plane_id in well_timepoint_planes_mapping:
                    well_timepoint_plane_channels_mapping = well_timepoint_planes_mapping[plane_id]
                    if channel_id in well_timepoint_plane_channels_mapping:
                        well_timepoint_plane_channel_y_coord_mapping = well_timepoint_plane_channels_mapping[channel_id]
                        if y_coord_value in well_timepoint_plane_channel_y_coord_mapping:
                            well_timepoint_plane_channel_y_coord_mapping[y_coord_value].append(image)
                        else:
                            well_timepoint_plane_channel_y_coord_mapping[y_coord_value] = [image]
                    else:
                        well_timepoint_plane_channels_mapping[channel_id] = {y_coord_value: [image]}
                else:
                    well_timepoint_planes_mapping[plane_id] = {channel_id: {y_coord_value: [image]}}
            else:
                well_timepoints_mapping[timepoint_id] = {plane_id: {channel_id: {y_coord_value: [image]}}}
        else:
            well_grouping[well_id] = {timepoint_id: {plane_id: {channel_id: {y_coord_value: [image]}}}}
        return well_id

    def get_wells_grid_mapping(self, hcs_schema_prefix, original_images_list):
        wells_grid_mapping = dict()
        for image in original_images_list:
            row_id = image.find(hcs_schema_prefix + 'Row').text
            column_id = image.find(hcs_schema_prefix + 'Col').text
            y_coord_value = image.find(hcs_schema_prefix + 'PositionY').text
            x_coord_value = image.find(hcs_schema_prefix + 'PositionX').text
            well_id = tuple([column_id, row_id])
            well_grid_details = wells_grid_mapping.get(well_id, WellGrid())
            well_grid_details.add_x_coord(x_coord_value)
            well_grid_details.add_y_coord(y_coord_value)
            wells_grid_mapping[well_id] = well_grid_details
        return wells_grid_mapping

    def get_channel_dimensions(self, hcs_file_root, wells_grid_mapping):
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_file_root)
        y_scaling = 1
        x_scaling = 1
        for well_grid in wells_grid_mapping.values():
            y_scaling = max(y_scaling, well_grid.get_height())
            x_scaling = max(x_scaling, well_grid.get_height())
        channel_dimensions = dict()
        for channel_map in hcs_file_root.find(hcs_schema_prefix + 'Maps').findall(hcs_schema_prefix + 'Map'):
            for entry in channel_map.findall(hcs_schema_prefix + 'Entry'):
                channel_size_x = entry.find(hcs_schema_prefix + 'ImageSizeX')
                if channel_size_x is not None:
                    channel_size_x = int(channel_size_x.text)
                    channel_size_y = int(entry.find(hcs_schema_prefix + 'ImageSizeY').text)
                    channel_id = entry.get('ChannelID')
                    channel_dimensions[channel_id] = tuple([channel_size_x, channel_size_y])
                    resolution_x = entry.find(hcs_schema_prefix + 'ImageResolutionX').text
                    resolution_y = entry.find(hcs_schema_prefix + 'ImageResolutionY').text
                    entry.find(hcs_schema_prefix + 'ImageResolutionX').text = \
                        str(float(resolution_x) * x_scaling).upper()
                    entry.find(hcs_schema_prefix + 'ImageResolutionY').text = \
                        str(float(resolution_y) * y_scaling).upper()
        return channel_dimensions


def try_process_hcs(hcs_root_dir):
    parser = None
    processing_result = 1
    try:
        parser = HcsFileParser(hcs_root_dir)
        processing_result = parser.process_file()
        return processing_result
    except Exception as e:
        log_info('An error occurred during [{}] parsing: {}'.format(hcs_root_dir, str(e)))
        print(traceback.format_exc())
    finally:
        if parser:
            if processing_result != 2:
                parser.clear_tmp_stat_file()
            parser.clear_tmp_local_dir()


def remove_not_empty_string(string_list):
    return filter(lambda string: string is not None and len(string.strip()) > 0, string_list)


def process_hcs_files():
    paths_to_hcs_roots = remove_not_empty_string(os.getenv('HCS_TARGET_DIRECTORIES', '').split(','))
    if len(paths_to_hcs_roots) == 0:
        lookup_paths = remove_not_empty_string(os.getenv('HCS_LOOKUP_DIRECTORIES', '').split(','))
        if not lookup_paths:
            log_success('No paths for HCS processing specified')
            exit(0)
        log_info('Following paths are specified for processing: {}'.format(lookup_paths))
        log_info('Lookup for unprocessed files')
        paths_to_hcs_roots = HcsProcessingDirsGenerator(lookup_paths).generate_paths()
        if not paths_to_hcs_roots:
            log_success('Found no files requires processing in the lookup directories.')
            exit(0)
        log_info('Found {} files for processing.'.format(len(paths_to_hcs_roots)))
    else:
        log_info('{} files for processing are specified.'.format(len(paths_to_hcs_roots)))
    processing_threads = int(os.getenv('HCS_PARSER_PROCESSING_THREADS', 1))
    if processing_threads < 1:
        log_info('Invalid number of threads [{}] is specified for processing, use single one instead'
                 .format(processing_threads))
        processing_threads = 1
    log_info('{} thread(s) enabled for HCS processing'.format(processing_threads))
    if TAGS_PROCESSING_ONLY:
        log_info('Only tags will be processed, since TAGS_PROCESSING_ONLY is set to `true`')
    if processing_threads == 1:
        for file_path in paths_to_hcs_roots:
            try_process_hcs(file_path)
    else:
        pool = multiprocessing.Pool(processing_threads)
        pool.map(try_process_hcs, paths_to_hcs_roots)
    log_success('Finished HCS files processing')
    exit(0)


if __name__ == '__main__':
    process_hcs_files()
