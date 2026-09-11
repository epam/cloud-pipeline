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

// Runs in `setupFiles` for the live suite only. Unlike the unit suite this does
// not load config/polyfills.js: the environment is `node`, so there is no
// `window` for it to patch.

process.env.NODE_ENV = process.env.NODE_ENV || 'test';
process.env.BABEL_ENV = process.env.BABEL_ENV || 'test';

// Loads the dotenv chain, whose first entry is the untracked .env.test.local —
// the only file that may name a real deployment. Its absence is the normal
// case; a live test then skips itself rather than failing.
require('../../config/env');

require('./timezone');

// Read after the chain above, so CP_TEST_* from .env.test.local are visible.
const {liveApi, liveTestsEnabled, liveApiHint, unitEnvironment} = require('../config');

if (liveTestsEnabled) {
  // Set before any module loads, which is load-bearing:
  // src/models/basic/RemotePost.js computes `static prefix = SERVER + API_PATH`
  // at class-definition time from src/config.js's process.env reads.
  process.env.SERVER = liveApi.url;
  process.env.API_PATH = liveApi.apiPath;
  process.env.PUBLIC_URL = unitEnvironment.PUBLIC_URL;
  process.env.VERSION = unitEnvironment.VERSION;

  // Node 14 has no native fetch, and whatwg-fetch is XHR-based so it needs a
  // DOM. node-fetch v2 is CommonJS and works here; it becomes redundant once
  // these tests run on Node 18 or later.
  const nodeFetch = require('node-fetch');
  // Remote.js's fetchOptions is fixed to `credentials: 'include'` — the
  // browser's cookie-based session auth, which node-fetch has no cookie jar
  // for. The live suite has no browser session at all, so it authenticates
  // with CP_TEST_API_TOKEN instead, merged in here rather than in Remote.js:
  // this keeps the token path scoped to the live suite and out of the
  // request path every real user goes through.
  global.fetch = (url, options = {}) => nodeFetch(url, {
    ...options,
    headers: {...liveApi.headers, ...options.headers}
  });
  global.Headers = nodeFetch.Headers;
  global.Request = nodeFetch.Request;
  global.Response = nodeFetch.Response;
} else {
  console.log(liveApiHint);
  // Every live test file wraps its tests in `describeLive` from @test, which
  // skips them when the suite is unconfigured. This catches the file that
  // forgot to, with the same message rather than a request to `undefined`.
  global.fetch = function () {
    throw new Error(liveApiHint);
  };
}
