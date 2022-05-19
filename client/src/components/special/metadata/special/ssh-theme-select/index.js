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
  value,
  onChange,
  disabled,
  style,
  size = 'small'
}) {
  const handleChange = value => {
    onChange && onChange(value);
  };
  return (
    <Select
      onChange={handleChange}
      value={value}
      size={size}
      disabled={disabled}
      style={style}
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
  );
}

SshThemeSelect.metadataKey = 'ui.ssh.theme';

SshThemeSelect.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  style: PropTypes.object,
  size: PropTypes.oneOf(['small', 'large', 'default'])
};

export {sshThemesList};
export default SshThemeSelect;
