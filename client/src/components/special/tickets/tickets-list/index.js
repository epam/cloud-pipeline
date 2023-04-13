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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Select,
  Dropdown,
  Icon,
  Pagination,
  Menu,
  Input,
  Spin,
  Alert,
  message,
  Modal
} from 'antd';
import measureTextWidth from '../../../../utils/measure-text-width';
import displayDate from '../../../../utils/displayDate';
import highlightText from '../../highlightText';
import Label from '../special/label';
import GitlabIssuesLoad from '../../../../models/gitlab-issues/GitlabIssuesLoad';
import getAuthor from '../special/utilities/get-author';
import UserName from '../../UserName';
import NewTicketForm from '../special/new-ticket-form';
import GitlabIssueCreate from '../../../../models/gitlab-issues/GitlabIssueCreate';
import GitlabIssueUpdate from '../../../../models/gitlab-issues/GitlabIssueUpdate';
import GitlabIssueDelete from '../../../../models/gitlab-issues/GitlabIssueDelete';
import {
  buildTicketsFiltersQuery,
  parseTicketsFilters,
  ticketsFiltersAreEqual
} from '../special/utilities/routing';
import styles from './ticket-list.css';
import mainStyles from '../tickets.css';

const PAGE_SIZE = 20;

@inject('preferences', 'authenticatedUserInfo')
@inject((stores, props) => {
  const {
    location = {}
  } = props;
  const {
    page,
    search,
    statuses,
    default: defaultFilters
  } = parseTicketsFilters(location.search);
  return {
    page,
    search,
    statuses,
    default: defaultFilters
  };
})
@observer
class TicketsList extends React.Component {
  state = {
    filters: {
      search: '',
      statuses: []
    },
    page: 1,
    error: undefined,
    tickets: [],
    total: 0,
    pending: false,
    newTicketModalVisible: false,
    newTicketPending: false
  };

  componentDidMount () {
    this.fetchCurrentPage();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!ticketsFiltersAreEqual(this.props, prevProps)) {
      this.fetchCurrentPage();
    }
  }

  @computed
  get enableControls () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  @computed
  get predefinedLabels () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return (preferences.gitlabIssueStatuses || []);
    }
    return [];
  }

  get filtersTouched () {
    const {filters} = this.state;
    return filters.search || filters.statuses.length > 0;
  }

  get tableGridTemplate () {
    const {
      tickets
    } = this.state;
    const labelsOnPage = [...new Set((tickets || []).reduce((acc, cur) => {
      acc.push(...(cur.labels || []));
      return acc;
    }, []))].filter(label => this.predefinedLabels.includes(label));
    const [longestWidth = 0] = measureTextWidth(labelsOnPage, '13px')
      .sort((a, b) => b - a);
    const minWidth = 50;
    const statusWidth = `${Math.max(minWidth, longestWidth + 10)}px`;
    return `"status title controls" auto / ${statusWidth} 1fr auto`;
  }

  submitFilters = () => {
    const {
      filters = {}
    } = this.state;
    const {
      search,
      statuses
    } = filters;
    this.changeFilters({
      page: 1,
      search,
      statuses
    }, false);
  };

  onFiltersChange = (field, eventType) => (event) => {
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
    if (value === undefined) {
      return;
    }
    this.setState({
      filters: {
        ...this.state.filters,
        [field]: value
      }
    });
  };

  clearFilters = () => {
    this.changeFilters({
      page: 1,
      search: '',
      statuses: [],
      default: false
    });
  };

  fetchCurrentPageToken = 0;

  fetchCurrentPage = () => {
    const {
      search,
      statuses,
      default: defaultFilters,
      page = 0,
      preferences
    } = this.props;
    this.fetchCurrentPageToken += 1;
    const token = this.fetchCurrentPageToken;
    this.setState({
      pending: true
    }, async () => {
      const state = {
        pending: false
      };
      const commit = () => {
        if (token === this.fetchCurrentPageToken) {
          this.setState(state);
        }
      };
      const request = new GitlabIssuesLoad(page, PAGE_SIZE);
      try {
        await preferences.fetchIfNeededOrWait();
        const payload = {};
        if (search && search.length > 0) {
          payload.search = search;
        }
        let statusesPayload = [];
        if (defaultFilters) {
          const {
            not = false,
            labels: initialLabels = []
          } = preferences.gitlabIssueDefaultFilters || {};
          payload.labelsFilter = preferences.gitlabIssueDefaultFilters;
          statusesPayload = this.predefinedLabels
            .filter((aLabel) => (not && !initialLabels.includes(aLabel)) ||
              (!not && initialLabels.includes(aLabel)));
        } else if (statuses && statuses.length > 0) {
          const correctedStatuses = this.predefinedLabels
            .filter((aLabel) => !(statuses || []).includes(aLabel));
          payload.labelsFilter = {
            labels: correctedStatuses,
            not: true
          };
          statusesPayload = statuses;
        }
        await request.send(payload);
        if (request.error) {
          throw new Error(request.error);
        }
        const {
          elements = [],
          totalCount = 0
        } = request.value || {};
        state.tickets = elements;
        state.total = totalCount;
        state.error = undefined;
        state.filters = {
          search,
          statuses: statusesPayload
        };
      } catch (error) {
        state.error = error.message;
      } finally {
        commit();
      }
    });
  };

  changeFilters = (filters, reFetchPage = true) => {
    const {
      search: currentSearch,
      default: currentDefaultFilters,
      statuses: currentStatuses = [],
      page: currentPage,
      router
    } = this.props;
    const {
      page = currentPage,
      search = currentSearch,
      statuses = currentStatuses,
      default: defaultFilters = currentDefaultFilters
    } = filters || {};
    const filtersPayload = {
      page,
      search,
      default: defaultFilters,
      statuses
    };
    if (
      router &&
      typeof router.push === 'function' &&
      !ticketsFiltersAreEqual(filtersPayload, this.props)
    ) {
      const query = buildTicketsFiltersQuery(filtersPayload);
      const url = query && query.length
        ? `/tickets?${query}`
        : '/tickets';
      router.push(url);
    } else if (reFetchPage) {
      this.fetchCurrentPage();
    }
  };

  onPageChange = (page) => {
    this.changeFilters({page});
  };

  onSelectTicket = (ticket) => {
    const {
      router
    } = this.props;
    if (!ticket || !router) {
      return;
    }
    const query = buildTicketsFiltersQuery(this.props);
    const url = query && query.length
      ? `/tickets/${ticket.iid}?${query}`
      : `/tickets/${ticket.iid}`;
    router.push(url);
  };

  renderTableHeader = () => {
    const {
      filters
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.ticketContainer,
            styles.header,
            'cp-divider',
            'bottom',
            'cp-card-background-color'
          )
        }
        style={{grid: this.tableGridTemplate}}
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
          {
            this.filtersTouched && (
              <a
                className={
                  classNames(
                    styles.tableControl
                  )
                }
                onClick={this.filtersTouched ? this.clearFilters : undefined}
                style={{whiteSpace: 'nowrap'}}
              >
                Clear filters
              </a>
            )
          }
          <Input.Search
            placeholder="Search"
            value={filters.search}
            onChange={this.onFiltersChange('search', 'input')}
            id="tickets-search-filter"
            className={styles.tableControl}
            style={{width: 200}}
            onPressEnter={this.submitFilters}
            onBlur={this.submitFilters}
          />
          <Select
            onChange={this.onFiltersChange('statuses', 'select')}
            value={filters.statuses}
            style={{minWidth: '200px'}}
            className={styles.tableControl}
            placeholder="Select ticket status"
            mode="multiple"
            id="tickets-labels-filter"
            onBlur={this.submitFilters}
          >
            {
              this.predefinedLabels.map(label => (
                <Select.Option key={label}>
                  {label}
                </Select.Option>
              ))
            }
          </Select>
        </div>
      </div>
    );
  };

  renderTicket = (ticket) => {
    const {
      filters,
      pending
    } = this.state;
    const getLabel = (labels) => (labels || [])
      .find(label => this.predefinedLabels.includes(label));
    const currentLabel = getLabel(ticket.labels);
    const menu = (
      <Menu
        onClick={({key}) => this.onSelectNewStatus(key, ticket)}
        selectedKeys={[]}
        style={{cursor: 'default', minWidth: '120px'}}
      >
        <Menu.ItemGroup title="Select new status">
          <Menu.Divider />
          {
            this.predefinedLabels
              .filter(label => label !== currentLabel)
              .map(label => (
                <Menu.Item key={label} style={{cursor: 'pointer'}}>
                  {label}
                </Menu.Item>
              ))
          }
        </Menu.ItemGroup>
      </Menu>
    );
    return (
      <div
        key={ticket.iid}
        className={
          classNames(
            styles.ticketContainer,
            'cp-divider',
            'bottom',
            'cp-table-element',
            {'cp-table-element-disabled': pending}
          )
        }
        style={{grid: this.tableGridTemplate}}
        onClick={() => this.onSelectTicket(ticket)}
      >
        <Label
          className={styles.status}
          label={currentLabel}
        />
        <div
          className={styles.title}
        >
          <b>
            {highlightText(ticket.title, filters.search)}
          </b>
          <span
            className="cp-text-not-important"
            style={{fontSize: 'smaller'}}
          >
            <span style={{marginRight: '5px'}}>
              {`#${ticket.iid}`}
            </span>
            {'Opened '}
            {displayDate(ticket.created_at, 'D MMM YYYY, HH:mm')}
            {' by '}
            <UserName
              showIcon
              userName={getAuthor(ticket)}
            />
          </span>
        </div>
        <div
          className={styles.controls}
          onClick={this.enableControls
            ? (event) => event.stopPropagation()
            : undefined
          }
        >
          {
            this.enableControls && (
              <Dropdown
                overlay={menu}
                trigger={['click']}
                onClick={e => e.stopPropagation()}
                disabled={pending}
              >
                <Icon
                  type="ellipsis"
                  className={styles.controlsIcon}
                />
              </Dropdown>
            )
          }
        </div>
      </div>
    );
  }

  reload = () => {
    this.changeFilters({page: 1});
  };

  openNewTicketModal = () => {
    this.setState({
      newTicketModalVisible: true
    });
  };

  closeNewTicketModal = () => {
    this.setState({
      newTicketModalVisible: false
    });
  };

  createNewTicket = (payload) => {
    this.setState({
      newTicketPending: true
    }, async () => {
      const hide = message.loading('Creating ticket...', 0);
      const request = new GitlabIssueCreate();
      try {
        await request.send(payload);
        if (request.error) {
          throw new Error(request.error);
        }
      } catch (error) {
        message.error(request.error, 5);
      } finally {
        hide();
        if (request.error) {
          this.setState({
            newTicketPending: false
          });
        } else {
          this.setState({
            newTicketPending: false,
            newTicketModalVisible: false
          }, () => this.reload());
        }
      }
    });
  };

  deleteTicketConfirmation = (ticket) => {
    if (!ticket) {
      return;
    }
    const {
      title,
      iid: id
    } = ticket;
    Modal.confirm({
      title: (
        <div>
          Are you sure you want to remove ticket <b>{title}</b> (#{id})?
        </div>
      ),
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.deleteTicket(ticket),
      okText: 'REMOVE',
      cancelText: 'CANCEL'
    });
  };

  deleteTicket = async (ticket) => {
    if (!ticket) {
      return;
    }
    const {
      iid: id
    } = ticket;
    const hide = message.loading(`Deleting ticket ${id}...`, 0);
    const request = new GitlabIssueDelete(id);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.reload();
    }
  };

  updateTicket = (ticket) => {
    if (!ticket) {
      return;
    }
    this.setState({
      pending: true
    }, async () => {
      const hide = message.loading('Updating ticket...', 0);
      const request = new GitlabIssueUpdate();
      try {
        const payload = {
          attachments: ticket.attachments,
          description: ticket.description,
          iid: ticket.iid,
          labels: ticket.labels,
          title: ticket.title
        };
        await request.send(payload);
        if (request.error) {
          throw new Error(request.error);
        }
      } catch (error) {
        message.error(request.error, 5);
      } finally {
        hide();
        if (request.error) {
          this.setState({
            pending: false
          });
        } else {
          this.setState({
            pending: false
          }, () => this.reload());
        }
      }
    });
  };

  onSelectNewStatus = (status, ticket) => {
    if (this.predefinedLabels.find(label => label === status)) {
      const ticketLabels = (ticket.labels || [])
        .filter(label => !this.predefinedLabels.includes(label));
      ticketLabels.push(status);
      const updatedTicket = {
        ...ticket,
        labels: ticketLabels
      };
      this.updateTicket(updatedTicket);
    }
  };

  render () {
    const {
      newTicketModalVisible,
      tickets,
      total = 0,
      pending,
      error,
      newTicketPending
    } = this.state;
    const {
      page
    } = this.props;
    return (
      <div
        className={
          classNames(
            mainStyles.container,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        <div
          className={mainStyles.headerContainer}
        >
          <b className={mainStyles.heading}>
            Tickets
          </b>
          <Button
            onClick={this.reload}
            style={{marginLeft: 5}}
            disabled={pending}
          >
            Refresh
          </Button>
          <Button
            type="primary"
            onClick={this.openNewTicketModal}
            style={{marginLeft: 5}}
          >
            Create new ticket
          </Button>
        </div>
        {
          error && (
            (
              <Alert
                message={`Error retrieving tickets: ${error}`}
                type="error"
              />
            )
          )
        }
        {
          !error && (
            <div
              className={styles.ticketsTable}
            >
              {this.renderTableHeader()}
              <Spin
                spinning={pending}
                wrapperClassName={styles.tableSpin}
              >
                {tickets.map(this.renderTicket)}
              </Spin>
            </div>
          )
        }
        {
          !error && (
            <div className={styles.paginationRow}>
              <Pagination
                total={total}
                size="small"
                disabled={pending}
                current={page}
                onChange={this.onPageChange}
                pageSize={PAGE_SIZE}
              />
            </div>
          )
        }
        <NewTicketForm
          title="Create new ticket"
          onSave={this.createNewTicket}
          onCancel={this.closeNewTicketModal}
          pending={newTicketPending}
          renderAsModal
          modalVisible={newTicketModalVisible}
        />
      </div>
    );
  }
}

export default TicketsList;
