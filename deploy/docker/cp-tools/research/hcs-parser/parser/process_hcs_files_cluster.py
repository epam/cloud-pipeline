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
import math
from pipeline.api import PipelineAPI
import tempfile
import time
import multiprocessing
import re
import traceback
import subprocess
import urllib

from src.fs import get_processing_roots
from src.utils import HcsFileLogger, log_run_info, log_run_success
from src.utils import get_int_run_param, get_bool_run_param

SUCCESS_EXIT_CODE = '0'
ASYNC_EXIT_CODE = '777'
ERROR_EXIT_CODE = '1'
EMAIL_TEMPLATE = '''
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <style>
        table,
        td {{
            border: 1px solid black;
            border-collapse: collapse;
            padding: 5px;
        }}
    </style>
</head>

<body>
<p>Dear user,</p>
<p>*** This is a system generated email, do not reply to this email ***</p>
<p> {text} </p>
<p>
<table>
    <tr>
        <td><b>Image name</b></td>
        <td><b>Status</b></td>
        <td><b>Raw data</b></td>
    </tr>
    {events}
</table>
</p>
<p>Best regards,</p>
<p>{deploy_name} Platform</p>
</body>

</html>
'''

STARTED_TEXT = 'Starting HCS images processing in {deploy_name} Platform. ' \
               'You can monitor processing using this <a href="{api}/#/run/{run_id}/plain">link</a>. ' \
               'Please find list of images below:'

FINISHED_TEXT = 'HCS images processing in {deploy_name} Platform has been finished ' \
                'You can review processing logs using this <a href="{api}/#/run/{run_id}/plain">link</a>. ' \
                'Please find list of images below:'

EVENT_PATTERN = '''
 <tr>
            <td>{image}</td>
            <td>{status}</a></td>
            <td><a href="{api}/#/storage/{id}?path={path}">{folder_id}</a></td>
 </tr>
'''

CLUSTER_MAX_SIZE = get_int_run_param('CP_CAP_AUTOSCALE_WORKERS', 1)
TAGS_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_TAGS_ONLY')
EVAL_PROCESSING_ONLY = get_bool_run_param('HCS_PARSING_EVAL_ONLY')
FORCE_PROCESSING = get_bool_run_param('HCS_FORCE_PROCESSING')

ASYNC_MODE = get_bool_run_param('HCS_ASYNC_PROCESSING')
PIPE_INSTANCE_TYPE = os.getenv('HCS_WORKER_INSTANCE_TYPE', 'r5.4xlarge')
PIPE_INSTANCE_MEMORY = os.getenv('HCS_WORKER_MEMORY_GB', '120')
PIPE_INSTANCE_DISK = get_int_run_param('HCS_WORKER_DISK', 500)
PIPE_WORKER_IMAGE = os.getenv('docker_image')

COMMON_JAVA_OPTS = os.getenv('JAVA_OPTS')
MASTER_RUN_ID = os.getenv('RUN_ID')

HCS_INDEX_FILE_NAME = os.getenv('HCS_PARSING_INDEX_FILE_NAME', 'Index.xml')
HCS_IMAGE_DIR_NAME = os.getenv('HCS_PARSING_IMAGE_DIR_NAME', 'Images')
MEASUREMENT_INDEX_FILE_PATH = '/{}/{}'.format(HCS_IMAGE_DIR_NAME, HCS_INDEX_FILE_NAME)
HCS_CLUSTER_PROCESSING_MEMORY_SIZE_SLOT_FACTOR = get_int_run_param('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_FACTOR', 20)
HCS_CLUSTER_INSTANCE_SLOT_SIZE = get_int_run_param('HCS_CLUSTER_INSTANCE_SLOT_SIZE', 0)
HCS_CLUSTER_PROCESSING_MEMORY_CLUSTER_SLOT = \
    get_int_run_param('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_CLUSTER_SLOT', 16)
HCS_CLUSTER_PROCESSING_MEMORY_INSTANCE_SLOT = \
    get_int_run_param('HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_INSTANCE_SLOT', 8)


class HcsResult:
    def __init__(self, path, image, status):
        self.path = path
        self.image = image
        self.status = status


class HcsFileSgeParser:

    SUBMISSION_LOCK = multiprocessing.Lock()
    PENDING_JOB_STATUSES = ['qw', 'qw', 'hqw', 'hqw', 'hRwq', 'hRwq', 'hRwq', 'qw', 'qw']
    RUNNING_JOB_STATUSES = ['r', 't', 'Rr', 'Rt']

    def __init__(self, hcs_file_root_path, hcs_img_path):
        self.hcs_root_path = hcs_file_root_path
        self.hcs_img_path = hcs_img_path
        self.processing_logger = HcsFileLogger(hcs_file_root_path)

    @staticmethod
    def acquire_submission_lock():
        HcsFileSgeParser.SUBMISSION_LOCK.acquire()

    @staticmethod
    def release_submission_lock():
        try:
            HcsFileSgeParser.SUBMISSION_LOCK.release()
        except:
            pass

    def log_info(self, message):
        self.processing_logger.log_info(message)

    def process_file_using_pipe(self):
        self.log_info('Starting processing of folder {} with image preview {} using pipe command'
                      .format(self.hcs_root_path, self.hcs_img_path))

        env = self._get_propagated_env_vars(PIPE_INSTANCE_MEMORY)
        env_vars = ['{} \'{}\''.format(key, value) for key, value in env.items()]

        command = 'bash "$HCS_TOOLS_HOME/scripts/start.sh"; ' \
                  'result=$?; ' \
                  'pipe storage cp -f $ANALYSIS_DIR/hcs-parser-$RUN_ID.log $HCS_PARSING_LOGS_OUTPUT/{}/hcs-parser-worker-$RUN_ID.log;' \
                  'exit $result'.format(MASTER_RUN_ID)
        params = ' '.join(env_vars)

        pipe_cmd = 'pipe run -y -id {instance_disk} -it {instance_type} -di {docker_image} ' \
                   '-cmd \'{command}\' -pt on-demand -r 1 -- {params}'.format(
            instance_disk=PIPE_INSTANCE_DISK,
            instance_type=PIPE_INSTANCE_TYPE,
            docker_image=PIPE_WORKER_IMAGE,
            command=command,
            params=params)

        self.log_info('Submitting {} processing with command "{}"'.format(self.hcs_root_path, pipe_cmd))
        result = self._execute_and_get_stdout(pipe_cmd)
        self.log_info(result)
        return HcsResult(self.hcs_root_path, self.hcs_img_path, ASYNC_EXIT_CODE)

    def process_file_in_sge(self):
        self.log_info('Starting SGE processing of folder {} with image preview {}'
                      .format(self.hcs_root_path, self.hcs_img_path))
        hcs_root_size = self._calculate_hcs_dir_size_gigabytes()
        if hcs_root_size < 0:
            self.log_info('Size calculation fails, skip processing')
            return HcsResult(self.hcs_root_path, self.hcs_img_path, ERROR_EXIT_CODE)
        self.log_info('Total size: {} Gb'.format(hcs_root_size))
        if HCS_CLUSTER_INSTANCE_SLOT_SIZE > 0:
            memory_requirement_slots = HCS_CLUSTER_INSTANCE_SLOT_SIZE
            memory_requirement_gb = memory_requirement_slots * HCS_CLUSTER_PROCESSING_MEMORY_INSTANCE_SLOT
            slots_requirement = memory_requirement_slots
        else:
            memory_requirement_slots = int(math.ceil(hcs_root_size / HCS_CLUSTER_PROCESSING_MEMORY_SIZE_SLOT_FACTOR))
            memory_requirement_slots = max(memory_requirement_slots, 1)
            memory_requirement_gb = memory_requirement_slots * HCS_CLUSTER_PROCESSING_MEMORY_CLUSTER_SLOT
            slots_requirement = int(math.ceil(memory_requirement_gb / HCS_CLUSTER_PROCESSING_MEMORY_INSTANCE_SLOT))

        self.log_info('Memory requirements: [{} slot(s), {} Gb]'.format(memory_requirement_slots,
                                                                        memory_requirement_gb))
        script_path = self._create_hcs_processing_script(memory_requirement_gb)
        job_id = self._process_submission_in_sge_sync_mode(slots_requirement, script_path)
        self.log_info('Saving job script')
        HcsFileSgeParser._try_copy_to_cloud_output_folder(script_path, 'job-script-{}.sh'.format(job_id))
        job_stats_content = self._extract_job_stats(job_id)
        if job_stats_content is None:
            self.log_info('Unable to determine execution stats')
            return HcsResult(self.hcs_root_path, self.hcs_img_path, ERROR_EXIT_CODE)
        else:
            exit_code = self._finalize_execution_stats(job_id, job_stats_content)
            return HcsResult(self.hcs_root_path, self.hcs_img_path, exit_code)

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
        jvm_parameters = COMMON_JAVA_OPTS + ' -Xmx{}G'.format(heap_limit_gb)
        env_vars_string = '''
        export HCS_TARGET_DIRECTORIES="{}"
        export HCS_TARGET_IMG_NAMES="{}"
        export JAVA_OPTS="{}"
        export HCS_PARSER_PROCESSING_THREADS=1
        export PATH="{}"
        export BF_MAX_MEM="{}G"
        '''.format(self.hcs_root_path, self.hcs_img_path, jvm_parameters, os.getenv('PATH'), str(heap_limit_gb))
        for key, value in os.environ.items():
            if key.startswith('HCS_PARSING_'):
                if key == 'HCS_PARSING_PLATE_DETAILS_DICT':
                    env_vars_string += '\nexport {}="{}"'.format(key, value.replace('"', '\\"'))
                else:
                    env_vars_string += '\nexport {}="{}"'.format(key, value)
        return env_vars_string

    def _get_propagated_env_vars(self, memory_limit):
        result = {
            'HCS_TARGET_DIRECTORIES': self.hcs_root_path,
            'HCS_TARGET_IMG_NAMES': self.hcs_img_path,
            'JAVA_OPTS': COMMON_JAVA_OPTS + ' -Xmx{}G'.format(memory_limit),
            'HCS_PARSER_PROCESSING_THREADS': '1',
            'CP_CAP_LIMIT_MOUNTS': os.getenv('CP_CAP_LIMIT_MOUNTS')
        }
        for key, value in os.environ.items():
            if key.startswith('HCS_PARSING_'):
                if key == 'HCS_PARSING_PLATE_DETAILS_DICT':
                    result[key] = value.decode('string_escape').replace('"', '\\"')
                else:
                    result[key] = value
        return result

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
            .format(source_file_path, os.path.join(MASTER_RUN_ID, destination_file_name))
        return HcsFileSgeParser._execute_and_get_stdout(cp_command)

    def _create_hcs_processing_script(self, heap_limit_gb):
        processing_script_text = """    
        {}
        bash "$HCS_TOOLS_HOME/scripts/start.sh"
        result=$?
        pipe storage cp -f $ANALYSIS_DIR/hcs-parser-$RUN_ID.log $HCS_PARSING_LOGS_OUTPUT/{}/hcs-parser-worker-$RUN_ID.log
        exit $result
        """.format(self._build_env_vars_to_propagate(heap_limit_gb), MASTER_RUN_ID)
        hcs_processing_job_script_path = tempfile.mkstemp(dir='/tmp', suffix='.sh', prefix='hcs-job-')[1]
        HcsFileSgeParser._write_to_file(hcs_processing_job_script_path, processing_script_text)
        return hcs_processing_job_script_path

    def _extract_job_stats(self, job_id):
        job_stats_command = 'qacct -j ' + job_id
        self.log_info('Extracting execution stats for jobId=[{}]'.format(job_id))
        stats_extraction_retry_delay = get_int_run_param('HCS_CLUSTER_PROCESSING_QACCT_DELAY', 15)
        time.sleep(stats_extraction_retry_delay)
        retries = get_int_run_param('HCS_CLUSTER_PROCESSING_QACCT_RETRIES', 5)
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
        exit_code = re.search("\nexit_status (.*)\n", job_stats_content).group(1).strip()
        return exit_code

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
    parser = HcsFileSgeParser(hcs_root_dir.root_path, hcs_root_dir.hcs_img_path)
    try:
        return parser.process_file_using_pipe() if ASYNC_MODE else parser.process_file_in_sge()
    except Exception as e:
        parser.log_info('An error occurred during parsing: ' + str(e))
        print(traceback.format_exc())
        return HcsResult(hcs_root_dir.root_path, hcs_root_dir.hcs_img_path, ERROR_EXIT_CODE)
    finally:
        parser.release_submission_lock()


def process_hcs_files_cluster():
    should_force_processing = TAGS_PROCESSING_ONLY or EVAL_PROCESSING_ONLY or FORCE_PROCESSING
    paths_to_hcs_roots = get_processing_roots(should_force_processing, MEASUREMENT_INDEX_FILE_PATH)
    if not paths_to_hcs_roots or len(paths_to_hcs_roots) == 0:
        log_run_success('Found no files requires processing in the lookup directories.')
        exit(0)
    log_run_info('Found {} files for processing.'.format(len(paths_to_hcs_roots)))
    notify_processing_started(paths_to_hcs_roots)
    pool = multiprocessing.Pool(CLUSTER_MAX_SIZE)
    results = pool.map(try_process_hcs_in_cluster, paths_to_hcs_roots)
    notify_processing_finished(results)
    log_run_success('Finished HCS files processing')
    exit(0)


def get_api_link(url):
    return url.rstrip('/').replace('/restapi', '')


def prepare_path(path):
    path = '/'.join(path.split('/')[3:])
    return urllib.quote(path, safe='');


def build_notification_text(images, deploy_name, template, finish=False):
    message_str = ''
    api_link = get_api_link(os.environ['API'])
    data_storage_id = os.getenv('HCS_DATA_STORAGE_ID', '')
    markup_storage_id = os.getenv('HCS_MARKUP_STORAGE_ID', '')
    for image in images:
        image_name = os.path.basename(image.image).replace('.hcs', '')
        if finish and os.path.isfile(image.image):
            image_str = '<a href="%s/#/hcs?storage=%s&path=%s">%s</a>' % (api_link, markup_storage_id, prepare_path(image.image), image_name)
        else:
            image_str = image_name
        path = '/'.join(image.path.split('/')[3:])
        if finish:
            if image.status == SUCCESS_EXIT_CODE:
                status = 'Failure'
            elif image.status == ASYNC_EXIT_CODE:
                status = 'In Progress'
            else:
                status = 'Failure'
        else:
            status = image.status
        message_str += EVENT_PATTERN.format(**{'image': image_str,
                                               'path':  prepare_path(image.path),
                                               'status': status,
                                               'api': api_link,
                                               'id': data_storage_id,
                                               'folder_id': os.path.basename(path)})
    text = template.format(**{'api': api_link,
                              'run_id': os.getenv('RUN_ID'),
                              'deploy_name': deploy_name})

    return EMAIL_TEMPLATE.format(**{'events': message_str,
                                    'api': api_link,
                                    'text': text,
                                    'deploy_name': deploy_name})


def notify_processing_finished(results):
    notify_users = get_notification_settings()
    if not notify_users:
        return
    deploy_name = os.getenv('HCS_DEPLOY_NAME', 'Cloud Pipeline')
    api = PipelineAPI(os.environ['API'], 'logs')
    api.create_notification('[%s]: HCS images processing finished' % deploy_name,
                            build_notification_text(results, deploy_name, FINISHED_TEXT, finish=True),
                            notify_users[0],
                            copy_users=notify_users[1:] if len(notify_users) > 0 else None)


def notify_processing_started(paths_to_hcs_roots):
    notify_users = get_notification_settings()
    if not notify_users:
        return
    deploy_name = os.getenv('HCS_DEPLOY_NAME', 'Cloud Pipeline')
    api = PipelineAPI(os.environ['API'], 'logs')
    results = [HcsResult(root.root_path, root.hcs_img_path, 'Starting image processing') for root in paths_to_hcs_roots]
    api.create_notification('[%s]: HCS images processing started' % deploy_name,
                            build_notification_text(results, deploy_name, STARTED_TEXT),
                            notify_users[0],
                            copy_users=notify_users[1:] if len(notify_users) > 0 else None)


def get_notification_settings():
    notify_users = os.getenv('HCS_NOTIFY_USERS', '')
    if not notify_users:
        return None
    return notify_users.split(',')


if __name__ == '__main__':
    process_hcs_files_cluster()
