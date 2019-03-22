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
import {Checkbox, Button, Modal, Form, Input, Row, Select, Spin} from 'antd';
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

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit(values);
      }
    });
  };

  render() {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button onClick={this.props.onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>Create</Button>
      </Row>
    );
    const onClose = () => {
      resetFields();
    };
    return (
      <Modal maskClosable={!this.props.pending}
             afterClose={() => onClose()}
             closable={!this.props.pending}
             visible={this.props.visible}
             title="Create new rule"
             onCancel={this.props.onCancel}
             footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form>
            <Spin spinning={this.props.pipelines.pending}>
              <Form.Item {...this.formItemLayout} label={this.localizedString('Pipeline')}>
                {getFieldDecorator('pipelineId',
                  {
                    rules: [{required: true, message: `Please select ${this.localizedString('pipeline')}`}],
                    initialValue: `${this.props.pipelineId}`
                  })(
                  <Select disabled={this.props.pending || this.props.pipelineId !== undefined}>
                    {this.props.pipelines.value &&
                    this.props.pipelines.value.map(pipeline =>
                      <Select.Option key={pipeline.id}
                                     value={`${pipeline.id}`}>{pipeline.name}</Select.Option>)}
                  </Select>
                )}
              </Form.Item>
            </Spin>
            <Form.Item {...this.formItemLayout} label="File mask">
              {getFieldDecorator('fileMask', {rules: [{required: true, message: 'File mask is required'}]})(
                <Input
                  disabled={this.props.pending}
                  ref={this.initializeNameInput}
                  onPressEnter={this.handleSubmit} />
              )}
            </Form.Item>
            <Form.Item {...this.formItemLayout} label="Move to STS">
              {getFieldDecorator('moveToSts', {
                valuePropName: 'checked',
                initialValue: false
              })(
                <Checkbox disabled={this.props.pending}/>
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
