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
import IssuesLoad from '../../../models/issues/IssuesLoad';
import IssueCreate from '../../../models/issues/IssueCreate';
import LoadingView from '../../special/LoadingView';
import moment from 'moment';
import {Button, Icon, message, Row, Table, Alert} from 'antd';
import Issue from './Issue';
import EditIssueForm from './controls/EditIssueForm';
import {processUnusedAttachments} from './utilities/UnusedAttachmentsProcessor';
import styles from './Issues.css';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('issuesRenderer')
@inject((stores, params) => {
  return {
    issues: params.entityId && params.entityClass
      ? new IssuesLoad(params.entityId, params.entityClass)
      : null
  };
})
@observer
export default class Issues extends localization.LocalizedReactComponent {

  static propTypes = {
    entityId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    entityClass: PropTypes.oneOf([
      'PIPELINE',
      'FOLDER',
      'DATA_STORAGE',
      'TOOL',
      'TOOL_GROUP',
      'DOCKER_REGISTRY',
      'CONFIGURATION',
      'METADATA_ENTITY'
    ]),
    entityDisplayName: PropTypes.string,
    entity: PropTypes.object,
    onCloseIssuePanel: PropTypes.func,
    canNavigateBack: PropTypes.bool,
    onNavigateBack: PropTypes.func,
    onReloadIssues: PropTypes.func
  };

  state = {
    createNewIssueDialogVisible: false,
    operationInProgress: false,
    selectedIssue: null
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
  renderLabels = (labels) => {
    return (labels || []).map((label, index) => {
      return (
        <span key={`label_${index}`} className={styles.label}>
          {label}
        </span>
      );
    });
  };
  issuesColumns = [
    {
      dataIndex: 'name',
      key: 'name',
      render: (name, issue) => {
        const descriptions = [];
        if (issue.author && issue.createdDate) {
          descriptions.push(
            <Row
              key="opened by"
              className={styles.openedByRow}>
              Opened {moment.utc(issue.createdDate).fromNow()} by <b>{issue.author}</b>
            </Row>
          );
        } else if (issue.author) {
          descriptions.push(
            <Row
              key="opened by"
              className={styles.openedByRow}>
              Opened by {issue.author}
            </Row>
          );
        } else if (issue.createdDate) {
          descriptions.push(
            <Row
              key="opened by"
              className={styles.openedByRow}>
              Opened {moment.utc(issue.createdDate).fromNow()}
            </Row>
          );
        }
        return (
          <div style={{display: 'flex', flexDirection: 'column'}}>
            <Row type="flex" align="middle">
              <span className={styles.issueName}>{name}</span>
              {this.renderLabels(issue.labels)}
            </Row>
            {descriptions}
          </div>
        );
      }
    }
  ];
  openCreateIssueDialog = () => {
    this.setState({
      createNewIssueDialogVisible: true
    });
  };
  closeCreateIssueDialog = () => {
    this.setState({
      createNewIssueDialogVisible: false
    });
  };
  createIssue = async (values) => {
    const hide = message.loading(`Creating ${this.localizedString('issue')}...`, 0);
    const request = new IssueCreate();
    const htmlText = await this.props.issuesRenderer.renderAsync(values.comment.text, false);
    const attachments = await processUnusedAttachments(values.comment.text, values.comment.attachments);
    await request.send({
      name: values.name,
      text: values.comment.text,
      htmlText,
      attachments,
      entity: {
        entityId: this.props.entityId,
        entityClass: this.props.entityClass
      }
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateIssueDialog();
      this.props.issues && await this.props.issues.fetch();
      this.props.onReloadIssues && await this.props.onReloadIssues();
    }
  };
  onSelectIssue = (issue, shouldReload) => {
    this.setState({
      selectedIssue: issue,
      createNewIssueDialogVisible: false
    }, () => {
      if (!this.state.selectedIssue) {
        this.props.issues.fetch();
      }
      if (shouldReload) {
        this.props.onReloadIssues && this.props.onReloadIssues();
      }
    });
  };

  @computed
  get issues () {
    if (this.props.issues && this.props.issues.loaded) {
      return (this.props.issues.value || []).map(i => i);
    }
    return [];
  }

  render () {
    if (!roleModel.readAllowed(this.props.entity)) {
      return (
        <Alert type="error" message={`You have no permissions to view ${this.localizedString('issue')}s`} />
      );
    }
    if (this.props.issues && this.props.issues.pending && !this.props.issues.loaded) {
      return <LoadingView />;
    }
    if (!this.props.issues) {
      return <div />;
    }
    if (this.state.selectedIssue) {
      return (
        <Issue issue={this.state.selectedIssue} onNavigateBack={(shouldReload) => this.onSelectIssue(null, shouldReload)} />
      );
    } else {
      return (
        <div className={styles.container}>
          <Row className={styles.issuesNav} align="middle" type="flex" justify="space-between">
            <div>
              {
                this.props.canNavigateBack &&
                <Button
                  id="navigate-back-button"
                  size="small"
                  onClick={this.props.onNavigateBack}
                  style={{marginLeft: 2, marginRight: 5}}>
                  <Icon type="arrow-left" />
                </Button>
              }
              {
                this.props.entityDisplayName &&
                <span className={styles.title}>{this.props.entityDisplayName}</span>
              }
            </div>
            <Row className={styles.actions}>
              { roleModel.readAllowed(this.props.entity) &&
                !this.state.createNewIssueDialogVisible &&
                <Button
                  type="primary"
                  size="small"
                  onClick={this.openCreateIssueDialog}>
                  New {this.localizedString('issue')}
                </Button>
              }
            </Row>
          </Row>
          <div className={styles.scrollableContent}>
            {
              this.state.createNewIssueDialogVisible &&
              <Row className={styles.newIssueContainer}>
                <EditIssueForm
                  visible={this.state.createNewIssueDialogVisible}
                  pending={this.state.operationInProgress}
                  onCancel={this.closeCreateIssueDialog}
                  onSubmit={this.operationWrapper(this.createIssue)} />
              </Row>
            }
            <Table
              loading={this.props.issues && this.props.issues.pending}
              className={styles.table}
              rowKey="id"
              rowClassName={() => styles.issueRow}
              showHeader={false}
              onRowClick={(item) => this.onSelectIssue(item)}
              columns={this.issuesColumns}
              dataSource={this.issues}
              pagination={false}
              size="middle"
              locale={{emptyText: `No ${this.localizedString('issue')}s found`}}
              bordered={false} />
          </div>
        </div>
      );
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.entityId !== this.props.entityId || nextProps.entityClass !== this.props.entityClass) {
      this.setState({
        selectedIssue: null,
        createNewIssueDialogVisible: false
      });
    }
  }
}
