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

// The only module a test file imports from:
//
//   import {render, screen, click, waitFor, fn, stubApi} from '@test';
//
// `@test` is one moduleNameMapper entry today and one resolve.alias entry under
// Vite. Everything version- and framework-specific sits behind it, so a
// toolchain change edits the modules below and no test file.

export {
  render,
  cleanup,
  act,
  screen,
  within,
  waitFor,
  waitForElementToBeRemoved,
  getDefaultNormalizer
} from './render';

export {
  click,
  dblClick,
  change,
  clear,
  type,
  keyDown,
  keyUp,
  submit,
  focus,
  blur,
  hover,
  unhover
} from './events';

export {renderWithStores, renderWithRouter} from './providers';

export {
  fn,
  spyOn,
  restoreMocks,
  useFakeTimers,
  useRealTimers,
  advanceTimers,
  runAllTimers,
  setSystemTime,
  flushPromises,
  describeLive
} from './runner';

export {
  stubApi,
  restoreApi,
  ok,
  fail,
  unauthorized,
  jsonResponse
} from './http';

export {factory, sequence} from './factories';

export {
  apiPrefix,
  testTimezone,
  unitEnvironment,
  liveApi,
  liveTestsEnabled,
  liveApiHint
} from './config';
