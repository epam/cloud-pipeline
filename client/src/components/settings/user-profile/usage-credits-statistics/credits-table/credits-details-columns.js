/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import displayDate from '../../../../../utils/displayDate';
import {
  formatEntity,
  formatIncidentValue,
  getIncidentTypeClassName,
  hasActiveEntityFilter,
  INCIDENT_TYPES,
  normalizeRunIds
} from './utils';
import CheckboxFilterDropdown from './checkbox-filter-dropdown';
import DateRangeFilterDropdown from './date-range-filter-dropdown';
import EntityFilterDropdown from './entity-filter-dropdown';

function getCheckboxFilterProps ({
  key,
  filterKey,
  options,
  filters,
  draftFilters,
  filterDropdownVisible,
  onFilterDropdownVisibleChange,
  onDraftFiltersChange,
  onApplyFilter,
  onClearColumnFilter,
  single = false
}) {
  const selected = filters[filterKey] || [];
  const filtered = selected.length > 0;
  return {
    filterDropdown: (
      <CheckboxFilterDropdown
        options={options}
        selected={draftFilters[filterKey] || []}
        single={single}
        onChange={(values) => onDraftFiltersChange({[filterKey]: values})}
        onOk={onApplyFilter(key, (draft) => ({
          [filterKey]: (draft[filterKey] || []).slice()
        }))}
        onClear={onClearColumnFilter(key, filterKey)}
        clearDisabled={!filtered}
      />
    ),
    filterDropdownVisible: filterDropdownVisible[key],
    onFilterDropdownVisibleChange: onFilterDropdownVisibleChange(
      key,
      (appliedFilters) => ({
        [filterKey]: (appliedFilters[filterKey] || []).slice()
      })
    ),
    filtered,
    filteredValue: filtered ? ['filtered'] : []
  };
}

function getDateRangeFilterProps ({
  filters,
  draftFilters,
  filterDropdownVisible,
  onFilterDropdownVisibleChange,
  onDraftFiltersChange,
  onApplyFilter,
  onClearDateFilter,
  onDatePickerOpenChange
}) {
  const filtered = !!(filters.from || filters.to);
  return {
    filterDropdown: (
      <DateRangeFilterDropdown
        from={draftFilters.from}
        to={draftFilters.to}
        onChange={onDraftFiltersChange}
        onOk={onApplyFilter('createdDate', (draft) => ({
          from: draft.from,
          to: draft.to
        }))}
        onClear={onClearDateFilter}
        clearDisabled={!filtered}
        onPickerOpenChange={onDatePickerOpenChange}
      />
    ),
    filterDropdownVisible: filterDropdownVisible.createdDate,
    onFilterDropdownVisibleChange: onFilterDropdownVisibleChange(
      'createdDate',
      (appliedFilters) => ({
        from: appliedFilters.from,
        to: appliedFilters.to
      })
    ),
    filtered,
    filteredValue: filtered ? ['filtered'] : []
  };
}

function getEntityFilterProps ({
  filters,
  draftFilters,
  filterDropdownVisible,
  onFilterDropdownVisibleChange,
  onDraftFiltersChange,
  onApplyFilter,
  onClearEntityFilter
}) {
  const filtered = hasActiveEntityFilter(filters);
  return {
    filterDropdown: (
      <EntityFilterDropdown
        entityIds={draftFilters.entityIds}
        onlyEmpty={draftFilters.onlyEmpty}
        onChange={onDraftFiltersChange}
        onOk={onApplyFilter('entity', (draft) => ({
          entityIds: normalizeRunIds(draft.entityIds),
          onlyEmpty: draft.onlyEmpty === true
        }))}
        onClear={onClearEntityFilter}
        clearDisabled={!filtered}
      />
    ),
    filterDropdownVisible: filterDropdownVisible.entity,
    onFilterDropdownVisibleChange: onFilterDropdownVisibleChange(
      'entity',
      (appliedFilters) => ({
        entityIds: (appliedFilters.entityIds || []).slice(),
        onlyEmpty: appliedFilters.onlyEmpty === true
      })
    ),
    filtered,
    filteredValue: filtered ? ['filtered'] : []
  };
}

export function getCreditsDetailsColumns ({
  rules = [],
  rulesMap = {},
  filters,
  draftFilters,
  filterDropdownVisible,
  onFilterDropdownVisibleChange,
  onDraftFiltersChange,
  onApplyFilter,
  onClearColumnFilter,
  onClearDateFilter,
  onDatePickerOpenChange,
  onClearEntityFilter
}) {
  const filterHandlers = {
    filters,
    draftFilters,
    filterDropdownVisible,
    onFilterDropdownVisibleChange,
    onDraftFiltersChange,
    onApplyFilter,
    onClearColumnFilter
  };
  return [
    {
      key: 'createdDate',
      dataIndex: 'createdDate',
      title: 'Event time',
      ...getDateRangeFilterProps({
        filters,
        draftFilters,
        filterDropdownVisible,
        onFilterDropdownVisibleChange,
        onDraftFiltersChange,
        onApplyFilter,
        onClearDateFilter,
        onDatePickerOpenChange
      }),
      render: (createdDate) => displayDate(createdDate)
    },
    {
      key: 'ruleId',
      dataIndex: 'ruleId',
      title: 'Rule',
      ...getCheckboxFilterProps({
        ...filterHandlers,
        key: 'ruleId',
        filterKey: 'ruleIds',
        single: true,
        options: rules.map((rule) => ({
          text: rule.name,
          value: rule.id
        }))
      }),
      render: (ruleId) => {
        const rule = rulesMap[ruleId];
        return rule ? rule.name : ruleId;
      }
    },
    {
      key: 'entity',
      dataIndex: 'entity',
      title: 'Entity',
      ...getEntityFilterProps({
        filters,
        draftFilters,
        filterDropdownVisible,
        onFilterDropdownVisibleChange,
        onDraftFiltersChange,
        onApplyFilter,
        onClearEntityFilter
      }),
      render: formatEntity
    },
    {
      key: 'message',
      dataIndex: 'message',
      title: 'Message'
    },
    {
      key: 'incidentType',
      dataIndex: 'incidentType',
      title: 'Incident Type',
      ...getCheckboxFilterProps({
        ...filterHandlers,
        key: 'incidentType',
        filterKey: 'incidentTypes',
        options: INCIDENT_TYPES.map((type) => ({
          text: type,
          value: type
        }))
      }),
      render: (incidentType) => (
        <span className={getIncidentTypeClassName(incidentType)}>
          {incidentType}
        </span>
      )
    },
    {
      key: 'value',
      dataIndex: 'value',
      title: 'Value',
      render: (value, record) => (
        <span className={getIncidentTypeClassName(record.incidentType)}>
          {formatIncidentValue(value, record.incidentType)}
        </span>
      )
    }
  ];
}
