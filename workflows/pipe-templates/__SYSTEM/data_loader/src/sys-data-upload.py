# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipeline import Logger, TaskStatus
from model.entities_api import EntitiesAPI
import os
import time
import multiprocessing
from multiprocessing.pool import ThreadPool
import subprocess
import shlex

UPLOAD_TASK_NAME = 'Upload'
INPUT_CHECK_TASK_NAME = 'InputParametersCheck'
METADATA_TASK_NAME = 'MetadataValuesExtraction'

# TODO: Move this settings to the GUI
UPLOAD_RETRY_COUNT = 5
UPLOAD_RETRY_TIMEOUT_SEC = 5

def upload_data(src, dst, f_name_format, c_name, c_type, create_folders, entity_id, m_id, ent_api, upd_paths):
    if not dst.endswith('/'):
        dst = dst + '/'
    if f_name_format is not None and c_name is not None:
        if create_folders:
            dst = dst + c_name + '/' + f_name_format
        else:
            dst = dst + f_name_format.format(c_name)
    elif f_name_format is None and c_name is not None and create_folders:
        dst = dst + c_name + '/' + src.split('/')[-1:][0]
    else:
        dst = dst + src.split('/')[-1:][0]

    code = 1
    for upload_try_num in range(1, UPLOAD_RETRY_COUNT+1):
        Logger.info("Attempt #{}. Uploading {} to {}...".format(upload_try_num, src, dst), task_name=UPLOAD_TASK_NAME)
        Logger.info('Executing command \'pipe storage cp "{}" "{}" -f  > /dev/null\''.format(src, dst), task_name=UPLOAD_TASK_NAME)
        code = os.system('pipe storage cp "{}" "{}" -f > /dev/null'.format(src, dst))
        if code != 0:
            Logger.fail("Attempt #{}. Error uploading {} to {}".format(upload_try_num, src, dst), task_name=UPLOAD_TASK_NAME)
            if upload_try_num < UPLOAD_RETRY_COUNT:
                time.sleep(UPLOAD_RETRY_TIMEOUT_SEC)
            else:
                Logger.fail("All {} attempts failed for {}. Source is not uploaded".format(UPLOAD_RETRY_COUNT, src), task_name=UPLOAD_TASK_NAME)
        else:
            Logger.info("Uploading {} to {} done".format(src, dst), task_name=UPLOAD_TASK_NAME)
            if upd_paths:
                ent_api.update_key(m_id, entity_id, c_name, c_type, dst)
            break

    return code

if __name__ == '__main__':
    Logger.info("Checking input parameters", task_name=INPUT_CHECK_TASK_NAME)
    scripts_dir = os.environ['SCRIPTS_DIR']
    if 'DESTINATION_DIRECTORY' not in os.environ:
        Logger.fail("DESTINATION_DIRECTORY parameter is missing", task_name=INPUT_CHECK_TASK_NAME)
        exit(1)
    if 'METADATA_ID' not in os.environ:
        Logger.fail("METADATA_ID parameter is missing", task_name=INPUT_CHECK_TASK_NAME)
        exit(1)
    if 'METADATA_CLASS' not in os.environ:
        Logger.fail("METADATA_CLASS parameter is missing", task_name=INPUT_CHECK_TASK_NAME)
        exit(1)
    if 'METADATA_COLUMNS' not in os.environ:
        Logger.fail("METADATA_COLUMNS parameter is missing or invalid", task_name=INPUT_CHECK_TASK_NAME)
        exit(1)
    destination = os.environ['DESTINATION_DIRECTORY']
    api_path = os.environ['API']
    api_token = os.environ['API_TOKEN']
    metadata_id = os.environ['METADATA_ID']
    metadata_class = os.environ['METADATA_CLASS']
    metadata_columns_str = os.environ['METADATA_COLUMNS']
    metadata_entities = []
    file_name_format_column = None
    if 'METADATA_ENTITIES' in os.environ:
        metadata_entities = map(lambda e: e.strip(), os.environ['METADATA_ENTITIES'].split(','))
    if metadata_columns_str is None:
            metadata_columns_str = ''
    if 'FILE_NAME_FORMAT_COLUMN' in os.environ:
        file_name_format_column = os.environ['FILE_NAME_FORMAT_COLUMN']
    create_folders_for_columns = \
        os.environ['CREATE_FOLDERS_FOR_COLUMNS'].lower() == 'true' if 'CREATE_FOLDERS_FOR_COLUMNS' in os.environ else False
    update_paths = os.environ['UPDATE_PATH_VALUES'].lower() == 'true' if 'UPDATE_PATH_VALUES' in os.environ else False
    metadata_column_names = metadata_columns_str.split(',')
    metadata_columns_values = {}
    metadata_columns = []
    for column in metadata_column_names:
        column_name = column.strip()
        if len(column_name) > 0:
            metadata_columns.append(column_name)
            metadata_columns_values[column_name] = []
    Logger.info('Input parameters checked', task_name=INPUT_CHECK_TASK_NAME)
    Logger.info('Destination: {}'.format(destination), task_name=INPUT_CHECK_TASK_NAME)
    Logger.info('Metadata ID: {}'.format(metadata_id), task_name=INPUT_CHECK_TASK_NAME)
    Logger.info('Metadata Class: {}'.format(metadata_class), task_name=INPUT_CHECK_TASK_NAME)
    Logger.info('Metadata columns: {}'.format(', '.join(metadata_columns)), task_name=INPUT_CHECK_TASK_NAME)
    Logger.success("Done", task_name=INPUT_CHECK_TASK_NAME)

    Logger.info('Extracting metadata values (#{}, {}) for columns {}...'.format(
                    metadata_id,
                    metadata_class,
                    ', '.join(metadata_columns)
                ), task_name=METADATA_TASK_NAME)

    api = EntitiesAPI(api_path, api_token)
    for el in api.load_all(metadata_id, metadata_class):
        if len(metadata_entities) > 0 and str(el.id) not in metadata_entities:
            continue
        if el.data is not None:
            for column in metadata_columns:
                if column in el.data and 'value' in el.data[column]:
                    value = el.data[column]['value'].encode("utf-8")
                    if not value.lower().startswith('http://') and not value.lower().startswith('https://') and not value.lower().startswith('ftp://'):
                        Logger.info('Skipping {} ({}, #{}) - not http, https or ftp source'.format(
                                                el.data[column]['value'].encode("utf-8"),column,el.external_id),
                                    task_name=METADATA_TASK_NAME)
                        continue
                    column_type = el.data[column]['type']
                    file_name_format = None
                    if file_name_format_column is not None and file_name_format_column in el.data and 'value' in el.data[file_name_format_column]:
                        file_name_format = el.data[file_name_format_column]['value'].encode("utf-8") + '_{}' if not create_folders_for_columns else el.data[file_name_format_column]['value'].encode("utf-8")
                    metadata_columns_values[column].append((el.external_id, el.id, value, column_type, file_name_format))
                    Logger.info('{} ({}, #{})'.format(
                                    el.data[column]['value'].encode("utf-8"),
                                    column,
                                    el.external_id
                                ), task_name=METADATA_TASK_NAME)
    Logger.success("Done", task_name=METADATA_TASK_NAME)
    Logger.info("Starting uploading task", task_name=UPLOAD_TASK_NAME)

    cpu_count = multiprocessing.cpu_count()
    if 'MAX_THREADS_COUNT' in os.environ:
        max_threads_count = int(os.environ['MAX_THREADS_COUNT'])
        cpu_count = max_threads_count if max_threads_count < cpu_count else cpu_count

    pool = ThreadPool(cpu_count)
    pool_results = []
    for column in metadata_columns:
        for (external_id, internal_id, url, column_type, file_name_format) in metadata_columns_values[column]:
            upload_result = pool.apply_async(
                                upload_data,
                                (
                                    url,
                                    destination,
                                    file_name_format,
                                    column,
                                    column_type,
                                    create_folders_for_columns,
                                    internal_id,
                                    metadata_id,
                                    api,
                                    update_paths
                                )
                            )
            pool_results.append(upload_result)

    pool.close()
    pool.join()

    successes_count = sum([1 for x in pool_results if x.get() == 0])

    if successes_count == len(pool_results):
        Logger.success("Upload done. All transfers completed successfully", task_name=UPLOAD_TASK_NAME)
        exit(0)

    elif successes_count == 0:
        Logger.fail("Upload completed with errors. ALL transfers FAILED\nPlease review errors above", task_name=UPLOAD_TASK_NAME)
        exit(1)

    else:
        Logger.warn("Upload completed with errors. SOME of the transfers failed to complete\nPlease review errors above", task_name=UPLOAD_TASK_NAME)
        exit(0)

    
