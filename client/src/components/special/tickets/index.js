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
  message
} from 'antd';
import GitlabIssuesLoad from '../../../models/gitlab-issues/GitlabIssuesLoad';
import GitlabIssueDelete from '../../../models/gitlab-issues/GitlabIssueDelete';
import GitlabIssueCreate from '../../../models/gitlab-issues/GitlabIssueCreate';
import {TicketsList, Ticket, NewTicketForm} from './special';
import styles from './tickets-browser.css';

const MODES = {
  list: 'list',
  editTicket: 'editTicket',
  createNewTicket: 'createNewTicket'
};

const ticketsRequest = new GitlabIssuesLoad();

@inject((stores, {params}) => {
  return {
    tickets: ticketsRequest,
    ticketId: params.id
  };
})
@observer
export default class TicketsBrowser extends React.Component {
  state = {
    pending: false
  }

  get mode () {
    const {ticketId} = this.props;
    if (ticketId === 'new') {
      return MODES.createNewTicket;
    }
    if (ticketId !== undefined && ticketId !== 'new') {
      return MODES.editTicket;
    }
    return MODES.list;
  }

  @computed
  get tickets () {
    const {tickets} = this.props;
    if (tickets.loaded) {
      return tickets.value || [];
    }
    return [];
  }

  @computed
  get ticketId () {
    const {ticketId} = this.props;
    return ticketId;
  }

  createTicket = (payload, shouldNavigateBack = false) => {
    const {tickets} = this.props;
    this.setState({pending: true}, async () => {
      const request = new GitlabIssueCreate();
      const hide = message.loading(`Creating ticket...`, 0);
      await request.send(payload);
      await tickets.fetch();
      hide();
      this.setState({pending: false}, () => {
        if (request.error) {
          message.error(request.error, 5);
        } else {
          shouldNavigateBack && this.navigateBack();
        }
      });
    });
  };

  deleteTicket = (id, shouldNavigateBack = false) => {
    const {tickets} = this.props;
    if (!id) {
      return null;
    }
    this.setState({pending: true}, async () => {
      const request = new GitlabIssueDelete(id);
      const hide = message.loading(`Deleting ticket ${id}...`, 0);
      await request.fetch();
      await tickets.fetch();
      hide();
      this.setState({pending: false}, () => {
        if (request.error) {
          message.error(request.error, 5);
        } else {
          shouldNavigateBack && this.navigateBack();
        }
      });
    });
  };

  onCreateNewTicketClick = () => {
    const {router} = this.props;
    router && router.push(`/tickets/new`);
  };

  onSelectTicket = (iid) => {
    const {router} = this.props;
    router && router.push(`/tickets/${iid}`);
  };

  navigateBack = () => {
    const {router} = this.props;
    router && router.push(`/tickets`);
  };

  renderHeader = () => {
    const goBackButton = (
      <Button
        type="secondary"
        onClick={this.navigateBack}
        key="goBackButton"
      >
        Go back to tickets list
      </Button>
    );
    const content = {
      [MODES.createNewTicket]: [
        <b className={styles.heading} key="new">
          Create new ticket
        </b>,
        goBackButton
      ],
      [MODES.editTicket]: [
        <b className={styles.heading} key="edit">
          Edit ticket
        </b>,
        goBackButton
      ],
      [MODES.list]: [
        <b className={styles.heading} key="list">
          Tickets list
        </b>,
        <Button
          type="primary"
          onClick={this.onCreateNewTicketClick}
          key="createNewButton"
        >
          Create new ticket
        </Button>
      ]
    };
    return (
      <div
        className={classNames(
          'cp-divider',
          'bottom',
          styles.headerContainer
        )}
      >
        {content[this.mode] || null}
      </div>);
  };

  renderContent = () => {
    const {pending} = this.state;
    const content = {
      [MODES.createNewTicket]: (
        <NewTicketForm
          key="newTicketForm"
          onSave={this.createTicket}
        />
      ),
      [MODES.editTicket]: (
        <Ticket
          ticketId={this.ticketId}
          key="ticket"
          pending={pending}
          onNavigateBack={this.navigateBack}
          onSaveComment={this.createComment}
        />
      ),
      [MODES.list]: (
        <TicketsList
          tickets={this.tickets}
          key="ticketsList"
          onSelectTicket={this.onSelectTicket}
          onDeleteTicket={this.deleteTicket}
          onNavigateBack={this.navigateBack}
          pending={pending}
          hideControls
        />
      )
    };
    return content[this.mode] || null;
  };

  render () {
    return (
      <div
        className={
          classNames(
            styles.container,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        {this.renderHeader()}
        {this.renderContent()}
      </div>
    );
  }
}
