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
import {observer} from 'mobx-react';
import {Button, Form, Input, Modal, Row, Col, Select, Collapse, Spin} from 'antd';
import {ensureDayjs} from '../../../../../../../utils/dayjs';
import {NotificationForm, TransitionsForm} from '../../forms';
import compareArrays from '../../../../../../../utils/compareArrays';
import styles from './life-cycle-edit-modal.module.css';

const formItemLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 6},
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 14},
  },
};

const DESTINATIONS = {
  GLACIER_IR: 'S3 Glacier Instant Retrieval',
  GLACIER: 'S3 Glacier Flexible Retrieval',
  DEEP_ARCHIVE: 'S3 Glacier Deep Archive',
  DELETION: 'Deletion',
};

const METHODS = {
  ONE_BY_ONE: 'One by one',
  EARLIEST_FILE: 'By the earliest file',
  LATEST_FILE: 'By the latest file',
};

const CRITERIA = {
  DEFAULT: 'Default',
  MATCHING_FILES: 'Files matches',
};

const PANELS = {
  transitions: 'Transitions',
  notify: 'Notify',
};

function FormSection({children, title = '', disabled = false, expanded, onChange}) {
  return (
    <Collapse
      activeKey={expanded ? [title] : []}
      style={{marginBottom: '5px'}}
      onChange={() => onChange(title)}
    >
      <Collapse.Panel header={title} key={title} disabled={disabled}>
        <div className={styles.formSection}>{children}</div>
      </Collapse.Panel>
    </Collapse>
  );
}

@observer
class LifeCycleEditModal extends React.Component {
  formRef = React.createRef();

  state = {
    initialRule: null,
    useDefaultNotify: undefined,
    expandedPanels: [PANELS.transitions, PANELS.notify],
  };

  componentDidMount() {
    this.setInitialRule();
  }

  componentDidUpdate(prevProps) {
    if (this.props.rule !== prevProps.rule) {
      this.setInitialRule();
    }
  }

  get modified() {
    const {initialRule} = this.state;
    const {createNewRule} = this.props;
    const form = this.formRef.current;
    if (createNewRule) {
      return true;
    }
    if (!initialRule || !form) {
      return false;
    }
    const {notification = {}} = initialRule;
    const stringFieldModified = (formPath, initialValue) => {
      const fieldValue = form.getFieldValue(formPath);
      return `${initialValue}` !== `${fieldValue}`;
    };
    const objectFieldModified = (formPath, initialValue = {}) => {
      const fieldValue = form.getFieldValue(formPath) || {};
      const current = Object.values(fieldValue);
      const initial = Object.values(initialValue);
      return !!current
        .filter((x) => !initial.includes(x))
        .concat(initial.filter((x) => !current.includes(x))).length;
    };
    const arrayFieldModified = (formPath, initialValue, comparerFn = (a, b) => a === b) => {
      const fieldValue = form.getFieldValue(formPath);
      return !compareArrays(fieldValue, initialValue, comparerFn);
    };
    const transitionsModified = () => {
      const transitions = (form.getFieldValue('transitions') || []).filter(Boolean);
      const initialTransitions = initialRule.transitions || [];
      if (transitions.length !== initialTransitions.length) {
        return true;
      }
      return transitions.some((current, index) => {
        const initial = initialTransitions[index] || {};
        return (
          current.storageClass !== initial.storageClass ||
          `${current.transitionAfterDays}` !== `${initial.transitionAfterDays}` ||
          (ensureDayjs(current.transitionDate)?.diff(ensureDayjs(initial.transitionDate), 'day') ??
            0) !== 0
        );
      });
    };
    return (
      stringFieldModified('notification.body', notification.body) ||
      stringFieldModified('notification.disabled', !notification.enabled) ||
      stringFieldModified('notification.notifyUsers', notification.notifyUsers) ||
      stringFieldModified('notification.notifyBeforeDays', notification.notifyBeforeDays) ||
      stringFieldModified('notification.prolongDays', notification.prolongDays) ||
      stringFieldModified('notification.subject', notification.subject) ||
      stringFieldModified('objectGlob', initialRule.objectGlob) ||
      stringFieldModified('pathGlob', initialRule.pathGlob) ||
      objectFieldModified('transitionCriterion', initialRule.transitionCriterion) ||
      stringFieldModified('transitionMethod', initialRule.transitionMethod) ||
      transitionsModified() ||
      arrayFieldModified(
        'notification.recipients',
        (initialRule.notification || {}).recipients,
        (a, b) => a.name === b.name && a.principal === b.principal,
      )
    );
  }

  get notificationsDisabled() {
    const form = this.formRef.current;
    return form ? form.getFieldValue('notification.disabled') : undefined;
  }

  get useDefaultNotify() {
    const {useDefaultNotify} = this.state;
    const {rule} = this.props;
    const {notification = {}} = rule || {};
    if (useDefaultNotify === undefined) {
      return notification.subject === undefined && notification.body === undefined;
    }
    return useDefaultNotify;
  }

  get showMatches() {
    const form = this.formRef.current;
    const criteriaType = form ? form.getFieldValue('transitionCriterion.type') : undefined;
    return CRITERIA[criteriaType] === CRITERIA.MATCHING_FILES;
  }

  get showNotificationsForm() {
    const form = this.formRef.current;
    const method = form ? form.getFieldValue('transitionMethod') : undefined;
    return METHODS[method] !== METHODS.ONE_BY_ONE;
  }

  getNotificationPayload = (ruleNotification = {}, formNotification = {}) => {
    const form = this.formRef.current;
    const method = form ? form.getFieldValue('transitionMethod') : undefined;
    const notification = {
      ...ruleNotification,
      ...formNotification,
    };
    notification.enabled = !formNotification.disabled;
    delete notification.disabled;
    if (METHODS[method] === METHODS.ONE_BY_ONE) {
      notification.enabled = false;
    }
    if (!formNotification.disabled && this.useDefaultNotify) {
      delete notification.body;
      delete notification.subject;
    }
    return notification;
  };

  handleSubmit = (e) => {
    const {onOk} = this.props;
    e.preventDefault();
    this.formRef.current
      .validateFields()
      .then((values) => {
        const {rule = {}} = this.props;
        const {
          objectGlob,
          pathGlob,
          notification,
          transitionMethod,
          transitionCriterion,
          transitions,
        } = values;
        const payload = Object.assign({}, rule, {
          objectGlob,
          pathGlob,
          transitionMethod,
          transitionCriterion,
        });
        delete payload.prolongations;
        payload.notification = this.getNotificationPayload(rule.notification, notification);
        if (transitions && transitions.length) {
          payload.transitions = transitions.filter(Boolean).map((transition) => {
            const {transitionAfterDays, transitionDate, storageClass} = transition;
            return {
              storageClass,
              ...(transitionDate && {
                transitionDate: ensureDayjs(transitionDate).format('YYYY-MM-DD'),
              }),
              ...(transitionAfterDays !== undefined && {transitionAfterDays}),
            };
          });
        }
        if (onOk) {
          onOk(payload, rule.id);
        }
      })
      .catch((err) => {
        if (err && err.errorFields) {
          if (err.errorFields.some((f) => f.name && f.name[0] === 'notification')) {
            this.expandPanel(PANELS.notify);
          }
          if (err.errorFields.some((f) => f.name && f.name[0] === 'transitions')) {
            this.expandPanel(PANELS.transitions);
          }
        }
      });
  };

  setInitialRule = () => {
    const {rule} = this.props;
    this.setState({initialRule: rule});
  };

  onCancel = () => {
    const {onCancel} = this.props;
    if (this.formRef.current) {
      this.formRef.current.resetFields();
    }
    if (onCancel) {
      onCancel();
    }
  };

  expandPanel = (key) => {
    const {expandedPanels} = this.state;
    if (!expandedPanels.includes(key)) {
      this.setState({expandedPanels: [...expandedPanels, key]});
    }
  };

  collapsePanel = (key) => {
    const {expandedPanels} = this.state;
    this.setState({expandedPanels: expandedPanels.filter((k) => k !== key)});
  };

  onTogglePanel = (key) => {
    const {expandedPanels} = this.state;
    if (expandedPanels.includes(key)) {
      return this.collapsePanel(key);
    }
    this.expandPanel(key);
  };

  onChangeMethod = (key) => {
    const form = this.formRef.current;
    const {initialRule} = this.state;
    if (!form) return;
    if (METHODS[key] === METHODS.ONE_BY_ONE) {
      form.setFields({
        'notification.disabled': {
          value: true,
          errors: undefined,
        },
      });
      return this.collapsePanel(PANELS.notify);
    }
    return form.setFields({
      'notification.disabled': {
        value:
          (initialRule.notification || {}).enabled !== undefined
            ? !initialRule.notification.enabled
            : false,
        errors: undefined,
      },
    });
  };

  onChangeUseDefaultNotify = (checked) => {
    this.setState({useDefaultNotify: checked}, () => {
      if (this.formRef.current) {
        this.formRef.current.setFieldsValue({});
      }
    });
  };

  render() {
    const {visible, rule, createNewRule, pending} = this.props;
    const {expandedPanels} = this.state;
    if (!rule) {
      return null;
    }
    const notification = rule.notification || {};
    const transitions =
      rule.transitions && rule.transitions.length
        ? rule.transitions.map((t) => ({
            ...t,
            transitionDate: t.transitionDate ? ensureDayjs(t.transitionDate) : undefined,
          }))
        : [{}];
    const initialValues = {
      pathGlob: rule.pathGlob,
      objectGlob: rule.objectGlob,
      transitionMethod: rule.transitionMethod || 'ONE_BY_ONE',
      'transitionCriterion.type': rule.transitionCriterion
        ? rule.transitionCriterion.type
        : 'DEFAULT',
      'transitionCriterion.value': rule.transitionCriterion
        ? rule.transitionCriterion.value
        : undefined,
      transitions,
      notification: {
        disabled: notification.enabled !== undefined ? !notification.enabled : false,
        recipients: notification.recipients || [],
        notifyUsers: notification.notifyUsers !== undefined ? notification.notifyUsers : false,
        notifyBeforeDays: notification.notifyBeforeDays,
        prolongDays: notification.prolongDays,
        subject: notification.subject,
        body: notification.body,
      },
    };
    return (
      <Modal
        open={visible}
        onCancel={this.onCancel}
        title={`${createNewRule ? 'Create' : 'Edit'} transition rule`}
        width="70%"
        style={{maxWidth: '1100px', top: 20}}
        footer={
          <Row type="flex" justify="end">
            <Button onClick={this.onCancel}>CANCEL</Button>
            <Button type="primary" onClick={this.handleSubmit} disabled={pending || !this.modified}>
              SAVE
            </Button>
          </Row>
        }
      >
        <Spin spinning={pending}>
          <Form ref={this.formRef} initialValues={initialValues}>
            <Row type="flex" justify="space-between">
              <Col style={{width: '50%'}}>
                <Form.Item
                  {...formItemLayout}
                  label="Root path"
                  className={styles.formItem}
                  name="pathGlob"
                  rules={[{required: true, message: ' '}]}
                >
                  <Input disabled={!createNewRule} />
                </Form.Item>
                <Form.Item
                  {...formItemLayout}
                  label="Glob"
                  className={styles.formItem}
                  name="objectGlob"
                  rules={[{required: true, message: ' '}]}
                >
                  <Input disabled={!createNewRule} />
                </Form.Item>
              </Col>
              <Col style={{width: '50%'}}>
                <Form.Item
                  {...formItemLayout}
                  label="Method"
                  className={styles.formItem}
                  name="transitionMethod"
                  rules={[{required: true, message: ' '}]}
                >
                  <Select onChange={this.onChangeMethod}>
                    {Object.entries(METHODS).map(([key, description]) => (
                      <Select.Option value={key} key={key}>
                        {description}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>
                <Form.Item
                  {...formItemLayout}
                  label="Condition"
                  className={styles.formItem}
                  name="transitionCriterion.type"
                  rules={[{required: true, message: ' '}]}
                >
                  <Select>
                    {Object.entries(CRITERIA).map(([key, description]) => (
                      <Select.Option value={key} key={key}>
                        {description}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>
                {this.showMatches ? (
                  <Form.Item
                    {...formItemLayout}
                    className={styles.formItem}
                    label="Matches"
                    name="transitionCriterion.value"
                    rules={[{required: true, message: ' '}]}
                  >
                    <Input />
                  </Form.Item>
                ) : null}
              </Col>
            </Row>
            <FormSection
              title={PANELS.transitions}
              expanded={expandedPanels.includes(PANELS.transitions)}
              onChange={this.onTogglePanel}
            >
              <TransitionsForm form={this.formRef.current} rule={rule} />
            </FormSection>
            <FormSection
              title={PANELS.notify}
              disabled={!this.showNotificationsForm}
              expanded={this.showNotificationsForm && expandedPanels.includes(PANELS.notify)}
              onChange={this.onTogglePanel}
            >
              <NotificationForm
                form={this.formRef.current}
                rule={rule}
                notificationsDisabled={this.notificationsDisabled}
                useDefaultNotify={this.useDefaultNotify}
                onChangeUseDefaultNotify={this.onChangeUseDefaultNotify}
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
  pending: PropTypes.bool,
};

export {DESTINATIONS};
export default LifeCycleEditModal;
