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

from collections import OrderedDict
import codecs
import glob
import json
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
            lookup_paths, measurement_index_file, should_force_processing,
            skip=get_list_run_param('HCS_SKIP_FILES'),
            objmeta_file=os.getenv('HCS_OBJECT_META_FILE', None)).generate_paths()
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

    def __init__(self, lookup_paths, measurement_index_file_path, force_processing=False, skip=[], objmeta_file=None):
        self.lookup_paths = lookup_paths
        self.measurement_index_file_path = measurement_index_file_path
        self.force_processing = force_processing
        self.skip = skip
        self.objmeta_file = objmeta_file

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

    def get_obj_metadata(self, path):
        if not self.objmeta_file:
            return None
        if not os.path.exists(path):
            log_run_info('Specified object metadata file {} does not exist.'.format(path))
            return None
        metadata, _, _, _ = self.read_object_meta(path)
        return metadata

    def validate_upload_status(self, path, metadata):
        if not metadata:
            return None
        expected_image_number = self.read_expected_image_number(metadata, os.path.basename(path))
        uploaded_image_number = self.count_uploaded_images(path)
        if expected_image_number != uploaded_image_number:
            log_run_info('Expected {} images and found {} uploaded images for {}.'
                         .format(str(expected_image_number), str(uploaded_image_number), path))
        if expected_image_number and expected_image_number > uploaded_image_number:
            log_run_info('Path {} is missing some of the image files. '
                         'Processing will be skipped and upload will be restarted.'.format(path))
            return os.path.basename(path)
        return None

    def read_expected_image_number(self, metadata, id):
        for item in metadata:
            if item.get('OBJECTTYPE', '') == 'MEASUREMENT' and item.get('GUID', '') == id:
                return int(item.get('__NROFIMAGEFILES', 0))
        return 0

    def read_object_meta(self, path):
        version_line = ''
        timestamp_line = ''
        checksum_line = ''
        metadata = None
        with codecs.open(path, 'r', encoding="utf-8") as file:
            content = ''
            for line in file.readlines():
                if line.startswith('Version'):
                    version_line = line
                    continue
                if line.startswith('Timestamp'):
                    timestamp_line = line
                    continue
                if line.startswith('Checksum'):
                    checksum_line = line
                    continue
                else:
                    content = content + line
            metadata = json.loads(content, object_pairs_hook=OrderedDict)
        return metadata, version_line, timestamp_line, checksum_line

    def count_uploaded_images(self, path):
        return len(glob.glob(os.path.join(path, HcsParsingUtils.get_hcs_image_folder(), '**/*.tiff')))

    def reset_upload(self, root, ids):
        log_run_info('Removing upload status for ids {}'.format(','.join(ids)))
        objmeta_file_path = os.path.join(root, self.objmeta_file)
        filtered = []
        metadata, version, timestamp, checksum = self.read_object_meta(objmeta_file_path)
        for item in metadata:
            if item.get('OBJECTTYPE', '') == 'MEASUREMENT' and item.get('GUID', '') in ids:
                pass
            else:
                filtered.append(item)
        result = version + json.dumps(filtered, indent=2, separators=(',', ': '), ensure_ascii=False) + '\n' + timestamp + checksum
        with codecs.open(objmeta_file_path, 'w', encoding="utf-8") as file:
            file.write(result)

    def build_roots_with_preview(self, hcs_roots):
        result = {}
        names = {}
        ids_to_clean = []
        lookup_path = ''
        for root in hcs_roots:
            if not lookup_path:
                lookup_path = os.path.dirname(root)
            hcs_img_name = HcsParsingUtils.build_preview_file_name(root)
            if hcs_img_name not in names:
                names[hcs_img_name] = [root]
            else:
                names[hcs_img_name].append(root)
        metadata = self.get_obj_metadata(os.path.join(lookup_path, self.objmeta_file))
        for name, roots in names.items():
            with_id = len(roots) > 1
            if with_id:
                log_run_info('Found duplicate name {} for roots {}'.format(name, str(roots)))
            for root in roots:
                if os.path.basename(root) in self.skip:
                    log_run_info('Skipping file {}'.format(root))
                    continue
                invalid_id = self.validate_upload_status(root, metadata)
                if invalid_id:
                    ids_to_clean.append(invalid_id)
                else:
                    result[root] = HcsParsingUtils.build_preview_file_path(root, with_id=with_id)
        if ids_to_clean:
            self.reset_upload(lookup_path, ids_to_clean)
        return result
