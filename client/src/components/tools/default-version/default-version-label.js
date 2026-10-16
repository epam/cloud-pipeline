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

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Tooltip} from 'antd';
import {
  getToolDefaultVersion,
  isCustomDefaultVersion,
  LATEST_VERSION
} from '../../../utils/tool-default-version';
import styles from './default-version.css';

export function getDefaultVersionDescription (tool, versions = []) {
  const defaultVersion = getToolDefaultVersion(tool, versions);
  if (!defaultVersion) {
    return undefined;
  }
  if (isCustomDefaultVersion(tool, versions)) {
    return 'This version is launched, if a version is not specified';
  }
  const used = defaultVersion === LATEST_VERSION ? 'the latest version' : 'the only version';
  const {defaultVersion: missingDefaultVersion} = tool || {};
  if (missingDefaultVersion) {
    // the default version tag was removed from the registry
    return `The default version ${missingDefaultVersion} does not exist, so ${used} is used`;
  }
  return `A default version is not set, so ${used} is used`;
}

function DefaultVersionLabel (props) {
  const {
    className,
    style,
    tool,
    version,
    versions
  } = props;
  if (!version || version !== getToolDefaultVersion(tool, versions)) {
    return null;
  }
  return (
    <Tooltip title={getDefaultVersionDescription(tool, versions)}>
      <span
        className={classNames(className, 'cp-tag', 'accent', styles.label)}
        style={style}
      >
        default
      </span>
    </Tooltip>
  );
}

DefaultVersionLabel.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tool: PropTypes.object,
  version: PropTypes.string,
  versions: PropTypes.arrayOf(PropTypes.string)
};

export default DefaultVersionLabel;
