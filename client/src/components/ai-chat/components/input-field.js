/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Input} from 'antd';
import styles from './input-field.css';

export default class InputField extends React.Component {
  render () {
    const {
      value,
      onChange,
      onPressEnter,
      disabled,
      onClick
    } = this.props;

    return (
      <div className={styles.field}>
        <Input.TextArea
          style={{height: 20}}
          value={value}
          onChange={onChange}
          onPressEnter={onPressEnter}
          disabled={disabled}
          autosize={{minRows: 1, maxRows: 6}}
        />
        <button disabled={disabled} onClick={onClick} className={styles.iconChat}>
          <Icon type="message" style={{fontSize: 16, color: '#08c'}} />
        </button>
        <button disabled={disabled} onClick={()=> {}} className={styles.iconClip}>
          <Icon type="paper-clip" style={{fontSize: 16}} />
        </button>
      </div>
    );
  }
}

InputField.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  onPressEnter: PropTypes.func,
  onSubmit: PropTypes.func,
  onClick: PropTypes.func
};
