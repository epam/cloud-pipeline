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

import errno
import json
import os
import multiprocessing
import datetime
import shutil
import tempfile
import time
import traceback
import urllib
import xml.etree.ElementTree as ET

from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger
from pipeline.common import get_path_with_trailing_delimiter, get_path_without_trailing_delimiter


MAGNIFICATION_CAT_ATTR_NAME = os.getenv('HCS_PARSING_MAGNIFICATION_CAT_ATTR_NAME', 'Magnification')
HCS_ACTIVE_PROCESSING_TIMEOUT_MIN = int(os.getenv('HCS_ACTIVE_PROCESSING_TIMEOUT_MIN', 360))
TAGS_PROCESSING_ONLY = os.getenv('HCS_PARSING_TAGS_ONLY', 'false') == 'true'
FORCE_PROCESSING = os.getenv('HCS_FORCE_PROCESSING', 'false') == 'true'
HCS_CLOUD_FILES_SCHEMA = os.getenv('HCS_CLOUD_FILES_SCHEMA', 's3')

HCS_PROCESSING_TASK_NAME = 'HCS processing'
MEASUREMENT_INDEX_FILE_SUFFIX = '/Images/Index.xml'
OME_SCHEMA_PREFIX = '{http://www.openmicroscopy.org/Schemas/OME/2016-06}'
UNKNOWN_ATTRIBUTE_VALUE = 'NA'
HYPHEN = '-'
TAGS_MAPPING_RULE_DELIMITER = ','
TAGS_MAPPING_KEYS_DELIMITER = '='


class HcsParsingUtils:

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
        return HcsParsingUtils.get_file_without_extension(hcs_root_folder_path) + '.hcs'

    @staticmethod
    def get_stat_active_file_name(hcs_img_path):
        return HcsParsingUtils._get_service_file_name(hcs_img_path, 'hcsparser.inprog')

    @staticmethod
    def get_stat_file_name(hcs_img_path):
        return HcsParsingUtils._get_service_file_name(hcs_img_path, 'hcsparser')

    @staticmethod
    def get_service_directory(hcs_img_path):
        name_without_extension = HcsParsingUtils.get_basename_without_extension(hcs_img_path)
        parent_dir = os.path.dirname(hcs_img_path)
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
        return filter(lambda p: self.is_processing_required(p), hcs_roots)

    def find_all_hcs_roots(self):
        hcs_roots = set()
        for lookup_path in self.lookup_paths:
            dir_walk_root = os.walk(lookup_path)
            for dir_root, directories, files in dir_walk_root:
                for file in files:
                    full_file_path = os.path.join(dir_root, file)
                    if full_file_path.endswith(MEASUREMENT_INDEX_FILE_SUFFIX):
                        hcs_roots.add(full_file_path[:-len(MEASUREMENT_INDEX_FILE_SUFFIX)])
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
    def map_to_metadata_dict(metadata):
        metadata_entries = metadata.findall(OME_SCHEMA_PREFIX + 'XMLAnnotation')
        metadata_dict = dict()
        for entry in metadata_entries:
            entry_value = entry.find(OME_SCHEMA_PREFIX + 'Value')
            if entry_value is not None:
                metadata_record = entry_value.find(OME_SCHEMA_PREFIX + 'OriginalMetadata')
                if metadata_record is not None:
                    key = metadata_record.find(OME_SCHEMA_PREFIX + 'Key').text
                    value = metadata_record.find(OME_SCHEMA_PREFIX + 'Value').text
                    if key and value:
                        metadata_dict[key] = value
        return metadata_dict

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(HCS_PROCESSING_TASK_NAME, '[{}] {}'.format(self.hcs_img_path, message), status=status)

    def process_tags(self):
        existing_attributes_dictionary = self.load_existing_attributes()
        metadata = self.xml_info_tree.find(OME_SCHEMA_PREFIX + 'StructuredAnnotations')
        if not metadata:
            self.log_processing_info('No metadata found for file, skipping tags processing...')
            return 0
        metadata_dict = self.map_to_metadata_dict(metadata)
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
        instrument_details = self.xml_info_tree.find(OME_SCHEMA_PREFIX + 'Instrument')
        if len(instrument_details) > 0:
            objectives_details = instrument_details.find(OME_SCHEMA_PREFIX + 'Objective')
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

    def generate_ome_xml_info_file(self):
        self.log_processing_info('Generating XML description')
        HcsParsingUtils.create_service_dir_if_not_exist(self.hcs_img_path)
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_SUFFIX
        os.system('showinf -nopix -omexml-only "{}" > "{}"'.format(hcs_index_file_path, self.ome_xml_info_file_path))

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(HCS_PROCESSING_TASK_NAME, '[{}] {}'.format(self.hcs_root_dir, message), status=status)

    def create_tmp_stat_file(self):
        self.write_dict_to_file(self.tmp_stat_file_path, {})

    def create_stat_file(self):
        self.write_dict_to_file(self.stat_file_path, {})

    def write_dict_to_file(self, file_path, dict):
        self._mkdir(os.path.dirname(file_path))
        with open(file_path, 'w') as output_file:
            output_file.write(json.dumps(dict, indent=4))

    @staticmethod
    def _extract_time_series_details(hcs_index_file_path):
        hcs_xml_info_tree = ET.parse(hcs_index_file_path).getroot()
        hcs_schema_prefix = hcs_xml_info_tree.tag[:hcs_xml_info_tree.tag.rindex('}') + 1]
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
        plate_details = xml_info_tree.find(OME_SCHEMA_PREFIX + 'Plate')
        plate_height = 1
        plate_width = 1
        if plate_details:
            plate_width = plate_details.get('Columns')
            plate_height = plate_details.get('Rows')
        return plate_width, plate_height

    def clear_tmp_stat_file(self):
        if os.path.exists(self.tmp_stat_file_path):
            os.remove(self.tmp_stat_file_path)

    def clear_tmp_local_dir(self):
        if os.path.exists(self.tmp_local_dir):
            shutil.rmtree(self.tmp_local_dir)

    def _write_hcs_file(self, timepoints_count, plate_width, plate_height, comment=None):
        details = {
            'sourceDir': HcsParsingUtils.extract_cloud_path(self.hcs_root_dir),
            'previewDir': HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir),
            'time_series_details': timepoints_count,
            'plate_height': plate_height,
            'plate_width': plate_width,
            'comment': comment
        }
        self.log_processing_info('Saving preview info [source={}; preview={}] to [{}]'
                                 .format(self.hcs_root_dir, self.hcs_img_service_dir, self.hcs_img_path))
        self.write_dict_to_file(self.hcs_img_path, details)

    def _mkdir(self, path):
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
        localization_result = os.system('pipe storage cp -f -r "{}" "{}"'.format(hcs_root_cloud_path,
                                                                                 local_tmp_dir_trailing))
        return localization_result == 0

    def process_file(self):
        self.log_processing_info('Start processing')
        if os.path.exists(self.tmp_stat_file_path) \
                and not HcsParsingUtils.active_processing_exceed_timeout(self.tmp_stat_file_path):
            self.log_processing_info('This file is processed by another parser, skipping...')
            return 0
        self.create_tmp_stat_file()
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_SUFFIX
        time_series_details = self._extract_time_series_details(hcs_index_file_path)
        if len(time_series_details) != 1:
            self.log_processing_info('Only single sequence measurements are supported, for now, exiting...')
            return 1
        self.generate_ome_xml_info_file()
        xml_info_tree = ET.parse(self.ome_xml_info_file_path).getroot()
        plate_width, plate_height = self._get_plate_configuration(xml_info_tree)
        self._write_hcs_file(time_series_details, plate_width, plate_height)
        tags_processing_result = self.try_process_tags(xml_info_tree)
        if TAGS_PROCESSING_ONLY:
            return tags_processing_result
        if not self._localize_related_files():
            self.log_processing_info('Some errors occurred during copying files from the bucket, exiting...')
            return 1
        local_preview_dir = os.path.join(self.tmp_local_dir, 'preview')
        for sequence_id, timepoints in time_series_details.items():
            conversion_result = os.system('bash "{}" "{}" "{}" {}'.format(
                self._OME_TIFF_TIMEPOINT_CREATION_SCRIPT, hcs_index_file_path, local_preview_dir, sequence_id))
            if conversion_result != 0:
                self.log_processing_info('File processing was not successful...')
                return 1
        cloud_transfer_result = os.system('pipe storage cp -f -r "{}" "{}"'
                                          .format(local_preview_dir,
                                                  HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)))
        if cloud_transfer_result != 0:
            self.log_processing_info('Results transfer was not successful...')
            return 1
        self.create_stat_file()
        return 0

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


def log_success(message):
    log_info(message, status=TaskStatus.SUCCESS)


def log_info(message, status=TaskStatus.RUNNING):
    Logger.log_task_event(HCS_PROCESSING_TASK_NAME, message, status)


def try_process_hcs(hcs_root_dir):
    parser = None
    try:
        parser = HcsFileParser(hcs_root_dir)
        processing_result = parser.process_file()
        return processing_result
    except Exception as e:
        log_info('An error occurred during [{}] parsing: {}'.format(hcs_root_dir, str(e)))
    finally:
        if parser:
            pass
            parser.clear_tmp_stat_file()
            parser.clear_tmp_local_dir()


def process_hcs_files():
    lookup_paths = os.getenv('HCS_TARGET_DIRECTORIES', '').split(',')
    if not lookup_paths:
        log_success('No paths for HCS processing specified')
        exit(0)
    log_info('Following paths are specified for processing: {}'.format(lookup_paths))
    log_info('Lookup for unprocessed files')
    paths_to_hcs_files = HcsProcessingDirsGenerator(lookup_paths).generate_paths()
    if not paths_to_hcs_files:
        log_success('Found no files requires processing in the target directories.')
        exit(0)
    log_info('Found {} files for processing.'.format(len(paths_to_hcs_files)))
    processing_threads = int(os.getenv('HCS_PARSING_THREADS', 1))
    if processing_threads < 1:
        log_info('Invalid number of threads [{}] is specified for processing, use single one instead'
                 .format(processing_threads))
        processing_threads = 1
    log_info('{} threads enabled for HCS processing'.format(processing_threads))
    if TAGS_PROCESSING_ONLY:
        log_info('Only tags will be processed, since TAGS_PROCESSING_ONLY is set to `true`')
    if processing_threads == 1:
        for file_path in paths_to_hcs_files:
            try_process_hcs(file_path)
    else:
        pool = multiprocessing.Pool(processing_threads)
        pool.map(try_process_hcs, paths_to_hcs_files)
    log_success('Finished HCS files processing')
    exit(0)


if __name__ == '__main__':
    process_hcs_files()
