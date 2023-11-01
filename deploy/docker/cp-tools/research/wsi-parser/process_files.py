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

import uuid
import errno
import json
import os
import multiprocessing
import datetime
import re
import requests
import shutil
import struct
import tempfile
import time
import traceback
import urllib
import xml.etree.ElementTree as ET

from collections import OrderedDict
from abc import ABCMeta, abstractmethod
from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger
from pipeline.common import get_path_with_trailing_delimiter

ANIMAL_ID_PATTERN = '[0-9]+(\.[0-9]+)?$'
ANIMAL_ID_REMOVE_NON_DIGITS_PATTERN = '^[^0-9]+|[^0-9]+$'

WSI_PROCESSING_TASK_NAME = 'WSI processing'
TAGS_MAPPING_RULE_DELIMITER = ','
TAGS_MAPPING_KEYS_DELIMITER = '='
SCHEMA_PREFIX = '{http://www.openmicroscopy.org/Schemas/OME/2016-06}'
WSI_ACTIVE_PROCESSING_TIMEOUT_MIN = int(os.getenv('WSI_ACTIVE_PROCESSING_TIMEOUT_MIN', 360))
ZOOM_TILES_SIZE = int(os.getenv('WSI_PARSING_DZ_TILES_SIZE', 256))
TAGS_PROCESSING_ONLY = os.getenv('WSI_PARSING_TAGS_ONLY', 'false') == 'true'
TARGET_FORMAT = os.getenv('WSI_PARSING_TARGET_FORMAT', 'DZ')
REFR_IND_CAT_ATTR_NAME = os.getenv('WSI_PARSING_REFR_IND_CAT_ATTR_NAME', 'Immersion liquid')
EXTENDED_FOCUS_CAT_ATTR_NAME = os.getenv('WSI_PARSING_EXTENDED_FOCUS_CAT_ATTR_NAME', 'Extended Focus')
MAGNIFICATION_CAT_ATTR_NAME = os.getenv('WSI_PARSING_MAGNIFICATION_CAT_ATTR_NAME', 'Magnification')
STUDY_NAME_CAT_ATTR_NAME = os.getenv('WSI_PARSING_STUDY_NAME_CAT_ATTR_NAME', 'Study name')
SLIDE_NAME_CAT_ATTR_NAME = os.getenv('WSI_PARSING_SLIDE_NAME_CAT_ATTR_NAME', 'Slide Name')
STAIN_CAT_ATTR_NAME = os.getenv('WSI_PARSING_STAIN_CAT_ATTR_NAME', 'Stain')
STAIN_METHOD_CAT_ATTR_NAME = os.getenv('WSI_PARSING_STAIN_METHOD_CAT_ATTR_NAME', 'Stain method')
PREPARATION_CAT_ATTR_NAME = os.getenv('WSI_PARSING_PREPARATION_CAT_ATTR_NAME', 'Preparation')
GROUP_CAT_ATTR_NAME = os.getenv('WSI_PARSING_GROUP_CAT_ATTR_NAME', 'Group')
SEX_CAT_ATTR_NAME = os.getenv('WSI_PARSING_SEX_CAT_ATTR_NAME', 'Sex')
ANIMAL_ID_CAT_ATTR_NAME = os.getenv('WSI_PARSING_ANIMAL_ID_CAT_ATTR_NAME', 'Animal ID')
PURPOSE_CAT_ATTR_NAME = os.getenv('WSI_PARSING_PURPOSE_CAT_ATTR_NAME', 'Purpose')
SPECIES_CAT_ATTR_NAME = os.getenv('WSI_PARSING_SPECIES_CAT_ATTR_NAME', 'Species')
TISSUE_CAT_ATTR_NAME = os.getenv('WSI_PARSING_TISSUE_CAT_ATTR_NAME', 'Tissue')
MULTIPLE_TYPE_TISSUE_DELIMITER = os.getenv('WSI_PARSING_MULTI_TISSUE_DELIMITER', ':')
TAG_DELIMITER = os.getenv('WSI_PARSING_TAG_DELIMITER', ';')
EXCEPTIONS_MAPPINGS_FILE = os.getenv('WSI_PARSING_EXCEPTIONS_MAPPINGS_FILE')
FORCE_CATALOG_UPDATE = os.getenv('WSI_PARSING_FORCE_CATALOG_UPDATE', 'false') == 'true'
CLOUD_STORAGE_ID = os.getenv('WSI_PARSING_STORAGE_ID', None)
CATALOG_UPDATE_URL = os.getenv('WSI_PARSING_CATALOG_UPDATE_URL', None)

STAIN_METHOD_MAPPINGS = {
    'GENERAL': 'General',
    'SPECIAL': 'Special',
    'SS': 'Special',
    'IHC': 'IHC',
    'ISH': 'ISH'
}
UNKNOWN_ATTRIBUTE_VALUE = 'NA'
HYPHEN = '-'
UNK = 'Unk'


def prepare_exception_tags_mapping():
    exceptions_mapping = EXCEPTIONS_MAPPINGS_FILE
    if not exceptions_mapping:
        return {}
    if exceptions_mapping.lower().startswith("s3://"):
        exceptions_mapping = os.path.join(os.getenv('WSI_PARSER_HOME', ''), '%s-exceptions.json' % str(uuid.uuid4()))
        os.system('pipe storage cp -f "{}" "{}"'.format(EXCEPTIONS_MAPPINGS_FILE, exceptions_mapping))
    if not os.path.isfile(exceptions_mapping):
        return {}
    with open(exceptions_mapping) as json_file:
        data = json.load(json_file)
    for exceptions_key in data:
        data[exceptions_key] = dict((key.upper(), value) for key, value in data[exceptions_key].items())
    return data


EXCEPTION_TAGS_MAPPING = prepare_exception_tags_mapping()


class ImageDetails(object):

    def __init__(self, series_id, name, width, height, refractive_index, objective_id):
        self.id = series_id
        self.name = name
        self.width = int(width)
        self.height = int(height)
        self.refractive_index = refractive_index
        self.objective_id = objective_id

    @staticmethod
    def from_xml(i, name, image_xml):
        resolution_details = image_xml.find(SCHEMA_PREFIX + 'Pixels')
        width = resolution_details.get('SizeX')
        height = resolution_details.get('SizeY')
        objective_details = image_xml.find(SCHEMA_PREFIX + 'ObjectiveSettings')
        if objective_details is not None:
            refractive_index = float(objective_details.get('RefractiveIndex'))
            objective_id = objective_details.get('ID')
        else:
            refractive_index = -1
            objective_id = None
        details = ImageDetails(i, name, width, height, refractive_index, objective_id)
        return details


class WsiParsingUtils:

    TILES_DIR = 'tiles'
    PARSER_SERVICE_DIR = '.wsiparser'

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
        return os.path.join(parent_dir, WsiParsingUtils.PARSER_SERVICE_DIR, name_without_extension)

    @staticmethod
    def generate_local_service_directory(file_path):
        name_without_extension = WsiParsingUtils.get_basename_without_extension(file_path)
        return tempfile.mkdtemp(prefix=name_without_extension + WsiParsingUtils.PARSER_SERVICE_DIR + '.')

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


class WsiProcessingFileGenerator(object):

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
                if not full_file_path.endswith(WsiParsingUtils.TILES_DIR) \
                        and file_basename in os.path.basename(full_file_path):
                    related_subdirectories.add(full_file_path)
        return related_subdirectories

    @abstractmethod
    def find_all_matching_files(self):
        pass

    @abstractmethod
    def filter_processed_files(self, paths):
        pass

    def generate_paths(self):
        log_info('Generating paths using {}'.format(self.__class__.__name__))
        paths = self.find_all_matching_files()
        log_info('Found {} matching files.'.format(len(paths)))
        return self.filter_processed_files(paths)

    @staticmethod
    def is_processing_required(file_path):
        if os.getenv('WSI_PARSING_SKIP_DATE_CHECK') or TAGS_PROCESSING_ONLY:
            return True
        active_stat_file = WsiParsingUtils.get_stat_active_file_name(file_path)
        if os.path.exists(active_stat_file):
            return WsiParsingUtils.active_processing_exceed_timeout(active_stat_file)
        stat_file = WsiParsingUtils.get_stat_file_name(file_path)
        if not os.path.isfile(stat_file):
            return True
        stat_file_modification_date = WsiParsingUtils.get_file_last_modification_time(stat_file)
        if WsiProcessingFileGenerator.is_modified_after(file_path, stat_file_modification_date):
            return True
        related_directories = WsiProcessingFileGenerator.get_related_wsi_directories(file_path)
        for directory in related_directories:
            dir_root = os.walk(directory)
            for dir_root, directories, files in dir_root:
                for file in files:
                    if WsiProcessingFileGenerator.is_modified_after(os.path.join(dir_root, file),
                                                                    stat_file_modification_date):
                        return True
        return False


class DirListingParams:

    def __init__(self, absolute_dir_path, filter_dir_names, target_extensions):
        self.absolute_dir_path = absolute_dir_path
        self.filter_dir_names = filter_dir_names
        self.target_extensions = target_extensions


class DirListingResult:

    def __init__(self, files, nested_dirs):
        self.files = files
        self.nested_dirs = nested_dirs


def search_in_directory(search_params):
    files = set()
    nested_dirs = set()
    for (dir_path, nested_dir_names, file_names) in os.walk(search_params.absolute_dir_path):
        for file_name in file_names:
            if file_name.endswith(search_params.target_extensions):
                files.add(os.path.join(dir_path, file_name))
        for nested_dir_name in nested_dir_names:
            if nested_dir_name not in search_params.filter_dir_names:
                nested_dirs.add(os.path.join(dir_path, nested_dir_name))
        break
    return DirListingResult(files, nested_dirs)


class SingleThreadWsiProcessingFileGenerator(WsiProcessingFileGenerator):

    def __init__(self, lookup_paths, target_file_formats):
        super(SingleThreadWsiProcessingFileGenerator, self).__init__(lookup_paths, target_file_formats)

    def filter_processed_files(self, paths):
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


def return_none_if_processing_is_not_required(path):
    return path if WsiProcessingFileGenerator.is_processing_required(path) else None


class ParallelWsiProcessingFileGenerator(WsiProcessingFileGenerator):

    def __init__(self, lookup_paths, target_file_formats, processing_pool):
        super(ParallelWsiProcessingFileGenerator, self).__init__(lookup_paths, target_file_formats)
        self.processing_pool = processing_pool
        self.filter_dir_names = self.__get_dir_names_to_ignore()

    @staticmethod
    def __get_dir_names_to_ignore():
        return {dir_name.strip() for dir_name in
                os.getenv('WSI_PARSING_SEARCH_SKIP_DIRS', WsiParsingUtils.PARSER_SERVICE_DIR).split(',')}

    def filter_processed_files(self, paths):
        filtered_files = self.processing_pool.map(return_none_if_processing_is_not_required, paths)
        return [path for path in filtered_files if path is not None]

    def find_all_matching_files(self):
        matching_files = set()
        path_check_list = set(self.lookup_paths)
        while len(path_check_list) > 0:
            dirs_to_check = list()
            for i in range(0, min(self.processing_pool._processes, len(path_check_list))):
                dirs_to_check.append(path_check_list.pop())
            search_params_list = [DirListingParams(dir_path, self.filter_dir_names, self.target_file_formats)
                                  for dir_path
                                  in dirs_to_check]
            dir_search_results = self.processing_pool.map(search_in_directory, search_params_list)
            for result in dir_search_results:
                matching_files.update(result.files)
                path_check_list.update(result.nested_dirs)
        return matching_files


class UserDefinedMetadata:

    def __init__(self, path_list, value):
        self._path = self._convert_path_list_to_string(path_list)
        self._value = value

    @staticmethod
    def _convert_path_list_to_string(path):
        return '-'.join(str(p) for p in path) + '-'

    def get_path(self):
        return self._path

    def get_value(self):
        return self._value

    def __repr__(self):
        return 'UserMetadata({},{})'.format(self._path, self._value)


class VSIBinaryTagsReader:

    HEADER_BYTES_SIZE = 24

    NEW_VOLUME_HEADER = 0
    PROPERTY_SET_VOLUME = 1
    NEW_MDIM_VOLUME_HEADER = 2

    COLLECTION_VOLUME = 2000
    MULTIDIM_IMAGE_VOLUME = 2001
    IMAGE_FRAME_VOLUME = 2002
    DIMENSION_SIZE = 2003
    IMAGE_COLLECTION_PROPERTIES = 2004
    MULTIDIM_STACK_PROPERTIES = 2005
    FRAME_PROPERTIES = 2006
    DIMENSION_DESCRIPTION_VOLUME = 2007
    CHANNEL_PROPERTIES = 2008
    DISPLAY_MAPPING_VOLUME = 2011
    LAYER_INFO_PROPERTIES = 2012

    CHAR = 1
    UCHAR = 2
    SHORT = 3
    USHORT = 4
    INT = 5
    UINT = 6
    LONG = 7
    ULONG = 8
    FLOAT = 9
    DOUBLE = 10

    BOOLEAN = 12
    TCHAR = 13
    DWORD = 14
    TIMESTAMP = 17
    DATE = 18

    FIELD_TYPE = 271
    MEM_MODEL = 272
    COLOR_SPACE = 273
    UNICODE_TCHAR = 8192

    USER_DEFINED_ITEM = 27354
    USER_DEFINED_SPECIES = 2056

    def __init__(self, file_path):
        self.file_path = file_path
        self.previous_tag = 0
        self.parents = []
        self.user_data = []
        self._result_tags = {}

    @staticmethod
    def _convert_binary(bytes, format):
        return struct.unpack("<" + format, bytes)[0]

    @staticmethod
    def _read_short(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(2), 'h')

    @staticmethod
    def _read_int(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(4), 'i')

    @staticmethod
    def _read_long(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(8), 'q')

    @staticmethod
    def _read_float(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(4), 'f')

    @staticmethod
    def _read_double(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(8), 'd')

    @staticmethod
    def _read_bool(stream):
        return VSIBinaryTagsReader._convert_binary(stream.read(1), '?')

    @staticmethod
    def _read_string(stream, length):
        try:
            return stream.read(length).decode("utf-16-le", "backslashreplace").encode('utf-8', errors='ignore')
        except BaseException:
            return ''

    @staticmethod
    def _skip_bytes(stream, count):
        stream.read(count)

    def _get_file_length(self):
        return os.stat(self.file_path).st_size

    @staticmethod
    def _get_volume_name(tag):
        if tag == VSIBinaryTagsReader.COLLECTION_VOLUME or \
                tag == VSIBinaryTagsReader.MULTIDIM_IMAGE_VOLUME or \
                tag == VSIBinaryTagsReader.IMAGE_FRAME_VOLUME or \
                tag == VSIBinaryTagsReader.DIMENSION_SIZE or \
                tag == VSIBinaryTagsReader.IMAGE_COLLECTION_PROPERTIES or \
                tag == VSIBinaryTagsReader.MULTIDIM_STACK_PROPERTIES or \
                tag == VSIBinaryTagsReader.FRAME_PROPERTIES or \
                tag == VSIBinaryTagsReader.DIMENSION_DESCRIPTION_VOLUME or \
                tag == VSIBinaryTagsReader.CHANNEL_PROPERTIES or \
                tag == VSIBinaryTagsReader.DISPLAY_MAPPING_VOLUME or \
                tag == VSIBinaryTagsReader.LAYER_INFO_PROPERTIES:
            pass
        else:
            print('Unhandled volume {}'.format(tag))
        return ''

    @staticmethod
    def _get_tag_name(tag):
        return str(tag)

    def _push_to_user_defined_meta(self, path, value):
        self.user_data.append(UserDefinedMetadata(path, value))

    def _push_to_results(self, name, value):
        self._result_tags[name] = value

    def _collect_tags(self, vsi_file, tag_prefix):
        metadata_block_start_position = vsi_file.tell()
        file_length = self._get_file_length()
        if metadata_block_start_position + self.HEADER_BYTES_SIZE >= file_length:
            return False

        data_field_offset, tag_count = self.read_metadata_block_header(vsi_file)
        if tag_count > file_length or tag_count < 1:
            return False

        metadata_block_data_position = metadata_block_start_position + data_field_offset
        if metadata_block_data_position < 0 or metadata_block_data_position >= file_length:
            return False
        vsi_file.seek(metadata_block_data_position)

        for i in range(0, tag_count):
            field_type = self._read_int(vsi_file)
            tag = self._read_int(vsi_file)
            next_field = self._read_int(vsi_file) & 0xffffffff
            data_size = self._read_int(vsi_file)
            extra_tag = ((field_type & 0x8000000) >> 27) == 1
            extended_field = ((field_type & 0x10000000) >> 28) == 1
            inline_data = ((field_type & 0x40000000) >> 30) == 1

            real_type = field_type & 0xffffff

            if extra_tag:
                self._read_int(vsi_file)

            if tag < 0:
                if not inline_data and data_size + vsi_file.tell() < file_length:
                    self._skip_bytes(vsi_file, data_size)
                return False

            if extended_field and real_type == self.NEW_VOLUME_HEADER:
                self.extract_new_volume_header(data_size, file_length, tag, vsi_file)
            elif extended_field and (real_type == self.PROPERTY_SET_VOLUME or real_type == self.NEW_MDIM_VOLUME_HEADER):
                tag_name = self._get_volume_name(tag) if real_type == self.NEW_MDIM_VOLUME_HEADER else tag_prefix
                self.parents.append(tag)
                self._collect_tags(vsi_file, tag_name)
                self.parents.pop()
            else:
                value = str(data_size) if inline_data else ''
                if not inline_data and data_size > 0:
                    if real_type == self.CHAR or \
                            real_type == self.UCHAR:
                        value = str(vsi_file.read(1))
                    if real_type == self.SHORT or \
                            real_type == self.USHORT:
                        value = str(self._read_short(vsi_file))
                    if real_type == self.INT or \
                            real_type == self.UINT or \
                            real_type == self.DWORD or \
                            real_type == self.FIELD_TYPE or \
                            real_type == self.MEM_MODEL or \
                            real_type == self.COLOR_SPACE:
                        int_value = self._read_int(vsi_file)
                        value = str(int_value)
                    if real_type == self.LONG or \
                            real_type == self.ULONG or \
                            real_type == self.TIMESTAMP:
                        long_value = self._read_long(vsi_file)
                        value = str(long_value)
                    if real_type == self.FLOAT:
                        value = str(self._read_float(vsi_file))
                    if real_type == self.DOUBLE or \
                            real_type == self.DATE:
                        value = str(self._read_double(vsi_file))
                    if real_type == self.BOOLEAN:
                        value = str(self._read_bool(vsi_file))
                    if real_type == self.TCHAR or \
                            real_type == self.UNICODE_TCHAR:
                        value = self._read_string(vsi_file, data_size)

                if tag == self.USER_DEFINED_ITEM:
                    self._push_to_user_defined_meta(list(self.parents), value)

                if tag == self.USER_DEFINED_SPECIES:
                    self._push_to_results('Species', value)

            if next_field == 0 or tag == -494804095:
                if metadata_block_start_position + data_size + 32 < file_length \
                        and metadata_block_start_position + data_size >= 0:
                    vsi_file.seek(metadata_block_start_position + data_size + 32)
                return False

            next_fp_position = metadata_block_start_position + next_field
            if file_length > next_fp_position >= 0:
                vsi_file.seek(next_fp_position)
            else:
                break
        return True

    def extract_new_volume_header(self, data_size, file_length, tag, vsi_file):
        end_pointer = vsi_file.tell() + data_size
        while vsi_file.tell() < end_pointer and vsi_file.tell() < file_length:
            start = vsi_file.tell()
            self.parents.append(tag)
            if not self._collect_tags(vsi_file, self._get_volume_name(tag)):
                break
            self.parents.pop()
            end = vsi_file.tell()
            if start >= end:
                break

    def read_metadata_block_header(self, vsi):
        self._skip_bytes(vsi, 8)  # skipping unused info: header_size(short), version(short), volume_version(int)
        data_field_offset = self._read_long(vsi)
        flags = self._read_int(vsi)
        self._skip_bytes(vsi, 4)
        tag_count = flags & 0xfffffff
        return data_field_offset, tag_count

    def _resolve_tags(self):
        for item1 in self.user_data:
            for item2 in self.user_data:
                path1 = item1.get_path()
                path2 = item2.get_path()
                if path1 != path2 and path1.startswith(path2):
                    self._result_tags[item1.get_value()] = item2.get_value()

    def get_user_defined_tags(self):
        try:
            with open(self.file_path, "rb") as vsi_file:
                vsi_file.seek(8)
                self._collect_tags(vsi_file, '')
                self._resolve_tags()
                self._remove_empty_values()
                return self._result_tags
        except BaseException as e:
            print('An error occurred during binary tags reading:')
            print(traceback.format_exc())
            return {}

    def _remove_empty_values(self):
        for key, value in self._result_tags.items():
            if not value:
                del self._result_tags[key]


class WsiFileTagProcessor:

    CATEGORICAL_ATTRIBUTE = '/categoricalAttribute'

    def __init__(self, file_path, xml_info_tree):
        self.file_path = file_path
        self.xml_info_tree = xml_info_tree
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.system_dictionaries_url = self.api.api_url + self.CATEGORICAL_ATTRIBUTE
        self.cloud_path = WsiParsingUtils.extract_cloud_path(file_path)
        self.study_name_matchers = self._extract_study_name_matchers()

    def _extract_study_name_matchers(self):
        regex_rules_list = json.loads(os.getenv('WSI_PARSING_STUDY_NAME_REGEX_LIST',
                                                '["([a-zA-Z]{3})(-|_)+(\\\\d{2})(-|_)+(\\\\d{3,5})"]'))
        return [re.compile(rule.strip()) for rule in regex_rules_list]

    def log_processing_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(WSI_PROCESSING_TASK_NAME, '[{}] {}'.format(self.file_path, message), status=status)

    def process_tags(self, target_image_details):
        existing_attributes_dictionary = self.load_existing_attributes()
        metadata = self.xml_info_tree.find(SCHEMA_PREFIX + 'StructuredAnnotations')
        if not metadata:
            self.log_processing_info('No metadata found for file, skipping tags processing...')
            return 0
        metadata_dict = self.map_to_metadata_dict(metadata)
        tags_to_push = self.build_tags_dictionary(metadata_dict, existing_attributes_dictionary, target_image_details)
        if not tags_to_push:
            self.log_processing_info('No matching tags found')
            return 0
        pipe_tags, tags_dict = self.prepare_tags(existing_attributes_dictionary, tags_to_push)
        tags_to_push_str = ' '.join(pipe_tags)
        self.log_processing_info('Following tags will be assigned to the file: {}'.format(tags_to_push_str))
        url_encoded_cloud_file_path = WsiParsingUtils.extract_cloud_path(urllib.quote(self.file_path))
        result = os.system('pipe storage set-object-tags "{}" {}'.format(url_encoded_cloud_file_path, tags_to_push_str))
        if FORCE_CATALOG_UPDATE:
            self.update_search_catalog(tags_dict)
        return result

    def update_search_catalog(self, tags):
        if not CATALOG_UPDATE_URL or not CLOUD_STORAGE_ID:
            self.log_processing_info('Catalog update parameters are not configured')
            return
        path = self.file_path.replace('/cloud-data/', '')
        relative_path = path.split('/', 1)[1]
        changed_date = datetime.datetime.fromtimestamp(os.path.getmtime(self.file_path))
        data = [{
            "name": os.path.basename(self.file_path),
            "path": relative_path,
            "labels": {"StorageClass": "STANDARD"},
            "tags": tags,
            "type": "File",
            "size": os.path.getsize(self.file_path),
            "changed": changed_date.strftime("%Y-%m-%d %H:%M:%S.%f")
        }]
        self.log_processing_info('Updating search catalog with value %s' % str(data))
        self.execute_request(CATALOG_UPDATE_URL % str(CLOUD_STORAGE_ID), data, 5)

    def execute_request(self, url, data, attempts):
        count = 0
        while count < attempts:
            count += 1
            try:
                response = requests.post(url, data=json.dumps(data), headers={'content-type': 'application/json'},
                                         verify=False, timeout=10)
                if self.check_response(response):
                    return
            except Exception as e:
                self.log_processing_info(
                    'An error has occurred during request to catalog update: {}'.format(str(e.message)))
            time.sleep(5)
        self.log_processing_info('Exceeded maximum retry count {} for catalog update request'.format(attempts))

    def check_response(self, response):
        if response.status_code != 200:
            self.log_processing_info("Catalog update responded with status {}\n".format(str(response.status_code)))
            return False
        return True

    def map_to_metadata_dict(self, metadata):
        metadata_entries = metadata.findall(SCHEMA_PREFIX + 'XMLAnnotation')
        metadata_dict = dict()
        for entry in metadata_entries:
            entry_value = entry.find(SCHEMA_PREFIX + 'Value')
            if entry_value is not None:
                metadata_record = entry_value.find(SCHEMA_PREFIX + 'OriginalMetadata')
                if metadata_record is not None:
                    key = metadata_record.find(SCHEMA_PREFIX + 'Key').text
                    value = metadata_record.find(SCHEMA_PREFIX + 'Value').text
                    if key and value:
                        metadata_dict[key] = value
        return metadata_dict

    def build_tags_dictionary(self, metadata_dict, existing_attributes_dictionary, target_image_details):
        tags_dictionary = dict()
        common_tags_mapping = self.map_tags('WSI_PARSING_TAG_MAPPING', existing_attributes_dictionary)
        if common_tags_mapping:
            tags_dictionary.update(self.extract_matching_tags_from_metadata(metadata_dict, common_tags_mapping))
        tags_dictionary.update(self._get_advanced_mapping_dict(target_image_details, metadata_dict))
        if self.file_path.endswith('.vsi'):
            self._add_user_defined_tags_if_required(existing_attributes_dictionary, tags_dictionary)
        self._set_tags_default_values(tags_dictionary)
        self._try_build_slide_name(tags_dictionary)
        self._normalize_tags(tags_dictionary, existing_attributes_dictionary)
        return tags_dictionary

    def _add_user_defined_tags_if_required(self, existing_attributes_dictionary, tags_dictionary):
        user_defined_tags = VSIBinaryTagsReader(self.file_path).get_user_defined_tags()
        user_defined_tags_mapping = self.map_tags('WSI_PARSING_USER_DEFINED_TAG_MAPPING',
                                                  existing_attributes_dictionary)
        for key, value in user_defined_tags.items():
            if key in user_defined_tags_mapping:
                target_attribute_name = user_defined_tags_mapping[key]
                if target_attribute_name not in tags_dictionary:
                    tags_dictionary[target_attribute_name] = {value}

    def _set_tags_default_values(self, tags):
        if PURPOSE_CAT_ATTR_NAME not in tags or not tags[PURPOSE_CAT_ATTR_NAME]:
            tags[PURPOSE_CAT_ATTR_NAME] = {'Study'}
        if ANIMAL_ID_CAT_ATTR_NAME in tags:
            animal_ids_set = tags[ANIMAL_ID_CAT_ATTR_NAME]
            if len(animal_ids_set) == 1:
                animal_id_str = list(animal_ids_set)[0]
                animal_id_str = re.sub(ANIMAL_ID_REMOVE_NON_DIGITS_PATTERN, '', animal_id_str)
                if re.match(ANIMAL_ID_PATTERN, animal_id_str):
                    animal_id_int = int(animal_id_str.split('.')[0])
                    if SEX_CAT_ATTR_NAME not in tags or not tags[SEX_CAT_ATTR_NAME]:
                        tags[SEX_CAT_ATTR_NAME] = {'Male'} if animal_id_int % 1000 < 500 else {'Female'}
                    if GROUP_CAT_ATTR_NAME not in tags or not tags[GROUP_CAT_ATTR_NAME]:
                        tags[GROUP_CAT_ATTR_NAME] = {str(animal_id_int / 1000)}
        if PREPARATION_CAT_ATTR_NAME not in tags or not tags[PREPARATION_CAT_ATTR_NAME]:
            tags[PREPARATION_CAT_ATTR_NAME] = {'FFPE'}
        if STAIN_METHOD_CAT_ATTR_NAME not in tags or not tags[STAIN_METHOD_CAT_ATTR_NAME]:
            tags[STAIN_METHOD_CAT_ATTR_NAME] = {'General'}
        if STAIN_CAT_ATTR_NAME not in tags or not tags[STAIN_CAT_ATTR_NAME]:
            if list(tags[STAIN_METHOD_CAT_ATTR_NAME])[0] == 'General':
                tags[STAIN_CAT_ATTR_NAME] = {'H&E'}

    def _get_advanced_mapping_dict(self, target_image_details, metadata_dict):
        tags = dict()
        tags[REFR_IND_CAT_ATTR_NAME] = {self._get_refractive_index_substance(target_image_details)}
        tags[EXTENDED_FOCUS_CAT_ATTR_NAME] = {'Yes' if target_image_details.name.startswith('EFI ') else 'No'}
        magnification_value = self._get_magnification_attribute_value(target_image_details, metadata_dict)
        if magnification_value:
            tags[MAGNIFICATION_CAT_ATTR_NAME] = {magnification_value}
        tags[STUDY_NAME_CAT_ATTR_NAME] = self._try_extract_study_name(self.file_path)
        return tags

    def _try_extract_study_name(self, path):
        for study_name_matcher in self.study_name_matchers:
            matching_result = study_name_matcher.findall(path)
            if matching_result:
                study_names_found = set()
                for match_tuple in matching_result:
                    study_names_found.add(self._extract_study_name_from_tuple(match_tuple).upper())
                if UNKNOWN_ATTRIBUTE_VALUE in study_names_found:
                    study_names_found.remove(UNKNOWN_ATTRIBUTE_VALUE)
                if len(study_names_found) != 1:
                    self.log_processing_info('Unable to determine study name in the file path, matches found: [{}]'
                                             .format(study_names_found))
                    return {UNKNOWN_ATTRIBUTE_VALUE}
                return study_names_found
        self.log_processing_info('Unable to find match for study name in the file path...')
        return {UNKNOWN_ATTRIBUTE_VALUE}

    def _extract_study_name_from_tuple(self, tuple):
        name_chunks = list()
        for i in range(0, len(tuple), 2):
            name_chunks.append(tuple[i])
        if name_chunks:
            return HYPHEN.join(name_chunks)
        else:
            self.log_processing_info('Unable to build full study name from: {}'.format(tuple))
            return UNKNOWN_ATTRIBUTE_VALUE

    def _get_refractive_index_substance(self, target_image_details):
        refractive_index = target_image_details.refractive_index
        if refractive_index:
            if refractive_index > 1.5:
                return 'Oil'
            elif refractive_index > 1.46:
                return 'Glycerin'
            elif refractive_index > 1.3:
                return 'Water'
            elif refractive_index >= 0:
                return 'Dry'
        return 'Unknown'

    def build_magnification_from_numeric_string(self, magnification):
        return '{}x'.format(int(float(magnification)))

    def _get_magnification_attribute_value(self, target_image_details, metadata_dict):
        objective_id = target_image_details.objective_id
        if objective_id:
            instrument_details = self.xml_info_tree.find(SCHEMA_PREFIX + 'Instrument')
            if instrument_details:
                objectives_details = instrument_details.findall(SCHEMA_PREFIX + 'Objective')
                for i in range(0, len(objectives_details)):
                    objectives_detail = objectives_details[i]
                    if objectives_detail and objectives_details.get('ID') == objective_id:
                        magnification_value = objectives_details.get('NominalMagnification')
                        if magnification_value:
                            return self.build_magnification_from_numeric_string(magnification_value)
        for key, value in metadata_dict.items():
            magnitude_key = target_image_details.name + ' Microscope Objective Description'
            if key.startswith(magnitude_key):
                return value
            magnitude_key = target_image_details.name + ' Microscope Magnification'
            if key.startswith(magnitude_key):
                return self.build_magnification_from_numeric_string(value)
        return None

    def _normalize_tags(self, tags, system_dictionary):
        self._process_exceptions(tags)
        predefined_species_values = self._prepare_predefined_values(system_dictionary[SPECIES_CAT_ATTR_NAME])
        predefined_tissues_values = self._prepare_predefined_values(system_dictionary[TISSUE_CAT_ATTR_NAME])
        if SPECIES_CAT_ATTR_NAME in tags:
            current_species_values = [attr['value'] for attr in system_dictionary[SPECIES_CAT_ATTR_NAME]]
            tags[SPECIES_CAT_ATTR_NAME] = self._prepare_species_tag(tags[SPECIES_CAT_ATTR_NAME],
                                                                    predefined_species_values,
                                                                    current_species_values)
        if TISSUE_CAT_ATTR_NAME in tags:
            tags[TISSUE_CAT_ATTR_NAME] = self._prepare_tissues_tag(tags[TISSUE_CAT_ATTR_NAME],
                                                                   predefined_tissues_values)
        if STAIN_METHOD_CAT_ATTR_NAME in tags:
            self._normalize_stain_tags(tags)

    def _process_exceptions(self, tags):
        if not EXCEPTION_TAGS_MAPPING:
            return
        for tag_key, tag_values in tags.items():
            exceptions = EXCEPTION_TAGS_MAPPING.get(tag_key, None)
            if not exceptions:
                continue
            values = set()
            for tag_value in tag_values:
                exception_value = exceptions.get(tag_value.upper(), None)
                values.add(tag_value if not exception_value else exception_value)
            tags[tag_key] = values

    def _normalize_stain_tags(self, tags):
        stain_method = self._determine_stain_method(list(tags[STAIN_METHOD_CAT_ATTR_NAME])[0] or 'General', tags)
        tags[STAIN_METHOD_CAT_ATTR_NAME] = {stain_method}
        if stain_method == UNK:
            return
        if stain_method == 'General':
            tags[STAIN_CAT_ATTR_NAME] = self._prepare_general_stain_tag(tags.get(STAIN_CAT_ATTR_NAME, None))
            return
        if stain_method == 'Special' or stain_method == 'IHC':
            if tags.get(STAIN_CAT_ATTR_NAME):
                tags[STAIN_CAT_ATTR_NAME] = set([str(stain).strip().upper() for stain in tags[STAIN_CAT_ATTR_NAME]])

    @staticmethod
    def _determine_stain_method(stain_method, tags):
        stain_method = str(stain_method).strip().upper()
        if stain_method in STAIN_METHOD_MAPPINGS:
            return STAIN_METHOD_MAPPINGS[stain_method]
        for stain_mapping in STAIN_METHOD_MAPPINGS:
            if stain_mapping in stain_method:
                return STAIN_METHOD_MAPPINGS[stain_mapping]
        if stain_method == 'HE' and not tags.get(STAIN_CAT_ATTR_NAME):
            return 'General'
        if stain_method == 'IBA1':
            if not tags.get(STAIN_CAT_ATTR_NAME):
                tags[STAIN_CAT_ATTR_NAME] = {stain_method}
            return 'IHC'
        if stain_method == 'LFB':
            if not tags.get(STAIN_CAT_ATTR_NAME):
                tags[STAIN_CAT_ATTR_NAME] = {stain_method}
            return 'Special'
        return UNK

    @staticmethod
    def _prepare_predefined_values(system_dictionary_items):
        predefined_values = dict()
        for item in system_dictionary_items:
            value = item['value']
            predefined_values.update({str(value).upper(): value})
        return predefined_values

    @staticmethod
    def _prepare_species_tag(metadata_values, predefined_values, current_attribute_values):
        if not metadata_values:
            return {UNK}
        species = set()
        for metadata_value in metadata_values:
            if not metadata_value:
                species.add(UNK)
            metadata_value = str(metadata_value).strip()
            # case: values Ms and MS contained in attributes
            if metadata_value in current_attribute_values:
                species.add(metadata_value)
            else:
                metadata_value = str(metadata_value).strip().upper()
                if metadata_value in predefined_values:
                    species.add(predefined_values.get(metadata_value))
                else:
                    species.add(UNK)
        return species

    @staticmethod
    def _replace_with_predefined_value(metadata_value, predefined_value):
        return predefined_value + metadata_value[len(predefined_value):]

    @classmethod
    def _find_value_by_prefix(cls, metadata_value, predefined_values):
        for predefined_key, predefined_value in predefined_values.items():
            if metadata_value.upper() == predefined_key:
                return predefined_value
        possible_matches = []
        for predefined_value in predefined_values.keys():
            if metadata_value.upper().startswith(predefined_value):
                possible_matches.append(predefined_value)
        if not possible_matches:
            return UNK
        best_match = max(possible_matches, key=len)
        return cls._replace_with_predefined_value(metadata_value, predefined_values[best_match])

    @classmethod
    def _prepare_tissues_tag(cls, metadata_values, predefined_values):
        if not metadata_values:
            return {UNK}
        tissues = list()
        for metadata_value in metadata_values:
            if not metadata_value:
                tissues.append(UNK)
            metadata_value = str(metadata_value).strip()

            if MULTIPLE_TYPE_TISSUE_DELIMITER in metadata_value:
                tissues.extend(metadata_value.split(MULTIPLE_TYPE_TISSUE_DELIMITER))
            else:
                tissues.append(metadata_value)
        return set(cls._find_value_by_prefix(tissue, predefined_values) or UNK for tissue in tissues)

    @staticmethod
    def _prepare_general_stain_tag(metadata_values):
        if not metadata_values:
            return {'H&E'}
        metadata_value = str(list(metadata_values)[0]).strip().upper()
        if metadata_value == 'H&E' or metadata_value == 'HE':
            return {'H&E'}
        return {UNK}

    def prepare_tags(self, existing_attributes_dictionary, tags_to_push):
        attribute_updates = list()
        pipe_tags = list()
        tags_dict = {}
        for attribute_name, values_to_push in tags_to_push.items():
            if not attribute_name in existing_attributes_dictionary:
                self.log_processing_info(
                    '"{}" is not registered as Dictionary in API, this tag will be skipped for processing!'
                    .format(attribute_name))
                continue
            if len(values_to_push) > 1:
                self.log_processing_info('Multiple tags matches occurred for "{}": [{}]'
                                         .format(attribute_name, values_to_push))
                tag_value = TAG_DELIMITER.join(values_to_push)
            else:
                tag_value = list(values_to_push)[0]
            existing_values = existing_attributes_dictionary[attribute_name]
            existing_attribute_id = existing_values[0]['attributeId']
            existing_values_names = [existing_value['value'] for existing_value in existing_values]
            need_to_update_attributes = False
            for value in values_to_push:
                if value not in existing_values_names:
                    existing_values.append({'key': attribute_name, 'value': value})
                    need_to_update_attributes = True
            if need_to_update_attributes:
                attribute_updates.append({'id': int(existing_attribute_id),
                                          'key': attribute_name, 'values': existing_values})
            pipe_tags.append('\'{}\'=\'{}\''.format(attribute_name, tag_value))
            tags_dict[attribute_name] = tag_value
        if attribute_updates:
            self.log_processing_info('Updating following categorical attributes before tagging: {}'
                                     .format(attribute_updates))
            self.update_categorical_attributes(attribute_updates)
        return pipe_tags, tags_dict

    def update_categorical_attributes(self, attribute_updates):
        for attribute in attribute_updates:
            self.api.execute_request(self.system_dictionaries_url, method='post', data=json.dumps(attribute))

    def extract_matching_tags_from_metadata(self, metadata_dict, tags_mapping):
        tags_to_push = dict()
        for key in tags_mapping.keys():
            if key in metadata_dict:
                value = metadata_dict[key]
                values = []
                if value.startswith('[') and value.endswith(']'):
                    self.log_processing_info('Processing array value')
                    value = value[1:-1]
                    values = list(set([v.strip() for v in value.split(',')]))
                    if not values:
                        self.log_processing_info('Empty metadata values, skipping [{}]'.format(key))
                        continue
                else:
                    values.append(value.strip())

                target_tag = tags_mapping[key]
                for value in values:
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
                if value == STUDY_NAME_CAT_ATTR_NAME:
                    continue
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

    def _try_build_slide_name(self, tags_dictionary):
        slide_name_parts_tags = os.getenv('WSI_TAGS_PROCESSING_SLIDE_NAME_TAGS', '')
        slide_name_building_rules = slide_name_parts_tags.split(TAGS_MAPPING_RULE_DELIMITER) \
            if slide_name_parts_tags \
            else []
        slide_name_parts = []
        for tag in slide_name_building_rules:
            if tag not in tags_dictionary or not tags_dictionary[tag]:
                self.log_processing_info('No tag [{}] presented in tags, unable to build slide name...'.format(tag))
                return
            elif len(tags_dictionary[tag]) > 1:
                self.log_processing_info(
                    'Unable to build slide name, since tag [{}] contains more, than one value: {}'
                        .format(tag, tags_dictionary[tag]))
                return
            else:
                slide_name_parts.append(next(iter(tags_dictionary[tag])))
        if slide_name_parts:
            tags_dictionary[SLIDE_NAME_CAT_ATTR_NAME] = {'_'.join(slide_name_parts)}


class WsiFileParser:
    _DEFAULT_SERVICE_IMAGE_GROUPS = {'overview', 'label', 'thumbnail', 'macro', 'macro image', 'macro mask image',
                                     'label image', 'overview image', 'thumbnail image'}
    _DEEP_ZOOM_CREATION_SCRIPT = os.path.join(os.getenv('WSI_PARSER_HOME', '/opt/local/wsi-parser'),
                                              'create_deepzoom.sh')
    _OME_TIFF_CREATION_SCRIPT = os.path.join(os.getenv('WSI_PARSER_HOME', '/opt/local/wsi-parser'),
                                              'create_pyramid_ome_tiff.sh')

    def __init__(self, file_path):
        self.file_path = file_path
        self.service_image_groups = self._get_service_image_groups()
        self.log_processing_info('Generating XML description')
        self.xml_info_file = os.path.join(WsiParsingUtils.get_service_directory(file_path),
                                          WsiParsingUtils.get_basename_without_extension(self.file_path) + '_info.xml')
        self.generate_xml_info_file()
        self.xml_info_tree = ET.parse(self.xml_info_file).getroot()
        self.stat_file_name = WsiParsingUtils.get_stat_file_name(self.file_path)
        self.tmp_stat_file_name = WsiParsingUtils.get_stat_active_file_name(self.file_path)
        self.tmp_local_dir = WsiParsingUtils.generate_local_service_directory(self.file_path)

    def _get_service_image_groups(self):
        service_image_groups_parameter = os.getenv('WSI_SERVICE_SERIES_NAMES')
        if not service_image_groups_parameter:
            return self._DEFAULT_SERVICE_IMAGE_GROUPS
        else:
            return {group.strip() for group in service_image_groups_parameter.split(',')}

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

    def update_info_file(self, original_width, original_height):
        tiles_dir = self._get_tiles_dir()
        max_zoom = self._max_zoom_level(tiles_dir)
        if max_zoom < 0:
            self.log_processing_info('Unable to determine DZ depth calculation, skipping json file creation')
            return
        self._write_dz_info_to_file(os.path.join(tiles_dir, 'info.json'), original_width, original_height, max_zoom)

    def _get_tiles_dir(self):
        return os.path.join(WsiParsingUtils.get_service_directory(self.file_path), WsiParsingUtils.TILES_DIR)

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
            if os.path.isdir(os.path.join(tiles_dir, dz_layer_folder)) and dz_layer_folder.isdigit():
                dz_layers_folders += 1
        return dz_layers_folders - 1

    def _write_dz_info_to_file(self, dz_info_file_path, width, height, max_dz_level):
        details = {
            'width': width,
            'height': height,
            'minLevel': 0,
            'maxLevel': max_dz_level,
            'tileWidth': ZOOM_TILES_SIZE,
            'tileHeight': ZOOM_TILES_SIZE,
            'bounds': [0, width, 0, height]
        }
        self.log_processing_info(
            'Saving preview settings [width={}; height={}; tiles={}; maxLevel={}] to JSON configuration [{}]'.format(
                width, height, ZOOM_TILES_SIZE, max_dz_level, dz_info_file_path))
        with open(dz_info_file_path, 'w') as output_file:
            output_file.write(json.dumps(details, indent=4))

    def calculate_target_series(self):
        images = self.xml_info_tree.findall(SCHEMA_PREFIX + 'Image')
        series_mapping = self.group_image_series(images)
        self.log_processing_info('Following image groups are found: {}'.format(series_mapping.keys()))
        target_group = None
        target_image_details = None
        target_group_inx = 0
        for group_name in series_mapping.keys():
            if group_name not in self.service_image_groups:
                target_group = group_name
                break
            target_group_inx = target_group_inx + 1
        self.log_processing_info('Target group is: {}'.format(target_group))
        if target_group:
            target_image_details = series_mapping[target_group][0]
            self.log_processing_info('Target group is: {} {} {}'.format(target_image_details.id, target_image_details.name, target_image_details.refractive_index))
        return target_group_inx, target_image_details

    def group_image_series(self, images):
        base_name = os.path.basename(self.file_path)
        current_group_name = ''
        current_group_details_list = []
        series_mapping = OrderedDict()
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
        target_group_inx, target_image_details = self.calculate_target_series()
        if target_image_details is None or target_image_details.id is None:
            self.log_processing_info('Unable to determine target series, skipping DZ creation... ')
            return 1
        target_series = target_image_details.id
        self.create_tmp_stat_file(target_image_details)
        tags_processing_result = self.try_process_tags(target_image_details)
        if TAGS_PROCESSING_ONLY:
            return tags_processing_result
        elif self._is_same_series_selected(target_series):
            self.log_processing_info('The same series [{}] is selected for image processing, skipping... '
                                     .format(target_series))
            self.update_stat_file(target_image_details)
            return 0
        self.log_processing_info('Series #{} selected for conversions [width={}; height={}]'
                                 .format(target_series, target_image_details.width, target_image_details.height))
        if not self._localize_related_files():
            self.log_processing_info('Some errors occurred during copying files from the bucket, exiting...')
            return 1
        local_file_path = os.path.join(self.tmp_local_dir, os.path.basename(self.file_path))
        if TARGET_FORMAT == "DZ":
            self.log_processing_info('DZ is configured as conversion format for the file!')
            self._mkdir(self._get_tiles_dir())
            conversion_result = os.system('bash "{}" "{}" {} {} "{}" "{}" >> $ANALYSIS_DIR/parser-create-dz-$RUN_ID.log 2>&1'
                                          .format(self._DEEP_ZOOM_CREATION_SCRIPT,
                                                  local_file_path,
                                                  target_series,
                                                  ZOOM_TILES_SIZE,
                                                  self._get_tiles_dir(),
                                                  self.tmp_local_dir))
        elif TARGET_FORMAT == "OME_TIFF":
            self.log_processing_info('OME_TIFF is configured as conversion format for the file!')
            conversion_result = os.system('bash "{}" "{}" {} {} "{}" "{}" >> $ANALYSIS_DIR/parser-create-ome-tiff-$RUN_ID.log 2>&1'
                                          .format(self._OME_TIFF_CREATION_SCRIPT,
                                                  local_file_path,
                                                  target_group_inx,
                                                  ZOOM_TILES_SIZE,
                                                  WsiParsingUtils.get_service_directory(self.file_path),
                                                  self.tmp_local_dir))
        else:
            self.log_processing_info('Target format: {} is not supported, exiting...'.format(TARGET_FORMAT))
            return 1
        if conversion_result == 0:
            self.update_stat_file(target_image_details)
            if TARGET_FORMAT == "DZ":
                self.update_info_file(target_image_details.width, target_image_details.height)
            self.log_processing_info('File processing is finished')
        else:
            self.log_processing_info('File processing was not successful')
        return conversion_result

    def try_process_tags(self, target_image_details):
        tags_processing_result = 0
        try:
            if WsiFileTagProcessor(self.file_path, self.xml_info_tree).process_tags(target_image_details) != 0:
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
    Logger.log_task_event(WSI_PROCESSING_TASK_NAME, message, status)


def try_process_file(file_path):
    parser = None
    try:
        parser = WsiFileParser(file_path)
        processing_result = parser.process_file()
        return processing_result
    except Exception as e:
        log_info('An error occurred during [{}] parsing: {}'.format(file_path, str(e)))
    finally:
        if parser:
            parser.clear_tmp_stat_file()
            parser.clear_tmp_local_dir()


def get_processing_pool():
    processing_threads = int(os.getenv('WSI_PARSING_THREADS', 1))
    if processing_threads < 1:
        log_info('Invalid number of threads [{}] is specified for processing, use single one instead'
                 .format(processing_threads))
        processing_threads = 1
    log_info('{} threads enabled for WSI processing'.format(processing_threads))
    if processing_threads == 1:
        return None
    else:
        return multiprocessing.Pool(processing_threads)


def process_wsi_files():
    lookup_paths = os.getenv('WSI_TARGET_DIRECTORIES')
    if not lookup_paths:
        log_success('No paths for WSI processing specified')
        exit(0)
    target_file_formats = tuple(['.' + extension for extension in os.getenv('WSI_FILE_FORMATS', 'vsi,mrxs').split(',')])
    log_info('Following paths are specified for processing: {}'.format(lookup_paths))
    processing_pool = get_processing_pool()
    log_info('Lookup for unprocessed files')
    lookup_paths_set = set(lookup_paths.split(','))
    file_generator = SingleThreadWsiProcessingFileGenerator(lookup_paths_set, target_file_formats) \
        if processing_pool is None \
        else ParallelWsiProcessingFileGenerator(lookup_paths_set, target_file_formats, processing_pool)
    paths_to_wsi_files = file_generator.generate_paths()
    if not paths_to_wsi_files:
        log_success('Found no files requires processing in the target directories.')
        exit(0)
    log_info('Found {} files for processing.'.format(len(paths_to_wsi_files)))
    if TAGS_PROCESSING_ONLY:
        log_info('Only tags will be processed, since TAGS_PROCESSING_ONLY is set to `true`')
    if processing_pool is None:
        for file_path in paths_to_wsi_files:
            try_process_file(file_path)
    else:
        processing_pool.map(try_process_file, paths_to_wsi_files)
    log_success('Finished WSI files processing')
    exit(0)


if __name__ == '__main__':
    process_wsi_files()
