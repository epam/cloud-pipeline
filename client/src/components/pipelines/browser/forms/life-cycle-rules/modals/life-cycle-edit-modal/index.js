/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  Button,
  Input,
  Form,
  Modal,
  Row,
  Col,
  Select,
  Collapse,
  Spin
} from 'antd';
import moment from 'moment-timezone';
import {NotificationForm, TransitionsForm} from '../../forms';
import compareArrays from '../../../../../../../utils/compareArrays';
import styles from './life-cycle-edit-modal.css';

const formItemLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 6}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 14}
  }
};

const DESTINATIONS = {
  GLACIER_IR: 'S3 Glacier Instant Retrieval',
  GLACIER: 'S3 Glacier Flexible Retrieval (formally Glacier)',
  DEEP_ARCHIVE: 'S3 Glacier Deep Archive',
  DELETION: 'Deletion'
};

const METHODS = {
  ONE_BY_ONE: 'One by one',
  EARLIEST_FILE: 'By the earliest file',
  LATEST_FILE: 'By the latest file'
};

const CRITERIA = {
  DEFAULT: 'Default',
  MATCHING_FILES: 'Files matches'
};

function FormSection ({
  children,
  title = '',
  defaultExpanded = true
}) {
  return (
    <Collapse
      defaultActiveKey={defaultExpanded ? [title] : []}
      style={{marginBottom: '5px'}}
    >
      <Collapse.Panel header={title} key={title}>
        <div className={styles.formSection}>
          {children}
        </div>
      </Collapse.Panel>
    </Collapse>
  );
}

@inject('usersInfo')
@observer
class LifeCycleEditModal extends React.Component {
  state={
    initialRule: null
  }

  componentDidMount () {
    this.setInitialRule();
  }

  componentDidUpdate (prevProps) {
    if (this.props.rule !== prevProps.rule) {
      this.setInitialRule();
    }
  }

  setInitialRule = () => {
    const {rule} = this.props;
    this.setState({initialRule: rule});
  };

  @computed
  get usersInfo () {
    const {usersInfo} = this.props;
    if (usersInfo.loaded) {
      return usersInfo.value;
    }
    return [];
  }

  get modified () {
    const {initialRule} = this.state;
    const {form} = this.props;
    if (!initialRule) {
      return false;
    }
    const {notification} = initialRule;
    const stringFieldModified = (formPath, initialValue) => {
      const fieldValue = form.getFieldValue(formPath);
      return `${initialValue}` !== `${fieldValue}`;
    };
    const arrayFieldModified = (
      formPath,
      initialValue,
      comparerFn = ((a, b) => a === b)
    ) => {
      const fieldValue = form.getFieldValue(formPath);
      return !compareArrays(initialValue, fieldValue, comparerFn);
    };
    // TODO: add informedIserIds check when API change ids to user\group objects
    return stringFieldModified('notification.body', notification.body) ||
      stringFieldModified('notification.disabled', !notification.enabled) ||
      stringFieldModified('notification.notifyBeforeDays', notification.notifyBeforeDays) ||
      stringFieldModified('notification.prolongDays', notification.prolongDays) ||
      stringFieldModified('notification.subject', notification.subject) ||
      stringFieldModified('objectGlob', initialRule.objectGlob) ||
      stringFieldModified('pathGlob', initialRule.pathGlob) ||
      stringFieldModified('transitionCriterion', initialRule.transitionCriterion) ||
      stringFieldModified('transitionMethod', initialRule.transitionMethod) ||
      arrayFieldModified(
        'transitions',
        initialRule.transitions,
        (a, b) => (a.storageClass === b.storageClass &&
          a.transitionAfterDays === b.transitionAfterDays)
      );
  }

  get showMatches () {
    const {form} = this.props;
    const criteriaType = form.getFieldValue('transitionCriterion.type');
    return CRITERIA[criteriaType] === CRITERIA.MATCHING_FILES;
  }

  handleSubmit = (e) => {
    const {form, onOk} = this.props;
    e.preventDefault();
    form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        const {rule} = this.props;
        const {
          objectGlob,
          pathGlob,
          notification,
          transitionMethod,
          transitionCriterion,
          transitions
        } = values;
        const payload = {
          objectGlob,
          pathGlob,
          transitionMethod,
          transitionCriterion
        };
        if (notification) {
          payload.notification = notification;
          payload.notification.enabled = !notification.disabled;
          delete payload.notification.disabled;
        }
        if (notification && notification.informedUserIds) {
          const recipientsIds = this.getIdsFromUsers(notification.informedUserIds);
          payload.notification.informedUserIds = recipientsIds;
        }
        if (transitions && transitions.length) {
          payload.transitions = transitions
            .filter(Boolean)
            .map(transition => {
              const {
                transitionAfterDays,
                transitionDate,
                storageClass
              } = transition;
              return {
                storageClass,
                ...(transitionDate && {
                  transitionDate: moment(transitionDate).format('YYYY-MM-DD')
                }),
                ...(transitionAfterDays && {transitionAfterDays})
              };
            });
        }
        onOk && onOk(payload, rule.id);
      }
    });
  };

  getIdsFromUsers = (users = []) => {
    return users.map(user => {
      const userInfo = this.usersInfo.find(info => info.name === user.name);
      if (userInfo) {
        return userInfo.id;
      }
      return undefined;
    }).filter(Boolean);
  };

  onCancel = () => {
    const {onCancel, form} = this.props;
    form.resetFields();
    onCancel && onCancel();
  };

  render () {
    const {
      visible,
      rule,
      form,
      createNewRule
    } = this.props;
    const {pending} = this.props;
    const {getFieldDecorator} = form;
    if (!rule) {
      return null;
    }
    return (
      <Modal
        visible={visible}
        onCancel={this.onCancel}
        title={`${createNewRule ? 'Create' : 'Edit'} transition rule`}
        width="70%"
        style={{maxWidth: '1100px', top: 20}}
        footer={
          <Row type="flex" justify="end">
            <Button onClick={this.onCancel}>
              Cancel
            </Button>
            <Button
              type="primary"
              onClick={this.handleSubmit}
              disabled={pending || !this.modified}
            >
              Save
            </Button>
          </Row>
        }
      >
        <Spin spinning={pending}>
          <Form>
            <Row
              type="flex"
              justify="space-between"
            >
              <Col style={{width: '50%'}}>
                <Form.Item
                  {...formItemLayout}
                  label="Root path"
                  className={styles.formItem}
                >
                  {getFieldDecorator('pathGlob', {
                    initialValue: rule && rule.pathGlob
                      ? rule.pathGlob
                      : undefined,
                    rules: [{
                      required: true,
                      message: ' '
                    }]
                  })(
                    <Input
                      disabled={!createNewRule}
                    />
                  )}
                </Form.Item>
                <Form.Item
                  {...formItemLayout}
                  label="Glob"
                  className={styles.formItem}
                >
                  {getFieldDecorator('objectGlob', {
                    initialValue: rule && rule.objectGlob
                      ? rule.objectGlob
                      : undefined,
                    rules: [{
                      required: true,
                      message: ' '
                    }]
                  })(
                    <Input
                      disabled={!createNewRule}
                    />
                  )}
                </Form.Item>
              </Col>
              <Col style={{width: '50%'}}>
                <Form.Item
                  {...formItemLayout}
                  label="Method"
                  className={styles.formItem}
                >
                  {getFieldDecorator('transitionMethod', {
                    initialValue: rule && rule.transitionMethod
                      ? rule.transitionMethod
                      : 'ONE_BY_ONE',
                    rules: [{
                      required: true,
                      message: ' ',
                      validator: (rule, value, callback) => {
                        const notifyErrors = form
                          .getFieldError('notification.disabled') || [];
                        if (value !== 'ONE_BY_ONE' && notifyErrors.length) {
                          form.setFields({
                            'notification.disabled': {
                              errors: undefined
                            }
                          });
                        }
                        callback();
                      }
                    }]
                  })(
                    <Select>
                      {Object.entries(METHODS).map(([key, description]) => (
                        <Select.Option
                          value={key}
                          key={key}
                        >
                          {description}
                        </Select.Option>
                      ))}
                    </Select>
                  )}
                </Form.Item>
                <Form.Item
                  {...formItemLayout}
                  label="Condition"
                  className={styles.formItem}
                >
                  {getFieldDecorator('transitionCriterion.type', {
                    initialValue: rule && rule.transitionCriterion
                      ? rule.transitionCriterion.type
                      : 'DEFAULT',
                    rules: [{
                      required: true,
                      message: ' '
                    }]
                  })(
                    <Select>
                      {Object.entries(CRITERIA).map(([key, description]) => (
                        <Select.Option
                          value={key}
                          key={key}
                        >
                          {description}
                        </Select.Option>
                      ))}
                    </Select>
                  )}
                </Form.Item>
                {this.showMatches ? (
                  <Form.Item
                    {...formItemLayout}
                    className={styles.formItem}
                    label="Matches"
                  >
                    {getFieldDecorator('transitionCriterion.value', {
                      initialValue: rule && rule.transitionCriterion
                        ? rule.transitionCriterion.value
                        : undefined
                    })(
                      <Input />
                    )}
                  </Form.Item>
                ) : null}
              </Col>
            </Row>
            <FormSection title="Transitions">
              <TransitionsForm
                form={form}
                rule={rule}
              />
            </FormSection>
            <FormSection title="Notify">
              <NotificationForm
                form={form}
                rule={rule}
              />
            </FormSection>
          </Form>
        </Spin>
      </Modal>
    );
  }
}

LifeCycleEditModal.propTypes = {
  visible: PropTypes.bool,
  onOk: PropTypes.func,
  onCancel: PropTypes.func,
  rule: PropTypes.object,
  createNewRule: PropTypes.bool,
  pending: PropTypes.bool
};

export {DESTINATIONS};
export default Form.create()(LifeCycleEditModal);
