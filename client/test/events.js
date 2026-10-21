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

// React-version-bearing. React 15 delegates every handler from a single
// document-level listener, so dispatching a real bubbling DOM event on the node
// reaches it. This file is what gets rewritten over @testing-library/user-event
// after the React upgrade; the call sites — `await click(node)`,
// `await change(node, value)` — do not change.
//
// Every helper is async even though React 15 flushes synchronously. That is
// deliberate: user-event's API is async, and React 18 batches updates, so a
// call site written `await click(...)` today keeps working and one written
// without the await does not.

import {fireEvent} from '@testing-library/dom';

// One microtask turn, so a handler that resolved a promise has been observed by
// the time the helper returns.
function settle () {
  return Promise.resolve();
}

async function dispatch (type, node, init) {
  fireEvent[type](node, init);
  await settle();
}

export async function click (node, init) {
  await dispatch('click', node, init);
}

export async function dblClick (node, init) {
  await dispatch('dblClick', node, init);
}

/**
 * Sets a value on an input, select or textarea and fires `change`.
 *
 * @testing-library/dom assigns through the prototype's own value setter, which
 * bypasses the tracker React installs on the node — that is what makes React
 * see the value as changed and call onChange.
 */
export async function change (node, value) {
  await dispatch('change', node, {target: {value}});
}

export async function clear (node) {
  await change(node, '');
}

/**
 * Types text one character at a time, appending to whatever the node already
 * holds, with the keydown/keyup pair around each change. Controlled inputs need
 * the accumulated value on every event, not the single final one.
 */
export async function type (node, text) {
  let value = node.value === undefined || node.value === null ? '' : String(node.value);
  const characters = String(text).split('');
  for (let index = 0; index < characters.length; index += 1) {
    const character = characters[index];
    value += character;
    fireEvent.keyDown(node, {key: character});
    fireEvent.change(node, {target: {value}});
    fireEvent.keyUp(node, {key: character});
    await settle();
  }
}

export async function keyDown (node, init) {
  await dispatch('keyDown', node, init);
}

export async function keyUp (node, init) {
  await dispatch('keyUp', node, init);
}

export async function submit (node, init) {
  await dispatch('submit', node, init);
}

export async function focus (node, init) {
  await dispatch('focus', node, init);
}

export async function blur (node, init) {
  await dispatch('blur', node, init);
}

/**
 * Hovering fires `mouseover`/`mouseout`, not `mouseenter`/`mouseleave`: the
 * latter do not bubble, so React's document-level listener never sees them —
 * React synthesises onMouseEnter from mouseover itself. Anything built on
 * rc-trigger (antd's Tooltip, Dropdown, Popover) only responds to this pair.
 */
export async function hover (node, init) {
  fireEvent.mouseOver(node, init);
  fireEvent.mouseEnter(node, init);
  await settle();
}

export async function unhover (node, init) {
  fireEvent.mouseOut(node, init);
  fireEvent.mouseLeave(node, init);
  await settle();
}
