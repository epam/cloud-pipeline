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

# The pipeline templates (workflows/pipe-templates) and the demo pipelines (workflows/pipe-demo) run their Python
# scripts with the Python of the run ($CP_PYTHON_PATH, Python 3.12), which has the pipeline package of pipe-common.
# Their Python sources compile on Python 3 without a syntax warning, and the parts that differ on Python 3 work there.

import importlib.util
import json
import os
import shutil
import sys
import tempfile
import unittest
import warnings
import zipfile

WORKFLOWS_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATES_DIR = os.path.join(WORKFLOWS_DIR, 'pipe-templates')
DEMO_DIR = os.path.join(WORKFLOWS_DIR, 'pipe-demo')
DATA_LOADER_SRC = os.path.join(TEMPLATES_DIR, '__SYSTEM', 'data_loader', 'src')
COLLECT_SAMPLE_METRICS = os.path.join(DEMO_DIR, 'ngs', 'wes-analysis', 'src', 'collect_sample_metrics.py')


def _files(top_dir, suffix):
    for root, _, file_names in os.walk(top_dir):
        for file_name in sorted(file_names):
            if file_name.endswith(suffix):
                yield os.path.join(root, file_name)


def _read(path):
    with open(path) as f:
        return f.read()


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class _SilentLogger(object):

    @staticmethod
    def info(message, task_name=None):
        pass


class TemplatesPython3Test(unittest.TestCase):

    def test_python_templates_run_with_the_python_of_the_run(self):
        commands = {}
        for name in ('__COMMON/python', '__SYSTEM/data_loader'):
            configurations = json.loads(_read(os.path.join(TEMPLATES_DIR, name, 'config.json')))
            commands[name] = configurations[0]['configuration']['cmd_template'].split()[0]
        self.assertEqual({'__COMMON/python': '${CP_DOLLAR}CP_PYTHON_PATH',
                          '__SYSTEM/data_loader': '${CP_DOLLAR}CP_PYTHON_PATH'}, commands)

    def test_no_template_runs_the_python_first_on_path(self):
        for path in _files(TEMPLATES_DIR, 'config.json'):
            for configuration in json.loads(_read(path)):
                command = configuration['configuration'].get('cmd_template', '')
                self.assertNotIn(command.split()[0] if command else '', ('python', 'python2', 'python3'), path)

    def test_sources_compile_on_python3_without_warnings(self):
        failures = []
        sources = list(_files(TEMPLATES_DIR, '.py')) + list(_files(DEMO_DIR, '.py'))
        for path in sources:
            with open(path, 'rb') as f:
                source = f.read()
            with warnings.catch_warnings():
                warnings.simplefilter('error')
                try:
                    compile(source, path, 'exec', dont_inherit=True)
                except (SyntaxError, SyntaxWarning, DeprecationWarning) as e:
                    failures.append('{}: {}'.format(path, e))
        self.assertGreater(len(sources), 10)
        self.assertEqual([], failures)

    def test_demo_scripts_run_python_with_the_python_of_the_run(self):
        found = []
        for path in _files(DEMO_DIR, '.sh'):
            for number, line in enumerate(_read(path).splitlines(), 1):
                if 'CP_PYTHON2_PATH' in line:
                    found.append('%s:%d' % (path, number))
        self.assertEqual([], found)
        self.assertIn('${CP_PYTHON_PATH} ${SCRIPTS_DIR}/src/read_samples.py',
                      _read(os.path.join(DEMO_DIR, 'ngs', 'demultiplex', 'src', 'demultiplex.sh')))


class DataLoaderPython3Test(unittest.TestCase):

    def setUp(self):
        sys.path.insert(0, DATA_LOADER_SRC)

    def tearDown(self):
        sys.path.remove(DATA_LOADER_SRC)
        for name in [name for name in sys.modules if name == 'model' or name.startswith('model.')]:
            del sys.modules[name]

    def test_the_entities_api_imports_on_python3(self):
        # sys-data-upload.py imports it as model.entities_api: its own imports are relative
        from model.entities_api import EntitiesAPI
        from model.entity import Entity
        self.assertEqual('abc', Entity.load({'id': 1, 'externalId': 'abc', 'data': {}}).external_id)
        self.assertTrue(callable(EntitiesAPI.load_all))

    def test_the_upload_reads_metadata_values_as_text(self):
        text = _read(os.path.join(DATA_LOADER_SRC, 'sys-data-upload.py'))
        self.assertNotIn('.encode(', text)
        self.assertIn("metadata_entities = [e.strip() for e in os.environ['METADATA_ENTITIES'].split(',')]", text)


class CollectSampleMetricsPython3Test(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.module = _load('collect_sample_metrics', COLLECT_SAMPLE_METRICS)
        # The events go to the log of the run: not here
        self.module.Logger = _SilentLogger

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def test_the_fastqc_report_is_read_from_the_zip_as_text(self):
        report = os.path.join(self.tmp, 'S1_R1_fastqc.zip')
        with zipfile.ZipFile(report, 'w') as archive:
            archive.writestr('S1_R1_fastqc/fastqc_data.txt',
                             '##FastQC\t0.11.9\nTotal Sequences\t1000\nSequences flagged as poor quality\t5\n'
                             'Sequence length\t150\n%GC\t48\n>>END_MODULE\n')
        self.assertEqual((1000, 5, '48', '150'), self.module.FastQCReader.read(report, 'Task'))

    def test_the_mean_coverage_is_a_whole_number(self):
        coverage = os.path.join(self.tmp, 'coverage.txt')
        with open(coverage, 'w') as f:
            f.write('chr1\t1\t3\nchr1\t2\t4\n')
        self.assertEqual(3, self.module.CoverageMetricsReader.read(coverage, 'Task'))


if __name__ == '__main__':
    unittest.main()
