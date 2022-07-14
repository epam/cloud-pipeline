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
import {Button, Icon} from 'antd';
import moment from 'moment-timezone';
import MetadataEntityFilter from '../../../../models/folderMetadata/MetadataEntityFilter';
import classNames from 'classnames';
import {getPredefinedFilterClassName, localDateToUTC} from './predefined-filter-utilities';

function mapColumnName (name) {
  if (name === 'externalId') {
    return 'ID';
  }
  return name;
}

function unmapColumnName (name) {
  if (name === 'ID') {
    return 'externalId';
  }
  return name;
}

function isPredefined (name) {
  return ['externalID'].includes(name);
}

function arraysAreTheSame (array1, array2) {
  const a = [...new Set(array1 || [])].sort();
  const b = [...new Set(array2 || [])].sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function predefinedFiltersAreTheSame (filtersA, filtersB) {
  if (!filtersA && !filtersB) {
    return true;
  }
  if (!filtersA || !filtersB) {
    return false;
  }
  const {
    filters: a
  } = filtersA;
  const {
    filters: b
  } = filtersB;
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    startDateFrom: aStart,
    endDateTo: aEnd,
    ...restA
  } = a;
  const {
    startDateFrom: bStart,
    endDateTo: bEnd,
    ...restB
  } = b;
  if (aStart !== bStart || aEnd !== bEnd) {
    return false;
  }
  const aKeys = Object.keys(restA).sort();
  const bKeys = Object.keys(restB).sort();
  if (aKeys.length !== bKeys.length || !arraysAreTheSame(aKeys, bKeys)) {
    return false;
  }
  for (let i = 0; i < aKeys.length; i++) {
    if (!arraysAreTheSame(restA[aKeys[i]], restB[aKeys[i]])) {
      return false;
    }
  }
  return true;
}

function generateFilterPayload (filter) {
  const {
    startDateFrom,
    endDateTo,
    ...filters
  } = filter ? (filter.filters || {}) : {};
  const start = localDateToUTC(startDateFrom);
  const end = localDateToUTC(endDateTo);
  return {
    filters: Object.entries(filters)
      .map(([key, values]) => ({
        key: unmapColumnName(key),
        values,
        predefined: isPredefined(unmapColumnName(key))
      })),
    startDateFrom: start && start.isValid()
      ? start.format('YYYY-MM-DD HH:mm:ss.SSS')
      : undefined,
    endDateTo: end && end.isValid()
      ? end.format('YYYY-MM-DD HH:mm:ss.SSS')
      : undefined
  };
}

async function getFilterCount (filter, folderId, metadataClass) {
  const metadataRequest = new MetadataEntityFilter();
  await metadataRequest.send({
    page: 1,
    pageSize: 1,
    folderId,
    metadataClass,
    ...generateFilterPayload(filter)
  });
  if (metadataRequest.error) {
    throw new Error(metadataRequest.error);
  }
  return (metadataRequest.value || {}).totalCount || 0;
}

class PredefinedFilterButton extends React.PureComponent {
  state = {
    count: 0,
    countPending: true
  };

  componentDidMount () {
    this.updateCount();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.folderId !== this.props.folderId ||
      prevProps.metadataClass !== this.props.metadataClass ||
      !predefinedFiltersAreTheSame(prevProps.filter, this.props.filter)
    ) {
      this.updateCount();
    }
  }

  updateCount = () => {
    const {
      filter,
      folderId,
      metadataClass
    } = this.props;
    if (!filter) {
      this.setState({
        count: 0,
        countPending: false
      });
    } else {
      this.setState({
        count: 0,
        countPending: true
      }, () => {
        getFilterCount(filter, folderId, metadataClass)
          .catch(() => Promise.resolve(0))
          .then(count => this.setState({
            count,
            countPending: false
          }));
      });
    }
  };

  get isApplied () {
    const {currentFilter, filter} = this.props;
    if (!filter) {
      return false;
    }
    const currentFilterIsEmpty = !currentFilter ||
      (
        !currentFilter.startDateFrom &&
        !currentFilter.endDateTo &&
        (currentFilter.filters || []).length === 0
      );
    const {
      endDateTo,
      startDateFrom,
      ...predefinedFilterFields
    } = (filter || {}).filters || {};
    const predefinedFilterIsEmpty = !endDateTo &&
      !startDateFrom &&
      Object.keys(predefinedFilterFields).length === 0;
    const datesAreTheSame = (a, b) => {
      if (!a && !b) {
        return true;
      }
      if (!a || !b) {
        return false;
      }
      return a.isValid() &&
        b.isValid() &&
        a.get('year') === b.get('year') &&
        a.get('month') === b.get('month') &&
        a.get('date') === b.get('date');
    };
    const startDatesAreTheSame = datesAreTheSame(
      startDateFrom ? localDateToUTC(startDateFrom) : undefined,
      currentFilter.startDateFrom ? moment.utc(currentFilter.startDateFrom) : undefined
    );
    const endDatesAreTheSame = datesAreTheSame(
      endDateTo ? localDateToUTC(endDateTo) : undefined,
      currentFilter.endDateTo ? moment.utc(currentFilter.endDateTo) : undefined
    );
    const createdDateFiltersTheSame = startDatesAreTheSame && endDatesAreTheSame;

    if (currentFilterIsEmpty) {
      return predefinedFilterIsEmpty;
    }
    if (!createdDateFiltersTheSame) {
      return false;
    }
    return arraysAreTheSame(
      (currentFilter.filters || []).map(o => o.key),
      Object.keys(predefinedFilterFields).map(o => unmapColumnName(o))
    ) &&
      (currentFilter.filters || [])
        .every((currentFilterField) => {
          const field = currentFilterField.key;
          const mapped = mapColumnName(field);
          const predefinedFieldFilterValue = predefinedFilterFields[mapped] || [];
          return arraysAreTheSame(currentFilterField.values, predefinedFieldFilterValue);
        });
  }

  onClick = (e) => {
    if (e && e.target) {
      e.target.blur();
    }
    const {
      onClick,
      filter
    } = this.props;
    if (typeof onClick === 'function') {
      if (this.isApplied) {
        onClick(undefined);
        return;
      }
      const filterModel = generateFilterPayload(filter);
      onClick(filterModel);
    }
  };

  getButtonProps = () => {
    const {
      filter,
      className,
      style
    } = this.props;
    if (!filter) {
      return null;
    }
    const {scheme = {}} = filter;
    const {
      icon,
      style: schemeStyle
    } = scheme;
    const applied = this.isApplied;
    return {
      className: classNames(
        className,
        getPredefinedFilterClassName(scheme, applied)
      ),
      style: Object.assign({}, style, schemeStyle),
      icon,
      type: 'default'
    };
  };

  render () {
    const {filter} = this.props;
    if (!filter) {
      return null;
    }
    const {
      count,
      countPending
    } = this.state;
    const {
      name,
      title
    } = filter;
    return (
      <Button
        {...this.getButtonProps()}
        onClick={this.onClick}
        size="small"
      >
        {name || title}
        {
          countPending && (
            <Icon type="loading" style={{marginLeft: 5}} />
          )
        }
        {
          !countPending && (
            <span style={{whiteSpace: 'pre'}}>
              {`: ${count}`}
            </span>
          )
        }
      </Button>
    );
  }
}

PredefinedFilterButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filter: PropTypes.shape({
    name: PropTypes.string,
    scheme: PropTypes.shape({
      icon: PropTypes.string,
      type: PropTypes.string,
      style: PropTypes.object
    }),
    title: PropTypes.string,
    filters: PropTypes.object
  }),
  currentFilter: PropTypes.object,
  onClick: PropTypes.func,
  filterId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  metadataClass: PropTypes.string
};

export default PredefinedFilterButton;
