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

// React-version-bearing. Everything that knows this application renders with
// React 15 lives here, and nowhere else. When React is upgraded this file
// becomes a re-export of @testing-library/react and no test file changes.
//
// Components are rendered into a container attached to document.body, which is
// what makes `screen` work — and `screen` behaves identically before and after
// the upgrade.

import ReactDOM from 'react-dom';
import {getQueriesForElement} from '@testing-library/dom';

export {
  screen,
  within,
  waitFor,
  waitForElementToBeRemoved,
  getDefaultNormalizer
} from '@testing-library/dom';

const mountedContainers = new Set();

function unmount (container) {
  ReactDOM.unmountComponentAtNode(container);
  if (container.parentNode) {
    container.parentNode.removeChild(container);
  }
  mountedContainers.delete(container);
}

/**
 * Renders a React element into a fresh container attached to document.body.
 *
 * Returns the container, the queries scoped to it, and `rerender` / `unmount`.
 * Prefer the unscoped `screen` queries; the scoped ones are for the rare case
 * of two renders in one test.
 */
export function render (element, options = {}) {
  const container = options.container || document.createElement('div');
  if (!container.parentNode) {
    document.body.appendChild(container);
  }
  mountedContainers.add(container);
  ReactDOM.render(element, container);
  return Object.assign(
    {
      container,
      rerender: (next) => ReactDOM.render(next, container),
      unmount: () => unmount(container)
    },
    getQueriesForElement(container)
  );
}

/**
 * Unmounts everything `render` mounted and detaches its containers. Registered
 * as an `afterEach` below, so a test never has to call it.
 */
export function cleanup () {
  Array.from(mountedContainers).forEach(unmount);
}

/**
 * A pass-through today: React 15 has no `act`, and ReactDOM.render and its
 * event dispatch are synchronous. It exists so that call sites are already
 * written the way React 18 requires, and awaits the callback's result plus one
 * microtask turn so promise-based updates have settled on return.
 */
export async function act (callback) {
  const result = typeof callback === 'function' ? callback() : undefined;
  if (result && typeof result.then === 'function') {
    await result;
  }
  await Promise.resolve();
}

if (typeof afterEach === 'function') {
  afterEach(cleanup);
}
