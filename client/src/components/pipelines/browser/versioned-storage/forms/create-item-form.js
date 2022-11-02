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
import checkFileExistence from '../utils';

// eslint-disable-next-line
const NAME_VALIDATION_TEXT = 'Name can contain only letters, digits, "_", "-", and "."';

@Form.create()
class CreateItemForm extends React.Component {
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
    const {onSubmit, form} = this.props;
    e.preventDefault();
    form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.checkPathExistence().then((pathExist) => {
          if (pathExist) {
            this.setState({pathOccupied: true});
          } else {
            this.setState({pathOccupied: false}, () => {
              onSubmit && onSubmit(values);
            });
          }
        });
      }
    });
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
      path,
      form
    } = this.props;
    this.setState({checkInProgress: true});
    const pathExist = await checkFileExistence(
      pipelineId,
      `${path || ''}${form.getFieldsValue().name}`
    );
    this.setState({checkInProgress: false});
    return pathExist;
  };

  render () {
    const {pathOccupied, checkInProgress} = this.state;
    const {documentType} = this.props;
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button
          onClick={this.props.onCancel}
          id="versioned-storage-create-item-modal-cancel-btn"
        >
          CANCEL
        </Button>
        <Button
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}
          id="versioned-storage-create-item-modal-ok-btn"
        >
          OK
        </Button>
      </Row>
    );
    const onClose = () => {
      this.setState({
        checkInProgress: false,
        pathOccupied: false
      }, () => {
        resetFields();
      });
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending && !pathOccupied}
        visible={this.props.visible}
        title={this.props.title}
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending || checkInProgress}>
          <Form>
            <Form.Item
              {...this.formItemLayout}
              label="Name"
              validateStatus={pathOccupied ? 'error' : undefined}
              help={pathOccupied
                ? `${documentType} with that name already exists`
                : undefined
              }
            >
              {getFieldDecorator('name', {
                rules: [
                  {
                    required: true,
                    message: 'Name is required'
                  },
                  {
                    pattern: /^[\da-zA-Z.\-_]+$/,
                    message: NAME_VALIDATION_TEXT
                  }
                ],
                initialValue: this.props.name
              })(
                <Input
                  ref={this.initializeNameInput}
                  onPressEnter={this.handleSubmit}
                  disabled={this.props.pending}
                  onChange={this.onNameChange}
                />
              )}
            </Form.Item>
            <Form.Item
              {...this.formItemLayout}
              label="Comment"
            >
              {getFieldDecorator('comment')(
                <Input
                  disabled={this.props.pending}
                  type="textarea"
                />
              )}
            </Form.Item>
          </Form>
        </Spin>
      </Modal>
    );
  }

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
