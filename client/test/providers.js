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

// MobX-, router- and antd-locale-bearing. A test file must never hand-roll a
// <Provider>, a <Router> or antd's own <LocaleProvider>: MobX 3 -> 6,
// react-router 3 -> 6 and an antd major all rewrite these wrappers, and each
// should rewrite one file.

import React from 'react';
import {Provider} from 'mobx-react';
import {Router, Route, createMemoryHistory} from 'react-router';
import {LocaleProvider} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
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
  const result = render(<Provider {...stores}>{element}</Provider>, options);
  return {
    ...result,
    // render()'s own `rerender` replaces the root with whatever it's given;
    // re-wrapping in the same <Provider> here is what lets a rerendered
    // element keep injecting from `stores`.
    rerender: (next) => result.rerender(<Provider {...stores}>{next}</Provider>)
  };
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

/**
 * Renders inside antd's <LocaleProvider locale={enUS}>, the way
 * src/components/main/App.js wraps the whole application. Needed only when a
 * test interacts with or asserts on antd's own default, locale-driven UI (for
 * example a column's default filter icon, whose `title` otherwise renders in
 * antd's bundled default locale, zh-CN, rather than the English production
 * actually shows).
 *
 * `options.stores`, if passed, wraps in a <Provider> as well, nested inside
 * the <LocaleProvider> - the same opt-in shape `renderWithRouter` above uses
 * for the same purpose. The two providers use React's legacy context API
 * independently of each other, so nesting order carries no behaviour; this
 * one is not a claim about how Root.js/App.js nest their own two Providers
 * (where the store one is actually outermost, above App.js's own
 * <LocaleProvider>).
 */
export function renderWithLocale (element, options = {}) {
  const {stores} = options;
  const tree = stores ? <Provider {...stores}>{element}</Provider> : element;
  return render(<LocaleProvider locale={enUS}>{tree}</LocaleProvider>, options);
}
