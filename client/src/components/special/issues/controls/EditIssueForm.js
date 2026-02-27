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
import {Button, Form, Input, message, Modal, Row} from 'antd';
import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';
import styles from './EditIssueForm.css';
import IssueComment from './IssueComment';

@roleModel.authenticationInfo
@localization.localizedComponent
export default class EditIssueForm extends localization.LocalizedReactComponent {

  formRef = React.createRef();

  static propTypes = {
    issue: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      name: PropTypes.string,
      text: PropTypes.string,
      mask: PropTypes.number
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    onDelete: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
  };
  state = {
    deleteDialogVisible: false
  };
  handleSubmit = (e) => {
    e.preventDefault();
    this.formRef.current.validateFields()
      .then(async (values) => {
        if (!values.comment || !values.comment.text) {
          message.error(`${this.localizedString('Issue')} description is required`, 3);
        } else {
          await this.props.onSubmit(values);
          this.formRef.current.resetFields();
        }
      })
      .catch(() => {});
  };

  onClose = () => {
    this.formRef.current && this.formRef.current.resetFields();
    this.props.onCancel && this.props.onCancel();
  };

  openDeleteDialog = () => {
    this.setState({deleteDialogVisible: true});
  };

  closeDeleteDialog = () => {
    this.setState({deleteDialogVisible: false});
  };

  onDeleteClicked = () => {
    this.closeDeleteDialog();
    if (this.props.onDelete) {
      this.props.onDelete();
    }
  };

  getDeleteModalFooter = () => {
    return (
      <Row type="flex" justify="space-between">
        <Button
          id="edit-issue-delete-dialog-cancel-button"
          onClick={this.closeDeleteDialog}>Cancel</Button>
        <Button
          id="edit-issue-delete-dialog-delete-button"
          danger
          onClick={() => this.onDeleteClicked()}>Delete</Button>
      </Row>
    );
  };

  renderActions = () => {
    if (this.props.issue) {
      return (
        <Row type="flex" justify="space-between">
          <Button
            id="delete-issue"
            danger
            onClick={this.openDeleteDialog}
            size="small">Remove</Button>
          <Row type="flex" className={styles.actions} justify="end">
            <Button
              id="cancel-edit-issue-button"
              onClick={this.onClose}
              size="small">Cancel</Button>
            <Button
              id="save-edit-issue-button"
              type="primary"
              onClick={this.handleSubmit}
              size="small">Save</Button>
          </Row>
        </Row>
      );
    } else {
      return (
        <Row type="flex" className={styles.actions} justify="end">
          <Button
            id="cancel-create-issue-button"
            onClick={this.onClose}
            size="small">Cancel</Button>
          <Button
            id="create-issue-button"
            type="primary"
            onClick={this.handleSubmit}
            size="small">Create</Button>
        </Row>
      );
    }
  };
  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };
  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  render () {
    if (this.props.visible) {
      const issue = this.props.issue;
      return (
        <div style={{flex: 1, display: 'flex', flexDirection: 'column'}}>
          <Form
            ref={this.formRef}
            className="edit-issue-form"
            initialValues={{
              name: issue ? issue.name : '',
              comment: {
                text: issue && issue.text ? issue.text : ''
              }
            }}
          >
            <Form.Item
              key="issue name"
              className="edit-issue-form-name-container"
              style={{marginBottom: 5}}
              name="name"
              rules={[{required: true, message: `${this.localizedString('Issue')} title is required`}]}
            >
              <Input
                size="default"
                placeholder="Title"
                disabled={this.props.pending}
                onPressEnter={this.handleSubmit}
                ref={this.initializeNameInput}
              />
            </Form.Item>
            <Form.Item
              key="issue description"
              className="edit-issue-form-description-container"
              style={{marginBottom: 5}}
              name="comment"
            >
              <IssueComment disabled={this.props.pending} />
            </Form.Item>
          </Form>
          {this.renderActions()}
          <Modal
            onCancel={this.closeDeleteDialog}
            visible={this.state.deleteDialogVisible}
            title={`Are you sure you want to delete ${this.localizedString('issue')}?`}
            footer={this.getDeleteModalFooter()}>
            <p>This operation cannot be undone.</p>
          </Modal>
        </div>
      );
    } else {
      return null;
    }
  }

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}
