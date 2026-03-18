# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import pytest
from unittest.mock import patch, MagicMock

from src.utilities.capacity_block_processor import CapacityBlockProcessor

CAPACITY_INSTANCE = 'p5.48xlarge'
REGULAR_INSTANCE = 'm5.xlarge'

CAPACITY_BLOCK_CONFIG = {
    CAPACITY_INSTANCE: {
        'cpu_requests_enabled': True,
        'gpu_requests_enabled': True,
        'ram_requests_enabled': False,
        'parameters': {
            'CP_CAP_SCHEDULING': 'CAPACITY_BLOCK',
            'CP_CAP_TIMEOUT': '3600'
        },
        'kube_assign_policy': {
            'selector': {'capacity-type': 'capacity-block'}
        }
    }
}


def _mock_preference(value):
    preference = MagicMock()
    preference.value = value
    return preference


class TestCapacityBlockProcessorInit:

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_none_instance_type_skips_api_call(self, mock_get_pref):
        processor = CapacityBlockProcessor(None)
        mock_get_pref.assert_not_called()
        assert processor._config is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_empty_instance_type_skips_api_call(self, mock_get_pref):
        processor = CapacityBlockProcessor('')
        mock_get_pref.assert_not_called()
        assert processor._config is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_capacity_block_instance_loads_config(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        assert processor._config == CAPACITY_BLOCK_CONFIG[CAPACITY_INSTANCE]

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_regular_instance_has_no_config(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(REGULAR_INSTANCE)
        assert processor._config is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_no_preference_returns_no_config(self, mock_get_pref):
        mock_get_pref.return_value = None
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        assert processor._config is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_empty_preference_value_returns_no_config(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference('')
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        assert processor._config is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_malformed_json_returns_no_config(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference('{invalid json}')
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        assert processor._config is None


class TestCapacityBlockProcessorVerify:

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_non_capacity_block_skips_verification(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(REGULAR_INSTANCE)
        processor.verify({})

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_all_required_params_present_passes(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {
            'CP_CAP_REQUESTS_CPU': '4',
            'CP_CAP_REQUESTS_GPU': '1'
        }
        processor.verify(params)

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_missing_cpu_request_exits(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {'CP_CAP_REQUESTS_GPU': '1'}
        with pytest.raises(SystemExit) as exc_info:
            processor.verify(params)
        assert exc_info.value.code == 1

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_missing_gpu_request_exits(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {'CP_CAP_REQUESTS_CPU': '4'}
        with pytest.raises(SystemExit) as exc_info:
            processor.verify(params)
        assert exc_info.value.code == 1

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_disabled_check_does_not_require_param(self, mock_get_pref):
        """ram_requests_enabled is False in config, so CP_CAP_REQUESTS_RAM is not required."""
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {
            'CP_CAP_REQUESTS_CPU': '4',
            'CP_CAP_REQUESTS_GPU': '1'
        }
        processor.verify(params)

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_empty_param_value_treated_as_missing(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {
            'CP_CAP_REQUESTS_CPU': '',
            'CP_CAP_REQUESTS_GPU': '1'
        }
        with pytest.raises(SystemExit) as exc_info:
            processor.verify(params)
        assert exc_info.value.code == 1


class TestCapacityBlockProcessorApplyConfig:

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_non_capacity_block_returns_original_params(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(REGULAR_INSTANCE)
        original = {'MY_PARAM': 'value'}
        result_params, policy = processor.apply_config(original)
        assert result_params is original
        assert policy is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_capacity_block_merges_config_parameters(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {'CP_CAP_REQUESTS_CPU': '4'}
        result_params, _ = processor.apply_config(params)
        assert result_params['CP_CAP_SCHEDULING'] == 'CAPACITY_BLOCK'
        assert result_params['CP_CAP_TIMEOUT'] == '3600'
        assert result_params['CP_CAP_REQUESTS_CPU'] == '4'

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_user_params_not_overwritten_by_config(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        params = {'CP_CAP_SCHEDULING': 'CUSTOM_VALUE'}
        result_params, _ = processor.apply_config(params)
        assert result_params['CP_CAP_SCHEDULING'] == 'CUSTOM_VALUE'

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_capacity_block_returns_kube_policy(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        _, policy = processor.apply_config({})
        assert policy == {'selector': {'capacity-type': 'capacity-block'}}

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_none_parameters_creates_new_dict(self, mock_get_pref):
        mock_get_pref.return_value = _mock_preference(json.dumps(CAPACITY_BLOCK_CONFIG))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        result_params, _ = processor.apply_config(None)
        assert result_params['CP_CAP_SCHEDULING'] == 'CAPACITY_BLOCK'
        assert result_params['CP_CAP_TIMEOUT'] == '3600'

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_config_without_kube_policy_returns_none(self, mock_get_pref):
        config_no_policy = {
            CAPACITY_INSTANCE: {
                'parameters': {'CP_CAP_SCHEDULING': 'CAPACITY_BLOCK'}
            }
        }
        mock_get_pref.return_value = _mock_preference(json.dumps(config_no_policy))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        _, policy = processor.apply_config({})
        assert policy is None

    @patch('src.utilities.capacity_block_processor.PreferenceAPI.get_preference')
    def test_config_without_parameters_key_merges_nothing(self, mock_get_pref):
        config_no_params = {
            CAPACITY_INSTANCE: {
                'kube_assign_policy': {'selector': {'capacity-type': 'capacity-block'}}
            }
        }
        mock_get_pref.return_value = _mock_preference(json.dumps(config_no_params))
        processor = CapacityBlockProcessor(CAPACITY_INSTANCE)
        result_params, policy = processor.apply_config({'MY_PARAM': 'value'})
        assert result_params == {'MY_PARAM': 'value'}
        assert policy is not None
