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
import PoolShortDescription from '../../pool-short-description';
import styles from './pool-selector.css';

function PoolSelector ({
  value,
  pools = [],
  onChange
}) {
  const onSelectChange = ({key}) => {
    onChange && onChange(Number(key));
  };
  const renderOverlay = () => {
    const sorted = (pools || [])
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    return (
      <Menu
        onClick={onSelectChange}
      >
        {
          sorted.map((pool) => (
            <Menu.Item key={`${pool.id}`}>
              {Number(pool.id) === Number(value)
                ? <Icon type="check" />
                : undefined
              }
              <span style={{marginLeft: 5}}>
                {pool.name}
              </span>
            </Menu.Item>
          ))
        }
      </Menu>
    );
  };
  const pool = pools.find(p => Number(p.id) === Number(value));
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
            {pool ? pool.name : `Pool #${value}`}
          </span>
          <Icon type="setting" style={{marginLeft: 2}} />
        </div>
      </Dropdown>
      <PoolShortDescription
        className={styles.description}
        pool={pool}
      />
    </div>
  );
}

PoolSelector.propTypes = {
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pools: PropTypes.array,
  onChange: PropTypes.func
};

export default PoolSelector;
