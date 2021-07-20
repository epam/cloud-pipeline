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
import MultizoneUrlPopover from './multizone-url-popover';
import styles from './multizone-url.css';

export {MultizoneUrlPopover};

export default class MultizoneUrl extends React.Component {
  state = {
    visible: false
  }
  handleVisibilityChange = (visible) => {
    const {visibilityChanged} = this.props;
    this.setState({
      visible
    }, () => visibilityChanged && visibilityChanged(visible));
  }

  render () {
    const {
      className,
      style,
      dropDownIconStyle,
      regions,
      defaultRegion: defaultRegionValue,
      title,
      getPopupContainer
    } = this.props;
    const regionsKeys = Object.keys(regions || {});
    const fallbackRegion = [...regionsKeys].pop();
    const defaultRegion = defaultRegionValue === undefined
      ? fallbackRegion
      : defaultRegionValue;
    const menu = (
      <Menu
        style={{minWidth: 150}}
      >
        {
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
            ))
        }
      </Menu>
    );
    if (Object.values(regions || {}).length > 0) {
      return (
        <div
          className={className}
          style={style}
        >
          <a
            target="_blank"
            href={regions[defaultRegion]}
            className={styles.link}
            onClick={e => e.stopPropagation()}
          >
            {title || regions[defaultRegion] || '\u00A0'}
          </a>
          {
            regionsKeys.length > 1 && (
              <Dropdown
                trigger={['click']}
                overlay={menu}
                placement="bottomRight"
                style={{minWidth: '150px'}}
                onVisibleChange={this.handleVisibilityChange}
                visible={this.state.visible}
                getPopupContainer={getPopupContainer}
                onClick={(e) => e.stopPropagation()}
              >
                <Icon
                  className={styles.expander}
                  type="down"
                  onClick={e => e.stopPropagation()}
                  style={dropDownIconStyle}
                />
              </Dropdown>)
          }
        </div>
      );
    }
    return null;
  }
}
MultizoneUrl.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  dropDownIconStyle: PropTypes.object,
  regions: PropTypes.object,
  defaultRegion: PropTypes.string,
  title: PropTypes.node,
  getPopupContainer: PropTypes.func,
  visibilityChanged: PropTypes.func
};
