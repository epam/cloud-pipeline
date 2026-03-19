# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import logging
import os

logger = logging.getLogger(__name__)

_DEFAULT_STATE_DIR = '/opt/cp-pipeline-run-cleanup-job/state'

_HARDCODED_DEFAULTS = {
    'cleanup_days_after': 30,
    'cleanup_statuses': ['FAILURE'],
    'output_param_names': [],
    'archive_runs': False,
    'dry_run': True,
    'page_size': 100,
    'archive_batch_size': 100,
    'delete_data_totally': False,
    'state_file': None,
    'nfs_support': False,
}


class GlobalConfig:
    """Global configuration loaded from environment variables."""

    def __init__(self):
        self.api_url = os.environ.get('API')
        self.jwt_token = os.environ.get('API_TOKEN')
        if not self.jwt_token or not self.api_url:
            raise EnvironmentError('API_TOKEN and API environment variables are required')
        self.config_file = os.environ.get('CP_CLEANUP_RUNS_CONFIG_FILE')
        if not self.config_file:
            raise EnvironmentError('CP_CLEANUP_RUNS_CONFIG_FILE environment variable is required')


class PipelineCleanupConfig:
    """Per-pipeline cleanup configuration built by merging hard-coded defaults,
    JSON-level defaults, and per-pipeline overrides."""

    def __init__(self, pipeline_id, merged):
        self.pipeline_id = pipeline_id
        self.cleanup_days_after = merged['cleanup_days_after']
        self.cleanup_statuses = merged['cleanup_statuses']
        self.output_param_names = merged['output_param_names']
        self.archive_runs = merged['archive_runs']
        self.dry_run = merged['dry_run']
        self.page_size = merged['page_size']
        self.archive_batch_size = merged['archive_batch_size']
        self.delete_data_totally = merged['delete_data_totally']
        self.state_file = merged['state_file'] or os.path.join(
            _DEFAULT_STATE_DIR, 'pipeline_{}.txt'.format(pipeline_id)
        )
        self.nfs_support = merged['nfs_support']


def _merge_defaults(json_defaults, pipeline_entry):
    """Merge hard-coded defaults -> JSON defaults -> pipeline entry (last wins)."""
    merged = dict(_HARDCODED_DEFAULTS)
    for layer in (json_defaults, pipeline_entry):
        for key in _HARDCODED_DEFAULTS:
            if key in layer:
                merged[key] = layer[key]
    return merged


def load_pipeline_configs(config_file):
    """Load per-pipeline cleanup configurations from a JSON file.

    Expected format::

        {
            "default": { ... optional shared defaults ... },
            "pipelines": [
                { "pipeline_id": 123, ... per-pipeline overrides ... },
                ...
            ]
        }
    """
    with open(config_file, 'r') as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError('Config file must contain a JSON object '
                         'with "default" and "pipelines" keys')
    json_defaults = data.get('default') or {}
    pipelines = data.get('pipelines')
    if not isinstance(pipelines, list):
        raise ValueError('"pipelines" must be a JSON array of pipeline configurations')

    configs = []
    for entry in pipelines:
        pipeline_id = entry.get('pipeline_id')
        if pipeline_id is None:
            raise ValueError('Each pipeline config entry must specify "pipeline_id"')
        merged = _merge_defaults(json_defaults, entry)
        configs.append(PipelineCleanupConfig(pipeline_id, merged))
    return configs
