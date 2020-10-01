/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import AWSRegionTag from '../../special/AWSRegionTag';
import {Link} from 'react-router';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import styles from './data-storage-badge.css';

@inject('dataStorages')
@observer
class DataStorageBadge extends React.Component {
  @computed
  get storageInfo () {
    const {dataStorages, storageId} = this.props;
    console.log(storageId);
    if (dataStorages.loaded) {
      const [storage] = (this.props.dataStorages.value || [])
        .filter(storage => +storage.id === +storageId)
        .map((s) => s);
      return storage;
    }
    return null;
  }

  componentDidMount () {
    this.props.dataStorages.fetchIfNeededOrWait();
  }

  render () {
    const {storageId} = this.props;
    if (!storageId || !this.storageInfo) {
      return null;
    }
    return (
      <Link
        className={styles.storageItem}
        to={`/storage/${this.storageInfo.id}`}
      >
        <AWSRegionTag regionId={this.storageInfo.regionId} />
        <span className={classNames(
          styles.storageName, {
            [styles.sensitiveStorageName]: this.storageInfo.sensitive
          }
        )}>{this.storageInfo.name}</span>
      </Link>
    );
  }
}

DataStorageBadge.propTypes = {
  storageId: PropTypes.string
};

DataStorageBadge.defaultProps = {
  storageId: null
};

export default DataStorageBadge;
