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
import {Link} from 'react-router';
import PropTypes from 'prop-types';
import {Button, Checkbox, Col, Form, Input, Modal, Row, Spin, Tabs} from 'antd';
import PermissionsForm from '../../roleModel/PermissionsForm';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';

@Form.create()
@localization.localizedComponent
export default class EditRegistryForm extends localization.LocalizedReactComponent {

  state = {
    activeTab: 'info',
    displayCredentials: false,
    displayCertificateInput: false,
    certificateValue: null
  };

  static propTypes = {
    registry: PropTypes.shape({
      path: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number,
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      externalUrl: PropTypes.string,
      pipelineAuth: PropTypes.bool,
      userName: PropTypes.string,
      password: PropTypes.string
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    onDelete: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
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
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        if (!this.state.displayCertificateInput && this.fileInputControl &&
          this.fileInputControl.files && this.fileInputControl.files[0]) {
          const fileReader = new FileReader();
          const onSubmit = this.props.onSubmit;
          fileReader.onload = function (fileLoadedEvent) {
            values.certificate = fileLoadedEvent.target.result;
            onSubmit(values);
          };
          fileReader.readAsText(this.fileInputControl.files[0], 'UTF-8');
        } else if (this.state.displayCertificateInput && this.state.certificateValue) {
          values.certificate = this.state.certificateValue;
          this.props.onSubmit(values);
        } else {
          this.props.onSubmit(values);
        }
      }
    });
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  displayCredentials = () => {
    this.setState({displayCredentials: true});
  };

  onClickCertificateLink = () => {
    this.setState({displayCertificateInput: !this.state.displayCertificateInput});
  };

  renderCredentialsSection = () => {
    if (!this.props.registry || roleModel.isOwner(this.props.registry)) {
      const initializeFileInputControl = (input) => {
        this.fileInputControl = input;
      };

      if (this.state.displayCredentials) {
        const {getFieldDecorator} = this.props.form;
        return [
          <Form.Item
            key="user name"
            {...this.formItemLayout} label="User name">
            {getFieldDecorator('userName',
              {
                initialValue: this.props.registry ? this.props.registry.userName : undefined
              })(
                <Input disabled={this.props.pending} />
            )}
          </Form.Item>,
          <Form.Item
            key="password"
            {...this.formItemLayout} label="Password">
            {getFieldDecorator('password',
              {
                initialValue: this.props.registry ? this.props.registry.password : undefined
              })(
                <Input
                  type="password"
                  disabled={this.props.pending} />
            )}
          </Form.Item>,
          <Row key="upload control" type="flex">
            <Col xs={24} sm={6} style={{textAlign: 'right', paddingRight: 10}}>Certificate: </Col>
            <Col xs={24} sm={18}>
              <input
                disabled={this.state.displayCertificateInput}
                ref={initializeFileInputControl}
                style={{width: '100%'}}
                type="file" />
            </Col>
          </Row>,
          <Row style={{marginTop: 5, marginBottom: 5}} key="certificate link" type="flex">
            <Col xs={24} sm={6} />
            <Col xs={24} sm={18}>
              <Link
                id="certificate-link"
                onClick={this.onClickCertificateLink}
              >
                Insert certificate as plain text
              </Link>
            </Col>
          </Row>,
          this.state.displayCertificateInput &&
          <Row key="certificate input" type="flex">
            <Col xs={24} sm={6} />
            <Col xs={24} sm={18}>
              <Input
                value={this.state.certificateValue}
                onChange={(e) => this.setState({certificateValue: e.target.value})}
                type="textarea"
                autosize={{minRows: 4}}
              />
            </Col>
          </Row>,
          <Form.Item
            style={{marginTop: 20, marginBottom: 10}}
            key="external url"
            {...this.formItemLayout} label="External URL">
            {getFieldDecorator('externalUrl',
              {
                initialValue: this.props.registry ? this.props.registry.externalUrl : undefined
              })(
                <Input disabled={this.props.pending} />
            )}
          </Form.Item>,
          <Row key="pipeline auth">
            <Col xs={24} sm={6} />
            <Col xs={24} sm={18}>
              <Form.Item>
                {getFieldDecorator('pipelineAuth',
                  {
                    initialValue: this.props.registry ? this.props.registry.pipelineAuth : undefined,
                    valuePropName: 'checked'
                  })(
                  <Checkbox disabled={this.props.pending}>{this.localizedString('Pipeline')} authentication</Checkbox>
                )}
              </Form.Item>
            </Col>
          </Row>
        ];
      } else {
        return (
          <Row style={{textAlign: 'right'}}>
            <a onClick={this.displayCredentials}>Edit credentials</a>
          </Row>
        );
      }
    }
    return undefined;
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Col span={12}>
          <Row type="flex" justify="start">
            {
              this.props.registry &&
              <Button type="danger" onClick={this.props.onDelete}>DELETE</Button>
            }
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button onClick={this.props.onCancel}>CANCEL</Button>
            <Button
              type="primary" htmlType="submit"
              onClick={this.handleSubmit}>
              {this.props.registry ? 'SAVE' : 'ADD'}
            </Button>
          </Row>
        </Col>
      </Row>
    );
    const onClose = () => {
      resetFields();
      this.setState({activeTab: 'info', displayCredentials: false});
    };
    let title = 'Create registry';
    if (this.props.registry) {
      title = 'Edit registry';
    }
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={title}
        onCancel={this.props.onCancel}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending}>
          <Tabs
            size="small"
            activeKey={this.state.activeTab}
            onChange={this.onSectionChange}>
            <Tabs.TabPane key="info" tab="Info">
              <Form>
                <Form.Item {...this.formItemLayout} label="Path">
                  {getFieldDecorator('path',
                    {
                      rules: [{required: true, message: 'Path is required'}],
                      initialValue: this.props.registry ? this.props.registry.path : undefined
                    })(
                      <Input
                        ref={!this.props.registry ? this.initializeNameInput : null}
                        onPressEnter={this.handleSubmit}
                        disabled={this.props.pending || !!this.props.registry} />
                  )}
                </Form.Item>
                <Form.Item {...this.formItemLayout} style={{marginBottom: 10}} label="Description">
                  {getFieldDecorator('description',
                    {
                      initialValue: this.props.registry ? this.props.registry.description : undefined
                    })(
                      <Input
                        ref={!!this.props.registry ? this.initializeNameInput : null}
                        onPressEnter={this.handleSubmit}
                        disabled={this.props.pending} />
                  )}
                </Form.Item>
                <Row key="security scan enabled">
                  <Col xs={24} sm={6} />
                  <Col xs={24} sm={18}>
                    <Form.Item style={{marginBottom: 10}}>
                      {getFieldDecorator('securityScanEnabled',
                        {
                          initialValue:
                            this.props.registry ? this.props.registry.securityScanEnabled : true,
                          valuePropName: 'checked'
                        })(
                        <Checkbox>Require security scanning</Checkbox>
                      )}
                    </Form.Item>
                  </Col>
                </Row>
                {this.renderCredentialsSection()}
              </Form>
            </Tabs.TabPane>
            {
              this.props.registry && roleModel.isOwner(this.props.registry) &&
              <Tabs.TabPane key="permissions" tab="Permissions">
                <PermissionsForm
                  objectIdentifier={this.props.registry.id}
                  objectType="DOCKER_REGISTRY" />
              </Tabs.TabPane>
            }
          </Tabs>
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
