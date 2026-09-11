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

// The live suite: opt-in, against a real deployment, configured only by the
// untracked .env.test.local. Runs in `node` — no DOM means no origin, hence no
// CORS and nothing to proxy. See test/AGENTS.md.

// `--passWithNoTests` lives on the `test:live` npm script rather than here:
// Jest 26 accepts it only as a CLI flag and warns that it is unknown as a
// config key. Nobody is expected to have .env.test.local and no live test is
// expected to exist, so neither may be a failure.

const base = require('./jest.config.base');

module.exports = Object.assign({}, base, {
  testEnvironment: 'node',
  testMatch: ['<rootDir>/src/**/*.live.test.js'],
  setupFiles: ['<rootDir>/test/setup/live.js']
});
