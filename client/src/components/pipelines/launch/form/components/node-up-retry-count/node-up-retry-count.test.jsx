/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Form} from 'antd';
import {render, screen, change} from '@test';
import NodeUpRetryCountFormItem from './index.jsx';
import {
  getNodeUpRetryCountParameter,
  getMaxNodeUpRetryCount
} from '../../utilities/launch-cluster';

const preferences = {allowNodeUpRetryCount: true, defaultNodeUpRetryCount: 5};

// The field is always rendered by a form that owns `getFieldDecorator`;
// `onForm` hands that form to the test, so it can read the value the
// launch form and the tool settings would send
class Harness extends React.Component {
  render () {
    const {form, onForm, initialValue, disabled, max} = this.props;
    onForm(form);
    return (
      <Form>
        <NodeUpRetryCountFormItem
          getFieldDecorator={form.getFieldDecorator}
          initialValue={initialValue}
          disabled={disabled}
          max={max}
        />
      </Form>
    );
  }
}

const HarnessForm = Form.create()(Harness);

function renderField (props = {}) {
  let form;
  render(
    <HarnessForm
      onForm={(f) => { form = f; }}
      max={getMaxNodeUpRetryCount(preferences)}
      {...props}
    />
  );
  return {
    getForm: () => form,
    input: screen.getByLabelText('Capacity retries')
  };
}

function validate (form) {
  return new Promise((resolve) => {
    form.validateFields((errors) => resolve(errors));
  });
}

function sentParameter (form) {
  return getNodeUpRetryCountParameter(form.getFieldValue('nodeUpRetryCount'), preferences);
}

test('shows a stored value, and sends it even when it equals the preference', () => {
  const {getForm, input} = renderField({initialValue: '5'});

  expect(input).toHaveValue('5');
  expect(sentParameter(getForm())).toEqual({type: 'int', required: false, value: 5});
});

test('is required: an empty value fails validation and nothing is sent', async () => {
  const {getForm, input} = renderField({initialValue: '5'});

  await change(input, '');

  expect(await validate(getForm())).toBeTruthy();
  expect(await screen.findByText('Capacity retries is required')).toBeInTheDocument();
  expect(sentParameter(getForm())).toBeUndefined();
});

test('a pre-filled default validates and is sent', async () => {
  const {getForm, input} = renderField({initialValue: `${preferences.defaultNodeUpRetryCount}`});

  expect(input).toHaveValue('5');
  expect(await validate(getForm())).toBeFalsy();
  expect(sentParameter(getForm())).toEqual({type: 'int', required: false, value: 5});
});

test('rejects a value that is not a positive integer', async () => {
  const {getForm, input} = renderField({initialValue: '5'});

  await change(input, '0');

  expect(await validate(getForm())).toBeTruthy();
  expect(await screen.findByText('Please enter a valid positive integer number'))
    .toBeInTheDocument();
});

test('accepts 999, and rejects a value above it', async () => {
  const {getForm, input} = renderField({initialValue: '5'});

  await change(input, '999');
  expect(await validate(getForm())).toBeFalsy();
  expect(sentParameter(getForm())).toEqual({type: 'int', required: false, value: 999});

  await change(input, '1000');
  expect(await validate(getForm())).toBeTruthy();
  expect(await screen.findByText('Maximum value is 999')).toBeInTheDocument();
  expect(sentParameter(getForm())).toBeUndefined();
});

test('takes its bound from the max prop, so a higher preference raises it', async () => {
  const {getForm, input} = renderField({initialValue: '5', max: 5000});

  await change(input, '1000');
  expect(await validate(getForm())).toBeFalsy();

  await change(input, '5001');
  expect(await validate(getForm())).toBeTruthy();
  expect(await screen.findByText('Maximum value is 5000')).toBeInTheDocument();
});

test('is disabled when asked to be', () => {
  renderField({initialValue: '12', disabled: true});

  expect(screen.getByLabelText('Capacity retries')).toBeDisabled();
});
