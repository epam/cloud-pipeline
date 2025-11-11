# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import re


class OmicsUrl:

    PATH_PATTERN = r"(\w+)://((\d+)\.storage\.([\w-]+)\.amazonaws\.com/(\d+)/(readSet|reference))(?:/(\d+))?(?:/(.*)?)?"
    STORAGE_PATH_PATTERN = r"\d+\.storage\.([\w-]+)\.amazonaws\.com/(\d+)/(?:readSet|reference)(?:/)?"

    @classmethod
    def parse_path(cls, path):
        result = re.search(OmicsUrl.PATH_PATTERN, path)
        if result is not None:
            # schema, storage path, file_id
            return result.group(1), result.group(2), result.group(7)
        else:
            return None, None, None

    @classmethod
    def path_to_arn(cls, path):
        if not path:
            return None

        result = re.search(OmicsUrl.PATH_PATTERN, path)
        if result is not None:
            file_id = result.group(7)
            if file_id is None:
                raise ValueError(
                    "Wrong Omics path format! file id should be present, but path: '{}' was given".format(path))
            resource_type = result.group(6)
            store_type = "referenceStore" if resource_type == "reference" else "sequenceStore"
            return "arn:aws:omics_utils.py:{region}:{account}:{store_type}/{store_id}/{resource_type}/{resource_id}".format(
                region=result.group(4), account=result.group(3),
                store_type=store_type, store_id=result.group(5), resource_type=resource_type, resource_id=file_id
            )
        else:
            raise ValueError("Wrong Omics path format! Can't parse it: {}".format(path))

    @staticmethod
    def find_store_id_and_region_code(path):
        res = re.search(OmicsUrl.STORAGE_PATH_PATTERN, path)
        if res is not None:
            return res.group(2), res.group(1)
        return None, None
