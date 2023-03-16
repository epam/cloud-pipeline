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

import errno
import json
import os
import datetime
import math
import numpy as np
import shutil
import sys
import tempfile
import traceback
import xml.etree.ElementTree as ET

from collections import OrderedDict
from functools import cmp_to_key
from pipeline.common import get_path_with_trailing_delimiter, get_path_without_trailing_delimiter
from PIL import Image
from tifffile import imread
from .utils import HcsParsingUtils, HcsFileLogger, log_run_info, get_int_run_param, get_bool_run_param
from .tags import HcsFileTagProcessor
from .evals import HcsFileEvalProcessor

RAW_TO_OME_TIFF_FLAGS = os.getenv('HCS_PARSING_RAW2OMETIFF_EXTRA_FLAGS')
BFORMATS_TO_RAW_FLAGS = os.getenv('HCS_PARSING_BIOFORMATS2RAW_EXTRA_FLAGS')
HCS_PARSING_OME_TIFF_FILE_NAME = os.getenv('HCS_PARSING_OME_TIFF_FILE_NAME', 'data.ome.tiff')
PLANE_COORDINATES_DELIMITER = os.getenv('HCS_PARSING_PLANE_COORDINATES_DELIMITER', '_')
HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')
MEASUREMENT_INDEX_FILE_PATH = '/{}/{}'.format(HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)

HCS_EVAL_DIR_NAME = os.getenv('HCS_EVAL_DIR_NAME', 'eval')
EVAL_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_EVAL_ONLY')

HCS_PREVIEW_OUTPUT_USE_ABSOLUTE_PATHS = get_bool_run_param('HCS_PARSING_PREVIEW_FIELDS_USE_ABSOLUTE_PATHS', 'true')
TAGS_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_TAGS_ONLY')
DEFAULT_CHANNEL_WIDTH = get_int_run_param('HCS_PARSING_DEFAULT_CHANNEL_WIDTH', 1080)
DEFAULT_CHANNEL_HEIGHT = get_int_run_param('HCS_PARSING_DEFAULT_CHANNEL_HEIGHT', 1080)

WELL_DETAILS_MAPPING = json.loads(os.getenv('HCS_PARSING_PLATE_DETAILS_DICT', '{}'))
DEFAULT_PLATE_SIZE = float(os.getenv('HCS_PARSING_PLATE_DEFAULT_SIZE', 0.006))
OME_TIFF_SEQUENCE_CREATION_SCRIPT = os.path.join(os.getenv('HCS_PARSER_HOME', '/opt/local/hcs-tools'),
                                                 'scripts/convert_to_ome_tiff.sh')
HCS_OME_COMPATIBLE_INDEX_FILE_NAME = 'Index.xml'
OVERVIEW_DIR_NAME = 'overview'
PATH_DELIMITER = '/'


class HcsRoot:
    def __init__(self, root_path, hcs_img_path):
        self.root_path = root_path
        self.hcs_img_path = hcs_img_path


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
        self.__fields = set()
        self.__height = None
        self.__width = None
        self.__field_size_y = None
        self.__field_size_x = None

    def add_x_coord(self, value):
        self.__x_coords.add(value)

    def add_y_coord(self, value):
        self.__y_coords.add(value)

    def add_field(self, field):
        self.__fields.add(field)

    def get_width(self):
        return self.__width

    def set_width(self, value):
        self.__width = value

    def calculate_width(self, size, resolution):
        x_min = sys.maxsize
        x_max = -sys.maxsize - 1
        field_size = size * resolution
        self.__field_size_x = field_size
        for field in self.__fields:
            if field[0] < x_min:
                x_min = field[0]
            if field[0] + field_size > x_max:
                x_max = field[0] + field_size
        return math.ceil((x_max - x_min) / field_size)

    def get_height(self):
        return self.__height

    def set_height(self, value):
        self.__height = value

    def calculate_height(self, size, resolution):
        y_min = sys.maxsize
        y_max = -sys.maxsize - 1
        field_size = size * resolution
        self.__field_size_y = field_size
        for field in self.__fields:
            if field[1] < y_min:
                y_min = field[1]
            if field[1] + field_size > y_max:
                y_max = field[1] + field_size
        return math.ceil((y_max - y_min) / field_size)

    def get_values_dict(self):
        return dict({y_coord: set(self.__x_coords) for y_coord in self.__y_coords})

    def get_field_size(self):
        return self.__field_size_y


class HcsFileParser:

    def __init__(self, hcs_root_dir, hcs_img_path):
        self.hcs_root_dir = get_path_without_trailing_delimiter(hcs_root_dir)
        self.hcs_img_path = hcs_img_path
        self.hcs_img_service_dir = HcsParsingUtils.get_service_directory(self.hcs_img_path)
        self.ome_xml_info_file_path = os.path.join(self.hcs_img_service_dir, 'info.ome.xml')
        self.stat_file_path = HcsParsingUtils.get_stat_file_name(self.hcs_img_path)
        self.tmp_stat_file_path = HcsParsingUtils.get_stat_active_file_name(self.hcs_img_path)
        self.tmp_local_dir = HcsParsingUtils.generate_local_service_directory(self.hcs_img_path)
        self.parsing_start_time = None
        self._processing_logger = HcsFileLogger(self.hcs_root_dir)

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

    def generate_ome_xml_info_file(self):
        self._processing_logger.log_info('Generating XML description')
        HcsParsingUtils.create_service_dir_if_not_exist(self.hcs_img_path)
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_PATH
        if HCS_INDEX_FILE_NAME != HCS_OME_COMPATIBLE_INDEX_FILE_NAME:
            compatible_index_path = os.path.join(self.hcs_img_service_dir, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
            shutil.copy(hcs_index_file_path, compatible_index_path)
            hcs_index_file_path = compatible_index_path
        self.generate_bioformats_ome_xml(hcs_index_file_path, self.ome_xml_info_file_path)

    def build_parsing_details(self):
        return {
            "bioformats2raw_extra_flags": BFORMATS_TO_RAW_FLAGS,
            "raw2ometiff_extra_flags": RAW_TO_OME_TIFF_FLAGS,
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
            self._processing_logger.log_info('Cleaning up temporary processing file: [{}]'.format(self.tmp_stat_file_path))
            os.remove(self.tmp_stat_file_path)

    def clear_tmp_local_dir(self):
        if os.path.exists(self.tmp_local_dir):
            self._processing_logger.log_info('Cleaning up temporary dir: [{}]'.format(self.tmp_local_dir))
            shutil.rmtree(self.tmp_local_dir)

    def _write_hcs_file(self, time_series_details, plate_width, plate_height, comment=None):
        if HCS_PREVIEW_OUTPUT_USE_ABSOLUTE_PATHS:
            source_dir = HcsParsingUtils.extract_cloud_path(self.hcs_root_dir)
            preview_dir = HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)
        else:
            hcs_file_root_dir = os.path.dirname(self.hcs_img_path)
            source_dir = os.path.relpath(self.hcs_root_dir, hcs_file_root_dir)
            preview_dir = os.path.relpath(self.hcs_img_service_dir, hcs_file_root_dir)
        ome_data_file_name = HCS_PARSING_OME_TIFF_FILE_NAME
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
        self._processing_logger.log_info('Saving preview info [source={}; preview={}] to [{}]'
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
        self._processing_logger.log_info('Localizing data files...')
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
        self._processing_logger.log_info('Start processing')
        self.parsing_start_time = datetime.datetime.now()
        if os.path.exists(self.tmp_stat_file_path) \
                and not HcsParsingUtils.active_processing_exceed_timeout(self.tmp_stat_file_path):
            self._processing_logger.log_info('This file is processed by another parser, skipping...')
            return 2
        self.create_tmp_stat_file()
        hcs_index_file_path = self.hcs_root_dir + MEASUREMENT_INDEX_FILE_PATH
        time_series_details = self._extract_time_series_details(hcs_index_file_path)
        self.generate_ome_xml_info_file()
        xml_info_tree = ET.parse(self.ome_xml_info_file_path).getroot()
        plate_width, plate_height = self._get_plate_configuration(xml_info_tree)
        wells_tags = self.read_wells_tags()
        if wells_tags:
            self._processing_logger.log_info("Tags " + str(wells_tags))
        if not TAGS_PROCESSING_ONLY and not EVAL_PROCESSING_ONLY:
            if not self._localize_related_files():
                self._processing_logger.log_info('Some errors occurred during copying files from the bucket, exiting...')
                return 1
            else:
                self._processing_logger.log_info('Localization is finished.')
            local_preview_dir = os.path.join(self.tmp_local_dir, 'preview')
            hcs_local_index_file_path = get_path_without_trailing_delimiter(self.tmp_local_dir) \
                                        + MEASUREMENT_INDEX_FILE_PATH
            for sequence_id, timepoints in time_series_details.items():
                self._processing_logger.log_info('Processing sequence with id={}'.format(sequence_id))
                sequence_index_file_path = self.extract_sequence_data(sequence_id, hcs_local_index_file_path)
                conversion_result = os.system('bash "{}" "{}" "{}" {}'.format(
                    OME_TIFF_SEQUENCE_CREATION_SCRIPT, sequence_index_file_path, local_preview_dir, sequence_id))
                if conversion_result != 0:
                    self._processing_logger.log_info('File processing was not successful...')
                    return 1
                sequence_overview_index_file_path, wells_grid_mapping = self.build_sequence_overview_index(sequence_index_file_path)
                conversion_result = os.system('bash "{}" "{}" "{}" {} "{}"'.format(
                    OME_TIFF_SEQUENCE_CREATION_SCRIPT, sequence_overview_index_file_path, local_preview_dir,
                    sequence_id, 'overview_data.ome.tiff'))
                if conversion_result != 0:
                    self._processing_logger.log_info('File processing was not successful: well preview generation failure')
                    return 1
                self.write_dict_to_file(os.path.join(local_preview_dir, sequence_id, 'wells_map.json'),
                                        self.build_wells_map(sequence_id, wells_grid_mapping, wells_tags))
            cloud_transfer_result = os.system('pipe storage cp -f -r "{}" "{}"'
                                              .format(local_preview_dir,
                                                      HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)))
            if cloud_transfer_result != 0:
                self._processing_logger.log_info('Results transfer was not successful...')
                return 1
            self._write_hcs_file(time_series_details, plate_width, plate_height)
        if not EVAL_PROCESSING_ONLY:
            tags_processing_result = self.try_process_tags(xml_info_tree, wells_tags)
            if TAGS_PROCESSING_ONLY:
                if wells_tags:
                    for sequence_id, timepoints in time_series_details.items():
                        path = os.path.join(self.hcs_img_service_dir, sequence_id, 'wells_map.json')
                        self.write_dict_to_file(path, self.update_wells_json(path, wells_tags))
                return tags_processing_result
        if not TAGS_PROCESSING_ONLY:
            eval_processing_result = self.try_process_eval()
            if EVAL_PROCESSING_ONLY:
                return eval_processing_result
        self.create_stat_file()
        return 0

    def update_wells_json(self, path, wells_tags):
        self._processing_logger.log_info('Updating well tags for %s' % path)
        with open(path, 'r') as well_json:
            current_data = json.load(well_json)
            for well_key, data in current_data.items():
                chunks = well_key.split(PLANE_COORDINATES_DELIMITER)
                well_tuple = (chunks[0], chunks[1])
                data['tags'] = wells_tags.get(well_tuple, {})
            return current_data

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
            last_delim_index = file_name.rfind(PATH_DELIMITER)
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
        plate = HcsParsingUtils.extract_plate_from_hcs_xml(hcs_xml_info_root)
        for well in plate.findall(hcs_schema_prefix + 'Well'):
            if well.get('id') not in sequence_wells:
                plate.remove(well)
        sequence_index_file_path = os.path.join(sequence_data_local_dir, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        ET.register_namespace('', hcs_schema_prefix[1:-1])
        hcs_xml_info_tree.write(sequence_index_file_path)
        return sequence_index_file_path

    def build_wells_map(self, sequence_id, wells_grid_mapping, wells_tags):
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
        self._processing_logger.log_info('Extracted the following plate configuration: round [%s], size [%f]' %
                                         ('true' if is_well_round else 'false', well_size))
        for well_key, fields_list in measured_wells.items():
            chunks = well_key.split(PLANE_COORDINATES_DELIMITER)
            well_tuple = (chunks[0], chunks[1])
            well_tags = wells_tags.get(well_tuple, {})
            wells_mapping[well_key] = self.build_well_details(fields_list, well_size, is_well_round,
                                                              wells_grid_mapping[well_tuple], well_tags)
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

    def build_well_details(self, fields_list, well_size, is_well_round, well, well_tags):
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
            # This branch is covering the case, when only a single field is presented inside the well.
            # In that case there is not enough info to build the well's grid,
            # so some 'static' grid with the field at the well's center is used
            well_viewer_radius = 1
            x_coord_padding = y_coord_padding = 1
            well_view_width = well_view_height = 3
        to_ome_mapping = dict()
        coordinates = {}
        for field in fields_list:
            field_x_coord = x_coords.index(field.x) + 1 + x_coord_padding
            field_y_coord = y_coords.index(field.y) + 1 + y_coord_padding
            to_ome_mapping[self.build_cartesian_coords_key(field_x_coord, field_y_coord)] = field.ome_image_id
            coordinates[field.ome_image_id] = (field.x, field.y)
        well_details = {
            'width': well_view_width,
            'height': well_view_height,
            'round_radius': round(well_viewer_radius, 2) if is_well_round else None,
            'to_ome_wells_mapping': HcsFileParser.ordered_by_coords(to_ome_mapping),
            'field_size': well.get_field_size(),
            'coordinates': coordinates,
            'tags': well_tags
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
        root_xml_file = '-'.join(os.path.basename(self.hcs_root_dir).split('-')[:-1]) + '.xml'
        root_xml_file_path = os.path.join(self.hcs_root_dir, root_xml_file)
        if os.path.exists(root_xml_file_path):
            try:
                xml_info_root = ET.parse(root_xml_file_path).getroot()
                hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(xml_info_root)
                experiment = HcsParsingUtils.find_in_xml(xml_info_root, hcs_schema_prefix + 'Experiment')
                plate_type = HcsParsingUtils.find_in_xml(experiment, hcs_schema_prefix + 'PlateType')
                is_well_round = HcsParsingUtils.find_in_xml(plate_type, hcs_schema_prefix + 'WellFormBottom').text.lower() == 'circle'
                if is_well_round:
                    well_size = float(HcsParsingUtils.find_in_xml(plate_type, hcs_schema_prefix + 'WellDiameterBottom').text)
                else:
                    x_size = float(HcsParsingUtils.find_in_xml(plate_type, hcs_schema_prefix + 'WellWidthXBottom').text)
                    y_size = float(HcsParsingUtils.find_in_xml(plate_type, hcs_schema_prefix + 'WellWidthYBottom').text)
                    well_size = max(x_size, y_size)
                return is_well_round, well_size
            except Exception as e:
                self._processing_logger.log_info('An error occurred during reading plate configuration processing: {}'
                                                 .format(str(e)))
                print(traceback.format_exc())

        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        plate = HcsParsingUtils.extract_plate_from_hcs_xml(hcs_xml_info_root)
        plate_type = plate.find(hcs_schema_prefix + 'PlateTypeName').text
        well_configuration = WELL_DETAILS_MAPPING.get(plate_type, {'size': DEFAULT_PLATE_SIZE})
        well_size = well_configuration['size']
        is_well_round = True if 'is_round' not in well_configuration else well_configuration['is_round'] == 'false'
        return is_well_round, well_size

    def read_wells_tags(self):
        return HcsFileTagProcessor.read_well_tags(self.hcs_root_dir)

    def try_process_eval(self):
        result = 0
        local_eval_folder = os.path.join(self.tmp_local_dir, HCS_EVAL_DIR_NAME)
        harmony_eval_folder = os.path.join(self.hcs_root_dir, HCS_EVAL_DIR_NAME)
        if not os.path.exists(harmony_eval_folder) or not os.listdir(harmony_eval_folder):
            self._processing_logger.log_info('Evaluation files not found.')
            return result
        try:
            HcsFileEvalProcessor(harmony_eval_folder, local_eval_folder)\
                .parse_evaluations()
            eval_results_path = os.path.join(local_eval_folder, HCS_EVAL_DIR_NAME)
            if os.path.exists(eval_results_path) and os.listdir(eval_results_path):
                cloud_transfer_result = os.system('pipe storage cp -f -r "{}" "{}"'
                                                  .format(eval_results_path,
                                                          HcsParsingUtils.extract_cloud_path(self.hcs_img_service_dir)
                                                          + '/' + HCS_EVAL_DIR_NAME + '/'))
                if cloud_transfer_result != 0:
                    self._processing_logger.log_info('Evaluations transfer was not successful.')
        except Exception as e:
            self._processing_logger.log_info('An error occurred during evaluations processing: {}'.format(str(e)))
            print(traceback.format_exc())
            result = 1
        return result

    def try_process_tags(self, xml_info_tree, wells_tags):
        tags_processing_result = 0
        try:
            if HcsFileTagProcessor(self.hcs_root_dir, self.hcs_img_path, xml_info_tree).process_tags(wells_tags) != 0:
                self._processing_logger.log_info('Some errors occurred during file tagging')
                tags_processing_result = 1
        except Exception as e:
            self._processing_logger.log_info('An error occurred during tags processing: {}'.format(str(e)))
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
        self._processing_logger.log_info('Scaling overview TIFF files...')
        well_layers = self.build_well_layers(original_images_list, sequence_data_root_path,
                                             channel_dimensions, sequence_preview_dir_path, wells_grid_mapping)
        wells_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Wells')
        self._processing_logger.log_info('Merging overview TIFF files...')
        for well in wells_list.findall(hcs_schema_prefix + 'Well'):
            self.merge_well_layers(original_images_list, sequence_preview_dir_path, well, well_layers,
                                   wells_grid_mapping, channel_dimensions)
        hcs_xml_info_root.append(original_images_list)
        preview_sequence_index_file_path = os.path.join(sequence_preview_dir_path, HCS_OME_COMPATIBLE_INDEX_FILE_NAME)
        ET.register_namespace('', hcs_schema_prefix[1:-1])
        hcs_xml_info_tree.write(preview_sequence_index_file_path)
        return preview_sequence_index_file_path, wells_grid_mapping

    def merge_well_layers(self, original_images_list, sequence_preview_dir_path, well, well_layers,
                          wells_grid_mapping, channel_dimensions):
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
                    merged_image = self.merge_image(channel_images, hcs_schema_prefix, sequence_preview_dir_path,
                                                    channel_dimensions[channel_id], wells_grid_mapping[well_id])
                    merged_image.save(os.path.join(sequence_preview_dir_path, well_preview_full_name))
                    bottom_row_details = list(well_rows_data)[0]
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

    def merge_image(self, images, hcs_schema_prefix, sequence_preview_dir_path, channel, well):
        image_size_x = None
        image_size_y = None

        name = None

        initial_size_x = channel[0]
        initial_size_y = channel[1]

        resolution_x = channel[2]
        resolution_y = channel[3]

        pixel_size_x = None
        size_x = None

        pixel_size_y = None
        size_y = None

        x_start = sys.maxint
        y_start = sys.maxint

        coordinates = {}

        for list in images.values():
            for image in list:
                file_name = image.find(hcs_schema_prefix + 'URL').text

                if image_size_x is None:
                    name = file_name
                    array = imread(os.path.join(sequence_preview_dir_path, file_name))

                    image_size_y = array.shape[0]
                    pixel_size_y = resolution_y * initial_size_y / image_size_y
                    size_y = pixel_size_y * image_size_y

                    image_size_x = array.shape[1]
                    pixel_size_x = resolution_x * initial_size_x / image_size_x
                    size_x = pixel_size_x * image_size_x

                x = float(image.find(hcs_schema_prefix + 'PositionX').text)
                y = float(image.find(hcs_schema_prefix + 'PositionY').text)
                if x < x_start:
                    x_start = x
                if y < y_start:
                    y_start = y
                coordinates[file_name] = [x, y, image.find(hcs_schema_prefix + 'FieldID').text]

        result = np.zeros((initial_size_y, initial_size_x))
        self._processing_logger.log_info("Merging images %s %dx%d" % (name, initial_size_x, initial_size_y))

        if len(coordinates) == 1:
            for name, coord in coordinates.items():
                image = imread(os.path.join(sequence_preview_dir_path, name), key=0)
                return Image.fromarray(image)

        for name, coord in coordinates.items():
            coord[0] = coord[0] - x_start
            coord[1] = coord[1] - y_start

            row_end = int((coord[1] + size_y) / pixel_size_y)
            row_start = int(coord[1] / pixel_size_y)

            col_start = int(coord[0] / pixel_size_x)
            col_end = int((coord[0] + size_x) / pixel_size_x)

            result[row_start:row_end, col_start:col_end] = \
                np.flipud(imread(os.path.join(sequence_preview_dir_path, name), key=0))
        merged_image = np.flipud(result)

        return Image.fromarray(merged_image)

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
        return well_grouping

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
            well_grid_details.add_field((float(x_coord_value), float(y_coord_value)))
            wells_grid_mapping[well_id] = well_grid_details
        return wells_grid_mapping

    def get_channel_dimensions(self, hcs_file_root, wells_grid_mapping):
        hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_file_root)
        channel_dimensions = dict()
        for channel_map in hcs_file_root.find(hcs_schema_prefix + 'Maps').findall(hcs_schema_prefix + 'Map'):
            for entry in channel_map.findall(hcs_schema_prefix + 'Entry'):
                channel_size_x = entry.find(hcs_schema_prefix + 'ImageSizeX')
                y_scaling = 1
                x_scaling = 1

                if channel_size_x is not None:
                    resolution_x = float(entry.find(hcs_schema_prefix + 'ImageResolutionX').text)
                    resolution_y = float(entry.find(hcs_schema_prefix + 'ImageResolutionY').text)
                    channel_size_x = int(channel_size_x.text)
                    channel_size_y = int(entry.find(hcs_schema_prefix + 'ImageSizeY').text)
                    channel_id = entry.get('ChannelID')
                    channel_dimensions[channel_id] = tuple([channel_size_x, channel_size_y,
                                                            resolution_x, resolution_y])

                    for well_grid in wells_grid_mapping.values():
                        height = well_grid.calculate_height(resolution_y, channel_size_y)
                        width = well_grid.calculate_width(resolution_x, channel_size_x)
                        size = max(height, width)
                        well_grid.set_height(size)
                        well_grid.set_width(size)
                        y_scaling = max(y_scaling, size)
                        x_scaling = max(x_scaling, size)

                    entry.find(hcs_schema_prefix + 'ImageResolutionX').text = \
                        str(float(resolution_x) * x_scaling).upper()
                    entry.find(hcs_schema_prefix + 'ImageResolutionY').text = \
                        str(float(resolution_y) * y_scaling).upper()
        return channel_dimensions
