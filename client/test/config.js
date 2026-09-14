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

// The one module in the harness that reads `process.env`. No test file may:
// the mechanism behind these values is toolchain-specific (dotenv today, Vite's
// `loadEnv` and `envPrefix` later), and this file is where that difference is
// absorbed.
//
// CommonJS on purpose — the setup files under test/setup/ `require` it before
// any module under test is loaded, and test files reach it through @test.

// The unit suite's settings are code, not configuration. Fixed values, in the
// repository, so a green run means the same thing on every machine and nothing
// a unit test depends on is untracked.
const unitEnvironment = {
  SERVER: 'https://cloud-pipeline.test',
  API_PATH: '/restapi',
  PUBLIC_URL: '',
  VERSION: '0.0.0-test'
};

// Everything the unit suite's fetch stub matches against.
const apiPrefix = unitEnvironment.SERVER + unitEnvironment.API_PATH;

const testTimezone = 'UTC';

function read (name) {
  return (process.env[name] || '').trim();
}

// The live suite's knobs, namespaced rather than reusing SERVER: that makes it
// structurally impossible for somebody's client/.env to influence a test run,
// and marks the variables as unmistakably test-only. They come from the
// untracked .env.test.local — the only file that may name a real deployment.
const liveApiUrl = read('CP_TEST_API_URL').replace(/\/+$/, '');
const liveApiToken = read('CP_TEST_API_TOKEN');

const liveTestsEnabled = liveApiUrl.length > 0;

const liveApi = {
  // The server root, e.g. https://cloud-pipeline.example.com. The API path is
  // not a knob: '/restapi' is what config/env.js itself hardcodes.
  url: liveTestsEnabled ? liveApiUrl : undefined,
  apiPath: unitEnvironment.API_PATH,
  prefix: liveTestsEnabled ? liveApiUrl + unitEnvironment.API_PATH : undefined,
  token: liveApiToken || undefined,
  headers: liveApiToken ? {Authorization: `Bearer ${liveApiToken}`} : {}
};

// Printed by test/setup/live.js and by the skipped describe, so an unconfigured
// live run says what to do rather than just reporting nothing.
const liveApiHint =
  'Live API tests are not configured. Create client/.env.test.local with ' +
  'CP_TEST_API_URL (and CP_TEST_API_TOKEN if the endpoint needs one) to run ' +
  'them. The file is untracked, and `npm test` neither needs nor reads it.';

module.exports = {
  unitEnvironment,
  apiPrefix,
  testTimezone,
  liveTestsEnabled,
  liveApi,
  liveApiHint
};
