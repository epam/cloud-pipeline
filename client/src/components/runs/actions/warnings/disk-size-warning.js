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
import PropTypes from 'prop-types';
import {inject, observer, Provider} from 'mobx-react';
import {Alert} from 'antd';
import preferencesLoad from '../../../../models/preferences/PreferencesLoad';
import Markdown from '../../../special/markdown';

function getDiskSizeThresholdConfiguration (preferences, disk) {
  const diskSize = disk && !Number.isNaN(Number(disk)) ? Number(disk) : 0;
  if (!diskSize || !preferences.loaded) {
    return null;
  }
  return (preferences.launchDiskSizeThresholds || [])
    .slice()
    .sort((a, b) => b.threshold - a.threshold)
    .find((config) => config.threshold <= diskSize);
}

function getDiskSizeThresholdConfigurationRestrictions (preferences, disk) {
  const diskSize = disk && !Number.isNaN(Number(disk)) ? Number(disk) : 0;
  if (!diskSize) {
    return {
      pause: true
    };
  }
  if (!preferences.loaded) {
    return {
      pause: false
    };
  }
  const getValue = (value) => (value === undefined ? true : value);
  return (preferences.launchDiskSizeThresholds || [])
    .filter((config) => config.threshold <= diskSize)
    .reduce((result, configuration) => ({
      pause: getValue(result.pause) && getValue(configuration.pause)
    }), {
      pause: true
    });
}

function diskSizeAllowsPause (preferences, disk) {
  return getDiskSizeThresholdConfigurationRestrictions(
    preferences,
    disk
  ).pause;
}

function formatMessage (configuration, disk) {
  if (!configuration) {
    return '';
  }
  let result = configuration.disclaimer || '';
  result = result.replace(/\{(disk|size)}/ig, disk);
  result = result.replace(/\{(threshold|max)}/ig, configuration.threshold);
  return result;
}

const emptyStore = {loaded: false};

function DiskSizeWarning (
  {
    className,
    style,
    disk,
    preferences,
    showIcon,
    type
  }
) {
  const match = getDiskSizeThresholdConfiguration(preferences, disk);
  if (!match || !match.disclaimer) {
    return null;
  }
  return (
    <Provider
      pipelinesLibrary={emptyStore}
      dockerRegistries={emptyStore}
      hiddenObjects={emptyStore}
      preferences={preferences}
    >
      <Alert
        message={(
          <Markdown
            className="no-margin-markdown"
            md={formatMessage(match, disk)}
          />
        )}
        type={match.disclaimerType || type || 'warning'}
        className={className}
        style={style}
        showIcon={showIcon}
      />
    </Provider>
  );
}

DiskSizeWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disk: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  showIcon: PropTypes.bool,
  type: PropTypes.oneOf(['warning', 'info', 'error', 'success'])
};

export {
  diskSizeAllowsPause,
  getDiskSizeThresholdConfiguration,
  getDiskSizeThresholdConfigurationRestrictions
};

export default inject(() => ({
  preferences: preferencesLoad
}))(observer(DiskSizeWarning));
