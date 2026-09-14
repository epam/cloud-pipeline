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

// The unit suite: hermetic, in jsdom, with no network. See test/AGENTS.md.

const base = require('./jest.config.base');

module.exports = Object.assign({}, base, {
  testEnvironment: 'jsdom',
  testURL: 'http://localhost',
  testMatch: [
    '<rootDir>/src/**/*.test.{js,jsx}',
    // The build tooling under scripts/ is plain node, but the lint baseline it
    // carries decides whether a check passes, so it is tested here too.
    '<rootDir>/scripts/**/*.test.js'
  ],
  // The live suite is opt-in through `npm run test:live` and must never be
  // picked up here — it needs a real deployment.
  testPathIgnorePatterns: ['\\.live\\.test\\.js$'],
  setupFiles: [
    '<rootDir>/config/polyfills.js',
    '<rootDir>/test/setup/env.js'
  ],
  // Runs once `expect` exists, unlike setupFiles above.
  setupFilesAfterEnv: ['<rootDir>/test/setup/matchers.js']
});
