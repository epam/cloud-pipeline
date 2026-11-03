/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {stubApi} from '@test';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import runDefaultParameters from '../../../../../models/pipelines/PipelineRunDefaultParameters';
import {CP_NODEUP_RETRY_COUNT} from './parameters';
import {
  getNodeUpRetryCountParameter,
  getParameterConfig,
  getVisibleParameters,
  parametersToPayloadParams,
  parametersToConfigurationParams,
  setNodeUpRetryCountValue,
  validateParameters
} from './parameter-utilities';

async function loadPreferences (
  allowNodeUpRetryCount,
  systemParameters = [{name: CP_NODEUP_RETRY_COUNT, type: 'int'}]
) {
  stubApi({
    '/preferences': [
      {name: 'ui.launch.allow.nodeup.count', value: `${allowNodeUpRetryCount}`},
      {name: 'cluster.nodeup.retry.count', value: '4'}
    ],
    '/run/defaultParameters': systemParameters
  });
  preferences.invalidateCache();
  runDefaultParameters.invalidateCache();
  await preferences.fetch();
  await runDefaultParameters.fetch();
}

const other = {
  name: 'OTHER',
  value: 'a',
  configs: [{configId: 'default', type: 'string', value: 'a'}]
};

function nodeUpRetryCount (value) {
  return {
    name: CP_NODEUP_RETRY_COUNT,
    value,
    system: true,
    configs: [{configId: 'default', type: 'int', value}]
  };
}

describe('getVisibleParameters', () => {
  const system = [{name: CP_NODEUP_RETRY_COUNT, system: true}, {name: 'SYSTEM', system: true}];
  const regular = [{name: CP_NODEUP_RETRY_COUNT}, {name: 'OTHER'}];
  const names = (parameters) => parameters.map((p) => p.name);

  it('hides CP_NODEUP_RETRY_COUNT from the system parameters when asked to', () => {
    expect(names(getVisibleParameters(system, true, false, undefined, true, true)))
      .toEqual(['SYSTEM']);
  });

  it('hides CP_NODEUP_RETRY_COUNT from the other parameters when asked to', () => {
    expect(names(getVisibleParameters(regular, false, false, undefined, true, true)))
      .toEqual(['OTHER']);
  });

  it('keeps a CP_NODEUP_RETRY_COUNT the user added, to show its name error', () => {
    const added = [{name: CP_NODEUP_RETRY_COUNT, userParameter: true}, {name: 'OTHER'}];
    expect(names(getVisibleParameters(added, false, false, undefined, true, true)))
      .toEqual([CP_NODEUP_RETRY_COUNT, 'OTHER']);
  });

  it('shows CP_NODEUP_RETRY_COUNT as a usual parameter by default', () => {
    expect(names(getVisibleParameters(system, true, false, undefined, true)))
      .toContain(CP_NODEUP_RETRY_COUNT);
    expect(names(getVisibleParameters(regular, false, false, undefined, true)))
      .toContain(CP_NODEUP_RETRY_COUNT);
  });
});

describe('parametersToPayloadParams / parametersToConfigurationParams', () => {
  it('keep a CP_NODEUP_RETRY_COUNT value', () => {
    const parameters = [nodeUpRetryCount('7')];
    expect(parametersToPayloadParams(parameters)).toHaveProperty(CP_NODEUP_RETRY_COUNT);
    expect(parametersToConfigurationParams(parameters).parameters)
      .toHaveProperty(CP_NODEUP_RETRY_COUNT);
  });

  it('drop an empty CP_NODEUP_RETRY_COUNT with the preference on, so it is not sent', async () => {
    await loadPreferences(true);
    ['', undefined].forEach((value) => {
      const parameters = [other, nodeUpRetryCount(value)];
      expect(parametersToPayloadParams(parameters)).not.toHaveProperty(CP_NODEUP_RETRY_COUNT);
      expect(parametersToConfigurationParams(parameters).parameters)
        .not.toHaveProperty(CP_NODEUP_RETRY_COUNT);
    });
  });

  it('keep an empty CP_NODEUP_RETRY_COUNT, as any other parameter, when the preference is off',
    async () => {
      await loadPreferences(false);
      const parameters = [other, nodeUpRetryCount('')];
      expect(parametersToPayloadParams(parameters)).toHaveProperty(CP_NODEUP_RETRY_COUNT);
      expect(parametersToConfigurationParams(parameters).parameters)
        .toHaveProperty(CP_NODEUP_RETRY_COUNT);
    });
});

describe('getNodeUpRetryCountParameter', () => {
  it('finds the parameter by name, in any case', () => {
    const parameter = {...nodeUpRetryCount('7'), name: 'cp_nodeup_retry_count'};
    expect(getNodeUpRetryCountParameter([other, parameter])).toBe(parameter);
  });

  it('finds nothing when the parameter is not set', () => {
    expect(getNodeUpRetryCountParameter([other])).toBeUndefined();
    expect(getNodeUpRetryCountParameter(undefined)).toBeUndefined();
  });

  it('skips a parameter the user added under this name', () => {
    const added = {...nodeUpRetryCount('7'), userParameter: true};
    expect(getNodeUpRetryCountParameter([other, added])).toBeUndefined();
  });
});

describe('setNodeUpRetryCountValue', () => {
  beforeEach(() => loadPreferences(true));

  it('adds the parameter when it is not set', () => {
    const result = setNodeUpRetryCountValue([other], '7');
    expect(result).toHaveLength(2);
    expect(result[0]).toBe(other);
    expect(result[1]).toMatchObject({name: CP_NODEUP_RETRY_COUNT, type: 'int', value: '7'});
    expect(parametersToPayloadParams(result)[CP_NODEUP_RETRY_COUNT])
      .toMatchObject({type: 'int', value: '7'});
  });

  it('updates the value of the parameter that is set', () => {
    const result = setNodeUpRetryCountValue([other, nodeUpRetryCount('3')], '7');
    expect(result).toHaveLength(2);
    expect(result[1]).toMatchObject({name: CP_NODEUP_RETRY_COUNT, value: '7'});
  });

  it('removes the parameter when the value is cleared', () => {
    expect(setNodeUpRetryCountValue([other, nodeUpRetryCount('3')], '')).toEqual([other]);
  });
});

describe('validateParameters', () => {
  const errorOf = (value) => {
    const parameters = setNodeUpRetryCountValue([], value);
    const {parameters: validated} = validateParameters(parameters);
    return getNodeUpRetryCountParameter(validated).error;
  };

  const requiredErrorOf = () => {
    const config = getParameterConfig(CP_NODEUP_RETRY_COUNT, {type: 'int', required: true});
    const parameter = {name: CP_NODEUP_RETRY_COUNT, value: '', config, configs: [config]};
    const {parameters: validated} = validateParameters([parameter]);
    return getNodeUpRetryCountParameter(validated).error;
  };

  // built the way "Add parameter" builds it, then renamed by the user
  const addedNameErrorOf = () => {
    const config = {
      ...getParameterConfig('', {type: 'string'}),
      name: CP_NODEUP_RETRY_COUNT,
      userParameter: true
    };
    const parameter = {
      name: CP_NODEUP_RETRY_COUNT,
      value: '7',
      userParameter: true,
      config,
      configs: [config]
    };
    const {parameters: [validated]} = validateParameters([parameter]);
    return validated.nameError;
  };

  it('reserves the name of a parameter the user adds with the preference on', async () => {
    await loadPreferences(true, []);
    expect(addedNameErrorOf()).toBe('Name is reserved');
  });

  it('allows the name of a parameter the user adds with the preference off', async () => {
    await loadPreferences(false, []);
    expect(addedNameErrorOf()).toBeUndefined();
  });

  describe('with ui.launch.allow.nodeup.count on', () => {
    beforeEach(() => loadPreferences(true));

    it('accepts a positive integer up to the maximum', () => {
      expect(errorOf('7')).toBeUndefined();
      expect(errorOf('999')).toBeUndefined();
    });

    it('reports a value that is not a positive integer', () => {
      expect(errorOf('0')).toBe('Please enter a valid positive integer number');
      expect(errorOf('abc')).toBe('Please enter a valid positive integer number');
    });

    it('reports a value above the maximum', () => {
      expect(errorOf('1000')).toBe('Maximum value is 999');
    });

    it('does not require a value, even when the configuration marks it required', () => {
      expect(requiredErrorOf()).toBeUndefined();
    });
  });

  describe('with ui.launch.allow.nodeup.count off', () => {
    beforeEach(() => loadPreferences(false));

    it('does not check the value', () => {
      expect(errorOf('0')).toBeUndefined();
      expect(errorOf('1000')).toBeUndefined();
    });

    it('requires a value when the configuration marks it required, as any other parameter', () => {
      expect(requiredErrorOf()).toBe('Required');
    });
  });
});
