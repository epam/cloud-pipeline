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
  Row,
  Spin,
  Alert
} from 'antd';
import PropTypes from 'prop-types';

import styles from './SharedItemInfo.css';
import DataStorageSharingPermissionsForm from './DataStorageSharingPermissionsForm';

class SharedItemInfo extends React.Component {
  state = {
    copySuccess: false,
    copyDisabled: false,
    showLink: false,
    usersToShare: [],
    permissions: {},
    pending: false
  }

  get itemName () {
    const {item} = this.props;
    if (item && item.name) {
      return item.name;
    }
    return '';
  }

  get sharedItemUrl () {
    return this.props.link;
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
    this.setState({
      copySuccess: false
    });
    this.props.close();
  }

  onChangeUsersToShare = (value) => {
    this.setState({usersToShare: value});
  }

  onShare = async (selectedPermissions) => {
    const {generateLinkFn} = this.props;
    this.setState({pending: true});
    await generateLinkFn({
      permissions: this.state.usersToShare,
      mask: selectedPermissions.mask
    });
    this.setState({pending: false});
    if (!this.props.sharingError) {
      this.setState({showLink: true});
    }
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
            disabled={copyDisabled}
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
        goToCreateShareLink={this.onShare}
      />
    );
  }

  renderContent = () => {
    return this.state.showLink
      ? this.renderShareForm()
      : this.renderUserSelectForm();
  }

  renderModalFooter = () => {
    return (this.state.showLink || this.props.sharingError)
      ? (
        <Row type="flex" justify="end">
          <Button
            type="primary"
            onClick={this.closeShareDialog}
            style={{marginRight: 5}}
          >
            OK
          </Button>
        </Row>
      ) : null;
  }
  render () {
    return (
      <Modal
        visible={this.props.visible}
        title={this.props.title}
        onCancel={this.closeShareDialog}
        footer={this.renderModalFooter()}
      >
        <div>
          {
            this.props.sharingError
              ? (
                <Alert
                  type="error"
                  message={this.props.sharingError}
                />)
              : (
                <Spin spinning={this.state.pending}>
                  {this.renderContent()}
                </Spin>
              )
          }
        </div>
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
  visible: PropTypes.bool,
  generateLinkFn: PropTypes.func,
  link: PropTypes.string
};

export default SharedItemInfo;
