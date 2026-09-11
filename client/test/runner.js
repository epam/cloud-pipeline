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

// Framework-bearing: the only file in the repository that names `jest`. A test
// file that reaches for `jest.fn()` directly has to be rewritten when the runner
// changes; one that imports `fn` from @test does not.
//
// `describe`, `it` and `expect` stay as globals — both Jest and Vitest provide
// them under the same names, so there is nothing to absorb.

import {liveTestsEnabled, liveApiHint} from './config';

/** A recording stub. Assert with `expect(spy).toHaveBeenCalledWith(...)`. */
export function fn (implementation) {
  return implementation ? jest.fn(implementation) : jest.fn();
}

/** Replaces a method on an object, recording calls. Restored by `restoreMocks`. */
export function spyOn (object, method) {
  return jest.spyOn(object, method);
}

export function restoreMocks () {
  jest.restoreAllMocks();
}

/**
 * Fake timers, always the modern implementation. Jest 26 still defaults to the
 * legacy one, and Vitest only has the modern one — passing it explicitly is what
 * makes behaviour match across the switch.
 */
export function useFakeTimers () {
  jest.useFakeTimers('modern');
}

export function useRealTimers () {
  jest.useRealTimers();
}

export function advanceTimers (milliseconds) {
  jest.advanceTimersByTime(milliseconds);
}

export function runAllTimers () {
  jest.runAllTimers();
}

/** Only meaningful under `useFakeTimers`. */
export function setSystemTime (time) {
  jest.setSystemTime(time);
}

/**
 * Lets every already-queued promise callback run. Needed by anything that goes
 * through src/utils/defer.js, which resolves on `process.nextTick`.
 *
 * Uses a real macrotask, so call it with real timers — under `useFakeTimers` it
 * would never resolve. Prefer `waitFor` in a DOM test; this is for the model
 * layer, where there is nothing to observe in the document.
 */
export function flushPromises () {
  return new Promise(resolve => setImmediate(resolve));
}

/**
 * `describe` for a live-API test file, and `describe.skip` when the suite is
 * unconfigured — which is the normal case and must never be a failure. Every
 * *.live.test.js wraps its tests in this.
 */
export const describeLive = liveTestsEnabled
  ? describe
  : (name, body) => describe.skip(`${name} — ${liveApiHint}`, body);
