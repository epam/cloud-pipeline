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
  blur,
  waitFor,
  fn,
  stubApi,
  flushPromises
} from '@test';
import UserAutoComplete from './UserAutoComplete';

// Covers the `onTextChange` prop only - the contract the Downloads modal's
// Owner column filter (components/special/downloads/async-tasks-list.js)
// depends on, and the reason this file exists at all. That column's own suite
// drives `onTextChange` through the real field, but only end to end: if
// `findUser` were refactored to early-return on an empty value, or to report
// the text after its lookup rather than before, the Owner filter would lose
// both its only clearing gesture and its guard against committing a name
// nobody picked, with that suite still green, because nothing there asserts
// the prop where it is defined. The other caller of this component
// (components/runs/run-table/columns/column-owner.js) reaches the same
// `findUser` on every search while passing no `onTextChange` at all, which
// is the other half of the contract pinned below.
//
// This component's other behaviour - searching, rendering candidates,
// selecting one - is deliberately NOT covered: it is pre-existing and not
// what the change that added `onTextChange` touched.
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
describe('onTextChange', () => {
  test('reports the empty string when the field is emptied by hand', async () => {
    stubApi();
    const onTextChange = fn();
    renderControl({value: 'someone-else', onTextChange});

    await clear(input());
    await flushPromises();

    expect(onTextChange).toHaveBeenCalledTimes(1);
    expect(onTextChange).toHaveBeenCalledWith('');
  });

  // The other half of the contract, and what tells an emptied field from one
  // holding a name nobody has picked yet: the text goes out verbatim on every
  // keystroke, not only when it empties.
  test('reports the text while the field still holds some', async () => {
    stubApi();
    const onTextChange = fn();
    renderControl({value: 'someone-else', onTextChange});

    await change(input(), 'som');
    await flushPromises();

    expect(onTextChange).toHaveBeenCalledWith('som');
  });

  // The split between the two callbacks, which is the whole reason
  // `onTextChange` had to be added: `onChange` fires only from
  // `onUserSelect`, so it can report a user who was picked but never a field
  // the user is still typing in. A caller watching `onChange` alone cannot
  // tell "cleared" or "half-typed" from "unchanged".
  test('does not reach onChange, which only ever reports a selection', async () => {
    stubApi();
    const onChange = fn();
    const onTextChange = fn();
    renderControl({value: 'someone-else', onChange, onTextChange});

    await clear(input());
    await change(input(), 'som');
    await flushPromises();

    expect(onTextChange).toHaveBeenCalledTimes(2);
    expect(onChange).not.toHaveBeenCalled();
  });

  // Opt-in: the callers that predate it (for example
  // components/runs/run-table/columns/column-owner.js) pass no
  // `onTextChange`, and must keep behaving exactly as before rather than
  // throwing on a keystroke.
  test('typing does not throw when no onTextChange is passed', async () => {
    stubApi();
    renderControl({value: 'someone-else'});

    await clear(input());
    await flushPromises();

    expect(input()).toBeInTheDocument();
  });

  // Losing focus with nothing selected drops the typed text from the field:
  // `onBlur` clears the component's own `valueInput`, so the field falls
  // back to rendering the `value` prop. That revert has to be reported too,
  // or a caller tracking the typed text would go on acting on text the field
  // no longer shows.
  test('reports the value prop again when the field reverts to it on blur', async () => {
    stubApi();
    const onTextChange = fn();
    renderControl({value: 'someone-else', onTextChange});

    await change(input(), 'som');
    await flushPromises();
    await blur(input());

    // rc-select 6 runs its own blur handling on a 10ms timer (`blurTimer` in
    // node_modules/rc-select/lib/Select.js), so antd's `onBlur` - and this
    // component's own - land after the event itself, not with it.
    await waitFor(() => expect(onTextChange).toHaveBeenLastCalledWith('someone-else'));
  });

  // The same revert where there is no `value` prop to go back to: the field
  // ends up empty, so the empty string is what gets reported, not the text
  // that was typed.
  test('reports the empty string on blur when there is no value prop', async () => {
    stubApi();
    const onTextChange = fn();
    renderControl({onTextChange});

    await change(input(), 'som');
    await flushPromises();
    await blur(input());

    await waitFor(() => expect(onTextChange).toHaveBeenLastCalledWith(''));
  });

  // Reported before the lookup resolves, and regardless of what it answers -
  // what the user typed is already decided. `stubApi` with no route
  // registered for `UserFind` is what makes the request fail here.
  test('fires even when the lookup that follows it fails', async () => {
    stubApi();
    const onTextChange = fn();
    renderControl({value: 'someone-else', onTextChange});

    await clear(input());

    // Asserted BEFORE the pending request is allowed to settle.
    expect(onTextChange).toHaveBeenCalledTimes(1);

    await flushPromises();
    expect(onTextChange).toHaveBeenCalledTimes(1);
  });
});
