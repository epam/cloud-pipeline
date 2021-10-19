/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Input,
  Modal,
  Row
} from 'antd';
import PropTypes from 'prop-types';
import {SERVER} from '../../../../config';
import styles from './SharedItemInfo.css';
import DataStorageSharingPermissionsForm from './DataStorageSharingPermissionsForm';

class SharedItemInfo extends React.Component {
  state = {
    copySuccess: false,
    copyDisabled: false,
    isSharingMode: false,
    usersToShare: []
  }

  get itemName () {
    const {item} = this.props;
    if (item && item.name) {
      return item.name;
    }
    return '';
  }

  get sharedItemUrl () {
    const {item, storageId} = this.props;
    let serverUrl = `${SERVER}/#/storage`;
    let query;
    if (SERVER.endsWith('/')) {
      serverUrl = `${SERVER}#/storage`;
    }
    if (item && item.type.toLowerCase() === 'file') {
      const itemPath = item.path.split('/').slice(0, -1).join('/');
      query = `?path=${itemPath}&versions=false`;
    } else if (item && item.type.toLowerCase() === 'folder') {
      const itemPath = item.path.endsWith('/')
        ? item.path.substring(0, item.path.length - 1)
        : item.path;
      query = `?path=${itemPath}&versions=false`;
    }
    return `${serverUrl}/${storageId}${query}`;
  }

  copyUrlToClipboard = (event) => {
    event && event.stopPropagation();
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(this.sharedItemUrl).then(() => {
        this.setState({copySuccess: true});
      });
    } else {
      this.setState({copyDisabled: true});
    }
  };

  closeShareDialog = () => {
    this.props.close();
  }

  onChangeUsersToShare = (value) => {
    this.setState({usersToShare: value});
  }

  onCancel = () => {
    this.resetForm();
    this.props.close();
  }

  onShare = () => {
    this.resetForm();
    this.props.close();
  }

  resetForm = () => {
    this.setState({
      copySuccess: false,
      copyDisabled: false,
      isSharingMode: false,
      usersToShare: []
    });
  }

  renderShareForm = () => {
    const {copySuccess, copyDisabled} = this.state;
    const {item} = this.props;
    if (!item) {
      return null;
    }
    return (
      <div className={styles.container}
      >
        <span className={styles.mainText}>
          {`Link ${this.itemName ? `to ${this.itemName}` : ''} created.`}
        </span>
        <span className={styles.hint}>
          Make sure you copy the link below.
        </span>
        <div className={styles.urlSection}>
          <Input
            value={this.sharedItemUrl}
            readOnly
            style={{
              width: '100%'
            }}
          />
          <Button
            type={copySuccess ? '' : 'primary'}
            onClick={this.copyUrlToClipboard}
            disabled={copySuccess || copyDisabled}
            style={{
              marginLeft: '15px',
              minWidth: '130px'
            }}
          >
            {copySuccess ? 'Successfully copied' : 'Copy to clipboard'}
          </Button>
        </div>
      </div>
    );
  }

  renderUserSelectForm = () => {
    return (
      <DataStorageSharingPermissionsForm
        objectIdentifier={this.props.storageId}
        objectType={'DATA_STORAGE'}
        onChangeUsersToShare={(value) => this.onChangeUsersToShare(value)}
        usersToShare={this.state.usersToShare}
        goToCreateShareLink={() => this.setState({isSharingMode: true})}
      />
    );
  }

  renderContent = () => {
    return this.state.isSharingMode
      ? this.renderShareForm()
      : this.renderUserSelectForm();
  }
  render () {
    return (
      <Modal
        visible={this.props.visible}
        title={this.props.title}
        footer={this.state.isSharingMode ? (
          <Row type="flex" justify="end">
            <Button
              onClick={() => this.onCancel()}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              onClick={() => this.onShare()}
              style={{marginRight: 5}}
            >
              SHARE
            </Button>
          </Row>
        ) : null}
      >
        {this.renderContent()}
      </Modal>
    );
  }
}

SharedItemInfo.PropTypes = {
  storageId: PropTypes.string,
  item: PropTypes.object,
  title: PropTypes.string,
  close: PropTypes.func,
  submit: PropTypes.func,
  visible: PropTypes.bool
};

export default SharedItemInfo;
