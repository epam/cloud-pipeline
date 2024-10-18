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
import classNames from 'classnames';
import {observable, computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Row, Modal, message, Button, Input, Checkbox} from 'antd';
import styles from './create-dts-modal.css';
import DTSCreate from '../../../../../models/dts/DTSCreate';

@inject('dtsList')
@observer
export default class CreateDtsModal extends React.Component {
  state = {
    url: '',
    name: '',
    schedulable: true,
    prefixes: ''
  }

  @observable errors = {}

  componentDidMount () {
    this.validate();
  }

  reset = () => {
    this.setState({
      url: '',
      name: '',
      schedulable: true,
      prefixes: '',
      errors: {}
    }, this.validate);
  };

  onOk = async () => {
    const {onClose, dtsList} = this.props;
    const {url, name, schedulable, prefixes} = this.state;
    const request = new DTSCreate();
    await request.send({
      url,
      name,
      schedulable,
      prefixes: (prefixes || '').split(' ').filter(Boolean)
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

  validate = () => {
    const {url = '', name = '', prefixes = ''} = this.state;
    this.errors = {
      url: url.trim() ? undefined : 'Field is required',
      name: name.trim() ? undefined : 'Field is required',
      prefixes: prefixes.trim() ? undefined : 'Field is required'
    };
  };

  @computed
  get hasErrors () {
    return Object.values(this.errors).filter(Boolean).length > 0;
  }

  onCancel = () => {
    const {onClose} = this.props;
    this.reset();
    onClose && onClose();
  };

  onChangeInputItem = (key) => (event) => {
    this.setState({[key]: event.target.value}, this.validate);
  };

  onChangeCheckboxItem = (key) => (event) => {
    this.setState({[key]: event.target.checked}, this.validate);
  };

  renderFormItem = ({key, label = '', type = 'input'}) => {
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
    };
    const renderFn = renderers[type] || renderers.input;
    const error = this.errors[key];
    return (
      <div className={styles.formItemContainer}>
        {renderFn(key)}
        <div className={classNames(
          'cp-error',
          styles.errorContainer,
          {[styles.error]: !!error}
        )}>
          {this.errors[key] || ''} &nbsp;
        </div>
      </div>
    );
  };

  render () {
    const {visible} = this.props;
    if (!visible) {
      return null;
    }
    return (
      <Modal
        title="Create DTS"
        visible={visible}
        onOk={this.onOk}
        footer={(
          <Row type="flex" align="center" justify="end">
            <Button onClick={this.onCancel}>
              Cancel
            </Button>
            <Button
              disabled={this.hasErrors}
              type="primary"
              onClick={this.onOk}
              style={{marginLeft: 5}}
            >
              OK
            </Button>
          </Row>
        )}
        onCancel={this.onCancel}
      >
        {this.renderFormItem({key: 'url', label: 'Url:'})}
        {this.renderFormItem({key: 'name', label: 'Name:'})}
        {this.renderFormItem({key: 'prefixes', label: 'Prefixes:'})}
        {this.renderFormItem({key: 'schedulable', label: 'Schedulable:', type: 'checkbox'})}
      </Modal>
    );
  }
}

CreateDtsModal.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func
};
