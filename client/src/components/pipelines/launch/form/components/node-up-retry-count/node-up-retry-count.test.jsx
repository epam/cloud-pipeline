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
import {render, screen, click, change, keyDown, waitFor} from '@test';
import NodeUpRetryCountFormItem from './index.jsx';
import {getNodeUpRetryCountParameter} from '../../utilities/launch-cluster';

const preferences = {allowNodeUpRetryCount: true, defaultNodeUpRetryCount: 5};

// The field is always rendered by a form that owns `getFieldDecorator`;
// `onForm` hands that form to the test, so it can read the value the
// launch form and the tool settings would send
class Harness extends React.Component {
  render () {
    const {form, onForm, initialValue, disabled} = this.props;
    onForm(form);
    return (
      <Form>
        <NodeUpRetryCountFormItem
          getFieldDecorator={form.getFieldDecorator}
          initialValue={initialValue}
          disabled={disabled}
          placeholder={`${preferences.defaultNodeUpRetryCount}`}
        />
      </Form>
    );
  }
}

const HarnessForm = Form.create()(Harness);

function renderField (props = {}) {
  let form;
  render(<HarnessForm onForm={(f) => { form = f; }} {...props} />);
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

test('is empty, with the preference value as the placeholder, when nothing is stored', async () => {
  const {getForm, input} = renderField();

  expect(input).toHaveValue('');
  expect(input).toHaveAttribute('placeholder', '5');
  expect(screen.queryByRole('button', {name: 'Clear'})).not.toBeInTheDocument();
  expect(await validate(getForm())).toBeFalsy();
  expect(sentParameter(getForm())).toBeUndefined();
});

test('shows a stored value, and sends it even when it equals the preference', () => {
  const {getForm, input} = renderField({initialValue: '5'});

  expect(input).toHaveValue('5');
  expect(sentParameter(getForm())).toEqual({type: 'int', required: false, value: 5});
});

test('clearing a stored value leaves it unset, so nothing is sent', async () => {
  const {getForm, input} = renderField({initialValue: '12'});

  await click(screen.getByRole('button', {name: 'Clear'}));

  await waitFor(() => expect(input).toHaveValue(''));
  expect(screen.queryByRole('button', {name: 'Clear'})).not.toBeInTheDocument();
  expect(sentParameter(getForm())).toBeUndefined();
});

test('the clear button is focusable, and clears the value on Enter', async () => {
  const {getForm, input} = renderField({initialValue: '12'});
  const clear = screen.getByRole('button', {name: 'Clear'});

  expect(clear).toHaveAttribute('tabindex', '0');
  await keyDown(clear, {key: 'Enter'});

  await waitFor(() => expect(input).toHaveValue(''));
  expect(sentParameter(getForm())).toBeUndefined();
});

test('rejects a value that is not a positive integer', async () => {
  const {getForm, input} = renderField();

  await change(input, '0');

  expect(await validate(getForm())).toBeTruthy();
  expect(await screen.findByText('Please enter a valid positive integer number'))
    .toBeInTheDocument();
});

test('offers no clear button when disabled', () => {
  renderField({initialValue: '12', disabled: true});

  expect(screen.getByLabelText('Capacity retries')).toBeDisabled();
  expect(screen.queryByRole('button', {name: 'Clear'})).not.toBeInTheDocument();
});
