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
import {Checkbox, Button, Modal, Form, Input, Row, Col, Select, Spin} from 'antd';
import {inject, observer} from 'mobx-react';
import connect from '../../../../../utils/connect';
import localization from '../../../../../utils/localization';
import pipelines from '../../../../../models/pipelines/Pipelines';

@connect({
  pipelines
})
@localization.localizedComponent
@inject('pipelines', 'visible', 'onSubmit', 'onCancel', 'pending', 'pipelineId')
@Form.create()
@observer
export default class PipelineStorageRuleCreateDialog extends localization.LocalizedReactComponent {
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

  checkboxWrapperLayout = {
    xs: 12,
    sm: 11
  };

  checkboxLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 14}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 3}
    }
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit(values);
      }
    });
  };

  resetNameToInitialValue = () => {
      this.props.form.setFieldsValue({
        name: ''
      });
  }

  handlePipelineResultsChange = (e) => {
    const {setFieldsValue, getFieldValue, isFieldTouched} = this.props.form;

    const userAlreadySetMoveToStsTrue = isFieldTouched('moveToSts') && getFieldValue('moveToSts');
    const shouldNotChangeTrulyMoveToSts = !e.target.checked && userAlreadySetMoveToStsTrue;

    // We need it to trigger revalidation in case if user already receive 'required' error
    if(!e.target.checked && !getFieldValue('name')) {
      this.resetNameToInitialValue()
    }

    if (shouldNotChangeTrulyMoveToSts || userAlreadySetMoveToStsTrue) {
      return;
    }

    setFieldsValue({
      moveToSts: e.target.checked
    });
  };

  handeOnClose = () => {
    this.props.form.resetFields();
  }

  render () {
    const { getFieldDecorator, getFieldValue } = this.props.form;
    const {
      visible,
      onCancel,
      pipelines,
      pending,
      pipelineId
    } = this.props;
    const modalFooter = pending ? false : (
      <Row>
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>Create</Button>
      </Row>
    );
    const isMoveToStsDisabled = getFieldValue('isResult') || pending;
    const isRuleNameRequired = Boolean(getFieldValue('isResult'));

    return (
      <Modal
        maskClosable={!pending}
        afterClose={this.handeOnClose}
        closable={!pending}
        visible={visible}
        title="Create new rule"
        onCancel={onCancel}
        footer={modalFooter}
      >
        <Spin spinning={pending}>
          <Form>
            <Spin spinning={pipelines.pending}>
              <Form.Item {...this.formItemLayout} label={this.localizedString('Pipeline')}>
                {getFieldDecorator('pipelineId',
                  {
                    rules: [{
                      required: true,
                      message: `Please select ${this.localizedString('pipeline')}`
                    }],
                    initialValue: `${pipelineId}`
                  })(
                  <Select disabled={pending || pipelineId !== undefined}>
                      {pipelines?.value.map(pipeline => (
                          <Select.Option key={pipeline.id}
                                         value={`${pipeline.id}`}>{pipeline.name}
                          </Select.Option>
                        ))}
                    </Select>
                )}
              </Form.Item>
            </Spin>
            <Form.Item {...this.formItemLayout} label="Name">
              {getFieldDecorator('name', {rules: [{required: isRuleNameRequired, message: 'Name is required'}]})(
                <Input
                  disabled={pending}
                  onPressEnter={this.handleSubmit} />
              )}
            </Form.Item>
            <Form.Item {...this.formItemLayout} label="File mask">
              {getFieldDecorator('fileMask', {rules: [{required: true, message: 'File mask is required'}]})(
                <Input
                  disabled={pending}
                  ref={this.initializeNameInput}
                  onPressEnter={this.handleSubmit} />
              )}
            </Form.Item>
            <Row type={'flex'} justify={'start'} gutter={24}>
              <Col {...this.checkboxWrapperLayout}>
                <Form.Item {...this.checkboxLayout} label="Pipeline results">
                  {getFieldDecorator('isResult', {
                    valuePropName: 'checked',
                    initialValue: false,
                    onChange: this.handlePipelineResultsChange
                  })(
                    <Checkbox disabled={pending} />
                  )}
                </Form.Item>
              </Col>
              <Col {...this.checkboxWrapperLayout}>
                <Form.Item {...this.checkboxLayout} label="Move to STS">
                  {getFieldDecorator('moveToSts', {
                    valuePropName: 'checked',
                    initialValue: false
                  })(
                    <Checkbox disabled={isMoveToStsDisabled} />
                  )}
                </Form.Item>
              </Col>
            </Row>
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
