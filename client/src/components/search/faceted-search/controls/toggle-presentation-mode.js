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

import React from 'react';
import {Icon, Button} from 'antd';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import styles from './controls.css';

const Modes = {
  list: 'list',
  table: 'table'
};

function TogglePresentationMode (
  {
    className,
    mode,
    onChange,
    style,
    size = 'large'
  }
) {
  const onButtonClick = (event, mode) => {
    event && event.stopPropagation();
    onChange && onChange(mode);
  };
  return (
    <div
      style={style}
      className={classNames(
        styles.viewToggler,
        className
      )}
    >
      <Button.Group size={size}>
        <Button
          type={mode === Modes.list ? 'primary' : 'default'}
          style={{width: 80}}
          onClick={(e) => onButtonClick(e, Modes.list)}>
          <Icon type="bars" />
          List
        </Button>
        <Button
          type={mode === Modes.table ? 'primary' : 'default'}
          style={{width: 80}}
          onClick={(e) => onButtonClick(e, Modes.table)}>
          <Icon type="layout" />
          Table
        </Button>
      </Button.Group>
    </div>
  );
}

TogglePresentationMode.propTypes = {
  className: PropTypes.string,
  mode: PropTypes.oneOf([Modes.list, Modes.table]),
  onChange: PropTypes.func,
  style: PropTypes.object,
  size: PropTypes.string
};

export {Modes as PresentationModes};
export default TogglePresentationMode;
