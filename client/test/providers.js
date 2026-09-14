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

// MobX- and router-bearing. A test file must never hand-roll a <Provider> or a
// <Router>: MobX 3 -> 6 and react-router 3 -> 6 both rewrite these wrappers, and
// they should rewrite one file.

import React from 'react';
import {Provider} from 'mobx-react';
import {Router, Route, createMemoryHistory} from 'react-router';
import {render} from './render';

/**
 * Renders inside a MobX <Provider>, the way src/components/main/Root.js does.
 *
 * `stores` is a plain map of injection name to store — pass only what the
 * component under test injects. Stores injected under a constant rather than a
 * literal (HiddenObjects.injectionName, CURRENT_USER_ATTRIBUTES_STORE) go in
 * under that constant, computed at the call site.
 */
export function renderWithStores (element, stores = {}, options = {}) {
  return render(<Provider {...stores}>{element}</Provider>, options);
}

/**
 * Renders inside a router with an in-memory history, so a component that reads
 * `location`, `params` or `router` from its props works.
 *
 * The element is cloned with the route props, which is how react-router 3 hands
 * them to a route component. Returns the history alongside the queries, so a
 * test can assert on navigation.
 *
 * Options: `route` (the initial location, default '/'), `path` (the route
 * pattern, default '/'), `stores` (wraps in a <Provider> as well) and `history`
 * (bring your own).
 */
export function renderWithRouter (element, options = {}) {
  const {route = '/', path = '/', stores} = options;
  const history = options.history || createMemoryHistory(route);
  // Defined per call rather than per React render: a new component identity
  // would remount the subtree on every rerender.
  const RouteComponent = (props) => React.cloneElement(element, props);
  const tree = (
    <Router history={history}>
      <Route path={path} component={RouteComponent} />
    </Router>
  );
  const rendered = stores
    ? renderWithStores(tree, stores, options)
    : render(tree, options);
  return Object.assign({history}, rendered);
}
