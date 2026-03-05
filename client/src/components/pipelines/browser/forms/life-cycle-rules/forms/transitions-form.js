/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Form,
  DatePicker,
  Radio,
  Select,
  Input,
  Button,
  Alert
} from 'antd';
import {DeleteOutlined, PlusOutlined} from '@ant-design/icons';
import {momentToDayjs, dayjsToMoment} from '../../../../../../utils/antd-date-utils';
import {DESTINATIONS} from '../modals';
import styles from './life-cycle-forms.css';

const TRANSITION_PERIOD = {
  after: 'after',
  at: 'at'
};

const LIMIT_TRANSITIONS = 4;

class TransitionsForm extends React.Component {
  state = {
    transitions: [],
    userDefinedDateTypes: {}
  }

  get removeTransitionsEnabled () {
    const {transitions = []} = this.state;
    return transitions.filter(Boolean).length > 1;
  }

  get limitTransitionsReached () {
    const {transitions = []} = this.state;
    return transitions.filter(Boolean).length >= LIMIT_TRANSITIONS;
  }

  get formDestinations () {
    const {transitions} = this.state;
    const {form} = this.props;
    if (!form) return [];
    return transitions
      .map((_, index) => form.getFieldValue(['transitions', index, 'storageClass']));
  }

  componentDidMount () {
    this.setFormInitialValues();
  }

  componentDidUpdate (prevProps) {
    if (this.props.rule && prevProps.rule !== this.props.rule) {
      this.setFormInitialValues();
    }
  }

  addTransitionRule = (rule = {}) => {
    const {transitions} = this.state;
    const {form} = this.props;
    this.setState({transitions: [...transitions, rule]}, () => {
      form && form.setFieldsValue({});
    });
  };

  removeTransitionRule = (key) => {
    const {transitions} = this.state;
    const {form} = this.props;
    if (this.removeTransitionsEnabled) {
      const transitionsState = [...transitions];
      transitionsState[key] = undefined;
      this.setState({transitions: transitionsState}, () => {
        form && form.setFieldsValue({});
      });
    }
  };

  onChangeTransitionDateType = (event, index) => {
    const {form} = this.props;
    const {userDefinedDateTypes} = this.state;
    const periodType = event.target.value;
    const dateTypes = {...userDefinedDateTypes};
    dateTypes[index] = periodType;
    this.setState({userDefinedDateTypes: dateTypes}, () => {
      if (form) {
        form.setFields([
          {
            name: ['transitions', index, 'transitionAfterDays'],
            value: undefined,
            errors: undefined
          },
          {
            name: ['transitions', index, 'transitionDate'],
            value: undefined,
            errors: undefined
          }
        ]);
      }
    });
  };

  setFormInitialValues = () => {
    const {rule, form} = this.props;
    if (!rule) {
      return;
    }
    const blankRule = {};
    this.setState({
      transitions: rule.transitions && rule.transitions.length
        ? rule.transitions
        : [blankRule]
    }, () => form && form.setFieldsValue({}));
  };

  getTransitionDateType = (index) => {
    const {transitions, userDefinedDateTypes} = this.state;
    if (userDefinedDateTypes[index]) {
      return userDefinedDateTypes[index];
    } else if (transitions[index] && transitions[index].transitionDate) {
      return TRANSITION_PERIOD.at;
    }
    return TRANSITION_PERIOD.after;
  };

  renderTransitionWarnings = () => {
    if (this.formDestinations.some(value => DESTINATIONS[value] === DESTINATIONS.GLACIER_IR)) {
      return (
        <Alert
          message={(
            <p>
              Due to the AWS restrictions, files smaller
              than <b>128 kB</b> will not be transitioned to
              the <b>Glacier Instant Retrieval</b> layer
            </p>
          )}
          type="warning"
        />
      );
    }
    return null;
  };

  render () {
    const {form} = this.props;
    const {transitions} = this.state;
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div>
          {(transitions || []).map((transition, index) => {
            if (!transition) {
              return null;
            }
            return (
              <div
                key={`transitionRule-${index}`}
                className={styles.transitionRuleRow}
              >
                <div style={{display: 'flex', alignItems: 'center'}}>
                  <span style={{margin: '0px 5px 0px 10px', fontWeight: 'bold'}}>
                    Destination:
                  </span>
                  <Form.Item
                    className={styles.transitionFormItem}
                    style={{marginRight: 15}}
                    name={['transitions', index, 'storageClass']}
                    rules={[{
                      required: true,
                      message: ' '
                    }]}
                  >
                    <Select className={styles.destinationSelect}>
                      {Object.entries(DESTINATIONS).map(([key, description]) => (
                        <Select.Option
                          value={key}
                          key={key}
                        >
                          {description}
                        </Select.Option>
                      ))}
                    </Select>
                  </Form.Item>
                </div>
                <div className={styles.transitionDateBlock}>
                  <span style={{marginRight: 10, fontWeight: 'bold'}}>
                    Date:
                  </span>
                  <div style={{display: 'flex'}}>
                    <div style={{display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                      <Radio
                        onChange={e => this.onChangeTransitionDateType(e, index)}
                        style={{marginRight: 0}}
                        value={TRANSITION_PERIOD.after}
                        checked={this.getTransitionDateType(index) === TRANSITION_PERIOD.after}
                      >
                        After
                      </Radio>
                      <Form.Item
                        className={styles.transitionFormItem}
                        name={['transitions', index, 'transitionAfterDays']}
                        rules={[{
                          required: this.getTransitionDateType(index) === TRANSITION_PERIOD.after,
                          message: ' '
                        }]}
                      >
                        <Input
                          style={{minWidth: '35px'}}
                          disabled={this.getTransitionDateType(index) !== TRANSITION_PERIOD.after}
                        />
                      </Form.Item>
                      <span style={{margin: '0 15px 0 5px'}}>
                        days
                      </span>
                    </div>
                    <div style={{display: 'flex', alignItems: 'center'}}>
                      <Radio
                        onChange={e => this.onChangeTransitionDateType(e, index)}
                        style={{marginRight: 0}}
                        value={TRANSITION_PERIOD.at}
                        checked={this.getTransitionDateType(index) === TRANSITION_PERIOD.at}
                      >
                        At
                      </Radio>
                      <Form.Item
                        className={styles.transitionFormItem}
                        name={['transitions', index, 'transitionDate']}
                        getValueProps={(v) => ({value: momentToDayjs(v)})}
                        getValueFromEvent={(d) => dayjsToMoment(d)}
                        rules={[{
                          required: this.getTransitionDateType(index) === TRANSITION_PERIOD.at,
                          message: ' '
                        }]}
                      >
                        <DatePicker
                          disabled={this.getTransitionDateType(index) !== TRANSITION_PERIOD.at}
                          style={{marginRight: 15, minWidth: 90}}
                        />
                      </Form.Item>
                    </div>
                  </div>
                </div>
                <Button
                  danger
                  onClick={() => this.removeTransitionRule(index)}
                  className={styles.deleteTransitionBtn}
                  disabled={!this.removeTransitionsEnabled}
                >
                  <DeleteOutlined />
                </Button>
              </div>
            );
          })}
          <Button
            onClick={() => this.addTransitionRule({})}
            className={styles.addTransitionRuleBtn}
            disabled={this.limitTransitionsReached}
          >
            <PlusOutlined />
            Add
          </Button>
        </div>
        {this.renderTransitionWarnings()}
      </div>
    );
  }
}

TransitionsForm.propTypes = {
  form: PropTypes.object,
  rule: PropTypes.object
};

export default TransitionsForm;
