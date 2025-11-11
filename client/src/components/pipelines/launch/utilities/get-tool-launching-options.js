/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import {modifyPayloadForAllowedInstanceTypes} from '../../../runs/actions';

function prepareParameters (parameters) {
  const result = {};
  for (let key in parameters) {
    if (parameters.hasOwnProperty(key)) {
      result[key] = {
        type: parameters[key].type,
        value: parameters[key].value,
        required: parameters[key].required,
        defaultValue: parameters[key].defaultValue
      };
    }
  }
  return result;
}

function parameterIsNotEmpty (parameter, additionalCriteria) {
  return parameter !== null &&
    parameter !== undefined &&
    `${parameter}`.trim().length > 0 &&
    (!additionalCriteria || additionalCriteria(parameter));
}

function chooseDefaultValue (
  versionValue,
  toolValue,
  settingsValue,
  additionalCriteria
) {
  if (parameterIsNotEmpty(versionValue, additionalCriteria)) {
    return versionValue;
  }
  if (parameterIsNotEmpty(toolValue, additionalCriteria)) {
    return toolValue;
  }
  return settingsValue;
}

export default function getToolLaunchingOptions (
  stores,
  tool,
  toolVersion = 'latest'
) {
  return new Promise((resolve, reject) => {
    const {
      awsRegions,
      preferences
    } = stores || {};
    if (!preferences) {
      reject(new Error('Error fetching preferences'));
      return;
    }
    if (!awsRegions) {
      reject(new Error('Error fetching cloud regions'));
      return;
    }
    const request = new LoadToolVersionSettings(tool.id);
    preferences
      .fetchIfNeededOrWait()
      .then(() => {
        if (preferences.error) {
          reject(new Error(preferences.error));
        } else if (!preferences.loaded) {
          reject(new Error('Error fetching preferences'));
        } else {
          return Promise.resolve();
        }
      })
      .then(() => awsRegions.fetchIfNeededOrWait())
      .then(() => {
        if (awsRegions.error) {
          reject(new Error(awsRegions.error));
        } else if (!awsRegions.loaded) {
          reject(new Error('Error fetching cloud regions'));
        } else {
          return Promise.resolve();
        }
      })
      .then(() => request.fetch())
      .then(() => {
        if (request.error) {
          reject(new Error(request.error));
        } else if (!request.loaded) {
          reject(new Error('Error fetching tool parameters'));
        } else {
          const options = (request.value || []).slice();
          const extractSettings = o => {
            if (!o || !o.settings || o.settings.length === 0) {
              return undefined;
            }
            return (
              o.settings.find(s => s.default) ||
              o.settings[0] ||
              {}
            ).configuration;
          };
          const latestVersion = extractSettings(options.find(o => o.version === 'latest'));
          const currentVersion = extractSettings(options.find(o => o.version === toolVersion));

          const versionSettingValue = (settingName) => {
            if (currentVersion) {
              return currentVersion[settingName];
            }
            if (latestVersion) {
              return latestVersion[settingName];
            }
            return null;
          };
          const defaultRegion = (awsRegions.value || []).find(r => r.default) || {};
          const cloudRegionIdValue = parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
            ? versionSettingValue('cloudRegionId')
            : defaultRegion.id;
          const isSpotValue = parameterIsNotEmpty(versionSettingValue('is_spot'))
            ? versionSettingValue('is_spot')
            : preferences.useSpot;
          const allowedInstanceTypesRequest = new AllowedInstanceTypes({
            toolId: tool.id,
            regionId: cloudRegionIdValue,
            spot: isSpotValue
          });
          allowedInstanceTypesRequest
            .fetch()
            .then(() => {
              const payload = modifyPayloadForAllowedInstanceTypes({
                instanceType:
                  chooseDefaultValue(
                    versionSettingValue('instance_size'),
                    tool.instanceType,
                    preferences.getPreferenceValue('cluster.instance.type')
                  ),
                hddSize: +chooseDefaultValue(
                  versionSettingValue('instance_disk'),
                  tool.disk,
                  preferences.getPreferenceValue('cluster.instance.hdd'),
                  p => +p > 0
                ),
                timeout: +(tool.timeout || 0),
                cmdTemplate: chooseDefaultValue(
                  versionSettingValue('cmd_template'),
                  tool.defaultCommand,
                  preferences.getPreferenceValue('launch.cmd.template')
                ),
                dockerImage: tool.registry
                  ? `${tool.registry}/${tool.image}${toolVersion ? `:${toolVersion}` : ''}`
                  : `${tool.image}${toolVersion ? `:${toolVersion}` : ''}`,
                params: parameterIsNotEmpty(versionSettingValue('parameters'))
                  ? prepareParameters(versionSettingValue('parameters'))
                  : {},
                isSpot: isSpotValue,
                nodeCount: parameterIsNotEmpty(versionSettingValue('node_count'))
                  ? +versionSettingValue('node_count')
                  : undefined,
                cloudRegionId: cloudRegionIdValue
              }, allowedInstanceTypesRequest);
              resolve(payload);
            })
            .catch(reject);
        }
      })
      .catch(reject);
  });
}
