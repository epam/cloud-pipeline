/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Popover} from 'antd';
import DataStorageBadge from './data-storage-badge';
import styles from './data-storage-list.css';

const MAX_ITEMS_TO_SHOW = 5;

class DataStorageList extends React.Component {
  @computed
  get storages () {
    const {dataStorages, identifiers} = this.props;
    if (dataStorages.loaded) {
      const ids = new Set((identifiers || []).map(id => +id));
      const result = (dataStorages.value || []).filter(storage => ids.has(storage.id));
      result.sort((a, b) => {
        if (a.name.toLowerCase() > b.name.toLowerCase()) {
          return 1;
        }
        if (a.name.toLowerCase() < b.name.toLowerCase()) {
          return -1;
        }
        return 0;
      });
      result.sort((a, b) => {
        return Number(b.sensitive) - Number(a.sensitive);
      });
      return result;
    }
    return [];
  }

  render () {
    const storages = this.storages.slice(0, MAX_ITEMS_TO_SHOW);
    let extra;
    if (storages.length < this.storages.length) {
      extra = (
        <Popover
          placement="right"
          content={
            <div style={{maxHeight: '50vh', overflow: 'auto', paddingRight: 20}}>
              {
                this.storages.map(storage => (
                  <DataStorageBadge key={storage.id} storageId={storage.id} />
                ))
              }
            </div>
          }>
          <a className={styles.more}>and {this.storages.length - storages.length} more</a>
        </Popover>
      );
    }
    return (
      <div className={styles.container}>
        {
          storages.map(storage => (
            <DataStorageBadge key={storage.id} storageId={storage.id} />
          ))
        }
        {extra}
      </div>
    );
  }
}

DataStorageList.propTypes = {
  identifiers: PropTypes.array
};

DataStorageList.defaultProps = {
  identifiers: []
};

export default inject('dataStorages')(
  observer(
    DataStorageList
  )
);
