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
import {Checkbox, Button, Form, Modal, Input, Row, Col, Select, Spin} from 'antd';
import {inject, observer} from 'mobx-react';
import connect from '../../../../../utils/connect';
import localization from '../../../../../utils/localization';
import pipelines from '../../../../../models/pipelines/Pipelines';

@connect({
  pipelines,
})
@localization.localizedComponent
@inject('pipelines', 'visible', 'onSubmit', 'onCancel', 'pending', 'pipelineId')
@observer
export default class PipelineStorageRuleCreateDialog extends localization.LocalizedReactComponent {
  formRef = React.createRef();
  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6},
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18},
    },
  };

  checkboxWrapperLayout = {
    xs: 12,
    sm: 11,
  };

  checkboxLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 14},
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 3},
    },
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

  resetNameToInitialValue = () => {
    if (this.formRef.current) {
      this.formRef.current.setFieldsValue({name: ''});
    }
  };

  handlePipelineResultsChange = (e) => {
    const form = this.formRef.current;
    if (!form) return;

    const userAlreadySetMoveToStsTrue =
      form.isFieldTouched('moveToSts') && form.getFieldValue('moveToSts');
    const shouldNotChangeTrulyMoveToSts = !e.target.checked && userAlreadySetMoveToStsTrue;

    if (!e.target.checked && !form.getFieldValue('name')) {
      this.resetNameToInitialValue();
    }

    if (shouldNotChangeTrulyMoveToSts || userAlreadySetMoveToStsTrue) {
      return;
    }

    form.setFieldsValue({moveToSts: e.target.checked});
  };

  handeOnClose = () => {
    if (this.formRef.current) {
      this.formRef.current.resetFields();
    }
  };

  render() {
    const {visible, onCancel, pipelines, pending, pipelineId} = this.props;
    const modalFooter = pending ? (
      false
    ) : (
      <Row>
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>
          Create
        </Button>
      </Row>
    );

    return (
      <Modal
        mask={{closable: !pending}}
        afterClose={this.handeOnClose}
        closable={!pending}
        open={visible}
        title="Create new rule"
        onCancel={onCancel}
        footer={modalFooter}
      >
        <Spin spinning={pending}>
          <Form
            ref={this.formRef}
            initialValues={{
              pipelineId: `${pipelineId}`,
              name: '',
              fileMask: undefined,
              isResult: false,
              moveToSts: false,
            }}
          >
            <Spin spinning={pipelines.pending}>
              <Form.Item
                {...this.formItemLayout}
                label={this.localizedString('Pipeline')}
                name="pipelineId"
                rules={[
                  {
                    required: true,
                    message: `Please select ${this.localizedString('pipeline')}`,
                  },
                ]}
              >
                <Select disabled={pending || pipelineId !== undefined}>
                  {pipelines?.value.map((pipeline) => (
                    <Select.Option key={pipeline.id} value={`${pipeline.id}`}>
                      {pipeline.name}
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Spin>
            <Form.Item noStyle dependencies={['isResult']}>
              {({getFieldValue}) => (
                <Form.Item
                  {...this.formItemLayout}
                  label="Name"
                  name="name"
                  rules={[
                    {
                      required: Boolean(getFieldValue('isResult')),
                      message: 'Name is required',
                    },
                  ]}
                >
                  <Input disabled={pending} onPressEnter={this.handleSubmit} />
                </Form.Item>
              )}
            </Form.Item>
            <Form.Item
              {...this.formItemLayout}
              label="File mask"
              name="fileMask"
              rules={[{required: true, message: 'File mask is required'}]}
            >
              <Input
                disabled={pending}
                ref={this.initializeNameInput}
                onPressEnter={this.handleSubmit}
              />
            </Form.Item>
            <Row type={'flex'} justify={'start'} gutter={24}>
              <Col {...this.checkboxWrapperLayout}>
                <Form.Item
                  {...this.checkboxLayout}
                  label="Pipeline results"
                  name="isResult"
                  valuePropName="checked"
                >
                  <Checkbox disabled={pending} onChange={this.handlePipelineResultsChange} />
                </Form.Item>
              </Col>
              <Form.Item noStyle dependencies={['isResult']}>
                {({getFieldValue}) => (
                  <Col {...this.checkboxWrapperLayout}>
                    <Form.Item
                      {...this.checkboxLayout}
                      label="Move to STS"
                      name="moveToSts"
                      valuePropName="checked"
                    >
                      <Checkbox disabled={getFieldValue('isResult') || pending} />
                    </Form.Item>
                  </Col>
                )}
              </Form.Item>
            </Row>
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

  componentDidUpdate(prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}
