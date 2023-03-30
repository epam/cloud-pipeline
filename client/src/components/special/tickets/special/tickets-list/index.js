/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {
  Button,
  Select,
  Dropdown,
  Icon,
  Pagination,
  Menu,
  Input,
  Spin,
  message,
  Alert
} from 'antd';
import displayDate from '../../../../../utils/displayDate';
import highlightText from '../../../../special/highlightText';
import Label from '../label';
import GitlabIssuesLoad from '../../../../../models/gitlab-issues/GitlabIssuesLoad';
import styles from './ticket-list.css';

const PAGE_SIZE = 20;

@inject('preferences')
@observer
export default class TicketsList extends React.Component {
  static propTypes = {
    refreshTokenId: PropTypes.number,
    onSelectTicket: PropTypes.func,
    onDeleteTicket: PropTypes.func,
    onNavigateBack: PropTypes.func,
    pending: PropTypes.bool,
    hideControls: PropTypes.bool
  };

  state = {
    filters: {
      search: '',
      labels: []
    },
    page: 1,
    pending: false
  }

  _filtersRefreshTimeout;

  @observable
  ticketsRequest;

  componentDidMount () {
    this.fetchTickets();
  }

  componentDidUpdate (prevProps) {
    if (this.props.refreshTokenId !== prevProps.refreshTokenId) {
      this.fetchTickets();
    }
  }

  componentWillUnmount () {
    if (this._filtersRefreshTimeout) {
      clearTimeout(this._filtersRefreshTimeout);
    }
  }

  get pending () {
    return this.props.pending || this.state.pending;
  }

  @computed
  get tickets () {
    if (
      this.ticketsRequest &&
      this.ticketsRequest.loaded &&
      this.ticketsRequest.value
    ) {
      return this.ticketsRequest.value.elements || [];
    }
    return [];
  }

  @computed
  get totalTickets () {
    if (
      this.ticketsRequest &&
      this.ticketsRequest.loaded &&
      this.ticketsRequest.value
    ) {
      return this.ticketsRequest.value.totalCount || 0;
    }
    return 0;
  }

  @computed
  get predefinedLabels () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return (preferences.gitlabIssueStatuses || [])
        .map(status => status.toLowerCase());
    }
    return [];
  }

  get filtersTouched () {
    const {filters} = this.state;
    return filters.search || filters.labels.length > 0;
  }

  onFiltersChange = (field, eventType) => event => {
    let value;
    switch (eventType) {
      case 'checkbox':
        value = event.target.checked;
        break;
      case 'input':
        value = event.target.value;
        break;
      case 'select':
        value = event;
        break;
      default:
        value = undefined;
    }
    this.setState({
      page: 1,
      filters: {
        ...this.state.filters,
        [field]: value
      }
    }, () => {
      if (this._filtersRefreshTimeout) {
        clearTimeout(this._filtersRefreshTimeout);
      }
      if (value !== undefined) {
        this._filtersRefreshTimeout = setTimeout(() => {
          this.fetchTickets();
        }, 600);
      }
    });
  };

  clearFilters = () => {
    this.setState({
      page: 1,
      filters: {
        search: '',
        labels: []
      }
    }, () => {
      this.fetchTickets();
    });
  };

  fetchTickets = (page = 1) => {
    this.setState({
      page,
      pending: true
    }, async () => {
      const {filters} = this.state;
      this.ticketsRequest = new GitlabIssuesLoad(page, PAGE_SIZE);
      await this.ticketsRequest.send(filters);
      if (this.ticketsRequest.error) {
        message.error(this.ticketsRequest.error, 5);
      }
      this.setState({pending: false});
    });
  };

  onPageChange = page => {
    this.setState({page}, () => {
      const {page} = this.state;
      this.fetchTickets(page);
    });
  };

  onSelectMenu = (key, ticket) => {
    const {onDeleteTicket} = this.props;
    if (key === 'delete') {
      onDeleteTicket && onDeleteTicket(ticket.iid);
    }
  };

  onSelectTicket = (id) => {
    const {onSelectTicket} = this.props;
    if (this.pending || id === undefined) {
      return null;
    }
    onSelectTicket && onSelectTicket(id);
  };

  renderHeader = () => {
    const {filters} = this.state;
    return (
      <div
        className={classNames(
          styles.ticketContainer,
          styles.header,
          'cp-divider',
          'bottom',
          'cp-card-background-color'
        )}
      >
        <b
          className={styles.status}
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            fontSize: 'revert'
          }}
        >
          Status
        </b>
        <b
          className={styles.title}
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            flexDirection: 'row'
          }}
        >
          Title
        </b>
        <div className={styles.controls}>
          <Input.Search
            placeholder="Search by tickets title"
            value={filters.search}
            onChange={this.onFiltersChange('search', 'input')}
            id="tickets-search-filter"
            className={styles.tableControl}
            style={{minWidth: '200px'}}
          />
          <Select
            onChange={this.onFiltersChange('labels', 'select')}
            value={filters.labels}
            style={{minWidth: '200px'}}
            className={styles.tableControl}
            placeholder="Select ticket status"
            mode="multiple"
            id="tickets-labels-filter"
          >
            {this.predefinedLabels.map(label => (
              <Select.Option key={label}>
                {`${label.charAt(0).toUpperCase()}${label.slice(1)}`}
              </Select.Option>
            ))}
          </Select>
          <Button
            className={classNames(
              styles.tableControl,
              styles.clearButton,
              {[styles.hidden]: !this.filtersTouched}
            )}
            onClick={this.clearFilters}
            disabled={!this.filtersTouched}
          >
            <Icon type="close" />
          </Button>
        </div>
      </div>
    );
  };

  renderTicket = (ticket) => {
    const {hideControls} = this.props;
    const {filters} = this.state;
    const menu = (
      <Menu
        onClick={({key}) => this.onSelectMenu(key, ticket)}
        selectedKeys={[]}
        style={{cursor: 'pointer'}}
      >
        <Menu.Item key="close">
          Close ticket
        </Menu.Item>
        <Menu.Item key="delete">
          Delete
        </Menu.Item>
      </Menu>
    );
    const getLabel = (labels) => {
      const [label] = (labels || [])
        .filter(label => this.predefinedLabels.includes(label.toLowerCase()));
      return label;
    };
    return (
      <div
        key={ticket.iid}
        className={classNames(
          styles.ticketContainer,
          'cp-divider',
          'bottom',
          'cp-table-element',
          {'cp-table-element-disabled': this.pending}
        )}
        onClick={() => this.onSelectTicket(ticket.iid)}
      >
        <Label className={styles.status} label={getLabel(ticket.labels)} />
        <div className={styles.title}>
          <b>{highlightText(ticket.title, filters.search)}</b>
          <span
            className="cp-text-not-important"
            style={{fontSize: 'smaller'}}
          >
            Opened {displayDate(ticket.created_at, 'D MMM YYYY, HH:mm')} by {ticket.author.name}
          </span>
        </div>
        <div className={styles.controls}>
          {!hideControls ? (
            <Dropdown
              overlay={menu}
              trigger={['click']}
              onClick={e => e.stopPropagation()}
              disabled={this.pending}
            >
              <Icon
                type="ellipsis"
                style={{
                  cursor: 'pointer',
                  marginRight: 10,
                  fontSize: 'large',
                  fontWeight: 'bold'
                }}
              />
            </Dropdown>
          ) : null}
        </div>
      </div>
    );
  }

  render () {
    const {page} = this.state;
    if (this.ticketsRequest && this.ticketsRequest.error) {
      return (
        <Alert
          message="Error retrieving tickets"
          type="error"
        />
      );
    }
    return (
      <div className={classNames(
        styles.container,
        'cp-card-background-color'
      )}>
        <div
          className={styles.ticketsTable}
        >
          {this.renderHeader()}
          <Spin
            spinning={this.pending}
            wrapperClassName={styles.tableSpin}
          >
            {this.tickets.map(this.renderTicket)}
          </Spin>
        </div>
        <div className={styles.paginationRow}>
          <Pagination
            total={this.totalTickets}
            size="small"
            disabled={this.pending}
            current={page}
            onChange={this.onPageChange}
            pageSize={PAGE_SIZE}
          />
        </div>
      </div>
    );
  }
}
