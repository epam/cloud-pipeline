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
import {Select} from 'antd';

const sshThemesList = {
  default: 'Default (dark)',
  light: 'Light'
};

function SshThemeSelect ({
  metadata,
  onChange,
  readOnly,
  style,
  size = 'small'
}) {
  const {value = undefined} = metadata || {};
  const handleChange = newValue => {
    onChange && onChange(newValue);
  };
  const correctedValue = /^light$/i.test(value) ? 'light' : 'default';
  return (
    <div
      style={
        Object.assign(
          {
            width: '100%',
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            margin: '5px 0'
          },
          style || {}
        )
      }
    >
      <b>SSH terminal theme:</b>
      <Select
        onChange={handleChange}
        value={correctedValue}
        size={size}
        disabled={readOnly}
        style={{flex: 1, marginLeft: 5}}
      >
        {Object.entries(sshThemesList).map(([theme, text]) => (
          <Select.Option
            key={theme}
            value={theme}
          >
            {text}
          </Select.Option>
        ))}
      </Select>
    </div>
  );
}

SshThemeSelect.metadataKey = 'ui.ssh.theme';

SshThemeSelect.propTypes = {
  metadata: PropTypes.object,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  style: PropTypes.object,
  size: PropTypes.oneOf(['small', 'large', 'default'])
};

export {sshThemesList};
export default SshThemeSelect;
