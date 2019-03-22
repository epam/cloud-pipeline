/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {Popover} from 'antd';
import styles from './VersionScanResult.css';

@observer
export default class VersionScanResult extends React.Component {

  static propTypes = {
    result: PropTypes.shape({
      critical: PropTypes.number,
      high: PropTypes.number,
      medium: PropTypes.number,
      low: PropTypes.number,
      negligible: PropTypes.number
    }),
    className: PropTypes.string,
    whiteListed: PropTypes.bool,
    placement: PropTypes.oneOf([
      'top',
      'left',
      'right',
      'bottom',
      'topLeft',
      'topRight',
      'bottomLeft',
      'bottomRight',
      'leftTop',
      'leftBottom',
      'rightTop',
      'rightBottom'
    ])
  };

  state = {
    detailsVisible: false
  };

  openDetails = () => {
    this.setState({
      detailsVisible: true
    });
  };

  closeDetails = () => {
    this.setState({
      detailsVisible: false
    });
  };

  render () {
    if (!this.props.result) {
      return null;
    }
    const getDataItem = (name) => {
      if (this.props.result.hasOwnProperty(name)) {
        return {
          name,
          value: this.props.result[name] || 0
        };
      } else {
        return null;
      }
    };
    const data = [
      getDataItem('critical'),
      getDataItem('high'),
      getDataItem('medium'),
      getDataItem('low'),
      getDataItem('negligible')
    ];
    const total = data.reduce((totalValue, item) => {
      if (!totalValue) {
        totalValue = 0;
      }
      if (item) {
        totalValue += (item.value || 0);
      }
      return totalValue;
    }, 0);
    if (total === 0) {
      return null;
    }
    const renderInfo = (item) => {
      if (!item) {
        return null;
      }
      const {value, name} = item;
      if (!value || value === 0) {
        return null;
      }
      return (
        <div key={name} className={`${styles.containerItem} ${styles[name]}`} style={{
          flex: value / total
        }}>{'\u00A0'}</div>
      );
    };
    const capitalizedString = (string) => {
      if (!string) {
        return null;
      }
      return `${string[0].toUpperCase()}${string.substring(1)}`;
    };
    const classNames = [];
    if (this.props.className) {
      classNames.push(this.props.className);
    }
    classNames.push(styles.container);
    if (this.props.whiteListed) {
      classNames.push(styles.whiteList);
    }
    return (
      <Popover
        placement={this.props.placement}
        visible={this.state.detailsVisible}
        content={
          <table>
            <tbody>
              {
                this.props.whiteListed &&
                <tr>
                  <td colSpan={2} style={{borderBottom: '1px solid #ccc'}}>
                    <b>Version is in a white list</b>
                  </td>
                </tr>
              }
              {
                data.filter(item => !!item).map(item => {
                  return (
                    <tr key={item.name}>
                      <th>{capitalizedString(item.name)}:</th>
                      <td>{(item.value || 0).toString()}</td>
                    </tr>
                  );
                })
              }
            </tbody>
          </table>
      }>
        <div
          onMouseEnter={this.openDetails}
          onMouseLeave={this.closeDetails}
          className={classNames.join(' ')}>
          {
            data.map(renderInfo)
          }
        </div>
      </Popover>
    );
  }
}
