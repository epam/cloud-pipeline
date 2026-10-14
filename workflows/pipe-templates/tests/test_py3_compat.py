# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Exercises the data_loader system pipeline and the Python/Luigi template cmd_templates (issue
# #4532) under whichever interpreter runs it. There was no test runner in this package before.
# sys-data-upload.py itself is not imported here: `from pipeline import Logger, TaskStatus` pulls
# in the whole pipe-common `pipeline` package (api/storage/log/...), which needs third-party
# dependencies (boto3, azure, google-cloud, ...) unrelated to the Python 2/3 fix under test here -
# same reasoning as scripts/autoscaling/test_py3_compat.py excluding the azure/*.py scripts from
# its generic import test.

import json
import os
import re
import sys
import unittest

try:
    from unittest import mock
except ImportError:
    import mock

try:
    import importlib.util

    def _load_module_from_path(name, path):
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
except ImportError:
    # importlib.util doesn't exist under Python 2 - this test file itself needs to run under
    # either interpreter, same as the templates it exercises.
    import imp

    def _load_module_from_path(name, path):
        return imp.load_source(name, path)

PIPE_TEMPLATES_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_LOADER_SRC_DIR = os.path.join(PIPE_TEMPLATES_DIR, '__SYSTEM', 'data_loader', 'src')
SYS_DATA_UPLOAD_PATH = os.path.join(DATA_LOADER_SRC_DIR, 'sys-data-upload.py')

# Bug pattern fixed in sys-data-upload.py: `metadata_entities = map(...)` is a one-shot iterator
# under Python 3 (list under Python 2), but the code later calls `len(metadata_entities)` and
# repeats membership checks against it - fails/misbehaves under Python 3 unless wrapped in
# `list(...)`. The fixed form is `metadata_entities = list(map(...))`, which this pattern does not
# match (the `=` is followed by `list(`, not `map(`, so a correctly-wrapped call does not trip it).
BARE_MAP_ASSIGNMENT_PATTERN = re.compile(r'=\s*map\(')

# Bug pattern fixed in sys-data-upload.py: `.encode("utf-8")` turned the metadata value into
# `bytes` under Python 3, which was then compared with `.startswith('http://')` (a `str` literal)
# - raises TypeError. Fixed by dropping the `.encode(...)` calls and keeping the value as `str`.
ENCODE_UTF8_PATTERN = re.compile(r'''\.encode\(["']utf-8["']\)''')

BARE_PYTHON_INTERPRETER_PATTERN = re.compile(r'^python\s')


def _cmd_template_of(config_json_path):
    with open(config_json_path) as f:
        config = json.load(f)
    return config[0]['configuration']['cmd_template']


def _import_entities_api_module():
    # Imported as an actual member of the `model` package (not loaded as a standalone file by
    # path) - the same way sys-data-upload.py imports it (`from model.entities_api import
    # EntitiesAPI`, with only `src/`, not `src/model/`, on sys.path). This is what makes the
    # regression this test guards against reproducible: `importlib.util.spec_from_file_location`
    # on the file directly would load it with no parent package at all, so its own
    # `from .base import API` would fail regardless of whether the production bug (an implicit,
    # non-relative `from base import API`) is present or fixed.
    if DATA_LOADER_SRC_DIR not in sys.path:
        sys.path.insert(0, DATA_LOADER_SRC_DIR)
    import model.entities_api
    return model.entities_api


class ModelPackageImportTest(unittest.TestCase):
    # Regression test: model/entities_api.py used implicit relative imports
    # (`from base import API`, `from entity import Entity`) - legal under Python 2 only because it
    # runs inside the `model` package, but `model/__init__.py` makes `model` an explicit package,
    # and only `src/` (not `src/model/`) is ever put on sys.path by the job that runs this
    # pipeline - so Python 3 raises ModuleNotFoundError on `base`/`entity`. Fixed to explicit
    # relative imports (`from .base import API`, `from .entity import Entity`), which both
    # interpreters accept.
    def test_entities_api_imports_cleanly_as_a_package_member(self):
        module = _import_entities_api_module()
        self.assertTrue(hasattr(module, 'EntitiesAPI'))


class EntitiesApiBehaviorTest(unittest.TestCase):
    # Behavioral check that EntitiesAPI still paginates and builds Entity objects correctly after
    # the import fix above - a bare import-succeeds test would not catch a fix that imports
    # cleanly but is wired to the wrong classes.
    def setUp(self):
        self.entities_api_module = _import_entities_api_module()

    def test_load_all_paginates_and_builds_entities(self):
        api = self.entities_api_module.EntitiesAPI('https://api.example.com', 'test-token')

        page_1_elements = [{'id': i, 'externalId': 'sample-{}'.format(i), 'data': {}} for i in range(1, 21)]
        page_2_elements = [{'id': i, 'externalId': 'sample-{}'.format(i), 'data': {}} for i in range(21, 26)]
        responses = [
            {'payload': {'totalCount': 25, 'elements': page_1_elements}},
            {'payload': {'totalCount': 25, 'elements': page_2_elements}},
        ]

        with mock.patch.object(api, 'call', side_effect=responses) as call_mock:
            entities = list(api.load_all('folder-1', 'Sample'))

        self.assertEqual(call_mock.call_count, 2)
        self.assertEqual([e.external_id for e in entities], ['sample-{}'.format(i) for i in range(1, 26)])
        self.assertEqual([e.id for e in entities], list(range(1, 26)))


class SysDataUploadStaticPatternTest(unittest.TestCase):
    # Static source checks for the two bug patterns fixed in sys-data-upload.py (see the pattern
    # comments above) - the fixed code lives inline under `if __name__ == '__main__':`, not in a
    # separately-callable function, and importing the real module here would pull in the whole
    # pipe-common `pipeline` package (see the module-level comment), so a behavioral test would
    # need to shell out to the script as a subprocess for little extra confidence over asserting
    # directly on its source.
    def test_metadata_entities_is_not_a_bare_map_object(self):
        with open(SYS_DATA_UPLOAD_PATH) as f:
            content = f.read()
        self.assertIsNone(BARE_MAP_ASSIGNMENT_PATTERN.search(content))

    def test_metadata_values_are_not_encoded_to_bytes(self):
        with open(SYS_DATA_UPLOAD_PATH) as f:
            content = f.read()
        self.assertIsNone(ENCODE_UTF8_PATTERN.search(content))


class CmdTemplateInterpreterTest(unittest.TestCase):
    # Regression test: the `data_loader` and `python` templates' `cmd_template` invoked a bare
    # `python` - resolved by the job container's PATH, which recent base images (e.g.
    # library/ubuntu:latest, used by `data_loader`) may not provide at all. Fixed to resolve the
    # interpreter via `CP_PYTHON_PATH`, the same variable the job container launch script exports
    # (following whichever of Python 2.7/3.12 CP_PYTHON_VERSION selects), rather than a hardcoded
    # interpreter name.
    def test_data_loader_cmd_template_uses_cp_python_path(self):
        cmd_template = _cmd_template_of(os.path.join(PIPE_TEMPLATES_DIR, '__SYSTEM', 'data_loader', 'config.json'))
        self.assertIsNone(BARE_PYTHON_INTERPRETER_PATTERN.match(cmd_template))
        self.assertIn('CP_PYTHON_PATH', cmd_template)

    def test_python_template_cmd_template_uses_cp_python_path(self):
        cmd_template = _cmd_template_of(os.path.join(PIPE_TEMPLATES_DIR, '__COMMON', 'python', 'config.json'))
        self.assertIsNone(BARE_PYTHON_INTERPRETER_PATTERN.match(cmd_template))
        self.assertIn('CP_PYTHON_PATH', cmd_template)


if __name__ == '__main__':
    unittest.main()
