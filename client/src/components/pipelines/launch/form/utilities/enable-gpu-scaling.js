/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {Select} from 'antd';
import {getSelectOptions} from '../../../../special/instance-type-info';
import {getInstanceFamily} from '../../../../../utils/instance-family';

function parseCPUConfiguration (configuration) {
  const {
    Param,
    param = Param,
    DefaultValue,
    defaultValue = DefaultValue
  } = configuration || {};
  return {
    parameter: param,
    value: defaultValue
  };
}

function parseRestConfiguration (configuration) {
  return Object
    .entries(configuration || {})
    .map(([parameter, value]) => ({parameter, value}));
}

function parseConfiguration (configuration, isHybrid = false) {
  const {
    FamilyTypeCPU,
    familyTypeCPU = FamilyTypeCPU,
    FamilyTypeGPU,
    familyTypeGPU = FamilyTypeGPU,
    InstanceTypeCPU,
    instanceTypeCPU = InstanceTypeCPU,
    InstanceTypeGPU,
    instanceTypeGPU = InstanceTypeGPU,
    Parameters,
    parameters = Parameters,
    ...restConfiguration
  } = configuration || {};
  return {
    cpu: parseCPUConfiguration(isHybrid ? familyTypeCPU : instanceTypeCPU),
    gpu: parseCPUConfiguration(isHybrid ? familyTypeGPU : instanceTypeGPU),
    other: parseRestConfiguration({...restConfiguration, ...parameters})
  };
}

function configurationIsMissing (configuration) {
  return Object.keys(configuration || {}).length === 0;
}

function parseGPUScalingPreference (preferences) {
  const template = preferences ? preferences.autoscalingMultiQueuesTemplate : {};
  const providers = Object.keys(template || {});
  return providers
    .map(provider => {
      const providerValue = template[provider];
      const {
        Hybrid = {},
        hybrid = Hybrid,
        General = {},
        general = General
      } = providerValue || {};
      if (configurationIsMissing(hybrid) || configurationIsMissing(general)) {
        return false;
      }
      return {
        provider,
        general: parseConfiguration(general),
        hybrid: parseConfiguration(hybrid, true)
      };
    })
    .filter(Boolean);
}

/**
 * @typedef {Object} GPUScalingOptions
 * @property {string} [provider]
 * @property {boolean} [hybrid]
 * @property {Object} [parameters]
 */

/**
 * Gets GPU scaling configuration for provider
 * @param {string} provider
 * @param {Object} preferences
 * @returns {Object|undefined}
 */
function getScalingConfigurationForProvider (provider, preferences) {
  return parseGPUScalingPreference(preferences)
    .find(configuration => configuration.provider === provider);
}

/**
 * Checks if "Enable GPU scaling" option is available for provider
 * @param {string} provider
 * @param {Object} preferences
 * @returns {boolean}
 */
function gpuScalingAvailable (provider, preferences) {
  return !!provider && !!getScalingConfigurationForProvider(provider, preferences);
}

function getGPUScalingDefaultConfiguration (options, preferences) {
  const {
    provider,
    hybrid = false
  } = options;
  const configuration = getScalingConfigurationForProvider(provider, preferences);
  if (configuration) {
    return hybrid ? configuration.hybrid : configuration.general;
  }
  return undefined;
}

function readGPUScalingPreference (options, preferences) {
  const {
    autoScaled = false,
    parameters = {}
  } = options || {};
  const configuration = getGPUScalingDefaultConfiguration(options, preferences);
  if (configuration && autoScaled) {
    const readParameterValue = parameter => parameters && parameters[parameter]
      ? parameters[parameter].value
      : undefined;
    const {
      cpu = {},
      gpu = {},
      ...rest
    } = configuration;
    const cpuValue = readParameterValue(cpu?.parameter);
    const gpuValue = readParameterValue(gpu?.parameter);
    if (cpuValue || gpuValue) {
      return {
        cpu: {
          ...cpu,
          value: cpuValue
        },
        gpu: {
          ...gpu,
          value: gpuValue
        },
        ...rest
      };
    }
  }
  return undefined;
}

function getSkippedParameters (preferences, includeOther = true) {
  const parameters = parseGPUScalingPreference(preferences)
    .map(o => ([
      o.hybrid?.gpu?.parameter,
      o.hybrid?.cpu?.parameter,
      o.general?.gpu?.parameter,
      o.general?.cpu?.parameter,
      ...(includeOther ? (o.hybrid?.other || []).map(p => p.parameter) : []),
      ...(includeOther ? (o.general?.other || []).map(p => p.parameter) : [])
    ]))
    .reduce((r, c) => ([...r, ...c]), [])
    .filter(Boolean);
  return [...(new Set(parameters))];
}

const emptyValueKey = '__empty__';

const InstanceTypeSelector = (
  {
    value,
    onChange,
    instanceTypes = [],
    style = {},
    gpu = false,
    allowEmpty = false,
    emptyName,
    emptyTooltip
  }
) => {
  const sorted = instanceTypes.filter(t => !gpu || t.gpu);
  const onChangeCallback = (newValue) => {
    if (typeof onChange === 'function') {
      if (newValue === emptyValueKey) {
        onChange(undefined);
      } else {
        onChange(newValue);
      }
    }
  };
  return (
    <Select
      value={value || (allowEmpty ? emptyValueKey : undefined)}
      style={style}
      onChange={onChangeCallback}
    >
      {
        allowEmpty && (
          <Select.Option key={emptyValueKey} value={emptyValueKey} title={emptyTooltip}>
            {emptyName || (
              <span className="cp-text-not-important">
                Not set
              </span>
            )}
          </Select.Option>
        )
      }
      {getSelectOptions(sorted)}
    </Select>
  );
};

const InstanceFamilySelector = (
  {
    value,
    onChange,
    instanceTypes = [],
    style = {},
    gpu = false,
    provider,
    allowEmpty = false,
    emptyName,
    emptyTooltip
  }
) => {
  const sorted = instanceTypes.filter(t => !gpu || t.gpu);
  const families = [...new Set(sorted.map((i) => getInstanceFamily(i, provider)))]
    .filter(Boolean)
    .sort();
  const onChangeCallback = (newValue) => {
    if (typeof onChange === 'function') {
      if (newValue === emptyValueKey) {
        onChange(undefined);
      } else {
        onChange(newValue);
      }
    }
  };
  return (
    <Select
      value={value || (allowEmpty ? emptyValueKey : undefined)}
      style={style}
      onChange={onChangeCallback}
    >
      {
        allowEmpty && (
          <Select.Option key={emptyValueKey} value={emptyValueKey} title={emptyTooltip}>
            {emptyName || (
              <span className="cp-text-not-important">
                Not set
              </span>
            )}
          </Select.Option>
        )
      }
      {
        families.map((family) => (
          <Select.Option key={family} value={family}>
            {family}
          </Select.Option>
        ))
      }
    </Select>
  );
};

function applyParameters (configuration, parameters) {
  if (configuration) {
    const {
      gpu,
      cpu,
      other = []
    } = configuration;
    const newParameters = {...(parameters || {})};
    const applyParameter = (parameter) => {
      if (parameter && parameter.parameter) {
        newParameters[parameter.parameter] = {
          value: parameter.value
        };
      }
    };
    applyParameter(gpu);
    applyParameter(cpu);
    other.forEach(applyParameter);
    return newParameters;
  }
  return parameters;
}

function applyParametersArray (configuration, parametersArray) {
  if (configuration) {
    const {
      gpu,
      cpu,
      other = []
    } = configuration;
    const applyParameter = (parameter) => {
      if (parameter && parameter.parameter) {
        let param = parametersArray.find(o => o.name === parameter.parameter);
        if (!param) {
          parametersArray.push({
            name: parameter.parameter,
            value: parameter.value
          });
        } else {
          param.value = parameter.value;
        }
      }
    };
    applyParameter(gpu);
    applyParameter(cpu);
    other.forEach(applyParameter);
  }
}

function configurationChanged (previous, current) {
  if (!previous && !current) {
    return false;
  }
  if (!previous || !current) {
    return true;
  }
  return previous.gpu?.parameter !== current.gpu?.parameter ||
    previous.cpu?.parameter !== current.cpu?.parameter ||
    previous.gpu?.value !== current.gpu?.value ||
    previous.cpu?.value !== current.cpu?.value;
}

export {
  applyParameters,
  applyParametersArray,
  configurationChanged,
  getSkippedParameters,
  gpuScalingAvailable,
  getScalingConfigurationForProvider,
  getGPUScalingDefaultConfiguration,
  readGPUScalingPreference,
  InstanceTypeSelector,
  InstanceFamilySelector
};
