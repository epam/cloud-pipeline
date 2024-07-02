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
import {
  Button,
  message,
  Icon
} from 'antd';
import GitlabIssueCreate from '../../../models/gitlab-issues/GitlabIssueCreate';
import NewTicketForm from './special/new-ticket-form';
import styles from './tickets.css';

class NewTicketPage extends React.Component {
  state = {
    pending: false
  }

  createTicket = (payload) => {
    this.setState({pending: true}, async () => {
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
        this.setState({
          pending: false
        }, () => {
          if (!request.error) {
            this.navigateBack();
          }
        });
      }
    });
  };

  navigateBack = () => {
    const {router} = this.props;
    router && router.push(`/tickets`);
  };

  render () {
    const {
      pending
    } = this.state;
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
        <div
          className={styles.headerContainer}
        >
          <Button
            type="secondary"
            onClick={this.navigateBack}
            key="goBackButton"
            id="go-back-button"
            size="small"
            className={styles.goBackButton}
          >
            <Icon type="left" />
          </Button>
          <b
            className={styles.heading}
          >
            Create new ticket
          </b>
        </div>
        <NewTicketForm
          pending={pending}
          onSave={this.createTicket}
        />
      </div>
    );
  }
}

export default NewTicketPage;
