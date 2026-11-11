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

/* eslint-env jest */

import React from 'react';
import {observable} from 'mobx';
import {renderWithStores, screen, click, change, cleanup, waitFor, fn} from '@test';
import SystemParametersBrowser from './SystemParametersBrowser';

const loaded = (value) => observable({loaded: true, pending: false, value});

const systemParameters = [
  {name: 'CP_PARAMETER_A', type: 'STRING', defaultValue: 'a'},
  {name: 'CP_PARAMETER_B', type: 'BOOLEAN', defaultValue: 'true'}
];

// The dialog is never unmounted: its parent shows and hides it with `visible`,
// and re-renders it whenever the parent's own state changes
class Parent extends React.Component {
  state = {
    visible: false,
    renders: 0,
    notToShow: []
  };

  open = () => this.setState({visible: true});

  close = () => this.setState({visible: false});

  reRender = () => this.setState({renders: this.state.renders + 1});

  addParameterA = () => this.setState({notToShow: ['CP_PARAMETER_A']});

  onSave = (parameters) => {
    this.props.onSave(parameters);
    this.close();
  };

  render () {
    return (
      <div>
        <button onClick={this.open}>Open dialog</button>
        <button onClick={this.reRender}>Re-render parent</button>
        <button onClick={this.addParameterA}>Add CP_PARAMETER_A to the parent</button>
        <SystemParametersBrowser
          visible={this.state.visible}
          onCancel={this.close}
          onSave={this.onSave}
          notToShow={this.state.notToShow}
        />
      </div>
    );
  }
}

// Unmounts before the harness's own cleanup does, and waits: an antd Modal that is
// still open on unmount removes its DOM only after the close animation
afterEach(async () => {
  cleanup();
  await waitFor(() => expect(screen.queryAllByRole('dialog', {hidden: true})).toHaveLength(0));
});

async function openDialog (onSave = fn()) {
  renderWithStores(<Parent onSave={onSave} />, {
    runDefaultParameters: loaded(systemParameters),
    authenticatedUserInfo: loaded({admin: false, roles: []})
  });
  await click(screen.getByRole('button', {name: 'Open dialog'}));
  return onSave;
}

test('keeps the selected parameters when the parent re-renders', async () => {
  const onSave = await openDialog();

  await click(screen.getByText('CP_PARAMETER_A'));
  await click(screen.getByRole('button', {name: 'Re-render parent'}));

  expect(screen.getByRole('button', {name: 'OK (1)'})).toBeEnabled();

  await click(screen.getByText('CP_PARAMETER_B'));
  await click(screen.getByRole('button', {name: 'OK (2)'}));

  expect(onSave).toHaveBeenCalledTimes(1);
  expect(onSave.mock.calls[0][0].map((p) => p.name))
    .toEqual(['CP_PARAMETER_A', 'CP_PARAMETER_B']);
});

test('keeps the search string when the parent re-renders', async () => {
  await openDialog();

  await change(screen.getByPlaceholderText('Parameter'), 'CP_PARAMETER_B');
  await click(screen.getByRole('button', {name: 'Re-render parent'}));

  expect(screen.getByPlaceholderText('Parameter')).toHaveValue('CP_PARAMETER_B');
  expect(screen.queryByText('CP_PARAMETER_A')).not.toBeInTheDocument();
  expect(screen.getByText('CP_PARAMETER_B')).toBeInTheDocument();
});

test('does not save a selected parameter that the parent adds meanwhile', async () => {
  const onSave = await openDialog();

  await click(screen.getByText('CP_PARAMETER_A'));
  await click(screen.getByText('CP_PARAMETER_B'));
  await click(screen.getByRole('button', {name: 'Add CP_PARAMETER_A to the parent'}));

  expect(screen.queryByText('CP_PARAMETER_A')).not.toBeInTheDocument();

  await click(screen.getByRole('button', {name: 'OK (1)'}));

  expect(onSave).toHaveBeenCalledTimes(1);
  expect(onSave.mock.calls[0][0].map((p) => p.name)).toEqual(['CP_PARAMETER_B']);
});

test('clears the selection and the search string when the dialog is opened again', async () => {
  await openDialog();

  await change(screen.getByPlaceholderText('Parameter'), 'CP_PARAMETER_A');
  await click(screen.getByText('CP_PARAMETER_A'));
  await click(screen.getByRole('button', {name: 'Cancel'}));
  await click(screen.getByRole('button', {name: 'Open dialog'}));

  expect(screen.getByRole('button', {name: 'OK'})).toBeDisabled();
  expect(screen.getByPlaceholderText('Parameter')).toHaveValue('');
  expect(screen.getByText('CP_PARAMETER_B')).toBeInTheDocument();
});
