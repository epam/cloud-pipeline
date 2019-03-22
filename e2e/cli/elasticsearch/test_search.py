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
import os
import shutil
import uuid
from time import sleep
from urlparse import urlparse

import pytest

from common_utils.pipe_cli import pipe_storage_cp
from pipeline_api_provider import CloudPipelineApiProvider


def create_test_folder(path):
    if not os.path.exists(path):
        os.makedirs(path)


def create_test_file(path, content):
    create_test_folder(os.path.dirname(path))
    with open(path, 'w') as f:
        f.write(content)


def clean_test_data(path):
    if not os.path.exists(path):
        return
    if os.path.isfile(path):
        os.remove(path)
    else:
        shutil.rmtree(path)


def get_relative_path(url):
    parsed = urlparse(url)
    return parsed.geturl().replace("%s://" % parsed.scheme, '', 1).replace(parsed.hostname, '', 1).strip("/")


class TestSearch(object):

    pipeline_api = CloudPipelineApiProvider()
    common_prefix = str(uuid.uuid4()).replace("-", "")

    folder = None
    pipeline = None
    s3_storage = None
    issue = None
    s3_storage_file_local_path = None
    s3_storage_file_path = None

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')

        logging.info(cls.common_prefix)

        cls.folder = cls.pipeline_api.create_folder(cls.common_prefix)
        logging.info("Folder with name %s " % cls.common_prefix)

        cls.pipeline = cls.pipeline_api.create_pipeline(cls.common_prefix, cls.common_prefix)
        logging.info("Pipeline with name % s" % cls.common_prefix)

        cls.s3_storage = cls.pipeline_api.create_s3_data_storage(cls.common_prefix, cls.common_prefix)
        logging.info("S3 data storage with name % s" % cls.common_prefix)

        cls.issue = cls.pipeline_api.create_issue(cls.common_prefix, cls.common_prefix, cls.folder['id'], 'FOLDER')
        logging.info("Issue with name % s" % cls.common_prefix)

        cls.pipeline_api.create_comment(cls.issue['id'], cls.common_prefix)
        logging.info("Issue comment with text %s" % cls.common_prefix)

        cls.s3_storage_file_local_path = os.path.abspath(cls.common_prefix + ".txt")
        cls.s3_storage_file_path = 's3://%s/case/' % cls.common_prefix
        create_test_file(cls.s3_storage_file_local_path, cls.common_prefix)
        pipe_storage_cp(cls.s3_storage_file_local_path, cls.s3_storage_file_path)
        logging.info("S3 file with path %s" % cls.s3_storage_file_path)

        sleep(600)

    @classmethod
    def teardown_class(cls):
        cls.pipeline_api.delete_folder(cls.folder['id'])
        logging.info("Folder %d has been deleted" % cls.folder['id'])

        cls.pipeline_api.delete_pipeline(cls.pipeline['id'])
        logging.info("Pipeline %d has been deleted" % cls.pipeline['id'])

        cls.pipeline_api.delete_data_storage(cls.s3_storage['id'])
        logging.info("Data storage %d has been deleted" % cls.s3_storage['id'])

        clean_test_data(cls.s3_storage_file_local_path)

    @pytest.mark.run(order=1)
    def test_search_folder(self):
        result = self.pipeline_api.search(self.common_prefix, "FOLDER")
        self.verify_search_result(result)
        assert len(result['documents']) == 1
        for doc in result['documents']:
            assert int(doc['id']) == int(self.folder['id'])
            assert int(doc['elasticId']) == int(self.folder['id'])
            assert doc['name'] == self.folder['name']
            assert doc['type'] == 'FOLDER'
            self.verify_highlights(['name'], doc['highlights'])

    @pytest.mark.run(order=1)
    def test_search_pipeline(self):
        result = self.pipeline_api.search(self.common_prefix, "PIPELINE")
        self.verify_search_result(result)
        assert len(result['documents']) == 1
        for doc in result['documents']:
            assert int(doc['id']) == int(self.pipeline['id'])
            assert int(doc['elasticId']) == int(self.pipeline['id'])
            assert doc['name'] == self.pipeline['name']
            assert doc['type'] == 'PIPELINE'
            self.verify_highlights(['name', 'description'], doc['highlights'])

    @pytest.mark.run(order=1)
    def test_search_s3_data_storage(self):
        result = self.pipeline_api.search(self.common_prefix, "S3_STORAGE")
        self.verify_search_result(result)
        assert len(result['documents']) == 1
        for doc in result['documents']:
            assert int(doc['id']) == int(self.s3_storage['id'])
            assert int(doc['elasticId']) == int(self.s3_storage['id'])
            assert doc['name'] == self.s3_storage['name']
            assert doc['type'] == 'S3_STORAGE'
            self.verify_highlights(['name', 'description', 'path'], doc['highlights'])

    @pytest.mark.run(order=2)
    def test_search_issue(self):
        result = self.pipeline_api.search(self.common_prefix, "ISSUE")
        self.verify_search_result(result)
        assert len(result['documents']) == 1
        for doc in result['documents']:
            assert int(doc['id']) == int(self.issue['id'])
            assert int(doc['elasticId']) == int(self.issue['id'])
            assert doc['name'] == self.issue['name']
            assert doc['type'] == 'ISSUE'
            self.verify_highlights(['name', 'text', 'comments'], doc['highlights'])

    @pytest.mark.run(order=2)
    def test_search_s3_file(self):
        result = self.pipeline_api.search(self.common_prefix, "S3_FILE")
        self.verify_search_result(result)
        assert len(result['documents']) == 1
        for doc in result['documents']:
            name = get_relative_path(self.s3_storage_file_path)
            assert doc['id'] == name
            assert doc['name'] == name
            assert doc['type'] == 'S3_FILE'
            self.verify_highlights(['name', 'storage_name'], doc['highlights'])

    @staticmethod
    def verify_search_result(result):
        assert 'totalHits' in result and int(result['totalHits']) > 0
        assert 'searchSucceeded' in result and bool(result['searchSucceeded'])
        assert 'documents' in result
        for doc in result['documents']:
            assert 'elasticId' in doc
            assert 'id' in doc
            assert 'name' in doc
            assert 'type' in doc
            assert 'highlights' in doc

    @staticmethod
    def verify_highlights(expected_fields, actual_highlights):
        highlights = []

        assert len(expected_fields) == len(actual_highlights)
        for highlight in actual_highlights:
            assert 'fieldName' in highlight
            assert 'matches' in highlight and len(highlight['matches']) == 1
            highlights.append(highlight['fieldName'])
        assert set(expected_fields) == set(highlights)
