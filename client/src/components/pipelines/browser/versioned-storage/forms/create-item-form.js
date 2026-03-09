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
  Form,
  Modal,
  Input,
  Row,
  Spin
} from 'antd';
import PropTypes from 'prop-types';
import checkFileExistence from '../utils';

// eslint-disable-next-line
const NAME_VALIDATION_TEXT = 'Name can contain only letters, digits, "_", "-", and "."';

class CreateItemForm extends React.Component {
  formRef = React.createRef();
  state = {
    checkInProgress: false,
    pathOccupied: false
  };

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
    const {onSubmit} = this.props;
    e.preventDefault();
    this.formRef.current.validateFields()
      .then(async (values) => {
        const pathExist = await this.checkPathExistence();
        if (pathExist) {
          this.setState({pathOccupied: true});
        } else {
          this.setState({pathOccupied: false}, () => {
            onSubmit && onSubmit(values);
          });
        }
      })
      .catch(() => {});
  };

  onNameChange = () => {
    const {pathOccupied} = this.state;
    if (pathOccupied) {
      this.setState({pathOccupied: false});
    }
  };

  checkPathExistence = async () => {
    const {
      pipelineId,
      path
    } = this.props;
    this.setState({checkInProgress: true});
    const values = this.formRef.current.getFieldsValue();
    const pathExist = await checkFileExistence(
      pipelineId,
      `${path || ''}${values.name}`
    );
    this.setState({checkInProgress: false});
    return pathExist;
  };

  render () {
    const {pathOccupied, checkInProgress} = this.state;
    const {documentType} = this.props;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button onClick={this.props.onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>OK</Button>
      </Row>
    );
    const onClose = () => {
      this.setState({
        checkInProgress: false,
        pathOccupied: false
      }, () => {
        this.formRef.current && this.formRef.current.resetFields();
      });
    };
    return (
      <Modal
        mask={{closable: !this.props.pending}}
        afterClose={() => onClose()}
        closable={!this.props.pending && !pathOccupied}
        open={this.props.visible}
        title={this.props.title}
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending || checkInProgress}>
          <Form
            ref={this.formRef}
            initialValues={{name: this.props.name, comment: undefined}}
          >
            <Form.Item
              {...this.formItemLayout}
              label="Name"
              name="name"
              validateStatus={pathOccupied ? 'error' : undefined}
              help={pathOccupied
                ? `${documentType} with that name already exists`
                : undefined
              }
              rules={[
                {required: true, message: 'Name is required'},
                {pattern: /^[\da-zA-Z.\-_]+$/, message: NAME_VALIDATION_TEXT}
              ]}
            >
              <Input
                ref={this.initializeNameInput}
                onPressEnter={this.handleSubmit}
                disabled={this.props.pending}
                onChange={this.onNameChange}
              />
            </Form.Item>
            <Form.Item
              {...this.formItemLayout}
              label="Comment"
              name="comment"
            >
              <Input
                disabled={this.props.pending}
                type="textarea"
              />
            </Form.Item>
          </Form>
        </Spin>
      </Modal>
    );
  }

  initializeNameInput = (input) => {
    if (input) {
      this.nameInput = input;
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

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}

CreateItemForm.propTypes = {
  onCancel: PropTypes.func,
  onSubmit: PropTypes.func,
  pending: PropTypes.bool,
  visible: PropTypes.bool,
  name: PropTypes.string,
  title: PropTypes.string,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  path: PropTypes.string,
  documentType: PropTypes.string
};

export default CreateItemForm;
