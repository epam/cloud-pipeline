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
import {computed} from 'mobx';
import {observer, inject} from 'mobx-react';
import {Alert, Button, Icon, message, Spin} from 'antd';
import moment from 'moment-timezone';
import CommentCard from '../special/comment-card';
import CommentEditor from '../special/comment-editor';
import Label from '../special/label';
import getAuthor from '../special/utilities/get-author';
import GitlabIssueLoad from '../../../../models/gitlab-issues/GitlabIssueLoad';
import GitlabIssueComment from '../../../../models/gitlab-issues/GitlabIssueComment';
import UserName from '../../UserName';
import styles from './ticket.css';
import mainStyles from '../tickets.css';

@inject('preferences')
@inject((stores, props) => {
  const {
    params = {}
  } = props || {};
  const {
    id: ticketId
  } = params;
  return {
    ticketId
  };
})
@observer
class Ticket extends React.Component {
  state = {
    pending: false,
    error: undefined,
    ticket: undefined
  };

  editorRef;

  componentDidMount () {
    (this.fetchTicket)();
  };

  componentDidUpdate (prevProps) {
    if (this.props.ticketId !== prevProps.ticketId) {
      (this.fetchTicket)();
    }
  }

  @computed
  get predefinedLabels () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return (preferences.gitlabIssueStatuses || []);
    }
    return [];
  }

  get comments () {
    const {
      ticket = {}
    } = this.state;
    const {
      comments = []
    } = ticket;
    return comments
      .slice()
      .sort((a, b) => moment.utc(a.created_at) - moment.utc(b.created_at));
  }

  onSaveNewComment = ({attachments, description}) => {
    this.setState({pending: true}, async () => {
      const {ticketId} = this.props;
      const request = new GitlabIssueComment(ticketId);
      const hide = message.loading(`Creating comment...`, 0);
      await request.send({
        attachments,
        body: description
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
        this.setState({
          pending: false
        });
      } else {
        await this.fetchTicket();
        this.scrollToEditor();
      }
    });
  };

  fetchTicketToken = 0;

  fetchTicket = () => new Promise((resolve) => {
    const {
      ticketId
    } = this.props;
    this.fetchTicketToken += 1;
    const token = this.fetchTicketToken;
    if (ticketId) {
      this.setState({
        pending: true
      }, async () => {
        const state = {
          pending: false
        };
        const commit = () => {
          if (token === this.fetchTicketToken) {
            this.setState(state, resolve);
          } else {
            resolve();
          }
        };
        const hide = message.loading(`Fetching ticket #${ticketId}...`, 0);
        try {
          const request = new GitlabIssueLoad(ticketId);
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          }
          state.ticket = request.value || {};
          state.error = undefined;
        } catch (error) {
          state.error = error.message;
        } finally {
          hide();
          commit();
        }
      });
    } else {
      this.setState({
        pending: false,
        error: undefined,
        ticket: undefined
      }, () => resolve());
    }
  });

  scrollToEditor = () => {
    this.editorRef &&
    this.editorRef.scrollIntoView &&
    this.editorRef.scrollIntoView({behavior: 'smooth', block: 'end'});
  };

  renderComments = () => {
    const {
      ticket
    } = this.state;
    return (
      <div
        className={classNames(
          'cp-divider',
          'left',
          styles.commentsContainer
        )}
      >
        {[ticket, ...this.comments]
          .filter(Boolean)
          .filter((comment) => comment.description || comment.body)
          .map((comment, index) => (
            <CommentCard
              key={`comment-${comment.iid || comment.id || index}`}
              comment={comment}
              className={classNames(
                'cp-card-background-color',
                styles.card
              )}
            />
          ))}
      </div>
    );
  };

  renderInfoSection = () => {
    const {
      ticket
    } = this.state;
    const {
      labels = []
    } = ticket || {};
    const filteredLabels = labels
      .filter((aLabel) => this.predefinedLabels.includes(aLabel));
    return (
      <div className={styles.infoSection}>
        <div className={classNames(
          styles.infoBlock,
          styles.row,
          'cp-divider',
          'bottom'
        )}>
          <span>Author:</span>
          <UserName
            style={{marginLeft: 10}}
            userName={getAuthor(ticket)}
            showIcon
          />
        </div>
        {
          filteredLabels.length > 0 && (
            <div
              className={
                classNames(
                  styles.infoBlock,
                  styles.row,
                  'cp-divider',
                  'bottom'
                )
              }
            >
              <span>Status:</span>
              <div className={styles.labelsRow}>
                {
                  filteredLabels.map((label) => (
                    <Label
                      key={label}
                      label={label}
                    />
                  ))
                }
              </div>
            </div>
          )
        }
      </div>
    );
  }

  navigateBack = () => {
    const {
      router
    } = this.props;
    if (router) {
      router.push('/tickets');
    }
  };

  renderHeader = () => {
    const {
      ticket
    } = this.state;
    const {
      ticketId
    } = this.props;
    return (
      <div
        className={mainStyles.headerContainer}
      >
        <Button
          type="secondary"
          onClick={this.navigateBack}
          id="go-back-button"
          size="small"
          className={mainStyles.goBackButton}
        >
          <Icon type="left" />
        </Button>
        <span
          className={mainStyles.heading}
        >
          <b>
            {ticket && ticket.title ? ticket.title : 'Ticket'}
          </b>
          <span
            style={{marginLeft: 5}}
          >
            #{ticketId}
          </span>
        </span>
      </div>
    );
  };

  render () {
    const {
      ticket,
      error,
      pending
    } = this.state;
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
        {
          this.renderHeader()
        }
        {
          error && (
            <Alert
              message={error}
              type="error"
              style={{margin: '5px 0'}}
            />
          )
        }
        {
          !!pending && !ticket && (
            <Spin />
          )
        }
        {
          !pending && !ticket && !error && (
            <Alert
              message="Ticket not found"
              type="error"
              style={{margin: '5px 0'}}
            />
          )
        }
        {
          ticket && (
            <div className={styles.ticketContent}>
              <div className={styles.commentsSection}>
                {this.renderComments()}
                <div
                  ref={(el) => {
                    this.editorRef = el;
                  }}
                >
                  <CommentEditor
                    onSave={this.onSaveNewComment}
                    disabled={pending}
                    isNewComment
                  />
                </div>
              </div>
              {this.renderInfoSection()}
            </div>
          )
        }
      </div>
    );
  }
}

Ticket.propTypes = {
  ticketId: PropTypes.oneOfType(PropTypes.string, PropTypes.number)
};

export default Ticket;
