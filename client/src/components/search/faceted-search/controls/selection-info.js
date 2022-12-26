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
import BashCode from '../../../special/bash-code';

const SELECTION_PLACEHOLDERS = [
  {
    keys: ['STORAGE', 'STORAGE_ID', 'STORAGEID'],
    render: (item) => item.storageId
  },
  {
    keys: ['PATH'],
    render: (item) => item.path
  },
  {
    keys: ['NAME'],
    render: (item) => item.name
  }
];

@inject('preferences', 'dataStorages')
@observer
class SelectionInfo extends React.Component {
  componentDidMount () {
    const {preferences, dataStorages} = this.props;
    preferences.fetchIfNeededOrWait();
    dataStorages.fetchIfNeededOrWait();
  }

  @computed
  get templateParts () {
    const {preferences} = this.props;
    if (preferences.loaded && preferences.facetedFilterDownload) {
      const {selectionTemplate} = preferences.facetedFilterDownload;
      return (selectionTemplate || '').split(/({.*?})/gi).filter(Boolean);
    }
    return [];
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return this.props.dataStorages.value || [];
    }
    return [];
  }

  get code () {
    const {items} = this.props;
    const mapItemTemplate = (item) => {
      return this.templateParts.map((string) => {
        if (!/({.*?})/.test(string)) {
          return string;
        }
        const placeholder = string.slice(1, -1);
        if (placeholder.toLowerCase().startsWith('storage.')) {
          const storage = this.dataStorages
            .find(d => Number(d.id) === Number(item.storageId));
          const field = placeholder.split('.').pop();
          return storage && storage[field]
            ? storage[field]
            : '';
        }
        const placeholderInfo = SELECTION_PLACEHOLDERS
          .find(({keys}) => keys.includes(placeholder.toUpperCase()));
        if (placeholderInfo && placeholderInfo.render) {
          return placeholderInfo.render(item);
        }
      }).join('');
    };
    return items
      .map(mapItemTemplate)
      .join('\n');
  };

  render () {
    const {style, items} = this.props;
    if (!items || !items.length || !this.templateParts.length) {
      return null;
    }
    return (
      <BashCode
        style={style}
        code={this.code}
      />
    );
  }
}

SelectionInfo.propTypes = {
  items: PropTypes.arrayOf(PropTypes.shape({
    storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    path: PropTypes.string,
    name: PropTypes.string
  })),
  style: PropTypes.object
};

export default SelectionInfo;
