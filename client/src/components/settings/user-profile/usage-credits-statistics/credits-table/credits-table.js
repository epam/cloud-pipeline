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
import UsageCreditsEventsFilterMock from '../../../../../models/usage/UsageCreditsEventsFilterMock';
import UsageCreditsRulesMock from '../../../../../models/usage/UsageCreditsRulesMock';
import {
  applyClientEntityFilters,
  DEFAULT_PAGE_SIZE,
  EMPTY_DRAFT_FILTERS,
  EMPTY_FILTERS,
  exportCreditsEventsToCSV,
  formatDate,
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
      timestamp: false,
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
    const request = new UsageCreditsRulesMock();
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
    const {from, to, showEmpty} = filters;
    this.setState({pending: true});
    const request = new UsageCreditsEventsFilterMock(
      formatDate(from),
      formatDate(to)
    );
    await request.send(getEventsFilterPayload({
      user,
      filters,
      page,
      pageSize: this.pageSize
    }));
    if (request.loaded && request.value) {
      const elements = applyClientEntityFilters(request.value.elements || [], showEmpty)
        .map((item, index) => ({
          ...item,
          key: `${item.ruleId}-${item.entity && item.entity.id}-${index}`
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
          filters,
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
        timestamp: false,
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
    // DatePicker calendar is a portal; ignore outside-click close while it is open
    if (key === 'timestamp' && !visible && this._datePickerOpen) {
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
        timestamp: false
      },
      page: 1
    }, this.loadEvents);
  };

  clearEntityFilter = () => {
    const {filters, draftFilters, filterDropdownVisible} = this.state;
    this.setState({
      filters: {
        ...filters,
        entityId: '',
        showEmpty: true
      },
      draftFilters: {
        ...draftFilters,
        entityId: '',
        showEmpty: true
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
