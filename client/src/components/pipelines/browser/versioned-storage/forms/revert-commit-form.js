/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {
  Button,
  Modal,
  Form,
  Input,
  Row,
  Spin
} from 'antd';
import PropTypes from 'prop-types';

@Form.create()
class RevertCommitForm extends React.Component {
  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  handleSubmit = (e) => {
    const {onRevert, form} = this.props;
    e.preventDefault();
    form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        onRevert && onRevert(values);
      }
    });
  };

  render () {
    const {
      path,
      commit,
      form,
      pending,
      onCancel,
      visible
    } = this.props;
    const {getFieldDecorator, resetFields} = form;
    const modalFooter = pending ? false : (
      <Row>
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>Revert</Button>
      </Row>
    );
    const onClose = () => resetFields();
    const objectName = (path || '').split(/[\\/]/).pop();
    return (
      <Modal
        maskClosable={!pending}
        afterClose={() => onClose()}
        closable={!pending}
        visible={visible}
        title={
          objectName
            ? (<span>Revert <b>{objectName}</b> to revision <b>{commit}</b></span>)
            : undefined
        }
        onCancel={onCancel}
        footer={objectName ? modalFooter : undefined}
      >
        <Spin spinning={pending}>
          <Form>
            <Form.Item
              {...this.formItemLayout}
              label="Comment"
            >
              {getFieldDecorator('comment')(
                <Input
                  ref={this.initializeCommentInput}
                  disabled={pending}
                  onPressEnter={this.handleSubmit}
                  type="textarea"
                />
              )}
            </Form.Item>
          </Form>
        </Spin>
      </Modal>
    );
  }

  initializeCommentInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.commentInput = input.refs.input;
      this.commentInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };

  focusCommentInput = () => {
    if (this.props.visible && this.commentInput) {
      setTimeout(() => {
        this.commentInput.focus();
      }, 0);
    }
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusCommentInput();
    }
  }
}

RevertCommitForm.propTypes = {
  onCancel: PropTypes.func,
  onRevert: PropTypes.func,
  pending: PropTypes.bool,
  visible: PropTypes.bool,
  path: PropTypes.string,
  commit: PropTypes.string,
};

export default RevertCommitForm;
