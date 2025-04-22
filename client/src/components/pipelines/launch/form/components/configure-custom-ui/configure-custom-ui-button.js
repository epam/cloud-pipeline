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
import {Icon} from 'antd';
import classNames from 'classnames';
import ConfigureCustomUIModal from './configure-custom-ui-modal';

export default class ConfigureCustomUIButton extends React.Component {
  state = {
    visible: false
  };

  onOpenModal = () => {
    this.setState({visible: true});
  };

  onCloseModal = () => {
    this.setState({visible: false});
  };

  onOk = (configurations) => {
    const {onChange} = this.props;
    onChange && onChange(configurations);
    this.setState({visible: false});
  };

  render () {
    const {configurations, text, style} = this.props;
    const {visible} = this.state;
    const infoText = configurations.length
      ? `(${configurations.length} configuration${configurations.length > 1 ? 's' : ''} applied)`
      : '';
    return (
      <div style={Object.assign({
        width: 'fit-content'
      }, style)}>
        <a
          onClick={this.onOpenModal}
          className={classNames('cp-text', 'underline')}
          style={{textDecoration: 'underline'}}
        >
          <Icon type="setting" />
          <span style={{margin: '0 5px'}}>
            {text || 'Configure Custom UI pages'}
          </span>
          {infoText}
        </a>
        {visible ? (
          <ConfigureCustomUIModal
            visible={visible}
            onOk={this.onOk}
            onCancel={this.onCloseModal}
            configurations={configurations}
          />
        ) : null}
      </div>
    );
  }
}
