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

"""
This python file is one of the entry point to the hcs-parser.
Used when you need to run image processing on one node,
also used by process_hcs_files_cluster.py to run processing of particular hcs_roots.

Firstly it will find all hcs_roots to process based on:
  HCS_TARGET_PATHS - exact locations of hcs roots
  or
  HCS_LOOKUP_DIRECTORIES - folders to search to find all hcs_roots
  and
  HCS_ROOT_TYPE - Currently TIFF and CZI are supported, based on this parameter hcs-parser defines how to
                  search hcs_roots and later how to perform some of the image generation steps
                  (see process_hcs_files.py and processors.py)

Secondly script, based on HCS_ROOT_TYPE property, will initiate one of HcsFileParser (see processors.py) to run processing
"""

import os
import multiprocessing
import traceback

from src.utils import log_run_info, log_run_success
from src.utils import get_int_run_param, get_bool_run_param
from src.fs import get_processing_roots
from src.processors import HcsCZIFileParser, HcsTiffFileParser
from src.hcs_entity import HcsRootType

TAGS_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_TAGS_ONLY')
EVAL_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_EVAL_ONLY')
FORCE_PROCESSING = get_bool_run_param('HCS_PARSING_FORCE_PROCESSING')

HCS_OME_COMPATIBLE_INDEX_FILE_NAME = 'Index.xml'
OVERVIEW_DIR_NAME = 'overview'
HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')
MEASUREMENT_INDEX_FILE_PATH = '/{}/{}'.format(HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)
HCS_ROOT_TYPE = HcsRootType.get(os.getenv('HCS_ROOT_TYPE', 'TIFF'))

HCS_ROOT_SEARCH_MARK = MEASUREMENT_INDEX_FILE_PATH
if HCS_ROOT_TYPE == HcsRootType.CZI:
    HCS_ROOT_SEARCH_MARK = ".czi"

def try_process_hcs(hcs_root):
    parser = None
    processing_result = 1
    try:
        log_run_info('Starting processing of path {} of type {} with image preview {}'
                     .format(hcs_root.root_path, HCS_ROOT_TYPE, hcs_root.hcs_img_path))
        parser = initialize_hcs_parser(hcs_root)
        processing_result = parser.process_file()
        return processing_result
    except Exception as e:
        log_run_info('An error occurred during [{}] parsing: {}'.format(hcs_root.root_path, str(e)))
        print(traceback.format_exc())
        return 1
    finally:
        if parser:
            if processing_result != 2:
                parser.clear_tmp_stat_file()
            parser.clear_tmp_local_dir()


def initialize_hcs_parser(hcs_root):
    if HCS_ROOT_TYPE == HcsRootType.CZI:
        return HcsCZIFileParser(hcs_root.root_path, hcs_root.hcs_img_path)
    return HcsTiffFileParser(hcs_root.root_path, hcs_root.hcs_img_path)


def process_hcs_files():
    should_force_processing = TAGS_PROCESSING_ONLY or FORCE_PROCESSING
    paths_to_hcs_roots = get_processing_roots(should_force_processing, HCS_ROOT_SEARCH_MARK, HCS_ROOT_TYPE)
    if not paths_to_hcs_roots or len(paths_to_hcs_roots) == 0:
        log_run_success('Found no files requires processing in the lookup directories.')
        exit(0)
    log_run_info('Found {} files for processing.'.format(len(paths_to_hcs_roots)))

    processing_threads = get_int_run_param('HCS_PARSER_PROCESSING_THREADS', 1)
    if processing_threads < 1:
        log_run_info('Invalid number of threads [{}] is specified for processing, use single one instead'
                     .format(processing_threads))
        processing_threads = 1
    log_run_info('{} thread(s) enabled for HCS processing'.format(processing_threads))
    if TAGS_PROCESSING_ONLY:
        log_run_info('Only tags will be processed, since TAGS_PROCESSING_ONLY is set to `true`')
    if EVAL_PROCESSING_ONLY:
        log_run_info('Only evaluations will be processed, since EVAL_PROCESSING_ONLY is set to `true`')
    exit_code = 0
    if processing_threads == 1:
        for file_path in paths_to_hcs_roots:
            result = try_process_hcs(file_path)
            if result == 1:
                exit_code = result
    else:
        pool = multiprocessing.Pool(processing_threads)
        pool.map(try_process_hcs, paths_to_hcs_roots)
    log_run_success('Finished HCS files processing')
    exit(exit_code)


if __name__ == '__main__':
    process_hcs_files()
