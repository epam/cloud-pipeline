/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {Alert, Button, Input, Modal} from 'antd';
import roleModel from '../../../../../utils/roleModel';
import LoadingView from '../../../../special/LoadingView';
import BashCode from '../../../../special/bash-code';
import SharedLink from '../../../../../models/dataStorage/DataStorageGenerateSharedLink';
import styles from './storage-shared-link-button.css';

class StorageSharedLinkButton extends React.Component {
  state = {
    shared: false,
    link: undefined,
    pending: false,
    error: undefined,
    visible: false
  };

  @computed
  get dataStorageShareLinkDisclaimer () {
    if (this.props.preferences.loaded) {
      let code = (this.props.preferences.getPreferenceValue('data.sharing.disclaimer') || '');
      code = code.replace(/\\n/g, '\n');
      code = code.replace(/\\r/g, '\r');
      return code;
    }
    return null;
  }

  componentDidMount () {
    this.fetchStorageInfo();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.storageId !== this.props.storageId) {
      this.fetchStorageInfo();
    }
  }

  fetchStorageInfo = () => {
    const token = this.token = (this.token || 0) + 1;
    this.setState(
      {
        shared: false,
        link: undefined,
        pending: false,
        error: undefined,
        visible: false
      },
      async () => {
        const {
          storageId,
          dataStorages
        } = this.props;
        if (!storageId || !dataStorages) {
          return;
        }
        const request = dataStorages.load(storageId);
        try {
          await request.fetchIfNeededOrWait();
          if (request.loaded) {
            const storageInfo = request.value || {};
            const {
              shared,
              type
            } = storageInfo;
            const sharedLinkAvailable = roleModel.writeAllowed(storageInfo) &&
              !/^nfs$/i.test(type) &&
              shared;
            if (token === this.token) {
              this.setState({
                shared: sharedLinkAvailable
              });
            }
          }
        } catch (_) {}
      }
    );
  };

  fetchSharedLink = () => {
    const token = this.token = (this.token || 0) + 1;
    this.setState({
      pending: true
    }, async () => {
      const {storageId} = this.props;
      const request = new SharedLink(storageId);
      const state = {
        pending: false,
        error: undefined,
        link: undefined
      };
      try {
        await request.fetch();
        if (request.error) {
          throw new Error(request.error);
        }
        state.link = request.value;
      } catch (error) {
        state.error = error.message;
      } finally {
        if (token === this.token) {
          this.setState(state);
        }
      }
    });
  };

  openModal = () => {
    this.fetchSharedLink();
    this.setState({visible: true});
  };

  closeModal = () => {
    this.setState({visible: false});
  };

  renderModalContent = () => {
    const {
      pending,
      error,
      link
    } = this.state;
    if (link) {
      return (
        <Input
          autosize
          type="textarea"
          value={link}
          disabled={pending}
        />
      );
    }
    if (pending) {
      return (
        <LoadingView />
      );
    }
    if (error) {
      return (
        <Alert
          message={error}
          type="error"
        />
      );
    }
    return null;
  };

  render () {
    const {
      shared,
      visible
    } = this.state;
    if (!shared) {
      return null;
    }
    const {
      className,
      style
    } = this.props;
    return (
      <Button
        id="share-storage-button"
        size="small"
        onClick={this.openModal}
        className={className}
        style={style}
      >
        Share
        <Modal
          title="Share storage link"
          width="80%"
          visible={visible}
          onOk={this.closeModal}
          onCancel={this.closeModal}
          footer={(
            <Button
              type="primary"
              onClick={this.closeModal}>
              OK
            </Button>
          )}>
          <div>
            {this.renderModalContent()}
            {
              this.dataStorageShareLinkDisclaimer && (
                <BashCode
                  id="data-sharing-disclaimer"
                  className={styles.dataSharingDisclaimer}
                  code={this.dataStorageShareLinkDisclaimer}
                />
              )
            }
          </div>
        </Modal>
      </Button>
    );
  }
}

StorageSharedLinkButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
};

export default inject('dataStorages', 'preferences')(observer(StorageSharedLinkButton));
