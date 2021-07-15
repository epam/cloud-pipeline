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
import PropTypes from 'prop-types';
import {
  Dropdown,
  Menu,
  Icon
} from 'antd';
import AWSRegionTag from '../AWSRegionTag';
import styles from './multizone-url.css';

export default function MultizoneUrl (props) {
  const {
    className,
    style,
    regions,
    defaultRegion: defaultRegionValue,
    title
  } = props;
  const regionsKeys = Object.keys(regions);
  const fallbackRegion = regionsKeys.pop();
  const defaultRegion = defaultRegionValue || fallbackRegion;
  const menu = (
    <Menu
      style={{minWidth: 150}}
    >
      {
        regions &&
        regionsKeys
          .map((key) => (
            <Menu.Item key={key} style={{display: 'flex'}}>
              <AWSRegionTag
                style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
                regionUID={key}
              />
              <a
                className={styles.menuLink}
                target="_blank"
                href={regions[key]}
              >
                {key || (<i>Unknown region</i>)}
                {
                  key === defaultRegion
                    ? (<i style={{marginLeft: 5}}>(best)</i>)
                    : false
                }
              </a>
            </Menu.Item>
          )
          )
      }
    </Menu>
  );
  if (defaultRegion && Object.values(regions || {}).length > 0) {
    return (
      <div
        className={className}
        style={style}
      >
        <a
          target="_blank"
          href={regions[defaultRegion]}
          className={styles.link}
        >
          {title || regions[defaultRegion] || '\u00A0'}
          <AWSRegionTag
            style={{fontSize: 'larger', marginLeft: 2}}
            regionUID={defaultRegion}
          />
        </a>
        {
          regionsKeys.length > 1 && (
            <Dropdown
              trigger={['click']}
              overlay={menu}
              placement="bottomRight"
              style={{minWidth: '150px'}}
            >
              <Icon
                className={styles.expander}
                type="down"
              />
            </Dropdown>)
        }
      </div>
    );
  }
  return null;
}
MultizoneUrl.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  regions: PropTypes.object,
  defaultRegion: PropTypes.string,
  title: PropTypes.string
};
