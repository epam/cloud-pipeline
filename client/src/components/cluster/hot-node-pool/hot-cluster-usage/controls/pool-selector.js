/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Dropdown, Menu} from 'antd';
import styles from './pool-selector.css';

function PoolSelector ({
  value,
  clusterNames,
  onChange,
  description
}) {
  const onSelectChange = ({key}) => {
    onChange && onChange(key);
  };
  const renderOverlay = () => {
    return (
      <Menu
        onClick={onSelectChange}
      >
        {(clusterNames || []).map(clusterName => (
          <Menu.Item key={clusterName}>
            {value === clusterName
              ? <Icon type="check" />
              : undefined
            }
            <span style={{marginLeft: 5}}>
              {clusterName}
            </span>
          </Menu.Item>
        ))}
      </Menu>
    );
  };
  return (
    <div
      className={styles.container}
    >
      <Dropdown
        overlay={renderOverlay()}
        trigger={['click']}
      >
        <div className={styles.selector}>
          <span className={styles.selectorValue}>
            {value}
          </span>
          <Icon type="setting" style={{marginLeft: 2}} />
        </div>
      </Dropdown>
      <span className={styles.description}>
        {description}
      </span>
    </div>
  );
}

PoolSelector.propTypes = {
  value: PropTypes.string,
  clusterNames: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func,
  description: PropTypes.string
};

export default PoolSelector;
