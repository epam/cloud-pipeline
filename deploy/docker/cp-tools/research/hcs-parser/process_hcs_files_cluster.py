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

import os
import datetime
import math
import tempfile
import time
import xml.etree.ElementTree as ET
import multiprocessing
import re
import traceback
import subprocess
from pipeline.api import PipelineAPI, TaskStatus
from pipeline.log import Logger


def get_int_env_var(env_var_name, default_value):
    return int(os.getenv(env_var_name, default_value))


def log_success(message):
    log_info(message, status=TaskStatus.SUCCESS)


def log_info(message, status=TaskStatus.RUNNING):
    Logger.log_task_event(HCS_PROCESSING_TASK_NAME, message, status)


LOOKUP_PATHS = os.getenv('HCS_LOOKUP_DIRECTORIES', '').split(',')
CLUSTER_MAX_SIZE = get_int_env_var('CP_CAP_AUTOSCALE_WORKERS', 1)
RUN_ID = os.getenv('RUN_ID')

HCS_ACTIVE_PROCESSING_TIMEOUT_MIN = get_int_env_var('HCS_ACTIVE_PROCESSING_TIMEOUT_MIN', 360)
TAGS_PROCESSING_ONLY = os.getenv('HCS_PARSING_TAGS_ONLY', 'false') == 'true'
FORCE_PROCESSING = os.getenv('HCS_FORCE_PROCESSING', 'false') == 'true'
HCS_CLOUD_FILES_SCHEMA = os.getenv('HCS_CLOUD_FILES_SCHEMA', 's3')

HCS_PROCESSING_OUTPUT_FOLDER = os.getenv('HCS_PARSING_OUTPUT_FOLDER')
HCS_PROCESSING_TASK_NAME = 'HCS processing'
HCS_OME_COMPATIBLE_INDEX_FILE_NAME = 'Index.xml'
HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')
HCS_CLUSTER_PROCESSING_MEMORY_SIZE_SLOT_FACTOR = get_int_env_var('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_FACTOR', 20)
HCS_CLUSTER_PROCESSING_MEMORY_CLUSTER_SLOT = \
    get_int_env_var('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_CLUSTER_SLOT', 16)
HCS_CLUSTER_PROCESSING_MEMORY_INSTANCE_SLOT = \
    get_int_env_var('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_INSTANCE_SLOT', 8)
MEASUREMENT_INDEX_FILE_PATH = '/{}/{}'.format(HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)


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
        name_xml_element = HcsParsingUtils.extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix) \
            .find(hcs_schema_prefix + 'Name')
        if name_xml_element is not None:
            file_pretty_name = name_xml_element.text
            if file_pretty_name is not None:
                file_name = file_pretty_name
        preview_file_basename = file_name + '.hcs'
        parent_folder = HCS_PROCESSING_OUTPUT_FOLDER \
            if HCS_PROCESSING_OUTPUT_FOLDER is not None \
            else os.path.dirname(hcs_root_folder_path)
        return os.path.join(parent_folder, preview_file_basename)

    @staticmethod
    def extract_plate_from_hcs_xml(hcs_xml_info_root, hcs_schema_prefix=None):
        if not hcs_schema_prefix:
            hcs_schema_prefix = HcsParsingUtils.extract_xml_schema(hcs_xml_info_root)
        plates_list = hcs_xml_info_root.find(hcs_schema_prefix + 'Plates')
        plate = plates_list.find(hcs_schema_prefix + 'Plate')
        return plate

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


class HcsFileLogger:

    def __init__(self, file_path):
        self.file_path = file_path

    def log_info(self, message, status=TaskStatus.RUNNING):
        Logger.log_task_event(HCS_PROCESSING_TASK_NAME, '[{}] {}'.format(self.file_path, message), status)


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


class HcsFileSgeParser:

    SUBMISSION_LOCK = multiprocessing.Lock()
    PENDING_JOB_STATUSES = ['qw', 'qw', 'hqw', 'hqw', 'hRwq', 'hRwq', 'hRwq', 'qw', 'qw']
    RUNNING_JOB_STATUSES = ['r', 't', 'Rr', 'Rt']

    def __init__(self, hcs_file_root_path):
        self.hcs_root_path = hcs_file_root_path
        self.processing_logger = HcsFileLogger(hcs_file_root_path)

    @staticmethod
    def acquire_submission_lock():
        HcsFileSgeParser.SUBMISSION_LOCK.acquire()

    @staticmethod
    def release_submission_lock():
        HcsFileSgeParser.SUBMISSION_LOCK.release()

    def log_info(self, message):
        self.processing_logger.log_info(message)

    def process_file_in_sge(self):
        hcs_root_size = self._calculate_hcs_dir_size_gigabytes()
        if hcs_root_size < 0:
            self.log_info('Size calculation fails, skip processing')
            return
        self.log_info('Total size: {} Gb'.format(hcs_root_size))
        memory_requirement_slots = int(math.ceil(hcs_root_size / HCS_CLUSTER_PROCESSING_MEMORY_SIZE_SLOT_FACTOR))
        memory_requirement_gb = memory_requirement_slots * HCS_CLUSTER_PROCESSING_MEMORY_CLUSTER_SLOT
        self.log_info('Memory requirements: [{} slot(s), {} Gb]'.format(memory_requirement_slots,
                                                                        memory_requirement_gb))
        script_path = self._create_hcs_processing_script(memory_requirement_gb)
        slots_requirement = int(math.ceil(memory_requirement_gb / HCS_CLUSTER_PROCESSING_MEMORY_INSTANCE_SLOT))
        job_id = self._process_submission_in_sge_sync_mode(slots_requirement, script_path)
        self.log_info('Saving job script')
        HcsFileSgeParser._try_copy_to_cloud_output_folder(script_path, 'job-script-{}.sh'.format(job_id))
        job_stats_content = self._extract_job_stats(job_id)
        if job_stats_content is None:
            self.log_info('Unable to determine execution stats')
        else:
            self._finalize_execution_stats(job_id, job_stats_content)

    def _calculate_hcs_dir_size_gigabytes(self):
        path_chunks = self.hcs_root_path.split('/cloud-data/', 1)
        if len(path_chunks) != 2:
            self.log_info('Unable to determine cloud path')
            return -1
        cloud_path = path_chunks[1]
        cloud_path_chunks = cloud_path.split('/', 1)
        storage_name = cloud_path_chunks[0]
        relative_path = cloud_path_chunks[1] if len(cloud_path_chunks) == 2 else ''
        command = "pipe storage du '{}' -p '{}' -f GB | awk ' FNR > 1 {{ print $3 }}' ".format(storage_name, relative_path)
        output = subprocess.check_output(command, shell=True)
        try:
            return float(output.strip())
        except ValueError:
            return -1

    def _build_env_vars_to_propagate(self, heap_limit_gb):
        jvm_parameters = os.getenv('JAVA_OPTS') + ' -Xmx{}G'.format(heap_limit_gb)
        env_vars_string = '''
        export HCS_TARGET_DIRECTORIES="{}"
        export JAVA_OPTS="{}"
        export HCS_PARSER_PROCESSING_THREADS=1
        export PATH="{}"
        '''.format(self.hcs_root_path, jvm_parameters, os.getenv('PATH'))
        for key, value in os.environ.items():
            if key.startswith('HCS_PARSING_'):
                if key == 'HCS_PARSING_PLATE_DETAILS_DICT':
                    env_vars_string += '\nexport {}="{}"'.format(key, value.replace('"', '\\"'))
                else:
                    env_vars_string += '\nexport {}="{}"'.format(key, value)
        return env_vars_string

    @staticmethod
    def _write_to_file(file_path, content):
        with open(file_path, 'w') as output_file:
            output_file.write(content)

    @staticmethod
    def _execute_and_get_stdout(command):
        submission_proc = subprocess.Popen([command], stdout=subprocess.PIPE, shell=True)
        submission_proc.wait()
        (out, _) = submission_proc.communicate()
        return str(out)

    @staticmethod
    def _try_copy_to_cloud_output_folder(source_file_path, destination_file_name=None):
        if destination_file_name is None:
            destination_file_name = os.path.basename(source_file_path)
        cp_command = 'pipe storage cp -f "{}" "$HCS_PARSING_LOGS_OUTPUT/{}"' \
            .format(source_file_path, os.path.join(RUN_ID, destination_file_name))
        return HcsFileSgeParser._execute_and_get_stdout(cp_command)

    def _create_hcs_processing_script(self, heap_limit_gb):
        processing_script_text = """    
        {}
        bash "$HCS_TOOLS_HOME/scripts/start.sh"
        pipe storage cp -f $ANALYSIS_DIR/hcs-parser-$RUN_ID.log $HCS_PARSING_LOGS_OUTPUT/{}/hcs-parser-worker-$RUN_ID.log
        """.format(self._build_env_vars_to_propagate(heap_limit_gb), RUN_ID)
        hcs_processing_job_script_path = tempfile.mkstemp(dir='/tmp', suffix='.sh', prefix='hcs-job-')[1]
        HcsFileSgeParser._write_to_file(hcs_processing_job_script_path, processing_script_text)
        return hcs_processing_job_script_path

    def _extract_job_stats(self, job_id):
        job_stats_command = 'qacct -j ' + job_id
        self.log_info('Extracting execution stats for jobId=[{}]'.format(job_id))
        stats_extraction_retry_delay = get_int_env_var('HCS_CLUSTER_PROCESSING_QACCT_DELAY', 15)
        time.sleep(stats_extraction_retry_delay)
        retries = get_int_env_var('HCS_CLUSTER_PROCESSING_QACCT_RETRIES', 5)
        while retries > 0:
            try:
                return subprocess.check_output(job_stats_command, shell=True)
            except subprocess.CalledProcessError:
                retries -= 1
                time.sleep(stats_extraction_retry_delay)
        return None

    def _finalize_execution_stats(self, job_id, job_stats_content):
        node_name = re.search("\nhostname (.*)\n", job_stats_content).group(1).strip()
        self.log_info('Processing node=[{}], jobId=[{}]'.format(node_name, job_id))
        self.log_info('Saving job execution stats')
        job_stats_file_path = tempfile.mkstemp(dir='/tmp', suffix='.stat', prefix='hcs-' + job_id)[1]
        HcsFileSgeParser._write_to_file(job_stats_file_path, job_stats_content)
        HcsFileSgeParser._try_copy_to_cloud_output_folder(job_stats_file_path, 'job-{}.stat'.format(job_id))

    def _process_submission_in_sge_sync_mode(self, slots_requirement, script_path):
        HcsFileSgeParser.acquire_submission_lock()
        self.log_info('Submitting processing to the SGE cluster')
        submission_command = 'qsub -pe local {} "{}"'.format(slots_requirement, script_path)
        submission_output = self._execute_and_get_stdout(submission_command)
        job_id = re.search("Your job (\\d+) .*", submission_output).group(1)
        self.log_info('Submitted jobId=[{}], awaiting for node to be up...'.format(job_id))
        self._wait_while_job_is_pending(job_id)
        HcsFileSgeParser.release_submission_lock()
        self._wait_while_job_is_running(job_id)
        self.log_info('Processing job is finished')
        return job_id

    def _wait_while_job_is_running(self, job_id):
        while True:
            job_state = self._get_job_state(job_id)
            if not job_state or job_state not in HcsFileSgeParser.RUNNING_JOB_STATUSES:
                return

    def _wait_while_job_is_pending(self, job_id):
        retries = 120
        while retries > 0:
            if self._get_job_state(job_id) in HcsFileSgeParser.PENDING_JOB_STATUSES:
                retries -= 1
                time.sleep(15)
            else:
                return
        self._execute_and_get_stdout('qdel -f {}'.format(job_id))
        raise RuntimeError('Node for jobId=[{}] wasn\'t be able to boot up in 30 minutes. ')

    def _get_job_state(self, job_id):
        job_status_command = 'qstat | awk \'$1 ~ /^{}/\' | awk \'{{ print $5 }}\''.format(job_id)
        return self._execute_and_get_stdout(job_status_command).strip()


def try_process_hcs_in_cluster(hcs_root_dir):
    parser = HcsFileSgeParser(hcs_root_dir)
    try:
        return parser.process_file_in_sge()
    except Exception as e:
        parser.log_info('An error occurred during parsing: ' + str(e))
        parser.release_submission_lock()
        print(traceback.format_exc())


def process_hcs_files_cluster():
    if not LOOKUP_PATHS:
        log_success('No paths for HCS processing specified')
        exit(0)
    log_info('Following paths are specified for processing: {}'.format(LOOKUP_PATHS))
    log_info('Lookup for unprocessed files')
    paths_to_hcs_files = HcsProcessingDirsGenerator(LOOKUP_PATHS).generate_paths()
    if not paths_to_hcs_files:
        log_success('Found no files requires processing in the lookup directories.')
        exit(0)
    log_info('Found {} files for processing.'.format(len(paths_to_hcs_files)))
    pool = multiprocessing.Pool(CLUSTER_MAX_SIZE)
    pool.map(try_process_hcs_in_cluster, paths_to_hcs_files)
    log_success('Finished HCS files processing')
    exit(0)


if __name__ == '__main__':
    process_hcs_files_cluster()
