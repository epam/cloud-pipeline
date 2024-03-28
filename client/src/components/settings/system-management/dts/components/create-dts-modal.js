/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {Modal, message, Button, Input, Checkbox} from 'antd';
import styles from './create-dts-modal.css';
import DTSCreate from '../../../../../models/dts/DTSCreate';
import propTypes from 'prop-types';

@inject('dtsList')
@observer
export default class CreateDtsModal extends React.Component {
  state = {
    url: '',
    name: '',
    schedulable: true,
    prefixes: []
  }

  reset = () => {
    this.setState({
      url: '',
      name: '',
      schedulable: true,
      prefixes: ''
    })
  };

  onOk = async () => {
    const {onClose, dtsList} = this.props;
    const {url, name, schedulable, prefixes} = this.state;
    const request = new DTSCreate();
    await request.send({
      url,
      name,
      schedulable,
      prefixes: (prefixes || '').split('').filter(Boolean)
    });
    if (request.error) {
      message.error(request.error, 5);
    }
    if (dtsList) {
      await dtsList.fetch();
    }
    this.reset();
    onClose && onClose();
  };

  onCancel = () => {
    const {onClose} = this.props;
    this.reset();
    onClose && onClose();
  };

  onChangeInputItem = (key) => (event) => {
    this.setState({[key]: event.target.value});
  };

  onChangeCheckboxItem = (key) => (event) => {
    this.setState({[key]: event.target.checked});
  };

  renderFormItem = ({key, label= '', type = 'input'}) => {
    if (!key) {
      return null;
    }
    const renderers = {
      input: (key) => (
        <div className={styles.formItem}>
          <span className={styles.label}>{label}</span>
          <Input size="small" value={this.state[key]} onChange={this.onChangeInputItem(key)} />
        </div>
      ),
      checkbox: (key) => (
        <div className={styles.formItem}>
          <span className={styles.label}>{label}</span>
          <Checkbox checked={this.state[key]} onChange={this.onChangeCheckboxItem(key)} />
        </div>
      )
    }
    if (renderers[type]) {
      return renderers[type](key);
    }
    return renderers.input(key);
  };

  render () {
    const {visible} = this.props;
    if (!visible) {
      return null;
    }
    return(
      <Modal
        title="Create DTS"
        visible={visible}
        onOk={this.onOk}
        onCancel={this.onCancel}
      >
        {this.renderFormItem({key: 'url', label: 'Url:'})}
        {this.renderFormItem({key: 'name', label: 'Name:'})}
        {this.renderFormItem({key: 'prefixes', label: 'Prefixes:'})}
        {this.renderFormItem({key: 'schedulable', label: 'Schedulable:', type: 'checkbox'})}
      </Modal>
    )
  }
}

CreateDtsModal.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func,
};
