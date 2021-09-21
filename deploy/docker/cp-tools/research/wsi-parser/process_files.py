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
import xml.etree.ElementTree as ET

from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger
from pipeline.common import get_path_with_trailing_delimiter

WSI_PROCESSING_TASK_NAME = 'WSI processing'
TAGS_MAPPING_RULE_DELIMITER = ','
TAGS_MAPPING_KEYS_DELIMITER = '='
SCHEMA_PREFIX = '{http://www.openmicroscopy.org/Schemas/OME/2016-06}'
WSI_ACTIVE_PROCESSING_TIMEOUT_MIN = int(os.getenv('WSI_ACTIVE_PROCESSING_TIMEOUT_MIN', 360))
DZ_TILES_SIZE = int(os.getenv('WSI_PARSING_DZ_TILES_SIZE', 256))


class ImageDetails(object):

    def __init__(self, series_id, name, width, height):
        self.id = series_id
        self.name = name
        self.width = int(width)
        self.height = int(height)

    @staticmethod
    def from_xml(i, name, image_xml):
        resolution_details = image_xml.find(SCHEMA_PREFIX + 'Pixels')
        width = resolution_details.get('SizeX')
        height = resolution_details.get('SizeY')
        details = ImageDetails(i, name, width, height)
        return details


class WsiParsingUtils:

    TILES_DIR_SUFFIX = '.tiles'

    @staticmethod
    def get_file_without_extension(file_path):
        return os.path.splitext(file_path)[0]

    @staticmethod
    def get_basename_without_extension(file_path):
        return WsiParsingUtils.get_file_without_extension(os.path.basename(file_path))

    @staticmethod
    def get_file_last_modification_time(file_path):
        return int(os.stat(file_path).st_mtime)

    @staticmethod
    def get_stat_active_file_name(file_path):
        return WsiParsingUtils._get_service_file_name(file_path, 'wsiparser.inprog')

    @staticmethod
    def get_stat_file_name(file_path):
        return WsiParsingUtils._get_service_file_name(file_path, 'wsiparser')

    @staticmethod
    def get_service_directory(file_path):
        name_without_extension = WsiParsingUtils.get_basename_without_extension(file_path)
        parent_dir = os.path.dirname(file_path)
        return os.path.join(parent_dir, '.wsiparser', name_without_extension)

    @staticmethod
    def generate_local_service_directory(file_path):
        name_without_extension = WsiParsingUtils.get_basename_without_extension(file_path)
        return tempfile.mkdtemp(prefix=name_without_extension + '.wsiparser.')

    @staticmethod
    def create_service_dir_if_not_exist(file_path):
        directory = WsiParsingUtils.get_service_directory(file_path)
        if not os.path.exists(directory):
            os.makedirs(directory)

    @staticmethod
    def _get_service_file_name(file_path, suffix):
        name_without_extension = WsiParsingUtils.get_basename_without_extension(file_path)
        parent_dir = WsiParsingUtils.get_service_directory(file_path)
        parser_flag_file = '.{}.{}'.format(name_without_extension, suffix)
        return os.path.join(parent_dir, parser_flag_file)

    @staticmethod
    def active_processing_exceed_timeout(active_stat_file):
        processing_stat_file_modification_date = WsiParsingUtils.get_file_last_modification_time(active_stat_file)
        processing_deadline = datetime.datetime.now() - datetime.timedelta(minutes=WSI_ACTIVE_PROCESSING_TIMEOUT_MIN)
        return (processing_stat_file_modification_date - time.mktime(processing_deadline.timetuple())) < 0

    @staticmethod
    def extract_cloud_path(file_path):
        path_chunks = file_path.split('/cloud-data/', 1)
        if len(path_chunks) != 2:
            raise RuntimeError('Unable to determine cloud path of [{}]'.format(file_path))
        return 'cp://{}'.format(path_chunks[1])


class WsiProcessingFileGenerator:

    def __init__(self, lookup_paths, target_file_formats):
        self.lookup_paths = lookup_paths
        self.target_file_formats = target_file_formats

    @staticmethod
    def is_modified_after(file_path_a, modification_date):
        return WsiParsingUtils.get_file_last_modification_time(file_path_a) > modification_date

    @staticmethod
    def get_related_wsi_directories(file_path):
        parent_dir = os.path.dirname(file_path)
        related_subdirectories = set()
        for file in os.listdir(parent_dir):
            full_file_path = os.path.join(parent_dir, file)
            if os.path.isdir(full_file_path):
                file_basename = WsiParsingUtils.get_basename_without_extension(file_path)
                if not full_file_path.endswith(WsiParsingUtils.TILES_DIR_SUFFIX) and file_basename in os.path.basename(full_file_path):
                    related_subdirectories.add(full_file_path)
        return related_subdirectories

    def generate_paths(self):
        paths = self.find_all_matching_files()
        return filter(lambda p: self.is_processing_required(p), paths)

    def find_all_matching_files(self):
        paths = set()
        for lookup_path in self.lookup_paths:
            dir_root = os.walk(lookup_path)
            for dir_root, directories, files in dir_root:
                for file in files:
                    if file.endswith(self.target_file_formats):
                        paths.add(os.path.join(dir_root, file))
        return paths

    def is_processing_required(self, file_path):
        if os.getenv('WSI_PARSING_SKIP_DATE_CHECK'):
            return True
        active_stat_file = WsiParsingUtils.get_stat_active_file_name(file_path)
        if os.path.exists(active_stat_file):
            return WsiParsingUtils.active_processing_exceed_timeout(active_stat_file)
        stat_file = WsiParsingUtils.get_stat_file_name(file_path)
        if not os.path.isfile(stat_file):
            return True
        stat_file_modification_date = WsiParsingUtils.get_file_last_modification_time(stat_file)
        if self.is_modified_after(file_path, stat_file_modification_date):
            return True
        related_directories = self.get_related_wsi_directories(file_path)
        for directory in related_directories:
            dir_root = os.walk(directory)
            for dir_root, directories, files in dir_root:
                for file in files:
                    if self.is_modified_after(os.path.join(dir_root, file), stat_file_modification_date):
                        return True
        return False


class WsiFileTagProcessor:

    CATEGORICAL_ATTRIBUTE = '/categoricalAttribute'

    def __init__(self, file_path, xml_info_tree, tags_mapping_rules):
        self.file_path = file_path
        self.xml_info_tree = xml_info_tree
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.system_dictionaries_url = self.api.api_url + self.CATEGORICAL_ATTRIBUTE
        self.cloud_path = WsiParsingUtils.extract_cloud_path(file_path)
        self.tags_mapping_rules = tags_mapping_rules

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(WSI_PROCESSING_TASK_NAME, '[{}] {}'.format(self.file_path, message), status=status)

    def process_tags(self):
        existing_attributes_dictionary = self.load_existing_attributes()
        tags_mapping = self.map_tags(existing_attributes_dictionary)
        if not tags_mapping:
            self.log_processing_info('No tags to map found, skipping tags processing...')
            return 0
        metadata = self.xml_info_tree.find(SCHEMA_PREFIX + 'StructuredAnnotations')
        if not metadata:
            self.log_processing_info('No metadata found for file, skipping tags processing...')
            return 0
        tags_to_push = self.extract_matching_tags_from_metadata(metadata.findall(SCHEMA_PREFIX + 'XMLAnnotation'),
                                                                tags_mapping)
        if not tags_to_push:
            self.log_processing_info('No matching tags found')
            return 0
        pipe_tags = self.prepare_tags(existing_attributes_dictionary, tags_to_push)
        tags_to_push_str = ' '.join(pipe_tags)
        self.log_processing_info('Following tags will be assigned to the file: {}'.format(tags_to_push_str))
        return os.system('pipe storage set-object-tags "{}" {}'.format(self.cloud_path, tags_to_push_str))

    def prepare_tags(self, existing_attributes_dictionary, tags_to_push):
        attribute_updates = list()
        pipe_tags = list()
        for attribute_name, values_to_push in tags_to_push.items():
            if len(values_to_push) > 1:
                self.log_processing_info('Multiple tags matches occurred for "{}": [{}]'
                                         .format(attribute_name, values_to_push))
                continue
            value = list(values_to_push)[0]
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

    def extract_matching_tags_from_metadata(self, metadata_entries, tags_mapping):
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
                                self.log_processing_info('Processing array value')
                                value = value[1:-1]
                                values = list(set(value.split(',')))
                                if len(values) != 1:
                                    self.log_processing_info('Empty or multiple metadata values, skipping [{}]'
                                                             .format(key))
                                value = values[0]
                            target_tag = tags_mapping[key]
                            if target_tag in tags_to_push:
                                tags_to_push[target_tag].add(value)
                            else:
                                tags_to_push[target_tag] = {value}
        return tags_to_push

    def map_tags(self, existing_attributes_dictionary):
        tags_mapping = dict()
        for rule in self.tags_mapping_rules:
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


class WsiFileParser:
    _SYSTEM_IMAGE_NAMES = {'overview', 'label', 'thumbnail', 'macro', 'macro image', 'macro mask image', 'label image',
                           'overview image', 'thumbnail image'}
    _DEEP_ZOOM_CREATION_SCRIPT = os.path.join(os.getenv('WSI_PARSER_HOME', '/opt/local/wsi-parser'),
                                              'create_deepzoom.sh')

    def __init__(self, file_path, tags_mapping_rules):
        self.file_path = file_path
        self.tags_mapping_rules = tags_mapping_rules.split(TAGS_MAPPING_RULE_DELIMITER) if tags_mapping_rules else None
        self.log_processing_info('Generating XML description')
        self.xml_info_file = os.path.join(WsiParsingUtils.get_service_directory(file_path),
                                          WsiParsingUtils.get_basename_without_extension(self.file_path) + '_info.xml')
        self.generate_xml_info_file()
        self.xml_info_tree = ET.parse(self.xml_info_file).getroot()
        self.stat_file_name = WsiParsingUtils.get_stat_file_name(self.file_path)
        self.tmp_stat_file_name = WsiParsingUtils.get_stat_active_file_name(self.file_path)
        self.tmp_local_dir = WsiParsingUtils.generate_local_service_directory(self.file_path)

    def generate_xml_info_file(self):
        WsiParsingUtils.create_service_dir_if_not_exist(self.file_path)
        os.system('showinf -nopix -omexml-only "{}" > "{}"'.format(self.file_path, self.xml_info_file))

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(WSI_PROCESSING_TASK_NAME, '[{}] {}'.format(self.file_path, message), status=status)

    def create_tmp_stat_file(self, image_details):
        WsiParsingUtils.create_service_dir_if_not_exist(self.file_path)
        self._write_processing_stats_to_file(self.tmp_stat_file_name, image_details)

    def clear_tmp_stat_file(self):
        if os.path.exists(self.tmp_stat_file_name):
            os.remove(self.tmp_stat_file_name)

    def clear_tmp_local_dir(self):
        if os.path.exists(self.tmp_local_dir):
            shutil.rmtree(self.tmp_local_dir)

    def update_stat_file(self, target_image_details):
        WsiParsingUtils.create_service_dir_if_not_exist(self.file_path)
        self._write_processing_stats_to_file(self.stat_file_name, target_image_details)

    def _write_processing_stats_to_file(self, stat_file_path, target_image_details):
        details = {
            'file': self.file_path,
            'target_series': target_image_details.id,
            'width': target_image_details.width,
            'height': target_image_details.height
        }
        with open(stat_file_path, 'w') as output_file:
            output_file.write(json.dumps(details, indent=4))

    def update_dz_info_file(self, original_width, original_height):
        service_directory = WsiParsingUtils.get_service_directory(self.file_path)
        file_name = WsiParsingUtils.get_basename_without_extension(self.file_path)
        image = os.path.join(service_directory, '{}.jpeg'.format(file_name))
        if os.path.exists(image):
            tiles_dir = os.path.join(os.path.dirname(self.file_path), file_name + WsiParsingUtils.TILES_DIR_SUFFIX)
            max_zoom = self._max_zoom_level(tiles_dir)
            if max_zoom < 0:
                self.log_processing_info('Unable to determine DZ depth calculation, skipping json file creation')
                return
            self._write_dz_info_to_file(os.path.join(tiles_dir, 'info.json'),
                                        original_width,
                                        original_height,
                                        max_zoom)

    def _is_same_series_selected(self, selected_series):
        stat_file = WsiParsingUtils.get_stat_file_name(self.file_path)
        if not os.path.isfile(stat_file):
            return False
        with open(stat_file) as last_sync_stats:
            json_stats = json.load(last_sync_stats)
            if 'target_series' in json_stats and json_stats['target_series'] == selected_series:
                return True
            return False

    def _max_zoom_level(self, tiles_dir):
        dz_layers_folders = 0
        for dz_layer_folder in os.listdir(tiles_dir):
            if os.path.isdir(os.path.join(tiles_dir, dz_layer_folder)) and dz_layer_folder.isnumeric():
                dz_layers_folders += 1
        return dz_layers_folders - 1

    def _write_dz_info_to_file(self, dz_info_file_path, width, height, max_dz_level):
        details = {
            'width': width,
            'height': height,
            'minLevel': 0,
            'maxLevel': max_dz_level,
            'tileWidth': DZ_TILES_SIZE,
            'tileHeight': DZ_TILES_SIZE,
            'bounds': [0, width, 0, height]
        }
        with open(dz_info_file_path, 'w') as output_file:
            output_file.write(json.dumps(details, indent=4))

    def calculate_target_series(self):
        images = self.xml_info_tree.findall(SCHEMA_PREFIX + 'Image')
        series_mapping = self.group_image_series(images)
        self.log_processing_info('Following image groups are found: {}'.format(series_mapping.keys()))
        target_group = None
        target_image_details = None
        for group_name in series_mapping.keys():
            if group_name not in self._SYSTEM_IMAGE_NAMES:
                target_group = group_name
                break
        self.log_processing_info('Target group is: {}'.format(target_group))
        if target_group:
            target_image_details = series_mapping[target_group][0]
        return target_image_details

    def group_image_series(self, images):
        base_name = os.path.basename(self.file_path)
        current_group_name = ''
        current_group_details_list = []
        series_mapping = {}
        for i in range(0, len(images)):
            image_details = images[i]
            name = image_details.get('Name')
            details = ImageDetails.from_xml(i, name, image_details)
            current_group_details_list.append(details)
            if not name.startswith(base_name):
                if not current_group_name:
                    current_group_name = name
                series_mapping[current_group_name] = current_group_details_list
                current_group_name = name
                current_group_details_list = [details]
            elif not current_group_name:
                series_mapping[name] = [details]
        if current_group_name:
            series_mapping[current_group_name] = current_group_details_list
        return series_mapping

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
        image_cloud_path = WsiParsingUtils.extract_cloud_path(self.file_path)
        local_tmp_dir_trailing = get_path_with_trailing_delimiter(self.tmp_local_dir)
        main_file_localization_result = \
            os.system('pipe storage cp -f "{}" "{}"'.format(image_cloud_path, local_tmp_dir_trailing))
        if main_file_localization_result != 0:
            return False
        stacks_dir_name = '_{}_'.format(WsiParsingUtils.get_basename_without_extension(self.file_path))
        stacks_mnt_path = os.path.join(os.path.dirname(self.file_path), stacks_dir_name)
        if os.path.exists(stacks_mnt_path):
            local_stacks_dir = get_path_with_trailing_delimiter(os.path.join(self.tmp_local_dir, stacks_dir_name))
            if self._mkdir(local_stacks_dir):
                stacks_cloud_path = get_path_with_trailing_delimiter(
                    WsiParsingUtils.extract_cloud_path(stacks_mnt_path))
                stacks_localization_result = os.system('pipe storage cp -r -f "{}" "{}"'
                                                       .format(stacks_cloud_path, local_stacks_dir))
                if stacks_localization_result != 0:
                    self.log_processing_info('Some errors occurred during stacks localization')
            else:
                self.log_processing_info('Unable to create tmp folder for image stacks')
        else:
            self.log_processing_info('No stacks related files found')
        return True

    def process_file(self):
        self.log_processing_info('Start processing')
        if os.path.exists(self.tmp_stat_file_name) \
                and not WsiParsingUtils.active_processing_exceed_timeout(self.tmp_stat_file_name):
            log_info('This file is processed by another parser, skipping...')
            return 0
        if self.tags_mapping_rules:
            try:
                if WsiFileTagProcessor(self.file_path, self.xml_info_tree, self.tags_mapping_rules).process_tags() != 0:
                    self.log_processing_info('Some errors occurred during file tagging')
            except Exception as e:
                log_info('An error occurred during tags processing: {}'.format(str(e)))
        target_image_details = self.calculate_target_series()
        self.create_tmp_stat_file(target_image_details)
        target_series = target_image_details.id
        if target_series is None:
            self.log_processing_info('Unable to determine target series, skipping DZ creation... ')
            return 1
        elif self._is_same_series_selected(target_series):
            self.log_processing_info('The same series [{}] is selected for image processing, skipping... '
                                     .format(target_series))
            return 0
        self.log_processing_info('Series #{} selected for DZ creation [width={}; height={}]'
                                 .format(target_series, target_image_details.width, target_image_details.height))
        if not self._localize_related_files():
            self.log_processing_info('Some errors occurred during copying files from the bucket, exiting...')
            return 1
        local_file_path = os.path.join(self.tmp_local_dir, os.path.basename(self.file_path))
        conversion_result = os.system('bash "{}" {} {} "{}" "{}" "{}"'
                                      .format(self._DEEP_ZOOM_CREATION_SCRIPT,
                                              local_file_path,
                                              target_series,
                                              DZ_TILES_SIZE,
                                              os.path.dirname(self.file_path),
                                              self.tmp_local_dir))
        if conversion_result == 0:
            self.update_stat_file(target_image_details)
            self.update_dz_info_file(target_image_details.width, target_image_details.height)
            self.log_processing_info('File processing is finished')
        else:
            self.log_processing_info('File processing was not successful')
        return conversion_result


def log_success(message):
    log_info(message, status=TaskStatus.SUCCESS)


def log_info(message, status=TaskStatus.RUNNING):
    Logger.log_task_event(WSI_PROCESSING_TASK_NAME, message, status)


def try_process_file(file_path):
    parser = None
    try:
        parser = WsiFileParser(file_path, os.getenv('WSI_PARSING_TAG_MAPPING', ''))
        processing_result = parser.process_file()
        return processing_result
    except Exception as e:
        log_info('An error occurred during [{}] parsing: {}'.format(file_path, str(e)))
    finally:
        if parser:
            parser.clear_tmp_stat_file()
            parser.clear_tmp_local_dir()


def process_wsi_files():
    lookup_paths = os.getenv('WSI_TARGET_DIRECTORIES')
    if not lookup_paths:
        log_success('No paths for WSI processing specified')
        exit(0)
    target_file_formats = tuple(['.' + extension for extension in os.getenv('WSI_FILE_FORMATS', 'vsi,mrxs').split(',')])
    log_info('Following paths are specified for processing: {}'.format(lookup_paths))
    log_info('Lookup for unprocessed files')
    paths_to_wsi_files = WsiProcessingFileGenerator(lookup_paths.split(','), target_file_formats).generate_paths()
    if not paths_to_wsi_files:
        log_success('Found no files requires processing in the target directories.')
        exit(0)
    log_info('Found {} files for processing.'.format(len(paths_to_wsi_files)))
    processing_threads = int(os.getenv('WSI_PARSING_THREADS', 1))
    if processing_threads < 1:
        log_info('Invalid number of threads [{}] is specified for processing, use single one instead'
                 .format(processing_threads))
        processing_threads = 1
    log_info('{} threads enabled for WSI processing'.format(processing_threads))
    if processing_threads == 1:
        for file_path in paths_to_wsi_files:
            try_process_file(file_path)
    else:
        pool = multiprocessing.Pool(processing_threads)
        pool.map(try_process_file, paths_to_wsi_files)
    log_success('Finished WSI files processing')
    exit(0)


if __name__ == '__main__':
    process_wsi_files()
