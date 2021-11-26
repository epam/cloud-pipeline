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
import classNames from 'classnames';
import {message} from 'antd';
import {inject, observer} from 'mobx-react';
import DataStoragePathUsage from '../../../models/dataStorage/DataStoragePathUsage';
import DataStoragePathUsageUpdate from '../../../models/dataStorage/DataStoragePathUsageUpdate';
import displaySize from '../../../utils/displaySize';
import styles from './storage-size.css';

const REFRESH_REQUESTED_MESSAGE =
  'Storage size refresh has been requested. Please wait a couple of minutes.';

@inject('preferences')
@observer
class StorageSize extends React.PureComponent {
  state = {
    size: undefined
  };

  componentDidMount () {
    this.updateStorageSize();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storage !== this.props.storage ||
      prevProps.storageId !== this.props.storageId
    ) {
      this.updateStorageSize();
    }
  }

  updateStorageSize () {
    const {
      storage,
      storageId
    } = this.props;
    let id = storageId;
    if (id === undefined && typeof storage === 'object') {
      id = storage.id;
    }
    if (id !== undefined) {
      const request = new DataStoragePathUsage(id);
      request
        .fetch()
        .then(() => {
          if (request.loaded && request.value && request.value.size) {
            return Promise.resolve(request.value.size);
          }
          return Promise.resolve();
        })
        .then((size) => {
          this.setState({
            size
          });
        });
    } else {
      this.setState({
        size: undefined
      });
    }
  }

  refreshSize = async () => {
    const {
      storage,
      storageId,
      preferences
    } = this.props;
    let id = storageId;
    if (id === undefined && typeof storage === 'object') {
      id = storage.id;
    }
    if (id !== undefined) {
      try {
        const request = new DataStoragePathUsageUpdate(id);
        await request.fetch();
        await preferences.fetchIfNeededOrWait();
        const disclaimer = preferences.storageSizeRequestDisclaimer || REFRESH_REQUESTED_MESSAGE;
        message.info(disclaimer, 7);
      } catch (e) {
        message.error(e.message, 5);
      }
    }
  };

  render () {
    const {size} = this.state;
    const {className, style} = this.props;
    if (size) {
      return (
        <div
          className={
            classNames(
              styles.storageSize,
              'cp-text',
              className
            )
          }
          style={style}
        >
          <span>
            Storage size: {displaySize(size, size > 1024)}
          </span>
          <a
            className={styles.refreshButton}
            onClick={this.refreshSize}
          >
            Re-index
          </a>
        </div>
      );
    }
    return (
      <div
        className={
          classNames(
            styles.storageSize,
            className
          )
        }
        style={style}
      >
        <a
          className={styles.refreshButton}
          onClick={this.refreshSize}
        >
          Request storage re-index
        </a>
      </div>
    );
  }
}

StorageSize.propTypes = {
  className: PropTypes.string,
  storage: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  style: PropTypes.object
};

export default StorageSize;
