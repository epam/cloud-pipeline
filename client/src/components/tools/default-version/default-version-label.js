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
import styles from './default-version.css';

/**
 * The "default" label of a tool's version, which is set as the default one.
 * The version, which is used, while a default version is not set
 * (the latest or the only one), is not labeled.
 */
function DefaultVersionLabel (props) {
  const {
    className,
    style,
    tool,
    version
  } = props;
  if (!tool || !version || tool.defaultVersion !== version) {
    return null;
  }
  return (
    <Tooltip title="This version is launched, if a version is not specified">
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
  version: PropTypes.string
};

export default DefaultVersionLabel;
