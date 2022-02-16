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
import {Icon} from 'antd';
import classNames from 'classnames';
import styles from './panel.css';

function Panel (
  {
    children,
    className,
    onClose,
    style,
    title,
    visible = true
  }
) {
  if (visible && children) {
    return (
      <div
        className={
          classNames(
            styles.container,
            className
          )
        }
        style={style}
      >
        <div
          className={
            classNames(
              styles.panelContent,
              'cp-panel-card'
            )
          }
        >
          <div className={styles.panelHeader}>
            <b>{title}</b>
            <Icon
              className={styles.closeButton}
              type="close"
              size="small"
              onClick={onClose}
            />
          </div>
          <div className={styles.content}>
            {children}
          </div>
        </div>
      </div>
    );
  }
  return null;
}

Panel.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object,
  title: PropTypes.string,
  onClose: PropTypes.func,
  visible: PropTypes.bool
};

Panel.defaultProps = {
  visible: true
};

export default Panel;
