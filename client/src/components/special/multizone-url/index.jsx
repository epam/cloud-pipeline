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
import {Dropdown} from 'antd';
import {DownOutlined} from '@ant-design/icons';
import {inject, observer} from 'mobx-react';
import AWSRegionTag from '../AWSRegionTag';
import styles from './multizone-url.module.css';
import classNames from 'classnames';

@inject('multiZoneManager')
@observer
export default class MultizoneUrl extends React.Component {
  state = {
    visible: false,
  };

  handleVisibilityChange = (visible) => {
    const {visibilityChanged} = this.props;
    this.setState(
      {
        visible,
      },
      () => visibilityChanged && visibilityChanged(visible),
    );
  };

  onUrlClicked = (e) => {
    e.stopPropagation();
    this.handleVisibilityChange(false);
  };

  render() {
    const {
      className,
      children,
      style,
      dropDownIconStyle,
      configuration,
      getPopupContainer,
      multiZoneManager,
      target = '_blank',
    } = this.props;
    const regions = multiZoneManager.getSortedRegionsWithUrls(configuration);
    if (regions.length === 0) {
      return null;
    }
    const defaultRegion = regions[0];
    const menuItems = regions.map(({region, url}) => ({
      key: region,
      label: (
        <span style={{display: 'flex'}}>
          <AWSRegionTag
            style={{verticalAlign: 'top', marginLeft: -3, fontSize: 'larger'}}
            regionUID={region}
          />
          <a className={styles.menuLink} target={target} href={url} onClick={this.onUrlClicked}>
            {region || <i>Unknown region</i>}
            {region === defaultRegion.region ? <i style={{marginLeft: 5}}>(best)</i> : null}
          </a>
        </span>
      ),
    }));
    return (
      <div className={className} style={style}>
        <a
          target={target}
          href={defaultRegion.url}
          className={styles.link}
          onClick={this.onUrlClicked}
        >
          {children || defaultRegion.url || '\u00A0'}
        </a>
        {regions.length > 1 && (
          <Dropdown
            trigger={['click']}
            menu={{items: menuItems}}
            placement="bottomRight"
            style={{minWidth: '150px'}}
            onOpenChange={this.handleVisibilityChange}
            open={this.state.visible}
            getPopupContainer={getPopupContainer}
            onClick={(e) => e.stopPropagation()}
          >
            <DownOutlined
              className={classNames(styles.expander, 'cp-primary')}
              onClick={(e) => e.stopPropagation()}
              style={dropDownIconStyle}
            />
          </Dropdown>
        )}
      </div>
    );
  }
}
MultizoneUrl.propTypes = {
  className: PropTypes.string,
  target: PropTypes.string,
  style: PropTypes.object,
  dropDownIconStyle: PropTypes.object,
  configuration: PropTypes.object,
  children: PropTypes.node,
  getPopupContainer: PropTypes.func,
  visibilityChanged: PropTypes.func,
};
