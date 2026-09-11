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

// Settings shared by the unit suite (jest.config.js) and the live suite
// (jest.live.config.js). Everything that differs between the two — the
// environment, what it matches and how it is set up — is overridden there.

module.exports = {
  rootDir: __dirname,
  transform: {
    '^.+\\.(js|jsx)$': 'babel-jest',
    '^(?!.*\\.(js|jsx|json)$)': '<rootDir>/config/jest/fileTransform.js'
  },
  // Leaves node_modules JS untransformed. `.less` is deliberately not matched
  // here, so antd's `babel-plugin-import` style requires still reach
  // moduleNameMapper and get stubbed.
  transformIgnorePatterns: [
    '[/\\\\]node_modules[/\\\\].+\\.(js|jsx)$'
  ],
  moduleNameMapper: {
    '\\.(css|less|scss|sass)$': 'identity-obj-proxy',
    '^@test$': '<rootDir>/test'
  },
  moduleFileExtensions: ['js', 'jsx', 'json'],
  // A spy left installed by one test is invisible in the test that then fails
  // because of it. Vitest has the same option under the same name.
  restoreMocks: true,
  collectCoverageFrom: [
    'src/**/*.{js,jsx}',
    '!src/**/*.test.{js,jsx}'
  ],
  coverageDirectory: 'coverage'
};
