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

import fnmatch
import os
import time
from common.notification_sender import NotificationSender
from common.utils import PipelineUtils
from datetime import datetime, timedelta


class FileDetails(object):

    def __init__(self, path, modification_date, size):
        self.path = path
        self.modification_date = modification_date
        self.size = size


class StorageMonitor:

    def __init__(self, last_sync_file_path, lookup_paths, skip_masks):
        self.last_sync_date = self._get_last_sync_time_epoch(last_sync_file_path)
        self.lookup_paths = lookup_paths
        self.skip_masks = skip_masks

    def generate_paths(self):
        paths = set()
        for lookup_path in self.lookup_paths:
            dir_root = os.walk(lookup_path)
            for dir_root, directories, files in dir_root:
                for file in files:
                    file_full_path = os.path.join(dir_root, file)
                    if not self._match_any_masks(file_full_path):
                        file_stats = os.stat(file_full_path)
                        file_modification_date_epoch = int(file_stats.st_mtime)
                        if file_modification_date_epoch > self.last_sync_date:
                            modification_datetime = datetime.utcfromtimestamp(file_modification_date_epoch)
                            file_size = file_stats.st_size
                            paths.add(FileDetails(file_full_path, modification_datetime, file_size))
        return paths

    def _match_any_masks(self, file_path):
        for mask in self.skip_masks:
            if fnmatch.fnmatch(file_path, mask):
                return True
        return False

    @staticmethod
    def _get_last_sync_time_epoch(last_sync_file_path):
        last_sync_time = datetime.now() - timedelta(days=7)
        if os.path.exists(last_sync_file_path):
            with open(last_sync_file_path, 'r') as last_sync_file:
                last_sync_time = PipelineUtils.convert_str_to_date(last_sync_file.read())
        return time.mktime(last_sync_time.timetuple())


def build_unordered_list(list):
    list_html = "<ul>"
    for item in list:
        list_html += '<li>{}</li>'.format(item)
    list_html += "</ul>"
    return list_html


def build_file_update_table(files):
    table = '''
        <table>
            <tr>
                <td><b>File</b></td>
                <td><b>Modification date</b></td>
                <td><b>Size</b></td>
            </tr>
            {}
        </table>
        '''
    summary_rows = ''''''
    file_list = list(files)
    file_list.sort(key=lambda f: f.path)
    for file in file_list:
        summary_rows += '''<tr><td>{}</td><td>{}</td><td>{}</td></tr>'''\
            .format(file.path, PipelineUtils.convert_date_to_str(file.modification_date), file.size)
    return table.format(summary_rows)


if __name__ == '__main__':
    sync_start = datetime.now()
    notification_user = PipelineUtils.extract_notification_user()
    email_template_path = PipelineUtils.extract_email_template_path('/cloud-storage-monitor/template.html')
    notification_users_copy_list = PipelineUtils.extract_notification_cc_users_list()
    last_sync_file_path = PipelineUtils.extract_mandatory_parameter('CP_STORAGE_MONITOR_LAST_SYNC_TIME_FILE',
                                                                    'Path of the sync file is not specified!')
    notification_subject = os.getenv('CP_SERVICE_MONITOR_NOTIFICATION_SUBJECT', 'Cloud storage updates detected')
    run_id = os.getenv('RUN_ID', '0')
    api = PipelineUtils.initialize_api(run_id)
    logger = PipelineUtils.initialize_logger(api, run_id, 'CloudStorageMonitoring')
    paths_to_analyze = PipelineUtils.extract_set_from_parameter('CP_CLOUD_STORAGE_MONITOR_TARGET_PATHS')
    if not paths_to_analyze:
        raise RuntimeError('No paths are specified!')
    logger.info('Following paths to be analyzed: {}'.format(list(paths_to_analyze)))
    ignore_masks = PipelineUtils.extract_set_from_parameter('CP_CLOUD_STORAGE_MONITOR_IGNORE_GLOBS')
    if ignore_masks:
        logger.info('Following ignoring masks are specified: {}'.format(list(ignore_masks)))

    logger.info('Checking paths for updates...')
    storage_monitor = StorageMonitor(last_sync_file_path, paths_to_analyze, ignore_masks)
    file_updates = storage_monitor.generate_paths()
    if file_updates:
        logger.info('{} file updates detected'.format(len(file_updates)))
        sender = NotificationSender(api, logger, email_template_path, notification_user, notification_users_copy_list,
                                    notification_subject)

        sender.queue_notification(build_unordered_list(paths_to_analyze),
                                  datetime.utcfromtimestamp(storage_monitor.last_sync_date),
                                  build_file_update_table(file_updates))
    else:
        logger.info('No updates since the last sync detected.')
    logger.info('Updating last sync timestamp')
    PipelineUtils.save_timestamp_to_file(last_sync_file_path, sync_start)
    logger.info('Storage check is finished.')
