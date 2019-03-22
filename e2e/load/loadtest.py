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

import getopt
import sys
import time
from threading import Thread

from api_wrapper import FolderAPI, PipelineAPI

test_load_tree_threads_succeeded = 0
test_load_tree_threads_failed = 0

test_create_pipeline_threads_succeeded = 0
test_create_pipeline_threads_failed = 0


def test_load_tree_single(index, api_path, access_key):
    global test_load_tree_threads_succeeded
    global test_load_tree_threads_failed
    start_time = time.time()
    print 'Load tree: #{} thread started'.format(index)
    folder_api_instance = FolderAPI(api_path, access_key)
    try:
        folder_api_instance.load_tree()
        test_load_tree_threads_succeeded += 1
    except RuntimeError as error:
        test_load_tree_threads_failed += 1
        print 'Load tree: #{} thread error: {}'.format(index, error)
    print 'Load tree: #{} thread finished. {} seconds'.format(index, round(time.time() - start_time, 2))


def test_load_tree(api_path, access_key, count):
    threads = []
    print 'Load tree test started'
    for index in range(0, count):
        thread = Thread(target=test_load_tree_single, args=(index + 1, api_path, access_key))
        thread.start()
        threads.append(thread)
    for thread in threads:
        thread.join()
    print 'Load tree test finished'


def test_create_pipeline_single(index, api_path, access_key, folder, folder_id, file_size, files_count, file_contents):
    global test_create_pipeline_threads_succeeded
    global test_create_pipeline_threads_failed
    start_time = time.time()
    pipeline_creation_time = 0
    files_creation_time = 0
    pipeline_deletion_time = 0
    pipeline_name = '{}-{}'.format(folder, index)
    print 'Pipeline {}: #{} thread started'.format(pipeline_name, index)
    pipeline_api_instance = PipelineAPI(api_path, access_key)
    pipeline_id = None
    try:
        print 'Pipeline {}: #{} thread: creating pipeline {}...'.format(pipeline_name, index, pipeline_name)
        pipeline_creation_time_start = time.time()
        pipeline_id = pipeline_api_instance.create_pipeline(pipeline_name, folder_id)
        pipeline_creation_time = time.time() - pipeline_creation_time_start
    except RuntimeError as error:
        print 'Pipeline {}: #{} thread error: {}'.format(pipeline_name, index, error)
    if pipeline_id is not None:
        try:
            print 'Pipeline {}: #{} thread: pipeline #{} created'.format(pipeline_name, index, pipeline_id)
            files_creation_time_start = time.time()
            for file_index in range(0, files_count):
                print 'Pipeline {}: #{} thread: creating #{} file ({} bytes)'.format(
                    pipeline_name,
                    index,
                    file_index + 1,
                    file_size
                )
                file_name = 'src/test-{}-file-{}.txt'.format(pipeline_name, file_index + 1)
                commit = '{} created'.format(file_name)
                print 'Pipeline {}: #{} thread: fetching last commit id...'.format(pipeline_name, index)
                commit_id = pipeline_api_instance.load_last_version_commit_id(pipeline_id)
                print 'Pipeline {}: #{} thread: last commit id \'{}\''.format(pipeline_name, index, commit_id)
                print 'Pipeline {}: #{} thread: creating file {} in repository...'.format(pipeline_name, index, file_name)
                pipeline_api_instance.create_pipeline_file(pipeline_id, commit_id, file_name, file_contents, commit)
                print 'Pipeline {}: #{} thread: file {} created'.format(pipeline_name, index, file_name)
            files_creation_time = time.time() - files_creation_time_start
        except RuntimeError as error:
            print 'Pipeline {}: #{} thread error: {}'.format(pipeline_name, index, error)
        try:
            print 'Pipeline {}: #{} thread: removing pipeline #{}...'.format(pipeline_name, index, pipeline_id)
            pipeline_deletion_time_start = time.time()
            pipeline_api_instance.delete_pipeline(pipeline_id)
            pipeline_deletion_time = time.time() - pipeline_deletion_time_start
            print 'Pipeline {}: #{} thread: pipeline #{} removed'.format(pipeline_name, index, pipeline_id)
            test_create_pipeline_threads_succeeded += 1
        except RuntimeError as error:
            test_create_pipeline_threads_failed += 1
            print 'Pipeline {}: #{} thread error: {}'.format(pipeline_name, index, error)
    else:
        test_create_pipeline_threads_failed += 1
    print 'Pipeline {}: #{} thread finished. Total {} seconds. ' \
          'Pipeline creation: {}. ' \
          '{} files creation: {}. ' \
          'Pipeline deletion: {}'.format(pipeline_name, index, round(time.time() - start_time, 2), round(pipeline_creation_time, 2), files_count, round(files_creation_time, 2), round(pipeline_deletion_time, 2))
    pass


def test_create_pipeline(api_path, access_key, folder, count, file_size, files_count):
    threads = []
    print 'Create pipeline test started'
    print 'Creating folder \'{}\'...'.format(folder)
    file_contents = ''
    for c in range(0, file_size):
        file_contents += '{}'.format(c % 10)
    try:
        folder_api_instance = FolderAPI(api_path, access_key)
        folder_id = folder_api_instance.create_folder(folder)
        print 'Folder \'{}\' created. ID #{}'.format(folder, folder_id)
        for index in range(0, count):
            thread = Thread(target=test_create_pipeline_single, args=(
                index + 1,
                api_path,
                access_key,
                folder,
                folder_id,
                file_size,
                files_count,
                file_contents
            ))
            thread.start()
            threads.append(thread)
        for thread in threads:
            thread.join()
        print 'Removing folder \'{}\'...'.format(folder)
        folder_api_instance.delete_folder(folder_id)
        print 'Folder \'{}\' removed.'.format(folder)
        print 'Create pipeline test finished'
    except RuntimeError as error:
        print 'Create pipeline test failed: {}'.format(error)


def main(script_name, argv):
    try:
        opts, args = getopt.getopt(argv, "hf:c:a:k:s:n:", ["help", "folder=", "count=", "api=", "key=", "size=", "number="])
        folder = ''
        count = 1
        api = None
        key = None
        file_size = 1
        files_count = 1
        for opt, arg in opts:
            if opt in ("-h", "--help"):
                print script_name + ' -f <folder> -c <count> -a <api path> -k <authentication token> -s <file size in KB> -n <files count>'
                sys.exit()
            if opt in ("-f", "--folder"):
                folder = arg
            elif opt in ("-c", "--count"):
                count = int(arg)
            elif opt in ("-a", "--api"):
                api = arg
            elif opt in ("-k", "--key"):
                key = arg
            elif opt in ("-s", "--size"):
                file_size = int(arg)
            elif opt in ("-n", "--number"):
                files_count = int(arg)
        if not folder:
            print 'Folder name (-f <folder>) is required'
            print script_name + ' -f <folder> -c <count> -a <api path> -k <authentication token> -s <file size in KB> -n <files count>'
            sys.exit(2)
        if not api:
            print 'API path (-a <api path>) is required'
            print script_name + ' -f <folder> -c <count> -a <api path> -k <authentication token> -s <file size in KB> -n <files count>'
            sys.exit(2)
        if not key:
            print 'Authentication token (-k <authentication token>) is required'
            print script_name + ' -f <folder> -c <count> -a <api path> -k <authentication token> -s <file size in KB> -n <files count>'
            sys.exit(2)
        if file_size > 1024 * 10:
            choise = ''
            while choise not in ('y', 'n'):
                sys.stdout.write('File size is larger then 10 MB. Are you sure? y/n: ')
                choise = raw_input().lower()
                if choise == 'n':
                    sys.exit()
        test_load_tree(api, key, count)
        test_create_pipeline(api, key, folder, count, file_size * 1024, files_count)
        print ''
        print 'Load tree test: {} succeeded, {} failed'.format(
            test_load_tree_threads_succeeded,
            test_load_tree_threads_failed
        )
        print 'Create pipeline test: {} succeeded, {} failed'.format(
            test_create_pipeline_threads_succeeded,
            test_create_pipeline_threads_failed
        )
    except getopt.GetoptError:
        print script_name + ' -f <folder> -c <count> -a <api path> -k <authentication token> -s <file size in KB> -n <files count>'
        sys.exit(2)


if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
