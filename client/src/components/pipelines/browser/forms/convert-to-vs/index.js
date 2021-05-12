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
import {inject, observer} from 'mobx-react';
import {
  Modal,
  Button
} from 'antd';
import styles from './convert-to-vs.css';

@inject('preferences')
@observer
class ConvertToVersionedStorage extends React.Component {
  state = {
    pending: false
  };

  onConvertClicked = () => {
    const {
      onConvert
    } = this.props;
    if (!onConvert) {
      return Promise.resolve();
    }
    this.setState({
      pending: true
    }, () => {
      const promise = onConvert.then ? onConvert : (...opts) => Promise.resolve(onConvert(...opts));
      promise()
        .catch(() => {})
        .then(() => this.setState({pending: false}));
    });
  };

  render () {
    const {
      visible,
      storageName,
      onCancel
    } = this.props;
    const {
      pending
    } = this.state;
    const storage = storageName ? (<b>{storageName}</b>) : 'the storage';
    return (
      <Modal
        visible={visible}
        onCancel={onCancel}
        closable={!pending}
        maskClosable={!pending}
        title={(
          <span>
            Convert {storage} to Versioned Storage?
          </span>
        )}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              id="convert-to-versioned-storage-cancel-button"
              disabled={pending}
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              id="convert-to-versioned-storage-convert-button"
              type="primary"
              disabled={pending}
              onClick={this.onConvertClicked}
            >
              CONVERT
            </Button>
          </div>
        )}
      >
        <div className={styles.accent}>
          All the data will be moved under the version control.
        </div>
        <div className={styles.info}>
          This operation cannot be undone.
        </div>
      </Modal>
    );
  }
}

ConvertToVersionedStorage.propTypes = {
  storageName: PropTypes.string,
  visible: PropTypes.bool,
  onConvert: PropTypes.func,
  onCancel: PropTypes.func
};

export default ConvertToVersionedStorage;
