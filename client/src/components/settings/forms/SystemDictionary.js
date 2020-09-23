/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Form, Icon, Input, Row} from 'antd';

import styles from './SystemDictionary.css';
import compareArrays from '../../../utils/compareArrays';

@Form.create()
@observer
export default class SystemDictionary extends React.Component {
  static propTypes = {
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    dictionaryName: PropTypes.string,
    values: PropTypes.array
  };

  componentDidUpdate (prevProps) {
    if (prevProps.dictionaryName !== this.props.dictionaryName) {
      this.resetFormFields();
    }
  }

  @computed
  get modified () {
    const {dictionaryName} = this.props;
    const nameValue = this.props.form.getFieldValue('name');
    const values = Object.values(this.props.form.getFieldValue('values') || {});
    return nameValue !== dictionaryName || !compareArrays(values, this.props.values);
  }

  resetFormFields = () => {
    this.props.form && this.props.form.resetFields();
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, formValues) => {
      if (!err) {
        const {name, values} = formValues;
        this.props.onSubmit && this.props.onSubmit(name, values);
      }
    });
  };

  addValue = () => {
    const {getFieldValue, setFieldsValue} = this.props.form;
    const keys = getFieldValue('keys');
    const newId = Math.max(-1, ...keys.map(k => +k)) + 1;
    const nextKeys = keys.concat(newId);

    setFieldsValue({
      keys: nextKeys
    });
  };

  removeItem = (k) => {
    const keys = this.props.form.getFieldValue('keys');

    this.props.form.setFieldsValue({
      keys: keys.filter(key => key !== k)
    });
  };

  render () {
    if (!this.props.dictionaryName) {
      return null;
    }
    const {resetFields, getFieldDecorator, getFieldValue} = this.props.form;
    const {dictionaryName, values} = this.props;
    getFieldDecorator('keys', {initialValue: (values || []).map((value, index) => index)});
    const keys = getFieldValue('keys');
    return (
      <div className={styles.container}>
        <div className={styles.wrapper}>
          <Form layout="horizontal">
            <Form.Item
              key="name"
              label="Name"
              className={styles.nameFormItem}
              wrapperCol={{className: styles.nameWrapperCol}}
            >
              {getFieldDecorator('name', {
                initialValue: dictionaryName,
                rules: [{
                  required: true,
                  message: 'Required'
                }]
              })(
                <Input placeholder="Name" size="small" />
              )}
            </Form.Item>
            {
              keys.map((k) => (
                <Row
                  key={k}
                  type="flex"
                  className={styles.valueRow}
                >
                  <Form.Item className={styles.valueFormItem}>
                    {getFieldDecorator(`values.${k}`, {
                      initialValue: values[k]
                    })(
                      <Input
                        size="small"
                        placeholder="Value"
                        disabled={this.props.pending}
                      />
                    )}
                  </Form.Item>
                  <Icon
                    className={styles.removeValue}
                    type="minus-circle-o"
                    onClick={() => this.removeItem(k)}
                  />
                </Row>
              ))
            }
          </Form>
        </div>
        <Row className={styles.actions} type="flex" justify="end">
          <Button
            disabled={this.props.pending}
            size="small"
            onClick={this.addValue}
          >
            Add value
          </Button>
          <Button
            disabled={!this.modified}
            size="small"
            onClick={() => resetFields()}
          >
            Revert
          </Button>
          <Button
            disabled={!this.modified}
            type="primary"
            size="small"
            onClick={this.handleSubmit}
          >
            Save
          </Button>
        </Row>
      </div>
    );
  }
}
