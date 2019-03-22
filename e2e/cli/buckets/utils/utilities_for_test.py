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

import logging
from common_utils.pipe_cli import create_data_storage, delete_data_storage
from buckets.utils.cloud.utilities import wait_for_bucket_creation, wait_for_bucket_deletion


def create_buckets(*args):
    for bucket_name in args:
        create_data_storage(bucket_name)
        wait_for_bucket_creation(bucket_name)
        logging.info("Bucket {} created".format(bucket_name))


def create_bucket(bucket_name, **kwargs):
    create_data_storage(bucket_name, **kwargs)
    wait_for_bucket_creation(bucket_name if 'path' not in kwargs else kwargs['path'])
    logging.info("Bucket {} created".format(bucket_name))


def delete_buckets(*args):
    for bucket_name in args:
        delete_data_storage(bucket_name)
        wait_for_bucket_deletion(bucket_name)
        logging.info("Bucket {} deleted".format(bucket_name))


def prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash):
    if has_destination_slash:
        destination += "/"
    if has_source_slash:
        source += "/"
    return source, destination
