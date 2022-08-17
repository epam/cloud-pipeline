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
from .utils import HcsParsingUtils, log_run_info, get_list_run_param
from .processors import HcsRoot


def get_processing_roots(should_force_processing, measurement_index_file):
    paths_to_hcs_roots = get_list_run_param('HCS_TARGET_DIRECTORIES')
    if len(paths_to_hcs_roots) == 0:
        lookup_paths = get_list_run_param('HCS_LOOKUP_DIRECTORIES')
        if not lookup_paths:
            return []
        log_run_info('Following paths are specified for processing: {}'.format(lookup_paths))
        log_run_info('Lookup for unprocessed files')
        result = HcsProcessingDirsGenerator(
            lookup_paths, measurement_index_file, should_force_processing).generate_paths()
    else:
        result = []
        image_names = get_list_run_param('HCS_TARGET_IMG_NAMES')
        if image_names and len(image_names) == len(paths_to_hcs_roots):
            for index, root in enumerate(paths_to_hcs_roots):
                result.append(HcsRoot(root, image_names[index]))
        else:
            for root in paths_to_hcs_roots:
                result.append(HcsRoot(root, HcsParsingUtils.build_preview_file_path(root)))
    return result


class HcsProcessingDirsGenerator:

    def __init__(self, lookup_paths, measurement_index_file_path, force_processing=False):
        self.lookup_paths = lookup_paths
        self.measurement_index_file_path = measurement_index_file_path
        self.force_processing = force_processing

    @staticmethod
    def is_folder_content_modified_after(dir_path, modification_date):
        ignore_files = get_list_run_param('HCS_IGNORE_MODIFIED_FILES')
        dir_root = os.walk(dir_path)
        for dir_root, directories, files in dir_root:
            for file in files:
                if ignore_files and file in ignore_files:
                    continue
                if HcsParsingUtils.get_file_last_modification_time(os.path.join(dir_root, file)) > modification_date:
                    return True
        return False

    def generate_paths(self):
        hcs_roots = self.find_all_hcs_roots()
        log_run_info('Found {} HCS files'.format(len(hcs_roots)))
        roots_with_preview = self.build_roots_with_preview(hcs_roots)
        filtered = []
        for root, preview in roots_with_preview.items():
            if self.is_processing_required(root, preview):
                filtered.append(HcsRoot(root, preview))
        return filtered

    def find_all_hcs_roots(self):
        hcs_roots = set()
        for lookup_path in self.lookup_paths:
            dir_walk_root = os.walk(lookup_path)
            for dir_root, directories, files in dir_walk_root:
                for file in files:
                    full_file_path = os.path.join(dir_root, file)
                    if full_file_path.endswith(self.measurement_index_file_path):
                        hcs_roots.add(full_file_path[:-len(self.measurement_index_file_path)])
        return hcs_roots

    def is_processing_required(self, hcs_folder_root_path, hcs_img_path):
        if self.force_processing:
            return True
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

    def build_roots_with_preview(self, hcs_roots):
        result = {}
        names = {}
        for root in hcs_roots:
            hcs_img_name = HcsParsingUtils.build_preview_file_name(root)
            if hcs_img_name not in names:
                names[hcs_img_name] = [root]
            else:
                names[hcs_img_name].append(root)
        for name, roots in names.items():
            with_id = len(roots) > 1
            if with_id:
                log_run_info('Find duplicate name {} for roots {}'.format(name, str(roots)))
            for root in roots:
                result[root] = HcsParsingUtils.build_preview_file_path(root, with_id=with_id)
        return result
