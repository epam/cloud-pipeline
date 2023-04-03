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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {
  Checkbox,
  Icon,
  Input,
  Row
} from 'antd';
import {
  getFiltersState,
  onFilterDropdownVisibilityChangedGenerator,
  onFilterGenerator
} from './state-utilities';
import registryName from '../../../tools/registryName';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

function DockersFilterComponent (
  {
    dockerRegistries: dockerRegistriesRequest,
    search,
    onSearch,
    value,
    onChange,
    onOk,
    onClear
  }
) {
  if (!dockerRegistriesRequest) {
    return null;
  }
  const {
    pending,
    value: dockersRegistriesValue = {},
    loaded
  } = dockerRegistriesRequest;
  if (pending && !loaded) {
    return (
      <div
        className={
          classNames(
            styles.filterPopoverContainer,
            'cp-filter-popover-container'
          )
        }
      >
        <Icon type="loading" />
      </div>
    );
  }
  const images = [];
  const registries = dockersRegistriesValue.registries || [];
  for (let i = 0; i < registries.length; i++) {
    const groups = registries[i].groups || [];
    for (let j = 0; j < groups.length; j++) {
      const tools = (groups[j].tools || []).map(t => ({
        registry: registryName(registries[i]),
        group: groups[j].name,
        image: t.image.split('/').pop(),
        value: `${registries[i].path}/${t.image}`
      }));
      images.push(...tools);
    }
  }
  const imagesSorted = images.sort((a, b) => a.value.localeCompare(b.value));
  const searchString = (search || '').toLowerCase();
  const filterImages = (image) => {
    return searchString.length === 0 ||
      image.value.toLowerCase().includes(searchString) ||
      (image.registry || '').toLowerCase().includes(searchString) ||
      (image.group || '').toLowerCase().includes(searchString);
  };
  const enabled = new Set(value || []);
  const onChangeSelection = (dockerImage) => (event) => {
    if (event.target.checked && !enabled.has(dockerImage)) {
      onChange([...enabled, dockerImage]);
    } else if (!event.target.checked && enabled.has(dockerImage)) {
      onChange([...enabled].filter((s) => s !== dockerImage));
    }
  };
  const onSearchChanged = (event) => {
    if (typeof onSearch === 'function') {
      onSearch(event.target.value);
    }
  };
  return (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          'cp-filter-popover-container'
        )
      }
    >
      <Row>
        <Input.Search
          value={search}
          placeholder="Filter"
          onChange={onSearchChanged} />
      </Row>
      <Row>
        <div style={{maxHeight: 400, overflowY: 'auto'}}>
          {
            imagesSorted
              .filter(filterImages)
              .map((image) => (
                <Row
                  style={{margin: 5}}
                  key={image.value}
                >
                  <Checkbox
                    onChange={onChangeSelection(image.value)}
                    checked={enabled.has(image.value)}
                  >
                    <span>{image.registry}</span>
                    <Icon type="right" />
                    <span>{image.group}</span>
                    <Icon type="right" />
                    <span>{image.image}</span>
                  </Checkbox>
                </Row>
              ))
          }
        </div>
      </Row>
      <Row
        type="flex"
        justify="space-between"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOk}>OK</a>
        <a onClick={onClear}>Clear</a>
      </Row>
    </div>
  );
}

DockersFilterComponent.propTypes = {
  search: PropTypes.string,
  onSearch: PropTypes.func,
  value: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onChange: PropTypes.func,
  onOk: PropTypes.func,
  onClear: PropTypes.func
};

const DockersFilter = inject('dockerRegistries')(observer(DockersFilterComponent));

DockersFilter.propTypes = {
  search: PropTypes.string,
  onSearch: PropTypes.func,
  value: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onChange: PropTypes.func,
  onOk: PropTypes.func,
  onClear: PropTypes.func
};

function getColumnFilter (state, setState) {
  const parameter = 'dockerImages';
  const onFilterDropdownVisibleChange = onFilterDropdownVisibilityChangedGenerator(
    parameter,
    state,
    setState
  );
  const {
    value,
    visible: filterDropdownVisible,
    onChange,
    filtered,
    search,
    onSearch
  } = getFiltersState(parameter, state, setState);
  const onFilter = onFilterGenerator(parameter, state, setState);
  const clear = () => onFilter(undefined);
  const onOk = () => onFilter(value);
  return {
    filterDropdown: (
      <DockersFilter
        search={search}
        onSearch={onSearch}
        value={value}
        onChange={onChange}
        onOk={onOk}
        onClear={clear}
      />
    ),
    filterDropdownVisible,
    filtered,
    onFilterDropdownVisibleChange
  };
}

const getColumn = () => ({
  title: 'Docker image',
  dataIndex: 'dockerImage',
  key: 'dockerImages',
  className: styles.runRowDockerImage,
  render: (dockerImage, run) => {
    if (dockerImage) {
      const parts = dockerImage.split('/');
      return (
        <RunLoadingPlaceholder run={run} empty>
          <span>{parts[parts.length - 1]}</span>
        </RunLoadingPlaceholder>
      );
    }
    return undefined;
  }
});

export {
  getColumn,
  getColumnFilter
};
