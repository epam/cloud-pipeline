/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Input,
  Modal,
  Form,
  Spin,
  Row
} from 'antd';

const formItemLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 6}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 18}
  }
};

@Form.create()
class VersionedStorageDialog extends React.Component {
  handleSubmit = (e) => {
    const {form} = this.props;
    e.preventDefault();
    form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit(values);
      }
    });
  };

  render () {
    const {visible, onCancel, form, pending} = this.props;
    const {getFieldDecorator} = form;
    const modalFooter = (
      <Row>
        <Button
          onClick={this.props.onCancel}
          disabled={pending}
        >
          Cancel
        </Button>
        <Button
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}
          disabled={pending}
        >
          Create
        </Button>
      </Row>
    );
    return (
      <Modal
        visible={visible}
        onCancel={onCancel}
        onOk={this.handleSubmit}
        title="Create versioned storage"
        footer={modalFooter}
        afterClose={form.resetFields}
      >
        <Spin spinning={pending}>
          <Form onSubmit={this.handleSubmit}>
            <Form.Item
              key="name"
              label="Name:"
              {...formItemLayout}
            >
              {getFieldDecorator('name', {
                rules: [{required: true, message: 'Please input repository name'}]
              })(
                <Input
                  onPressEnter={this.handleSubmit}
                  disabled={this.props.pending}
                />
              )}
            </Form.Item>
            <Form.Item
              key="description"
              label="Description:"
              {...formItemLayout}
            >
              {getFieldDecorator('description')(
                <Input
                  type="textarea"
                  autosize={{minRows: 2, maxRows: 6}}
                  disabled={this.props.pending}
                  onPressEnter={this.handleSubmit}
                />
              )}
            </Form.Item>
          </Form>
        </Spin>
      </Modal>
    );
  }
}

VersionedStorageDialog.propTypes = {
  onSubmit: PropTypes.func,
  onCancel: PropTypes.func,
  visible: PropTypes.bool,
  pending: PropTypes.bool
};

export default VersionedStorageDialog;
