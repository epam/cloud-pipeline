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
import datetime
import shutil
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
            objmeta_file=os.getenv('HCS_OBJECT_META_FILE', None),
            hs_file=os.getenv('HCS_HARMONY_HS_FILE', '_hs.txt'),
            skip_markers=get_list_run_param('HCS_SKIP_MARKERS')).generate_paths()
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

    def __init__(self, lookup_paths, measurement_index_file_path, force_processing=False, skip=[],
                 objmeta_file=None, hs_file=None, skip_markers=[]):
        self.lookup_paths = lookup_paths
        self.measurement_index_file_path = measurement_index_file_path
        self.force_processing = force_processing
        self.skip = skip
        self.objmeta_file = objmeta_file
        self.hs_file = hs_file
        self.skip_markers = skip_markers

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
            full_img_name = preview[0]
            short_img_name = preview[1]
            matching_image = self.get_matching_file(full_img_name, short_img_name)
            log_run_info('Expected preview image path: {}'.format(matching_image))
            if self.is_processing_required(root, matching_image):
                filtered.append(HcsRoot(root, matching_image))
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
        if self.is_skip_marker_present(hcs_folder_root_path):
            return False
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

    def get_matching_file(self, full_img_path, short_img_path):
        if os.path.exists(full_img_path):
            return full_img_path
        if os.path.exists(short_img_path):
            with open(short_img_path, 'r') as hcs_file:
                content = "".join(hcs_file.readlines())
            if content:
                hcs_dict = json.loads(content)
                hcs_id = os.path.basename(hcs_dict['sourceDir'].rstrip('/'))
                expected_id = os.path.basename(full_img_path).replace('.hcs', '').rsplit('.', 2)[1]
                if hcs_id == expected_id:
                    return short_img_path
            return full_img_path
        else:
            return full_img_path

    def is_skip_marker_present(self, folder):
        if not self.skip_markers:
            return False
        for skip in self.skip_markers:
            if os.path.isfile(os.path.join(folder, skip)):
                return True
        return False

    def get_obj_metadata(self, root, file_name):
        if not file_name:
            return None
        path = os.path.join(root, file_name)
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
        for delete in ids:
            folder = os.path.join(root, delete)
            log_run_info('Deleting folder {}'.format(folder))
            if os.path.exists(folder):
                shutil.rmtree(folder)

    def build_roots_with_preview(self, hcs_roots):
        result = {}
        ids_to_clean = []
        lookup_path = os.path.dirname(list(hcs_roots)[0])
        metadata = self.get_obj_metadata(lookup_path, self.objmeta_file)
        for root in hcs_roots:
            invalid_id = self.validate_upload_status(root, metadata)
            if invalid_id:
                ids_to_clean.append(invalid_id)
            else:
                hcs_img_name = HcsParsingUtils.build_preview_file_path(root)
                hcs_img_full_name = HcsParsingUtils.build_preview_file_path(root, with_id=True)
                result[root] = (hcs_img_full_name, hcs_img_name)
        if ids_to_clean and not self.is_harmony_sync_in_progress(lookup_path):
            self.reset_upload(lookup_path, ids_to_clean)
        return result

    def is_harmony_sync_in_progress(self, root):
        if not self.hs_file or not self.objmeta_file:
            return False
        hs_timestamp = self.get_file_timestamp(os.path.join(root, self.hs_file))
        objmeta_timestamp = self.get_file_timestamp(os.path.join(root, self.objmeta_file))
        if not hs_timestamp or not objmeta_timestamp:
            return False
        return hs_timestamp > objmeta_timestamp

    def get_file_timestamp(self, path):
        result = None
        try:
            with open(path, 'r') as source:
                for line in source.readlines():
                    if line.startswith('Timestamp'):
                        value = line.split('\t')[1]
                        # Crop one millisecond digit from the end of the date
                        if len(value) > 32:
                            date_str = value[:len(value) - 7]
                            value = date_str + value[-6:]
                        parsed = datetime.datetime.strptime(value, "%Y-%m-%dT%H:%M:%S.%f%z")
                        log_run_info("Timestamp for {} is {}".format(path, str(parsed)))
                        return parsed
        except BaseException as e:
            log_run_info('Failed to read timestamp from {}: {}'.format(path, str(e)))
        return result


