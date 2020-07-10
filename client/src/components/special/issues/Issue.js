/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Alert, Button, Icon, message, Modal, Row, Tooltip} from 'antd';
import IssueCommentPreview from './controls/IssueCommentPreview';
import IssueComment from './controls/IssueComment';
import styles from './Issues.css';
import EditableField from '../../special/EditableField';
import displayDate from '../../../utils/displayDate';
import moment from 'moment-timezone';
import IssueLoad from '../../../models/issues/IssueLoad';
import IssueUpdate from '../../../models/issues/IssueUpdate';
import IssueDelete from '../../../models/issues/IssueDelete';
import IssueCommentSend from '../../../models/issues/IssueComment';
import IssueCommentUpdate from '../../../models/issues/IssueCommentUpdate';
import IssueCommentDelete from '../../../models/issues/IssueCommentDelete';
import {processUnusedAttachments} from './utilities/UnusedAttachmentsProcessor';
import LoadingView from '../../special/LoadingView';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('issuesRenderer')
@inject((stores, params) => {
  return {
    issueInfo: params.issue ? new IssueLoad(params.issue.id) : null,
    issueId: params.issue ? params.issue.id : null
  };
})
@observer
export default class Issue extends localization.LocalizedReactComponent {

  static propTypes = {
    readOnly: PropTypes.bool,
    onNavigateBack: PropTypes.func,
    issue: PropTypes.object
  };

  state = {
    newComment: null,
    newCommentAttachments: [],
    operationInProgress: false,
    editableCommentId: null,
    editableCommentText: null,
    editableCommentAttachments: [],
    editableCommentOriginalText: null,
    editableCommentOriginalAttachments: []
  };

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };

  @computed
  get isAdmin () {
    if (this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.admin;
    }
    return false;
  }

  @computed
  get isMineIssue () {
    if (this.props.issueInfo && this.props.issueInfo.loaded &&
      this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.userName === this.props.issueInfo.value.author;
    }
    return false;
  }

  isMineComment = (comment) => {
    if (this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.userName === comment.author;
    }
    return false;
  };

  canRemoveComment = (comment) => {
    return !this.props.readOnly &&
      !comment.rootComment &&
      (this.isMineComment(comment) || this.isAdmin);
  };

  canEditComment = (comment) => {
    return !this.props.readOnly &&
      (this.isMineComment(comment) || this.isAdmin);
  };
  onRenameIssue = async (name) => {
    const text = this.props.issueInfo.loaded ? this.props.issueInfo.value.text : undefined;
    const htmlText = await this.props.issuesRenderer.renderAsync(text, false);
    const payload = {
      labels: this.props.issueInfo.loaded ? (this.props.issueInfo.value.labels || []).map(l => l) : undefined,
      text,
      htmlText,
      name,
      status: this.props.issueInfo.loaded ? this.props.issueInfo.value.status : 'OPEN',
    };
    const hide = message.loading(`Renaming ${this.localizedString('issue')}...`, 0);
    const request = new IssueUpdate(this.props.issueId);
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      await this.props.issueInfo.fetch();
      hide();
    }
  };
  onUpdateIssue = async (text, attachments) => {
    const hide = message.loading('Updating comment...', 0);
    const issueAttachments = await processUnusedAttachments(text, attachments);
    issueAttachments.push(...(this.props.issueInfo.value.attachments || []));
    const htmlText = await this.props.issuesRenderer.renderAsync(text, false);
    const payload = {
      labels: this.props.issueInfo.loaded ? (this.props.issueInfo.value.labels || []).map(l => l) : undefined,
      text,
      htmlText,
      name: this.props.issueInfo.loaded ? this.props.issueInfo.value.name : undefined,
      status: this.props.issueInfo.loaded ? this.props.issueInfo.value.status : 'OPEN',
      attachments: issueAttachments
    };
    const request = new IssueUpdate(this.props.issueId);
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      await this.props.issueInfo.fetch();
      hide();
    }
  };
  onEditCommentClicked = (comment) => {
    this.setState({
      editableCommentId: comment.id,
      editableCommentOriginalText: comment.text,
      editableCommentOriginalAttachments: comment.attachments || []
    });
  };
  onEditComment = ({text, attachments}) => {
    this.setState({
      editableCommentText: text,
      editableCommentAttachments: attachments
    });
  };
  onApplyEditCommentClicked = async () => {
    if (this.state.editableCommentId === 'root') {
      await this.onUpdateIssue(this.state.editableCommentText, this.state.editableCommentAttachments);
      this.setState({
        editableCommentId: null,
        editableCommentText: null,
        editableCommentAttachments: [],
        editableCommentOriginalText: null,
        editableCommentOriginalAttachments: []
      });
    } else {
      const hide = message.loading('Updating comment...', 0);
      const request = new IssueCommentUpdate(this.props.issueId, this.state.editableCommentId);
      const attachments = await processUnusedAttachments(this.state.editableCommentText, this.state.editableCommentAttachments);
      attachments.push(...(this.state.editableCommentOriginalAttachments || []));
      const htmlText = await this.props.issuesRenderer.renderAsync(this.state.editableCommentText, false);
      await request.send({
        text: this.state.editableCommentText,
        htmlText,
        attachments
      });
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.issueInfo.fetch();
        hide();
        this.setState({
          editableCommentId: null,
          editableCommentText: null,
          editableCommentAttachments: [],
          editableCommentOriginalText: null,
          editableCommentOriginalAttachments: []
        });
      }
    }
  };
  onCancelEditComment = () => {
    this.setState({
      editableCommentId: null,
      editableCommentText: null,
      editableCommentAttachments: [],
      editableCommentOriginalText: null,
      editableCommentOriginalAttachments: []
    });
  };
  onCancelEditCommentClicked = () => {
    const onCancel = () => {
      this.onCancelEditComment();
    };
    if (this.state.editableCommentText !== this.state.editableCommentOriginalText) {
      Modal.confirm({
        title: 'All changes will be lost. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          onCancel();
        }
      });
    } else {
      onCancel();
    }
  };
  onDeleteCommentClicked = (comment) => {
    const onDelete = () => {
      this.operationWrapper(async () => {
        await this.deleteComment(comment.id);
      })();
    };
    Modal.confirm({
      title: 'Are you sure you want to delete comment?',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        onDelete();
      }
    });
  };
  deleteComment = async (commentId) => {
    const hide = message.loading('Removing comment...', 0);
    const request = new IssueCommentDelete(this.props.issueId, commentId);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.issueInfo.fetch();
      hide();
    }
  };
  renderComment = (comment, index) => {
    const isMine = this.isMineComment(comment);
    const date = (
      <Tooltip overlay={displayDate(comment.createdDate)}>
        <b style={{cursor: 'pointer'}}>{moment.utc(comment.createdDate).fromNow()}</b>
      </Tooltip>
    );
    const renderCommentActions = () => {
      const actions = [];
      if (this.state.editableCommentId === comment.id) {
        actions.push(
          <a
            disabled={this.state.editableCommentText === this.state.editableCommentOriginalText}
            key="apply"
            onClick={this.onApplyEditCommentClicked}>
            <Icon type="check" />
          </a>
        );
        actions.push(
          <a key="cancel" onClick={this.onCancelEditCommentClicked}>
            <Icon type="close" />
          </a>
        );
      } else {
        const disabled = !!this.state.editableCommentId;
        if (this.canEditComment(comment)) {
          actions.push(
            <a
              key="edit"
              disabled={disabled}
              onClick={() => this.onEditCommentClicked(comment)}>
              <Icon type="edit" />
            </a>
          );
        }
        if (this.canRemoveComment(comment)) {
          actions.push(
            <a
              key="delete"
              disabled={disabled}
              onClick={() => this.onDeleteCommentClicked(comment)}>
              <Icon type="delete" />
            </a>
          );
        }
      }
      return actions;
    };
    return (
      <Row
        key={`comment_${index}`}
        type="flex"
        className={isMine ? `${styles.commentContainer} ${styles.mine}` : styles.commentContainer}>
        <Row
          type="flex"
          align="middle"
          justify="space-between"
          className={isMine ? `${styles.commentAuthorContainer} ${styles.mine}` : styles.commentAuthorContainer}>
          <span>{isMine ? 'You' : comment.author} commented {date}</span>
          <Row className={styles.actions}>
            {renderCommentActions()}
          </Row>
        </Row>
        <Row
          type="flex"
          className={isMine ? `${styles.commentTextContainer} ${styles.mine}` : styles.commentTextContainer}>
          {
            this.state.editableCommentId === comment.id &&
            <IssueComment
              style={{marginTop: 5}}
              disabled={this.state.operationInProgress}
              placeholder="Comment"
              height={200}
              value={{text: this.state.editableCommentText || comment.text}}
              onChange={this.onEditComment} />
          }
          {
            this.state.editableCommentId !== comment.id &&
            <IssueCommentPreview text={comment.text} />
          }
        </Row>
      </Row>
    );
  };
  onNewCommentChanged = ({text, attachments}) => {
    this.setState({
      newComment: text,
      newCommentAttachments: attachments
    });
  };
  sendComment = async () => {
    const hide = message.loading('Sending comment...', 0);
    const request = new IssueCommentSend(this.props.issueId);
    const htmlText = await this.props.issuesRenderer.renderAsync(this.state.newComment, false);
    const attachments = await processUnusedAttachments(this.state.newComment, this.state.newCommentAttachments);
    await request.send({text: this.state.newComment, htmlText, attachments});
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.issueInfo.fetch();
      hide();
      this.setState({
        newComment: null,
        newCommentAttachments: []
      });
    }
  };
  deleteIssueConfirm = () => {
    const onDelete = () => {
      this.operationWrapper(this.deleteIssue)();
    };
    Modal.confirm({
      title: `Are you sure you want to delete ${this.localizedString('issue')}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        onDelete();
      }
    });
  };
  deleteIssue = async () => {
    const request = new IssueDelete(this.props.issueId);
    const hide = message.loading(`Deleting ${this.localizedString('issue')}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.props.onNavigateBack && this.props.onNavigateBack(true);
    }
  };

  @computed
  get canAddComment () {
    return !this.props.readOnly;
  }

  @computed
  get canEditIssue () {
    return !this.props.readOnly && (this.isMineIssue || this.isAdmin);
  }

  @computed
  get canRemoveIssue () {
    return !this.props.readOnly && (this.isMineIssue || this.isAdmin);
  }

  @computed
  get comments () {
    if (this.props.issueInfo && this.props.issueInfo.loaded) {
      const firstComment = {
        author: this.props.issueInfo.value.author,
        createdDate: this.props.issueInfo.value.createdDate,
        text: this.props.issueInfo.value.text,
        rootComment: true,
        id: 'root'
      };
      return [firstComment, ...(this.props.issueInfo.value.comments || []).map(c => c)];
    }
    return [];
  }

  @computed
  get editCommentInAction () {
    return !!this.state.editableCommentId;
  }

  render () {
    let content;
    if (this.props.issueInfo && this.props.issueInfo.pending && !this.props.issueInfo.loaded) {
      content = <LoadingView />;
    } else if (this.props.issueInfo && this.props.issueInfo.error) {
      content = <Alert type="warning" message={this.props.issueInfo.error} />
    } else {
      content = this.comments.map(this.renderComment);
    }
    return (
      <div className={styles.container}>
        <table className={styles.issueNav} style={{width: '100%', tableLayout: 'fixed'}}>
          <tbody>
          <tr>
            <td>
              <Row type="flex" className={styles.itemHeader} align="middle">
                <Button
                  id="navigate-back-button"
                  disabled={this.editCommentInAction}
                  size="small"
                  onClick={() => this.props.onNavigateBack && this.props.onNavigateBack(false)}
                  style={{marginRight: 5}}>
                  <Icon type="arrow-left" />
                </Button>
                <EditableField
                  inputId="issue-name"
                  displayId="issue-name-input"
                  style={{
                    paddingTop: 6,
                    flex: 1,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    display: 'flex'
                  }}
                  readOnly={!this.canEditIssue || this.state.operationInProgress || this.editCommentInAction}
                  allowEpmty={false}
                  onSave={this.operationWrapper(this.onRenameIssue)}
                  editStyle={{flex: 1}}
                  text={this.props.issueInfo && this.props.issueInfo.loaded ? this.props.issueInfo.value.name : ''} />
              </Row>
            </td>
            <td className={styles.actions} style={{width: 40}}>
              {
                this.canRemoveIssue &&
                <a
                  disabled={this.state.operationInProgress || this.editCommentInAction}
                  type="danger"
                  onClick={this.deleteIssueConfirm}>
                  <Icon type="delete" />
                </a>
              }
            </td>
          </tr>
          </tbody>
        </table>
        <div style={{flex: 1, overflow: 'auto'}}>
          {content}
          {
            this.canAddComment &&
            <Row type="flex" className={styles.commentContainer}>
              <IssueComment
                disabled={this.state.operationInProgress || this.editCommentInAction}
                placeholder="Comment"
                height={200}
                value={{text: this.state.newComment}}
                onChange={this.onNewCommentChanged} />
            </Row>
          }
          {
            this.canAddComment &&
            <Row type="flex" justify="end" style={{padding: 5}}>
              <Button
                disabled={!this.state.newComment || this.state.operationInProgress || this.editCommentInAction}
                id="send-comment"
                size="small"
                type="primary"
                onClick={this.operationWrapper(this.sendComment)}>
                Send
              </Button>
            </Row>
          }
        </div>
      </div>
    );
  }
}
