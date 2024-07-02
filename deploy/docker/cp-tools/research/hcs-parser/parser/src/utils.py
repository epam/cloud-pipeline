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

import os
import datetime
import tempfile
import time
import xml.etree.ElementTree as ET

from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger


def get_int_run_param(env_var_name, default_value):
    return int(os.getenv(env_var_name, default_value))


HCS_PROCESSING_TASK_NAME = 'HCS processing'
HCS_ACTIVE_PROCESSING_TIMEOUT_MIN = get_int_run_param('HCS_PARSING_ACTIVE_PROCESSING_TIMEOUT_MIN', 360)
HCS_CLOUD_FILES_SCHEMA = os.getenv('HCS_PARSING_CLOUD_FILES_SCHEMA', 's3')
HCS_PROCESSING_OUTPUT_FOLDER = os.getenv('HCS_PARSING_OUTPUT_FOLDER')
HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')


def get_list_run_param(env_var_name, delimiter=','):
    param_elements = os.getenv(env_var_name, '').split(delimiter)
    return filter(lambda string: string is not None and len(string.strip()) > 0, param_elements)


def get_bool_run_param(env_var_name, default='false'):
    return os.getenv(env_var_name, default) == 'true'


def log_run_success(message):
    log_run_info(message, status=TaskStatus.SUCCESS)


def log_run_info(message, status=TaskStatus.RUNNING):
    Logger.log_task_event(HCS_PROCESSING_TASK_NAME, message, status)


class HcsFileLogger:

    def __init__(self, file_path):
        self.file_path = file_path

    def log_info(self, message, status=TaskStatus.RUNNING):
        log_run_info('[{}] {}'.format(self.file_path, message), status)


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
    def extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix=None):
        if not hcs_schema_prefix:
            hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        plates_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Plates')
        plate = plates_list.find(hcs_schema_prefix + 'Plate')
        return plate

    @staticmethod
    def build_preview_file_path(hcs_root_folder_path, with_id=False):
        file_name = HcsParsingUtils.build_preview_file_name(hcs_root_folder_path)
        if with_id:
            file_name = file_name + '.' + hcs_root_folder_path.split('/')[-1]
        preview_file_basename = HcsParsingUtils.replace_special_chars(file_name) + '.hcs'
        parent_folder = HCS_PROCESSING_OUTPUT_FOLDER \
            if HCS_PROCESSING_OUTPUT_FOLDER is not None \
            else os.path.dirname(hcs_root_folder_path)
        return os.path.join(parent_folder, preview_file_basename)

    @staticmethod
    def build_preview_file_name(hcs_root_folder_path):
        index_file_abs_path = os.path.join(HcsParsingUtils.get_file_without_extension(hcs_root_folder_path),
                                           HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)
        hcs_xml_info_root = ET.parse(index_file_abs_path).getroot()
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        file_name = HcsParsingUtils.get_file_without_extension(hcs_root_folder_path)
        name_xml_element = HcsParsingUtils.extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix) \
            .find(hcs_schema_prefix + 'Name')
        if name_xml_element is not None:
            file_pretty_name = name_xml_element.text
            if file_pretty_name is not None:
                file_name = file_pretty_name
        return file_name

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
    def quote_string(string):
        return '"{}"'.format(string)

    @staticmethod
    def replace_special_chars(file_path):
        return file_path.replace('/', '|')

    @staticmethod
    def find_in_xml(element, name):
        if element is None:
            return None
        else:
            return element.find(name)

    @staticmethod
    def find_all_in_xml(element, name):
        if element is None:
            return []
        else:
            return element.findall(name)

    @staticmethod
    def get_hcs_image_folder():
        return HCS_IMAGE_DIR_NAME
