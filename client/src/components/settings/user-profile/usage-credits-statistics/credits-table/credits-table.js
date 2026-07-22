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
import {message, Table} from 'antd';
import UsageCreditsEventsFilter from '../../../../../models/usage/UsageCreditsEventsFilter';
import UsageCreditsRules from '../../../../../models/usage/UsageCreditsRules';
import {
  DEFAULT_PAGE_SIZE,
  EMPTY_DRAFT_FILTERS,
  EMPTY_FILTERS,
  exportCreditsEventsToCSV,
  getEventsFilterPayload,
  hasActiveCreditsDetailsFilters
} from './utils';
import {getCreditsDetailsColumns} from './credits-details-columns';
import CreditsTableHeader from './credits-table-header';
import styles from './credits-table.css';

export default class CreditsTable extends React.Component {
  state = {
    filters: {...EMPTY_FILTERS},
    draftFilters: {...EMPTY_DRAFT_FILTERS},
    filterDropdownVisible: {
      createdDate: false,
      ruleId: false,
      entity: false,
      incidentType: false
    },
    rules: [],
    rulesMap: {},
    page: 1,
    pending: false,
    exportPending: false,
    elements: [],
    totalCount: 0
  };

  componentDidMount () {
    this.loadRules().then(this.loadEvents);
  }

  componentDidUpdate (prevProps) {
    const prevUserId = prevProps.user && prevProps.user.id;
    const userId = this.props.user && this.props.user.id;
    if (prevUserId !== userId) {
      this.setState({page: 1}, this.loadEvents);
    }
  }

  get pageSize () {
    return this.props.pageSize || DEFAULT_PAGE_SIZE;
  }

  loadRules = async () => {
    const request = new UsageCreditsRules();
    await request.fetch();
    if (request.loaded && request.value) {
      const rules = request.value || [];
      const rulesMap = rules.reduce((acc, rule) => {
        acc[rule.id] = rule;
        return acc;
      }, {});
      this.setState({rules, rulesMap});
    }
  };

  loadEvents = async () => {
    const {user} = this.props;
    const {filters, page} = this.state;
    this.setState({pending: true});
    const request = new UsageCreditsEventsFilter();
    await request.send(getEventsFilterPayload({
      user,
      filters,
      page,
      pageSize: this.pageSize
    }));
    if (request.loaded && request.value) {
      const elements = (request.value.elements || [])
        .map((item, index) => ({
          ...item,
          key: item.id !== undefined && item.id !== null
            ? item.id
            : `${item.ruleId}-${item.entity && item.entity.id}-${index}`
        }));
      this.setState({
        elements,
        totalCount: request.value.totalCount || 0,
        pending: false
      });
      return;
    }
    this.setState({pending: false});
  };

  exportToCSV = () => {
    if (this.state.exportPending) {
      return;
    }
    this.setState({exportPending: true}, async () => {
      const hide = message.loading('Exporting usage credits...', 0);
      try {
        const {user} = this.props;
        const {filters} = this.state;
        await exportCreditsEventsToCSV({
          payload: getEventsFilterPayload({
            user,
            filters
          })
        });
      } catch (error) {
        message.error(error.message, 5);
      } finally {
        hide();
        this.setState({exportPending: false});
      }
    });
  };

  onResetFilters = () => {
    this.setState({
      filters: {...EMPTY_FILTERS},
      draftFilters: {...EMPTY_DRAFT_FILTERS},
      filterDropdownVisible: {
        createdDate: false,
        ruleId: false,
        entity: false,
        incidentType: false
      },
      page: 1
    }, this.loadEvents);
  };

  onTableChange = (pagination) => {
    const nextPage = pagination && pagination.current
      ? pagination.current
      : this.state.page;
    this.setState({page: nextPage}, this.loadEvents);
  };

  onFilterDropdownVisibleChange = (key, getDraftPatch) => (visible) => {
    if (key === 'createdDate' && !visible && this._datePickerOpen) {
      return;
    }
    const {filters, filterDropdownVisible, draftFilters} = this.state;
    this.setState({
      filterDropdownVisible: {
        ...filterDropdownVisible,
        [key]: visible
      },
      draftFilters: visible && getDraftPatch
        ? {
          ...draftFilters,
          ...getDraftPatch(filters)
        }
        : draftFilters
    });
  };

  onDraftFiltersChange = (patch) => {
    this.setState({
      draftFilters: {
        ...this.state.draftFilters,
        ...patch
      }
    });
  };

  applyFilter = (key, getFiltersPatch) => () => {
    const {filters, draftFilters, filterDropdownVisible} = this.state;
    this.setState({
      filters: {
        ...filters,
        ...getFiltersPatch(draftFilters)
      },
      filterDropdownVisible: {
        ...filterDropdownVisible,
        [key]: false
      },
      page: 1
    }, this.loadEvents);
  };

  clearColumnFilter = (key, filterKey) => () => {
    const {filters, draftFilters, filterDropdownVisible} = this.state;
    this.setState({
      filters: {
        ...filters,
        [filterKey]: []
      },
      draftFilters: {
        ...draftFilters,
        [filterKey]: []
      },
      filterDropdownVisible: {
        ...filterDropdownVisible,
        [key]: false
      },
      page: 1
    }, this.loadEvents);
  };

  onDatePickerOpenChange = (open) => {
    this._datePickerOpen = open;
  };

  clearDateFilter = () => {
    const {filters, draftFilters, filterDropdownVisible} = this.state;
    this.setState({
      filters: {
        ...filters,
        from: undefined,
        to: undefined
      },
      draftFilters: {
        ...draftFilters,
        from: undefined,
        to: undefined
      },
      filterDropdownVisible: {
        ...filterDropdownVisible,
        createdDate: false
      },
      page: 1
    }, this.loadEvents);
  };

  clearEntityFilter = () => {
    const {filters, draftFilters, filterDropdownVisible} = this.state;
    this.setState({
      filters: {
        ...filters,
        entityIds: [],
        onlyEmpty: false
      },
      draftFilters: {
        ...draftFilters,
        entityIds: [],
        onlyEmpty: false
      },
      filterDropdownVisible: {
        ...filterDropdownVisible,
        entity: false
      },
      page: 1
    }, this.loadEvents);
  };

  getColumns = () => {
    const {
      rules,
      rulesMap,
      filters,
      draftFilters,
      filterDropdownVisible
    } = this.state;
    return getCreditsDetailsColumns({
      rules,
      rulesMap,
      filters,
      draftFilters,
      filterDropdownVisible,
      onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange,
      onDraftFiltersChange: this.onDraftFiltersChange,
      onApplyFilter: this.applyFilter,
      onClearColumnFilter: this.clearColumnFilter,
      onClearDateFilter: this.clearDateFilter,
      onDatePickerOpenChange: this.onDatePickerOpenChange,
      onClearEntityFilter: this.clearEntityFilter
    });
  };

  render () {
    const {
      filters,
      pending,
      elements,
      totalCount,
      page
    } = this.state;
    const canResetAll = hasActiveCreditsDetailsFilters(filters);
    const {exportPending} = this.state;
    return (
      <div className={styles.container}>
        <CreditsTableHeader
          canResetAll={canResetAll}
          exportPending={exportPending}
          onResetFilters={this.onResetFilters}
          onExportToCSV={this.exportToCSV}
        />
        <Table
          size="small"
          rowKey="key"
          columns={this.getColumns()}
          dataSource={elements}
          loading={pending}
          onChange={this.onTableChange}
          pagination={{
            current: page,
            pageSize: this.pageSize,
            total: totalCount
          }}
        />
      </div>
    );
  }
}
