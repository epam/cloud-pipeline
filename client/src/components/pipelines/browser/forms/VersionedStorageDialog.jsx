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
import {Button, Form, Input, Modal, Spin, Row, Checkbox} from 'antd';

const formItemLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 6},
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 18},
  },
};

class VersionedStorageDialog extends React.Component {
  formRef = React.createRef();

  state = {
    predefinedFoldersChecked: false,
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.formRef.current
      .validateFields()
      .then((values) => {
        this.props.onSubmit(values);
      })
      .catch(() => {});
  };

  validateFolderStructure = (rule, value) => {
    if (this.props.name && value && value.toLowerCase() === this.props.name.toLowerCase()) {
      return Promise.reject(new Error('Name should be unique'));
    }
    return Promise.resolve();
  };

  togglePredefinedFolders = (e) => {
    const {predefinedFoldersChecked} = this.state;
    this.setState({predefinedFoldersChecked: !predefinedFoldersChecked});
  };

  render() {
    const {visible, onCancel, pending, folderStructureArea} = this.props;
    const {predefinedFoldersChecked} = this.state;
    const modalFooter = (
      <Row>
        <Button onClick={this.props.onCancel} disabled={pending}>
          Cancel
        </Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit} disabled={pending}>
          Create
        </Button>
      </Row>
    );
    return (
      <Modal
        open={visible}
        onCancel={onCancel}
        onOk={this.handleSubmit}
        title="Create versioned storage"
        footer={modalFooter}
        afterClose={() => this.formRef.current && this.formRef.current.resetFields()}
      >
        <Spin spinning={pending}>
          <Form
            ref={this.formRef}
            onSubmit={this.handleSubmit}
            initialValues={{
              name: undefined,
              description: undefined,
              foldersStructure: '',
            }}
          >
            <Form.Item
              key="name"
              label="Name:"
              {...formItemLayout}
              name="name"
              rules={[
                {required: true, message: 'Please input repository name'},
                {
                  pattern: /^[\da-zA-Z.\-_]+$/,
                  message: 'Repository name can contain only letters, digits, "_", "-", and "."',
                },
              ]}
            >
              <Input onPressEnter={this.handleSubmit} disabled={pending} />
            </Form.Item>
            <Form.Item
              key="description"
              label="Description:"
              {...formItemLayout}
              name="description"
            >
              <Input.TextArea
                autoSize={{minRows: 2, maxRows: 6}}
                disabled={pending}
                onPressEnter={this.handleSubmit}
              />
            </Form.Item>
            <Checkbox checked={predefinedFoldersChecked} onChange={this.togglePredefinedFolders}>
              Predefined folder structure
            </Checkbox>
            {folderStructureArea && predefinedFoldersChecked && (
              <Form.Item
                key="foldersStructure"
                name="foldersStructure"
                rules={[
                  {
                    pattern: /^[\da-zA-Z_\n\-/]+$/,
                    message: 'Path can contain only letters, digits, "_", "-", "/" and "."',
                  },
                ]}
              >
                <Input.TextArea
                  disabled={pending}
                  autoSize={{minRows: 5}}
                  placeholder={[
                    'To set folder paths, use "/" as divider,',
                    'for example:\n',
                    'folderA/folderB',
                    'folderA/folderC',
                    'folderC',
                  ].join('\n')}
                />
              </Form.Item>
            )}
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
  pending: PropTypes.bool,
  folderStructureArea: PropTypes.bool,
};

export default VersionedStorageDialog;
