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
import {computed, observable} from 'mobx';
import {observer} from 'mobx-react';
import {Alert, message, Spin} from 'antd';
import moment from 'moment-timezone';
import {CommentCard, CommentEditor, Label} from '../index.js';
import GitlabIssueLoad from '../../../../../models/gitlab-issues/GitlabIssueLoad';
import GitlabIssueComment from '../../../../../models/gitlab-issues/GitlabIssueComment';
import styles from './ticket.css';

@observer
export default class Ticket extends React.Component {
  static propTypes = {
    ticketId: PropTypes.oneOfType(PropTypes.string, PropTypes.number),
    pending: PropTypes.bool,
    onNavigateBack: PropTypes.func,
    onSaveComment: PropTypes.func
  };

  state = {
    editComment: undefined,
    pending: false
  }

  @observable
  ticketRequest;

  componentDidMount () {
    const {ticketId} = this.props;
    this.fetchTicket(ticketId);
  };

  componentDidUpdate (prevProps) {
    if (this.props.ticketId !== prevProps.ticketId) {
      this.fetchTicket(this.props.ticketId);
    }
  }

  @computed
  get ticket () {
    if (this.ticketRequest && this.ticketRequest.loaded) {
      return (this.ticketRequest || {}).value;
    }
    return null;
  }

  @computed
  get comments () {
    if (this.ticket) {
      return [...(this.ticket.comments || [])]
        .sort((a, b) => moment.utc(a.created_at) - moment.utc(b.created_at));
    }
    return [];
  }

  get pending () {
    return this.props.pending || this.state.pending;
  }

  onSelectCommentMenu = (key, comment) => {
    if (key === 'edit') {
      return this.setState({editComment: comment.iid || comment.id});
    }
  };

  onCancelEditing = () => {
    this.setState({editComment: undefined});
  };

  onSaveCommentEditing = () => {
    // API does not support comment editing at the moment
  };

  onSaveNewComment = ({description}) => {
    this.setState({pending: true}, async () => {
      const {iid} = this.ticket;
      const request = new GitlabIssueComment(iid);
      const hide = message.loading(`Creating comment...`, 0);
      await request.send({body: description});
      await this.ticketRequest.fetch();
      hide();
      this.setState({pending: false}, () => {
        if (request.error) {
          message.error(request.error, 5);
          this.ticketRequest = undefined;
        }
      });
    });
  };

  fetchTicket = (ticketId) => {
    if (!ticketId) {
      return;
    }
    this.setState({pending: true}, async () => {
      this.ticketRequest = new GitlabIssueLoad(ticketId);
      const hide = message.loading(`Fetching ticket ${ticketId}...`, 0);
      await this.ticketRequest.fetch();
      if (this.ticketRequest.error) {
        message.error(this.ticketRequest.error, 5);
      }
      hide();
      this.setState({pending: false});
    });
  };

  renderComments = () => {
    const {editComment} = this.state;
    return (
      <div
        className={classNames(
          'cp-divider',
          'left',
          styles.commentsContainer
        )}
      >
        {[this.ticket, ...this.comments]
          .filter(Boolean)
          .map((comment) => {
            const commentId = comment.iid || comment.id;
            if (editComment !== undefined && editComment === commentId) {
              return (
                <CommentEditor
                  key={commentId}
                  comment={comment}
                  onCancel={this.onCancelEditing}
                  onSave={this.onSaveCommentEditing}
                  className={styles.card}
                  disabled={this.pending}
                />
              );
            }
            return (
              <CommentCard
                key={commentId}
                comment={comment}
                className={styles.card}
              />
            );
          })}
      </div>
    );
  };

  renderInfoSection = () => {
    return (
      <div className={styles.infoSection}>
        <div className={classNames(
          styles.infoBlock,
          styles.row,
          'cp-divider',
          'bottom'
        )}>
          <span>Author:</span>
          <span style={{marginLeft: '10px'}}>
            {this.ticket.author.name}
          </span>
        </div>
        <div className={classNames(
          styles.infoBlock,
          styles.row,
          'cp-divider',
          'bottom'
        )}>
          <span>Status:</span>
          <div className={styles.labelsRow}>
            {(this.ticket.labels || []).map(label => (
              <Label label={label} key={label} />
            ))}
          </div>
        </div>
      </div>
    );
  }

  render () {
    if (!this.pending && !this.ticket) {
      return (
        <Alert type="error" message="Ticket not found." />
      );
    }
    if (this.pending && !this.ticket) {
      return <Spin />;
    }
    return (
      <div
        className={styles.container}
        key="list"
      >
        <div className={styles.content}>
          <div className={styles.ticketHeader}>
            <b>{this.ticket.title}</b>
            <span className={styles.ticketId}>
              {`#${this.ticket.iid}`}
            </span>
          </div>
          <div style={{display: 'flex', flexDirection: 'row'}}>
            <div className={styles.commentsSection}>
              {this.renderComments()}
              <CommentEditor
                onSave={this.onSaveNewComment}
                onCancel={this.onCancelEditing}
                disabled={this.pending}
                isNewComment
              />
            </div>
            {this.renderInfoSection()}
          </div>
        </div>
      </div>
    );
  }
}
