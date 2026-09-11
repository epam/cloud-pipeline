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

'use strict';

// Runs in `setupFiles`, i.e. before any module under test is loaded. That
// ordering is load-bearing: src/models/basic/RemotePost.js computes
// `static prefix = SERVER + API_PATH` at class-definition time from
// src/config.js's process.env reads, so anything set after the first import is
// already too late.

process.env.NODE_ENV = process.env.NODE_ENV || 'test';
process.env.BABEL_ENV = process.env.BABEL_ENV || 'test';

// Keeps the CRA dotenv chain wired (.env.test.local -> .env.test -> .env), so
// REACT_APP_* variables still behave as they do under webpack.
require('../../config/env');

require('./timezone');

// The unit suite's own settings are code, not configuration: they are assigned
// unconditionally, and after the chain above, so a developer's untracked
// client/.env cannot influence a unit test. dotenv never overwrites a variable
// that is already set, which is why the order has to be this way round.
//
// The webpack build injects these through DefinePlugin, but src/config.js reads
// them as ordinary process.env properties, so plain assignment is enough.
const {unitEnvironment} = require('../config');

Object.keys(unitEnvironment).forEach(name => {
  process.env[name] = unitEnvironment[name];
});

// jsdom implements neither TextEncoder nor TextDecoder, and it replaces the
// global object, so Node's own are not reachable either. src/utils/base64.js
// constructs both at module scope, so without this it throws on import rather
// than in a test. Node 14's implementations are spec-compliant and work across
// the realm boundary; jsdom's successors have the same gap, so this stays.
const {TextEncoder, TextDecoder} = require('util');

if (typeof global.TextEncoder === 'undefined') {
  global.TextEncoder = TextEncoder;
}

if (typeof global.TextDecoder === 'undefined') {
  global.TextDecoder = TextDecoder;
}

// The network guard. A unit test that reaches for the network fails here and
// says what to do instead, rather than making a real request or flaking in CI.
global.fetch = function () {
  throw new Error(
    'Network access in a unit test — use stubApi() from @test, ' +
    'or make this a *.live.test.js'
  );
};
