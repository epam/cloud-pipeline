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
import {inject, observer, PropTypes as mobxPropTypes} from 'mobx-react';
import {computed, toJS} from 'mobx';
import {
  Select,
  Dropdown,
  Icon,
  Pagination,
  Menu,
  Input
} from 'antd';
import displayDate from '../../../../../utils/displayDate';
import highlightText from '../../../../special/highlightText';
import Label from '../label';
import {ticketsFilter} from '../filter';
import styles from './ticket-list.css';

@inject('preferences')
@observer
export default class TicketsList extends React.Component {
  static propTypes = {
    tickets: mobxPropTypes.arrayOrObservableArray,
    onSelectTicket: PropTypes.func,
    onDeleteTicket: PropTypes.func,
    onNavigateBack: PropTypes.func,
    pending: PropTypes.bool,
    hideControls: PropTypes.bool
  };

  componentWillUnmount () {
    ticketsFilter.clear();
  }

  @computed
  get filters () {
    return toJS(ticketsFilter.filters);
  }

  @computed
  get availableStatuses () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return (preferences.gitlabIssueStatuses || [])
        .map(status => status.toLowerCase());
    }
    return [];
  }

  @computed
  get tickets () {
    const {tickets} = this.props;
    return tickets || [];
  }

  @computed
  get filteredTickets () {
    let filtered = this.tickets;
    if (this.filters.statuses.length > 0) {
      filtered = filtered.filter(({labels}) => (labels || [])
        .some(label => this.filters.statuses.includes(label)));
    }
    if (this.filters.title) {
      filtered = filtered.filter(({title}) => (title || '')
        .toLowerCase()
        .includes(this.filters.title.toLowerCase()));
    }
    return filtered;
  }

  onStatusChange = (statuses) => {
    ticketsFilter.setStatuses(statuses);
  };

  onSearch = (event) => {
    ticketsFilter.setTitle(event.target.value);
  };

  renderHeader = () => {
    return (
      <div
        className={classNames(
          styles.ticketContainer,
          styles.header,
          'cp-divider',
          'bottom'
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
            value={this.filters.title}
            onChange={this.onSearch}
            id="tickets-title-filter"
            style={{marginRight: '5px'}}
          />
          <Select
            onChange={this.onStatusChange}
            value={this.filters.statuses}
            style={{width: '250px'}}
            placeholder="Select ticket status"
            mode="multiple"
            id="tickets-statuses-filter"
          >
            {this.availableStatuses.map(status => (
              <Select.Option key={status}>
                {`${status.charAt(0).toUpperCase()}${status.slice(1)}`}
              </Select.Option>
            ))}
          </Select>
        </div>
      </div>
    );
  };

  onSelectMenu = (key, ticket) => {
    const {onDeleteTicket} = this.props;
    if (key === 'delete') {
      onDeleteTicket && onDeleteTicket(ticket.iid);
    }
  };

  onSelectTicket = (id) => {
    const {pending, onSelectTicket} = this.props;
    if (pending || id === undefined) {
      return null;
    }
    onSelectTicket && onSelectTicket(id);
  };

  renderTicket = (ticket) => {
    const {pending, hideControls} = this.props;
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
    const getStatus = (labels) => {
      const [status] = (labels || [])
        .filter(label => this.availableStatuses.includes(label.toLowerCase()));
      return status;
    };
    return (
      <div
        key={ticket.iid}
        className={classNames(
          styles.ticketContainer,
          'cp-divider',
          'bottom',
          'cp-table-element',
          {'cp-table-element-disabled': pending}
        )}
        onClick={() => this.onSelectTicket(ticket.iid)}
      >
        <Label className={styles.status} label={getStatus(ticket.labels)} />
        <div className={styles.title}>
          <b>{highlightText(ticket.title, this.filters.title)}</b>
          <span
            className="cp-text-not-important"
            style={{fontSize: 'smaller'}}
          >
            Opened {displayDate(ticket.created_at, 'D MMM YYYY, HH:mm')} by {ticket.author.username}
          </span>
        </div>
        <div className={styles.controls}>
          {!hideControls ? (
            <Dropdown
              overlay={menu}
              trigger={['click']}
              onClick={e => e.stopPropagation()}
              disabled={pending}
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
    return (
      <div className={classNames(
        styles.container,
        'cp-card-background-color'
      )}>
        <div className={styles.ticketsTable}>
          {this.renderHeader()}
          {this.filteredTickets.map(this.renderTicket)}
        </div>
        <div className={styles.paginationRow}>
          <Pagination
            defaultCurrent={1}
            total={1}
            size="small"
          />
        </div>
      </div>
    );
  }
}
