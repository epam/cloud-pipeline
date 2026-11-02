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
import {observable} from 'mobx';
import {
  renderWithStores,
  screen,
  within,
  change,
  clear,
  fn,
  stubApi,
  flushPromises
} from '@test';
import UserAutoComplete from './UserAutoComplete';

// Covers the `onClear` prop only - the contract the Downloads modal's Owner
// column filter (components/special/downloads/async-tasks-list.js) depends
// on, and the reason this file exists at all. That column's own suite drives
// `onClear` through the real field, but only end to end: if `findUser` were
// refactored to early-return on an empty value, or to report the clear after
// its lookup rather than before, the Owner filter would lose its only
// clearing gesture with that suite still green, because nothing there
// asserts the prop where it is defined. Five callers reach `findUser` on
// every empty search, so the contract is worth pinning here.
//
// This component's other behaviour - searching, rendering candidates,
// selecting one - is deliberately NOT covered: it is pre-existing and not
// what the change that added `onClear` touched.
//
// It carries its own `@inject('authenticatedUserInfo')` (for the "(you)"
// marker on a candidate), and mobx-react's `inject` throws when that store
// is absent from context, so every case renders through `renderWithStores`.
// A plain `observable` object is a sufficient double (rule 10).
function authenticatedUserInfoDouble (userName = 'current-user') {
  return observable({loaded: true, value: {userName}});
}

function renderControl (props = {}) {
  return renderWithStores(
    <UserAutoComplete {...props} />,
    {authenticatedUserInfo: authenticatedUserInfoDouble()}
  );
}

// `combobox` is the role antd puts on the control's own wrapper; the
// `textbox` inside it is the field the user types in.
function input () {
  return within(screen.getByRole('combobox')).getByRole('textbox');
}

// Every case stubs the API: `findUser` issues a `UserFind` request on every
// search, the empty string included, and the unit suite's network guard
// turns a real one into an immediate failure. Awaiting `flushPromises`
// afterwards lets that request settle while the component is still mounted -
// this component sets state when it resolves and has no unmount guard, so
// without it the run carries a "setState on an unmounted component" warning.
describe('onClear', () => {
  test('fires when the field is emptied by hand', async () => {
    stubApi();
    const onClear = fn();
    renderControl({value: 'someone-else', onClear});

    await clear(input());
    await flushPromises();

    expect(onClear).toHaveBeenCalledTimes(1);
  });

  test('is called with no arguments - it reports the clear, not a value', async () => {
    stubApi();
    const onClear = fn();
    renderControl({value: 'someone-else', onClear});

    await clear(input());
    await flushPromises();

    expect(onClear.mock.calls[0]).toEqual([]);
  });

  test('does not fire while the field still holds text', async () => {
    stubApi();
    const onClear = fn();
    renderControl({value: 'someone-else', onClear});

    await change(input(), 'som');
    await flushPromises();

    expect(onClear).not.toHaveBeenCalled();
  });

  // The split between the two callbacks, which is the whole reason `onClear`
  // had to be added: `onChange` fires only from `onUserSelect`, so it can
  // report a user who was picked but never the absence of one. A caller
  // watching `onChange` alone cannot tell "cleared" from "unchanged".
  test('does not reach onChange, which only ever reports a selection', async () => {
    stubApi();
    const onChange = fn();
    const onClear = fn();
    renderControl({value: 'someone-else', onChange, onClear});

    await clear(input());
    await flushPromises();

    expect(onClear).toHaveBeenCalledTimes(1);
    expect(onChange).not.toHaveBeenCalled();
  });

  // Opt-in: the callers that predate it (for example
  // components/runs/run-table/columns/column-owner.js) pass no `onClear`,
  // and must keep behaving exactly as before rather than throwing on an
  // emptied field.
  test('an emptied field does not throw when no onClear is passed', async () => {
    stubApi();
    renderControl({value: 'someone-else'});

    await clear(input());
    await flushPromises();

    expect(input()).toBeInTheDocument();
  });

  // Reported before the lookup resolves, and regardless of what it answers -
  // emptying the field is a decision the user has already made. `stubApi`
  // with no route registered for `UserFind` is what makes the request fail
  // here.
  test('fires even when the lookup that follows it fails', async () => {
    stubApi();
    const onClear = fn();
    renderControl({value: 'someone-else', onClear});

    await clear(input());

    // Asserted BEFORE the pending request is allowed to settle.
    expect(onClear).toHaveBeenCalledTimes(1);

    await flushPromises();
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});
