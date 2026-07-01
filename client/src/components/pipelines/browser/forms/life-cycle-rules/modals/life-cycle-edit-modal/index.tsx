/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {useCallback, useEffect, useMemo, useReducer, useState} from 'react';
import {Button, Collapse, Form, Input, Modal, Row, Col, Select, Spin} from 'antd';
import {ensureDayjs} from '../../../../../../../utils/dayjs';
import {NotificationForm, TransitionsForm} from '../../forms';
import compareArrays from '../../../../../../../utils/compareArrays';
import type {
  FormNotification,
  LifeCycleEditModalProps,
  Recipient,
  Rule,
  RuleNotification,
  TransitionCriterion,
  TransitionItem,
} from '../../types';
import styles from './life-cycle-edit-modal.module.css';

const formItemLayout = {
  labelCol: {xs: {span: 24}, sm: {span: 6}},
  wrapperCol: {xs: {span: 24}, sm: {span: 14}},
};

const DESTINATIONS = {
  GLACIER_IR: 'S3 Glacier Instant Retrieval',
  GLACIER: 'S3 Glacier Flexible Retrieval',
  DEEP_ARCHIVE: 'S3 Glacier Deep Archive',
  DELETION: 'Deletion',
} as const;

const METHODS = {
  ONE_BY_ONE: 'One by one',
  EARLIEST_FILE: 'By the earliest file',
  LATEST_FILE: 'By the latest file',
} as const;

const CRITERIA = {
  DEFAULT: 'Default',
  MATCHING_FILES: 'Files matches',
} as const;

const PANELS = {
  transitions: 'Transitions',
  notify: 'Notify',
} as const;

type PanelKey = (typeof PANELS)[keyof typeof PANELS];

interface FormSectionProps {
  children: React.ReactNode;
  title: string;
  disabled?: boolean;
  expanded?: boolean;
  onChange: (title: string) => void;
}

function FormSection({children, title = '', disabled = false, expanded, onChange}: FormSectionProps) {
  return (
    <Collapse
      activeKey={expanded ? [title] : []}
      style={{marginBottom: '5px'}}
      onChange={() => onChange(title)}
      items={[
        {
          key: title,
          label: title,
          collapsible: disabled ? 'disabled' : undefined,
          children: <div className={styles.formSection}>{children}</div>,
        },
      ]}
    />
  );
}

function LifeCycleEditModal({
  visible,
  onOk,
  onCancel: onCancelProp,
  rule,
  createNewRule,
  pending,
}: LifeCycleEditModalProps) {
  const [form] = Form.useForm();
  const [initialRule, setInitialRule] = useState<Rule | null>(null);
  const [useDefaultNotifyOverride, setUseDefaultNotifyOverride] = useState<boolean | undefined>(
    undefined,
  );
  const [expandedPanels, setExpandedPanels] = useState<PanelKey[]>([
    PANELS.transitions,
    PANELS.notify,
  ]);
  // Re-render trigger wired to onFieldsChange so modified/formValid stay in sync with field edits.
  const [, forceUpdate] = useReducer((n: number) => n + 1, 0);

  // Watch fields that drive conditional rendering.
  const transitionMethod =
    Form.useWatch('transitionMethod', form) ?? (rule?.transitionMethod ?? 'ONE_BY_ONE');
  const criteriaType = Form.useWatch('transitionCriterion.type', form);
  const notificationDisabled = Form.useWatch(['notification', 'disabled'], form);

  const showNotificationsForm =
    METHODS[transitionMethod as keyof typeof METHODS] !== METHODS.ONE_BY_ONE;
  const showMatches =
    CRITERIA[criteriaType as keyof typeof CRITERIA] === CRITERIA.MATCHING_FILES;

  const useDefaultNotify = useMemo(
    () =>
      useDefaultNotifyOverride !== undefined
        ? useDefaultNotifyOverride
        : rule?.notification?.subject === undefined && rule?.notification?.body === undefined,
    [useDefaultNotifyOverride, rule],
  );

  useEffect(() => {
    setInitialRule(rule ?? null);
    setUseDefaultNotifyOverride(undefined);
    form.resetFields();
  }, [rule, form]);

  // Computed inline on every render — forceUpdate (via onFieldsChange) keeps it fresh.
  const modified = (() => {
    if (createNewRule) return true;
    if (!initialRule) return false;

    const {notification = {}} = initialRule;

    const stringFieldModified = (path: string | string[], initial: unknown): boolean =>
      `${initial}` !== `${form.getFieldValue(path)}`;

    const arrayFieldModified = (
      path: string | string[],
      initial: unknown[] | undefined,
      comparerFn: (a: unknown, b: unknown) => boolean = (a, b) => a === b,
    ): boolean => !compareArrays(form.getFieldValue(path), initial, comparerFn);

    const transitionsModified = (): boolean => {
      const transitions = ((form.getFieldValue('transitions') as TransitionItem[]) ?? []).filter(
        Boolean,
      );
      const initialTransitions = initialRule.transitions ?? [];
      if (transitions.length !== initialTransitions.length) return true;
      return transitions.some((cur, i) => {
        const init = initialTransitions[i] ?? {};
        return (
          cur.storageClass !== init.storageClass ||
          `${cur.transitionAfterDays}` !== `${init.transitionAfterDays}` ||
          (ensureDayjs(cur.transitionDate)?.diff(ensureDayjs(init.transitionDate), 'day') ?? 0) !==
            0
        );
      });
    };

    return (
      stringFieldModified(['notification', 'body'], notification.body) ||
      stringFieldModified(['notification', 'disabled'], !notification.enabled) ||
      stringFieldModified(['notification', 'notifyUsers'], notification.notifyUsers) ||
      stringFieldModified(['notification', 'notifyBeforeDays'], notification.notifyBeforeDays) ||
      stringFieldModified(['notification', 'prolongDays'], notification.prolongDays) ||
      stringFieldModified(['notification', 'subject'], notification.subject) ||
      stringFieldModified('objectGlob', initialRule.objectGlob) ||
      stringFieldModified('pathGlob', initialRule.pathGlob) ||
      stringFieldModified('transitionCriterion.type', initialRule.transitionCriterion?.type) ||
      stringFieldModified('transitionCriterion.value', initialRule.transitionCriterion?.value) ||
      stringFieldModified('transitionMethod', initialRule.transitionMethod) ||
      transitionsModified() ||
      arrayFieldModified(
        ['notification', 'recipients'],
        initialRule.notification?.recipients,
        (a: unknown, b: unknown) => {
          const ra = a as Recipient;
          const rb = b as Recipient;
          return ra.name === rb.name && ra.principal === rb.principal;
        },
      )
    );
  })();

  const formValid = (() => {
    const hasErrors = form.getFieldsError().some(f => f.errors.length > 0);
    const pathGlob = form.getFieldValue('pathGlob');
    const objectGlob = form.getFieldValue('objectGlob');
    const criteriaValue = form.getFieldValue('transitionCriterion.value');
    return !hasErrors && !!pathGlob && !!objectGlob && (!showMatches || !!criteriaValue);
  })();

  const expandPanel = useCallback(
    (key: PanelKey) => setExpandedPanels(prev => (prev.includes(key) ? prev : [...prev, key])),
    [],
  );

  const onTogglePanel = useCallback(
    (key: string) =>
      setExpandedPanels(prev =>
        prev.includes(key as PanelKey)
          ? prev.filter(k => k !== key)
          : [...prev, key as PanelKey],
      ),
    [],
  );

  const getNotificationPayload = useCallback(
    (ruleNotification: RuleNotification = {}, formNotification: FormNotification = {}) => {
      const {disabled, ...rest} = formNotification;
      const method: string = form.getFieldValue('transitionMethod');
      const notification: RuleNotification = {
        ...ruleNotification,
        ...rest,
        enabled: !disabled,
      };
      if (METHODS[method as keyof typeof METHODS] === METHODS.ONE_BY_ONE) {
        notification.enabled = false;
      }
      if (!disabled && useDefaultNotify) {
        delete notification.body;
        delete notification.subject;
      }
      return notification;
    },
    [form, useDefaultNotify],
  );

  const handleSubmit = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      form
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .validateFields()
        .then((values: Record<string, any>) => {
          const {objectGlob, pathGlob, notification, transitionMethod: method, transitions} = values;
          const transitionCriterion: TransitionCriterion = {
            type: values['transitionCriterion.type'],
            value: values['transitionCriterion.value'],
          };
          const payload: Rule = {
            ...rule,
            objectGlob,
            pathGlob,
            transitionMethod: method,
            transitionCriterion,
          };
          delete payload.prolongations;
          payload.notification = getNotificationPayload(rule?.notification, notification);
          if (transitions?.length) {
            payload.transitions = (transitions as (TransitionItem & {transitionDate?: object})[])
              .filter(Boolean)
              .map(t => ({
                storageClass: t.storageClass,
                ...(t.transitionDate && {
                  // ensureDayjs is non-null here because t.transitionDate is truthy
                  transitionDate: ensureDayjs(t.transitionDate)!.format('YYYY-MM-DD'),
                }),
                ...(t.transitionAfterDays !== undefined && {
                  transitionAfterDays: t.transitionAfterDays,
                }),
              }));
          }
          onOk?.(payload, rule?.id);
        })
        .catch((err: {errorFields?: {name: string[]}[]}) => {
          if (!err?.errorFields) return;
          if (err.errorFields.some(f => f.name?.[0] === 'notification')) expandPanel(PANELS.notify);
          if (err.errorFields.some(f => f.name?.[0] === 'transitions'))
            expandPanel(PANELS.transitions);
        });
    },
    [form, rule, getNotificationPayload, onOk, expandPanel],
  );

  const onCancel = useCallback(() => {
    form.resetFields();
    onCancelProp?.();
  }, [form, onCancelProp]);

  const onChangeMethod = useCallback(
    (key: string) => {
      if (METHODS[key as keyof typeof METHODS] === METHODS.ONE_BY_ONE) {
        form.setFields([{name: 'notification.disabled', value: true, errors: []}]);
        setExpandedPanels(prev => prev.filter(k => k !== PANELS.notify));
        return;
      }
      form.setFields([
        {
          name: 'notification.disabled',
          value:
            initialRule?.notification?.enabled !== undefined
              ? !initialRule.notification.enabled
              : false,
          errors: [],
        },
      ]);
    },
    [form, initialRule],
  );

  const onChangeUseDefaultNotify = useCallback(
    (checked: boolean) => {
      setUseDefaultNotifyOverride(checked);
      form.setFieldsValue({});
    },
    [form],
  );

  if (!rule) return null;

  const {notification = {}} = rule;
  const transitions = rule.transitions?.length
    ? rule.transitions.map(t => ({
        ...t,
        transitionDate: t.transitionDate ? ensureDayjs(t.transitionDate) : undefined,
      }))
    : [{}];

  const initialValues = {
    pathGlob: rule.pathGlob,
    objectGlob: rule.objectGlob,
    transitionMethod: rule.transitionMethod ?? 'ONE_BY_ONE',
    'transitionCriterion.type': rule.transitionCriterion?.type ?? 'DEFAULT',
    'transitionCriterion.value': rule.transitionCriterion?.value,
    transitions,
    notification: {
      disabled: notification.enabled !== undefined ? !notification.enabled : false,
      recipients: notification.recipients ?? [],
      notifyUsers: notification.notifyUsers ?? false,
      notifyBeforeDays: notification.notifyBeforeDays,
      prolongDays: notification.prolongDays,
      subject: notification.subject,
      body: notification.body,
    },
  };

  return (
    <Modal
      open={visible}
      onCancel={onCancel}
      title={`${createNewRule ? 'Create' : 'Edit'} transition rule`}
      width="70%"
      style={{maxWidth: '1100px', top: 20}}
      footer={
        <Row justify="end">
          <Button onClick={onCancel}>CANCEL</Button>
          <Button
            type="primary"
            onClick={handleSubmit}
            disabled={pending || !modified || !formValid}
          >
            SAVE
          </Button>
        </Row>
      }
    >
      <Spin spinning={pending}>
        <Form form={form} initialValues={initialValues} onFieldsChange={() => forceUpdate()}>
          <Row justify="space-between">
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
                <Select
                  onChange={onChangeMethod}
                  options={Object.entries(METHODS).map(([value, label]) => ({value, label}))}
                />
              </Form.Item>
              <Form.Item
                {...formItemLayout}
                label="Condition"
                className={styles.formItem}
                name="transitionCriterion.type"
                rules={[{required: true, message: ' '}]}
              >
                <Select
                  options={Object.entries(CRITERIA).map(([value, label]) => ({value, label}))}
                />
              </Form.Item>
              {showMatches && (
                <Form.Item
                  {...formItemLayout}
                  className={styles.formItem}
                  label="Matches"
                  name="transitionCriterion.value"
                  rules={[{required: true, message: ' '}]}
                >
                  <Input />
                </Form.Item>
              )}
            </Col>
          </Row>
          <FormSection
            title={PANELS.transitions}
            expanded={expandedPanels.includes(PANELS.transitions)}
            onChange={onTogglePanel}
          >
            <TransitionsForm form={form} rule={rule} />
          </FormSection>
          <FormSection
            title={PANELS.notify}
            disabled={!showNotificationsForm}
            expanded={showNotificationsForm && expandedPanels.includes(PANELS.notify)}
            onChange={onTogglePanel}
          >
            <NotificationForm
              form={form}
              rule={rule}
              notificationsDisabled={notificationDisabled}
              useDefaultNotify={useDefaultNotify}
              onChangeUseDefaultNotify={onChangeUseDefaultNotify}
            />
          </FormSection>
        </Form>
      </Spin>
    </Modal>
  );
}

export type {Rule, LifeCycleEditModalProps} from '../../types';
export {DESTINATIONS};
export default LifeCycleEditModal;
