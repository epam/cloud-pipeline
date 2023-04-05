/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable} from 'mobx';
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

const MINIMUM_SEARCH_LENGTH = 3;
const touched = observable([]);

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
  const enabled = new Set(value || []);
  const recentActivity = new Set([...enabled, ...touched]);
  const filteredImages = imagesSorted.filter(({value, registry, group}) => {
    const targets = [value, registry, group].filter(Boolean);
    return searchString.length >= MINIMUM_SEARCH_LENGTH &&
      !recentActivity.has(value) &&
      targets.some(string => string.toLowerCase().includes(searchString));
  });
  const onChangeSelection = (dockerImage) => (event) => {
    if (event.target.checked && !enabled.has(dockerImage)) {
      touched.push(dockerImage);
      onChange([...enabled, dockerImage]);
    } else if (!event.target.checked && enabled.has(dockerImage)) {
      touched.push(dockerImage);
      onChange([...enabled].filter((s) => s !== dockerImage));
    }
  };
  const onSearchChanged = (event) => {
    if (typeof onSearch === 'function') {
      onSearch(event.target.value);
    }
  };
  const clearTouched = () => touched.splice(0, touched.length);
  const onOkClicked = () => {
    onOk();
  };
  const onClearClicked = () => {
    clearTouched();
    onClear();
  };
  const renderFilterField = (image) => {
    const checked = enabled.has(image.value);
    return (
      <Row key={image.value} style={{margin: 5}}>
        <Checkbox
          onChange={onChangeSelection(image.value)}
          checked={checked}
        >
          <span>{image.registry}</span>
          <Icon type="right" />
          <span>{image.group}</span>
          <Icon type="right" />
          <span>{image.image}</span>
        </Checkbox>
      </Row>
    );
  };
  return (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          styles.docker,
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
      {filteredImages.length > 0 ? (
        <Row style={{overflowY: 'auto'}}>
          {filteredImages.map(renderFilterField)}
        </Row>
      ) : (
        null
      )}
      {recentActivity.size > 0 ? (
        <Row
          className={classNames(
            styles.activeFilters,
            {'cp-divider top': filteredImages.length > 0}
          )}
        >
          <div
            className={classNames(
              styles.activeFiltersHeading,
              'cp-card-background-color'
            )}
          >
            Active filters:
          </div>
          {imagesSorted
            .filter(({value}) => recentActivity.has(value))
            .map(renderFilterField)
          }
        </Row>
      ) : null}
      <Row
        type="flex"
        justify="space-between"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOkClicked}>OK</a>
        <a onClick={onClearClicked}>Clear</a>
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
