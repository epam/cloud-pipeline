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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {Alert, Button, DatePicker, Form, Input, Radio, Select} from 'antd';
import type {FormInstance} from 'antd';
import {DeleteOutlined, PlusOutlined} from '@ant-design/icons';
import {ensureDayjs} from '../../../../../../utils/dayjs';
import {DESTINATIONS} from '../modals';
import type {Rule, TransitionItem} from '../types';
import styles from './life-cycle-forms.module.css';

const TRANSITION_PERIOD = {
  after: 'after',
  at: 'at',
} as const;

type TransitionPeriod = (typeof TRANSITION_PERIOD)[keyof typeof TRANSITION_PERIOD];

const LIMIT_TRANSITIONS = 4;

interface TransitionsFormProps {
  form: FormInstance;
  rule: Rule;
}

export default function TransitionsForm({form, rule}: TransitionsFormProps) {
  const [transitions, setTransitions] = useState<(TransitionItem | undefined)[]>(() =>
    rule?.transitions?.length ? rule.transitions : [{}],
  );
  const [userDefinedDateTypes, setUserDefinedDateTypes] = useState<
    Record<number, TransitionPeriod>
  >({});

  useEffect(() => {
    if (!rule) return;
    setTransitions(rule.transitions?.length ? rule.transitions : [{}]);
    setUserDefinedDateTypes({});
    form.setFieldsValue({});
  }, [rule, form]);

  const removeTransitionsEnabled = useMemo(
    () => transitions.filter(Boolean).length > 1,
    [transitions],
  );

  const limitTransitionsReached = useMemo(
    () => transitions.filter(Boolean).length >= LIMIT_TRANSITIONS,
    [transitions],
  );

  const transitionsValues = Form.useWatch('transitions', form);

  const formDestinations = useMemo(
    () =>
      (transitionsValues || []).map(
        (t: TransitionItem | undefined) => t?.storageClass,
      ),
    [transitionsValues],
  );

  const addTransitionRule = useCallback(
    (entry: TransitionItem = {}) => {
      setTransitions(prev => [...prev, entry]);
      form.setFieldsValue({});
    },
    [form],
  );

  const removeTransitionRule = useCallback(
    (key: number) => {
      if (!removeTransitionsEnabled) return;
      setTransitions(prev => {
        const next = [...prev];
        next[key] = undefined;
        return next;
      });
      form.setFieldsValue({});
    },
    [form, removeTransitionsEnabled],
  );

  const onChangeTransitionDateType = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>, index: number) => {
      const periodType = event.target.value as TransitionPeriod;
      setUserDefinedDateTypes(prev => ({...prev, [index]: periodType}));
      form.setFields([
        {name: ['transitions', index, 'transitionAfterDays'], value: undefined, errors: []},
        {name: ['transitions', index, 'transitionDate'], value: undefined, errors: []},
      ]);
    },
    [form],
  );

  const getTransitionDateType = useCallback(
    (index: number): TransitionPeriod => {
      if (userDefinedDateTypes[index]) return userDefinedDateTypes[index];
      const t = transitions[index];
      if (t && t.transitionDate) return TRANSITION_PERIOD.at;
      return TRANSITION_PERIOD.after;
    },
    [transitions, userDefinedDateTypes],
  );

  const showGlacierIrWarning = formDestinations.some(
    (v: string | undefined) =>
      DESTINATIONS[v as keyof typeof DESTINATIONS] === DESTINATIONS.GLACIER_IR,
  );

  return (
    <div style={{display: 'flex', flexDirection: 'column'}}>
      <div>
        {transitions.map((transition, index) => {
          if (!transition) return null;
          const dateType = getTransitionDateType(index);
          return (
            <div key={`transitionRule-${index}`} className={styles.transitionRuleRow}>
              <div style={{display: 'flex', alignItems: 'center'}}>
                <span style={{margin: '0px 5px 0px 10px', fontWeight: 'bold'}}>Destination:</span>
                <Form.Item
                  className={styles.transitionFormItem}
                  style={{marginRight: 15}}
                  name={['transitions', index, 'storageClass']}
                  rules={[{required: true, message: ' '}]}
                >
                  <Select
                    className={styles.destinationSelect}
                    options={Object.entries(DESTINATIONS).map(([value, label]) => ({
                      value,
                      label,
                    }))}
                  />
                </Form.Item>
              </div>
              <div className={styles.transitionDateBlock}>
                <span style={{marginRight: 10, fontWeight: 'bold'}}>Date:</span>
                <div style={{display: 'flex'}}>
                  <div style={{display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                    <Radio
                      onChange={e => onChangeTransitionDateType(e, index)}
                      style={{marginRight: 0}}
                      value={TRANSITION_PERIOD.after}
                      checked={dateType === TRANSITION_PERIOD.after}
                    >
                      After
                    </Radio>
                    <Form.Item
                      className={styles.transitionFormItem}
                      name={['transitions', index, 'transitionAfterDays']}
                      rules={[
                        {required: dateType === TRANSITION_PERIOD.after, message: ' '},
                      ]}
                    >
                      <Input
                        style={{minWidth: '35px'}}
                        disabled={dateType !== TRANSITION_PERIOD.after}
                      />
                    </Form.Item>
                    <span style={{margin: '0 15px 0 5px'}}>days</span>
                  </div>
                  <div style={{display: 'flex', alignItems: 'center'}}>
                    <Radio
                      onChange={e => onChangeTransitionDateType(e, index)}
                      style={{marginRight: 0}}
                      value={TRANSITION_PERIOD.at}
                      checked={dateType === TRANSITION_PERIOD.at}
                    >
                      At
                    </Radio>
                    <Form.Item
                      className={styles.transitionFormItem}
                      name={['transitions', index, 'transitionDate']}
                      getValueProps={v => ({value: ensureDayjs(v)})}
                      getValueFromEvent={d => ensureDayjs(d)}
                      rules={[{required: dateType === TRANSITION_PERIOD.at, message: ' '}]}
                    >
                      <DatePicker
                        disabled={dateType !== TRANSITION_PERIOD.at}
                        style={{marginRight: 15, minWidth: 90}}
                      />
                    </Form.Item>
                  </div>
                </div>
              </div>
              <Button
                danger
                onClick={() => removeTransitionRule(index)}
                className={styles.deleteTransitionBtn}
                disabled={!removeTransitionsEnabled}
              >
                <DeleteOutlined />
              </Button>
            </div>
          );
        })}
        <Button
          onClick={() => addTransitionRule({})}
          className={styles.addTransitionRuleBtn}
          disabled={limitTransitionsReached}
        >
          <PlusOutlined />
          Add
        </Button>
      </div>
      {showGlacierIrWarning ? (
        <Alert
          title={
            <p>
              Due to the AWS restrictions, files smaller than <b>128 kB</b> will not be transitioned
              to the <b>Glacier Instant Retrieval</b> layer
            </p>
          }
          type="warning"
        />
      ) : null}
    </div>
  );
}
