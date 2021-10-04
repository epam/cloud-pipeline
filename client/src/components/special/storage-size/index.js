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
import DataStoragePathUsage from '../../../models/dataStorage/DataStoragePathUsage';
import displaySize from '../../../utils/displaySize';
import styles from './storage-size.css';

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
      console.log('FETCH', id);
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

  render () {
    const {size} = this.state;
    const {className, style} = this.props;
    if (size) {
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
          Storage size: {displaySize(size, size > 1024)}
        </div>
      );
    }
    return null;
  }
}

StorageSize.propTypes = {
  className: PropTypes.string,
  storage: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  style: PropTypes.object
};

export default StorageSize;
