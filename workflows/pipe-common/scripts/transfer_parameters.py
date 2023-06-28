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

from pipeline import Logger, PipelineAPI
from pipeline.dts import DataTransferServiceClient, LocalToS3, S3ToLocal
from pipeline.storage import S3Bucket
from pipeline.common import get_path_with_trailing_delimiter, \
    get_path_without_trailing_delimiter, \
    get_path_without_first_delimiter, \
    replace_all_system_variables_in_path
from pipeline.api import DataStorageRule
import argparse
import os
import re
import urlparse
from timeit import default_timer as timer
from multiprocessing import Pool
import multiprocessing
import string
import random
import shutil
import socket

LOCALIZATION_TASK_NAME = 'InputData'
VALUE_DELIMITERS = [',', ' ', ';']
HTTP_FTP_SCHEMES = ['http://', 'ftp://', 'https://', 'ftps://']
CLOUD_STORAGE_PATHS = ['cp://', 's3://', 'az://', 'gs://']
TRANSFER_ATTEMPTS = 3


class ParallelType:
    Nothing = 1
    Threaded = 2
    Multiprocessing = 3

    @classmethod
    def current(cls):
        if 'CP_PARALLEL_TRANSFER' in os.environ:
            _mode = os.getenv('CP_PARALLEL_TRANSFER_MODE')
            if _mode == 'multiprocessing':
                return ParallelType.Multiprocessing
            elif _mode == 'threaded':
                return ParallelType.Threaded
            else:
                # Default value is a "multiprocessing", if CP_PARALLEL_TRANSFER is set at all
                return ParallelType.Multiprocessing
        else:
            return ParallelType.Nothing

class File:

    def __init__(self, filename, size):
        self.filename = filename
        self.size = size


class TransferChunk:

    def __init__(self, hostname, files, source, destination, common_folder, task_name, rules):
        self.hostname = hostname
        self.files = files
        self.source = source
        self.destination = destination
        self.common_folder = common_folder
        self.task_name = task_name
        self.rules = rules


class Node:

    def __init__(self, hostname):
        self.hostname = hostname


class Cluster:

    def __init__(self, nodes, slots_per_node):
        self.nodes = nodes
        self.slots_per_node = slots_per_node

    def get_size(self):
        return len(self.nodes) * self.slots_per_node

    @classmethod
    def build_cluster(cls, api, task_name):
        if ParallelType.current() == ParallelType.Nothing:
            Logger.info('Parallel transfer is not enabled.', task_name=task_name)
            return None
        if ParallelType.current() == ParallelType.Threaded:
            Logger.info('Parallel transfer is enabled using "pipe" threads.', task_name=task_name)
            return None
        slots_per_node = cls.get_slots_per_node()
        local_cluster = cls.build_local_cluster(slots_per_node)

        if cls.get_node_count() <= 0:
            Logger.info('No child nodes requested. Using only master node for data transfer.', task_name=task_name)
            return local_cluster

        distributed_cluster = cls.build_distributed_cluster(slots_per_node, api, task_name)
        if distributed_cluster is None or len(distributed_cluster.nodes) == 0:
            Logger.info('Failed to find child nodes. Using only master node for data transfer.', task_name=task_name)
            return local_cluster
        return distributed_cluster

    @classmethod
    def get_node_count(cls):
        env_val = os.getenv('node_count', 0)
        try:
            return int(env_val)
        except ValueError:
            return 0

    @classmethod
    def build_distributed_cluster(cls, slots_per_node, api, task_name):
        if not cls.is_distributed_cluster():
            return None
        Logger.info('Using distributed cluster for data transfer. Master node will be used.', task_name=task_name)
        child_runs = api.load_child_pipelines(os.getenv('RUN_ID'))
        nodes = [Node('localhost')]
        static_runs = filter(lambda run: cls.is_static_run(run), child_runs)
        if len(static_runs) > 0:
            child_hosts = [static_run['podId'] for static_run in static_runs]
            Logger.info('Found %d running child nodes for data transfer: %s.' % (len(static_runs), ','.join(child_hosts)),
                        task_name=task_name)
            nodes.extend([Node(host) for host in child_hosts])
        return Cluster(nodes, slots_per_node)

    @classmethod
    def is_distributed_cluster(cls):
        flag_values = ['yes', 'true']
        sge_enabled = os.getenv('CP_CAP_SGE', None) in flag_values
        autoscale_enabled = os.getenv('CP_CAP_AUTOSCALE', None) in flag_values
        hostfile_configured = os.path.isfile(os.getenv('DEFAULT_HOSTFILE', None))
        return (sge_enabled and autoscale_enabled) or hostfile_configured

    @classmethod
    def build_local_cluster(cls, slots_per_node):
        nodes = [Node('localhost')]
        return Cluster(nodes, slots_per_node)

    @classmethod
    def get_slots_per_node(cls):
        if 'CP_TRANSFER_THREADS' in os.environ:
            return int(os.environ['CP_TRANSFER_THREADS'])
        cpu_number = multiprocessing.cpu_count()
        if 'CP_TRANSFER_PROC_RATIO' in os.environ:
            ratio = int(os.environ['CP_TRANSFER_PROC_RATIO'])
            return max(1, cpu_number/ratio)
        if cpu_number < 32:
            return 1
        if 32 <= cpu_number < 64:
            return 2
        return 3

    @classmethod
    def is_static_run(cls, run):
        if run['status'] != 'RUNNING':
            return False
        if 'pipelineRunParameters' not in run:
            return True
        params = run['pipelineRunParameters']
        if not params:
            return True
        for param in params:
            if param['name'] == 'cluster_role_type' and param['value'] == 'additional':
                return False
        return True


class PathType(object):
    CLOUD_STORAGE = 'CS'
    DTS = 'DTS'
    HTTP_OR_FTP = 'http(s)/ftp(s)'


class ParameterType(object):
    INPUT_PARAMETER = 'input'
    METADATA_PARAMETER = 'metadata'
    COMMON_PARAMETER = 'common'
    OUTPUT_PARAMETER = 'output'


class RunParameter:

    def __init__(self, name, value, type):
        self.name = name
        self.value = value
        self.type = type


class LocalizedPath:

    def __init__(self, path, cloud_path, local_path, type, prefix=None, suffix=None):
        self.path = path
        self.cloud_path = cloud_path
        self.local_path = local_path
        self.type = type
        self.prefix = prefix
        self.suffix = suffix


class RemoteLocation:

    def __init__(self, env_name, original_value, type, paths, delimiter):
        self.env_name = env_name
        self.original_value = original_value
        self.type = type
        self.paths = paths
        self.delimiter = delimiter


class MetadataLocation:

    def __init__(self, param, folder_id, entity_class, entity_ids, local_path):
        self.param = param
        self.folder_id = folder_id
        self.entity_class = entity_class
        self.entity_ids = entity_ids
        self.local_path = local_path


def split(list, n):
    """Yield successive n-sized chunks from lst."""
    for i in xrange(0, len(list), n):
        yield list[i:i + n]


def transfer_async(chunk, with_file_list=True):
    if with_file_list and not chunk.files:
        Logger.info('Skipping empty chunk', task_name=chunk.task_name)
        return
    if with_file_list:
        file_list_name = ''.join(random.choice(string.ascii_lowercase) for _ in range(10)) + '.list'
        file_list_path = os.path.join(chunk.common_folder, file_list_name)
        with open(file_list_path, 'w') as file_list:
            for file in chunk.files:
                file_list.write('%s\t%d\n' % (file.filename, file.size))
    else:
        file_list_path = None
    bucket = S3Bucket()
    cmd = bucket.build_pipe_cp_command(chunk.source, chunk.destination, file_list=file_list_path, include=chunk.rules)
    if chunk.hostname != 'localhost':
        cmd = '(ssh %s API=$API API_TOKEN=$API_TOKEN RUN_ID=$RUN_ID "%s") & _CHUNK_PID=$! && wait $_CHUNK_PID' % \
              (chunk.hostname, cmd)
    Logger.info('Executing chunk transfer with cmd: %s' % cmd, task_name=chunk.task_name)
    bucket.execute_command(cmd, TRANSFER_ATTEMPTS)


def transfer_async_without_file_list(chunk):
    transfer_async(chunk, with_file_list=False)


class InputDataTask:
    def __init__(self, input_dir, common_dir, analysis_dir, task_name, bucket, report_file, rules, upload, env_suffix):
        self.input_dir = input_dir
        self.common_dir = common_dir
        self.analysis_dir = get_path_with_trailing_delimiter(analysis_dir)
        self.task_name = task_name
        transfer_bucket = bucket
        if bucket and not upload:
            transfer_bucket = self.build_run_specific_bucket_path(bucket)
        self.bucket = transfer_bucket
        self.report_file = report_file
        self.rules = rules
        api_url = os.environ['API']
        if 'API_EXTERNAL' in os.environ and os.environ['API_EXTERNAL']:
            api_url = os.environ['API_EXTERNAL']
        self.api_url = api_url
        self.token = os.environ['API_TOKEN']
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.is_upload = upload
        self.env_suffix = env_suffix
        self.extra_args = os.getenv('CP_TRANSFER_PIPE_INPUT_ARGS') if self.is_upload else os.getenv('CP_TRANSFER_PIPE_OUTPUT_ARGS')

    def run(self):
        Logger.info('Starting localization of remote data...', task_name=self.task_name)
        try:
            Logger.info('Processing metadata parameters...', task_name=self.task_name)
            metadata_locations = list(self.find_metadata_locations({ParameterType.METADATA_PARAMETER}))
            if not metadata_locations:
                Logger.info('No metadata sources found', task_name=self.task_name)
            else:
                if self.is_upload:
                    for location in metadata_locations:
                        Logger.info('Downloading metadata entities for folder #{} ({} {})...',
                                    location.entity_class, location.folder_id, location.entity_ids)
                        self.api.download_metadata_entities(output_path=location.local_path,
                                                            folder_id=location.folder_id,
                                                            entity_class=location.entity_class,
                                                            entity_ids=location.entity_ids,
                                                            file_format='csv')
            Logger.info('Processing remote parameters...', task_name=self.task_name)
            dts_registry = self.fetch_dts_registry()
            parameter_types = {ParameterType.INPUT_PARAMETER, ParameterType.COMMON_PARAMETER} if self.is_upload else \
                {ParameterType.OUTPUT_PARAMETER}
            remote_locations = list(self.find_remote_locations(dts_registry, parameter_types))
            if not remote_locations:
                Logger.info('No remote sources found', task_name=self.task_name)
            else:
                dts_locations = [path for location in remote_locations
                                 for path in location.paths if path.type == PathType.DTS]
                if self.is_upload:
                    self.transfer_dts(dts_locations, dts_registry)
                    self.localize_data(remote_locations)
                else:
                    rule_patterns = DataStorageRule.read_from_file(self.rules)
                    rules = []
                    for rule in rule_patterns:
                        if rule.move_to_sts:
                            rules.append(rule.file_mask)
                    self.localize_data(remote_locations, rules=rules)
                    self.transfer_dts(dts_locations, dts_registry, rules=rules)
            if self.is_upload and self.report_file:
                Logger.info('Writing report file {}...'.format(self.report_file), task_name=self.task_name)
                with open(self.report_file, 'w') as report:
                    for location in metadata_locations:
                        report.write('export {}="{}"\n'.format(location.param.name, location.local_path))
                        report.write('export {}="{}"\n'.format(location.param.name + '_ORIGINAL', location.param.value))
                    for location in remote_locations:
                        env_name = location.env_name
                        original_value = location.original_value
                        localized_value = location.delimiter.join(
                            [os.path.join(path.local_path, path.suffix) if path.suffix else path.local_path
                             for path in location.paths]
                        )
                        report.write('export {}="{}"\n'.format(env_name, localized_value))
                        report.write('export {}="{}"\n'.format(env_name + '_ORIGINAL', original_value))
            Logger.success('Finished localization of remote data', task_name=self.task_name)
        except BaseException as e:
            Logger.fail('Localization of remote data failed due to exception: %s' % e.message, task_name=self.task_name)
            exit(1)

    def fetch_dts_registry(self):
        result = {}
        try:
            dts_data = self.api.load_dts_registry()
        except BaseException as e:
            Logger.info("DTS is not available: %s" % e.message, task_name=self.task_name)
            return result
        for registry in dts_data:
            for prefix in registry['prefixes']:
                result[prefix] = registry['url']
        return result

    def find_remote_locations(self, dts_registry, param_types):
        for param in self.find_params(param_types):
            resolved_value = replace_all_system_variables_in_path(param.value)
            Logger.info('Found remote parameter %s (%s) with type %s' % (resolved_value, param.value, param.type),
                        task_name=self.task_name)
            original_paths = [resolved_value]
            delimiter = ''
            for supported_delimiter in VALUE_DELIMITERS:
                if resolved_value.find(supported_delimiter) != -1:
                    original_paths = re.split(supported_delimiter, resolved_value)
                    delimiter = supported_delimiter
                    break
            # Strip spaces, which may arise if the parameter was splitted by comma
            # e.g. "s3://bucket1/f1, s3://bucket2/f2" will be splitted into
            # "s3://bucket1/f1"
            # " s3://bucket2/f2"
            original_paths = map(str.strip, original_paths)
            paths = []
            for path in original_paths:
                if self.match_dts_path(path, dts_registry):
                    paths.append(self.build_dts_path(path, dts_registry, param.type))
                elif self.match_cloud_path(path):
                    paths.append(self.build_cloud_path(path, param.type))
                elif self.match_ftp_or_http_path(path):
                    paths.append(self.build_ftp_or_http_path(path, param.type))
            if paths:
                yield RemoteLocation(param.name, resolved_value, param.type, paths, delimiter)

    def find_metadata_locations(self, param_types):
        for param in self.find_params(param_types):
            Logger.info('Found metadata parameter %s=%s' % (param.name, param.value), task_name=self.task_name)
            try:
                folder_id, entity_class, entity_ids = param.value.split(':', 2)
            except Exception:
                Logger.warn('Metadata parameter value is malformed. The following format is expected: '
                            '<folder id>:<class name>:<id1,id2,...>')
                continue
            yield MetadataLocation(param=param, folder_id=folder_id, entity_class=entity_class, entity_ids=entity_ids,
                                   local_path=os.path.join(self.input_dir, param.name + '.metadata.csv'))

    def find_params(self, param_types=None):
        param_types = param_types or ()
        for param_name in os.environ:
            param_type = os.getenv(param_name + self.env_suffix)
            if not param_type:
                continue
            if param_type not in param_types:
                continue
            param_value = os.getenv(param_name)
            if not param_value:
                continue
            param_value = param_value.strip()
            yield RunParameter(name=param_name, value=param_value, type=param_type)

    @staticmethod
    def match_ftp_or_http_path(path):
        return any(path.startswith(scheme) for scheme in HTTP_FTP_SCHEMES)

    @staticmethod
    def match_cloud_path(path):
        return any(path.startswith(scheme) for scheme in CLOUD_STORAGE_PATHS)

    @staticmethod
    def match_dts_path(path, dts_registry):
        for prefix in dts_registry:
            if path.startswith(prefix):
                return True
        return False

    @staticmethod
    def build_run_specific_bucket_path(bucket):
        run_id = os.getenv('RUN_ID')
        run_folder_suffix = '{}/'.format(run_id) if run_id else ''
        run_specific_bucket_dir = get_path_with_trailing_delimiter(bucket) + run_folder_suffix
        return run_specific_bucket_dir

    def build_dts_path(self, path, dts_registry, input_type):
        for prefix in dts_registry:
            if path.startswith(prefix):
                if not self.bucket:
                    raise RuntimeError('Transfer bucket shall be set for DTS locations')
                relative_path = path.replace(prefix, '')
                cloud_path = self.join_paths(self.bucket, relative_path)

                if input_type == ParameterType.OUTPUT_PARAMETER:
                    local_path = self.analysis_dir
                else:
                    local_dir = self.get_local_dir(input_type)
                    local_path = self.join_paths(local_dir, relative_path)
                Logger.info('Found remote {} path {} matching DTS prefix {}. '
                            'It will be uploaded to bucket path {} and localized {} {}.'
                            .format(input_type, path, prefix, cloud_path,
                                    'from' if input_type == ParameterType.OUTPUT_PARAMETER else 'to',
                                    local_path), task_name=self.task_name)
                return LocalizedPath(path, cloud_path, local_path, PathType.DTS, prefix=prefix)
        raise RuntimeError('Remote path %s does not match any of DTS prefixes.')

    def build_cloud_path(self, path, input_type):
        return self._build_remote_path(path, input_type, PathType.CLOUD_STORAGE)

    def build_ftp_or_http_path(self, path, input_type):
        return self._build_remote_path(path, input_type, PathType.HTTP_OR_FTP)

    def _build_remote_path(self, path, input_type, path_type):
        path_suffix = None
        if input_type == ParameterType.OUTPUT_PARAMETER:
            local_path = self.analysis_dir
        else:
            if path.endswith('*'):
                Logger.info('Path {} ends with a wildcard. Whole parent directory will be downloaded.'.format(path),
                            task_name=self.task_name)
                path_suffix = os.path.basename(path)[:-1]
                path = os.path.dirname(path)
            remote = urlparse.urlparse(path)
            relative_path = path.replace('%s://%s' % (remote.scheme, remote.netloc), '')
            local_dir = self.get_local_dir(input_type)
            local_path = self.join_paths(local_dir, relative_path)
        Logger.info('Found %s %s path %s. It will be localized to %s.' % (path_type.lower(), input_type, path,
                                                                          local_path),
                    task_name=self.task_name)
        return LocalizedPath(path, path, local_path, path_type, suffix=path_suffix)

    def get_local_dir(self, type):
        return self.input_dir if type == ParameterType.INPUT_PARAMETER else self.common_dir

    def join_paths(self, prefix, suffix):
        trimmed_prefix = get_path_with_trailing_delimiter(prefix)
        trimmed_suffix = suffix[1:] if suffix.startswith('/') else suffix
        return trimmed_prefix + trimmed_suffix

    def transfer_dts(self, dts_locations, dts_registry, rules=None):
        grouped_paths = {}
        for path in dts_locations:
            if path.prefix not in grouped_paths:
                grouped_paths[path.prefix] = [path]
            else:
                grouped_paths[path.prefix].append(path)

        for prefix, paths in grouped_paths.iteritems():
            dts_url = dts_registry[prefix]
            Logger.info('Uploading {} paths using DTS service {}'.format(len(paths), dts_url),  self.task_name)
            dts_client = DataTransferServiceClient(dts_url, self.token, self.api_url, self.token, 10)
            dts_client.transfer_data([self.create_dts_path(path, rules) for path in paths], self.task_name)

    def create_dts_path(self, path, rules):
        return LocalToS3(path.path, path.cloud_path, rules) if self.is_upload \
            else S3ToLocal(path.cloud_path, path.path, rules)

    def localize_data(self, remote_locations, rules=None):
        cluster = Cluster.build_cluster(self.api, self.task_name)
        files = []
        for location in remote_locations:
            for path in location.paths:
                source, destination = self.get_local_paths(path, self.is_upload)
                if cluster is None or path.type == PathType.HTTP_OR_FTP:
                    self.perform_local_transfer(source, destination)
                elif not self.is_file(source):
                    self.perform_cluster_folder_transfer(source, destination, cluster, rules=rules)
                else:
                    files.append((source, destination))
        if files:
            self.perform_cluster_file_transfer(files, cluster, rules=rules)

    def perform_local_transfer(self, source, destination):
        Logger.info('Uploading files from {} to {} using local pipe'.format(source, destination), self.task_name)
        threads = Cluster.get_slots_per_node() if ParallelType.current() == ParallelType.Threaded else None
        if self.is_upload or self.rules is None:
            S3Bucket().pipe_copy(source, destination, TRANSFER_ATTEMPTS, threads=threads, extra_args=self.extra_args)
        else:
            S3Bucket().pipe_copy_with_rules(source, destination, TRANSFER_ATTEMPTS, self.rules, threads=threads, extra_args=self.extra_args)

    def perform_cluster_file_transfer(self, files, cluster, rules=None):
        Logger.info('Uploading {} files using cluster'.format(len(files)), self.task_name)
        common_folder = os.path.join(os.environ['SHARED_WORK_FOLDER'], 'transfer')
        applied_rules = None if self.is_upload else rules
        if not os.path.exists(common_folder):
            os.makedirs(common_folder)
        chunks = self.split_files_into_chunks(applied_rules, cluster, common_folder, files)
        transfer_pool = Pool(cluster.get_size())
        for part in split(chunks, cluster.get_size()):
            transfer_pool.map(transfer_async_without_file_list, part)
        shutil.rmtree(common_folder, ignore_errors=True)

    def split_files_into_chunks(self, applied_rules, cluster, common_folder, files):
        slots = []
        for node in cluster.nodes:
            for slot in range(0, cluster.slots_per_node):
                slots.append(node.hostname)
        chunks = []
        for i in range(0, len(files)):
            source, destination = files[i]
            current_host = slots[i % len(slots)]
            chunks.append(TransferChunk(current_host, [], source,
                                        destination, common_folder, self.task_name, applied_rules))
        return chunks

    def perform_cluster_folder_transfer(self, source, destination, cluster, rules=None):
        Logger.info('Uploading folders from {} to {} using cluster'.format(source, destination), self.task_name)
        common_folder = os.path.join(os.environ['SHARED_WORK_FOLDER'], 'transfer')
        applied_rules = None if self.is_upload else rules
        chunks = self.split_source_into_chunks(cluster, source, destination, common_folder, applied_rules)
        transfer_pool = Pool(len(chunks))
        transfer_pool.map(transfer_async, chunks)
        shutil.rmtree(common_folder, ignore_errors=True)

    def is_file(self, source):
        if source.endswith('/'):
            return False
        if self.match_cloud_path(source):
            source_path = urlparse.urlparse(source)
            # case when whole bucket is selected
            if not source_path.path or source_path.path == '/':
                return True
            # urlparse returns path as /folder/inner
            # convert it to cloud listing representation folder/inner/
            folder = get_path_with_trailing_delimiter(source_path.path.split('/')[-1])
            cloud_paths = S3Bucket().pipe_ls(get_path_without_trailing_delimiter(source),
                                          TRANSFER_ATTEMPTS, recursive=False, all=False, show_info=True)
            for path in cloud_paths:
                if path[0] == 'Folder' and path[1] == folder:
                    return False
            return True

        else:
            return os.path.isfile(source)

    def split_source_into_chunks(self, cluster, source, destination, common_folder, rules):
        if not os.path.exists(common_folder):
            os.makedirs(common_folder)
        source_files = self.fetch_source_files(source)
        chunks = []
        for node in cluster.nodes:
            for slot in range(0, cluster.slots_per_node):
                chunks.append(TransferChunk(node.hostname, [], source,
                                            destination, common_folder, self.task_name, rules))
        for i in range(0, len(source_files)):
            file = source_files[i]
            chunk_index = i % len(chunks)
            chunks[chunk_index].files.append(file)
        return chunks

    def fetch_source_files(self, source):
        """
        :return: list of files sorted by size DESC
        """
        if self.match_cloud_path(source):
            cloud_paths = S3Bucket().pipe_ls(get_path_with_trailing_delimiter(source),
                                          TRANSFER_ATTEMPTS, recursive=True, all=True, show_info=True)
            cloud_paths = filter(lambda x: x[0] == 'File' and not x[1].endswith('/'), cloud_paths)
            files = [File(self.get_path_without_folder(source, path[1]), int(path[2])) for path in cloud_paths]
        else:
            files = []
            for root, d_names, f_names in os.walk(source):
                for f in f_names:
                    path = os.path.join(root, f)
                    files.append(File(os.path.relpath(path, start=source), os.path.getsize(path)))
        return sorted(files, key=lambda x: x.size, reverse=True)

    def get_path_without_folder(self, source, path):
        prefix = urlparse.urlparse(source).path
        if prefix.startswith('/'):
            prefix = prefix[1:]
        if not prefix.endswith('/'):
            prefix += '/'
        if len(prefix) == 0 or prefix == '/':
            return path
        return path.replace(prefix, '', 1)

    @staticmethod
    def get_local_paths(path, upload):
        if upload:
            source = path.cloud_path if path.type == PathType.DTS else path.path
            destination = path.local_path
        else:
            source = path.local_path
            destination = path.path if path.type == PathType.HTTP_OR_FTP else path.cloud_path
        return source, destination


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--operation', required=True)
    parser.add_argument('--input-dir', required=True)
    parser.add_argument('--common-dir', required=True)
    parser.add_argument('--analysis-dir', required=True)
    parser.add_argument('--bucket', required=False, default=None)
    parser.add_argument('--storage-rules', required=False, default=None)
    parser.add_argument('--report-file', required=False, default=None)
    parser.add_argument('--task', required=False, default=LOCALIZATION_TASK_NAME)
    parser.add_argument('--env-suffix', required=False, default='_PARAM_TYPE')
    args = parser.parse_args()
    if args.operation == 'upload':
        upload = True
    elif args.operation == 'download':
        upload = False
    else:
        raise RuntimeError('Illegal operation %s' % args.operation)
    bucket = args.bucket
    if not bucket and 'CP_TRANSFER_BUCKET' in os.environ:
        bucket = os.environ['CP_TRANSFER_BUCKET']
    InputDataTask(args.input_dir, args.common_dir, args.analysis_dir,
                  args.task, bucket, args.report_file, args.storage_rules, upload,
                  args.env_suffix).run()


if __name__ == '__main__':
    start = timer()
    main()
    end = timer()
    print("Elapsed %d seconds" % (end - start))
