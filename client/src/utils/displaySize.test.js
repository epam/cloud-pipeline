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

import displaySize from './displaySize';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;

describe('displaySize', () => {
  it('reports a small value in bytes', () => {
    expect(displaySize(0)).toBe('0 bytes');
    expect(displaySize(1)).toBe('1 bytes');
    expect(displaySize(512)).toBe('512 bytes');
  });

  it('steps up a unit only above 1024, not at it', () => {
    expect(displaySize(1024)).toBe('1024 bytes');
    expect(displaySize(1025)).toBe('1.00 Kb');
  });

  it('walks up the whole unit scale', () => {
    expect(displaySize(2 * KB)).toBe('2.00 Kb');
    expect(displaySize(1.5 * MB)).toBe('1.50 Mb');
    expect(displaySize(3 * GB)).toBe('3.00 Gb');
  });

  it('stops at the largest unit it knows instead of overflowing it', () => {
    expect(displaySize(Math.pow(1024, 9))).toBe('1024.00 Yb');
  });

  it('rounds to a whole unit when digits are switched off', () => {
    expect(displaySize(1.5 * MB, false)).toBe('2 Mb');
    expect(displaySize(1.4 * MB, false)).toBe('1 Mb');
  });

  it('leaves a byte count unformatted even with digits on', () => {
    expect(displaySize(500.5)).toBe('500.5 bytes');
  });

  it('coerces a numeric string', () => {
    expect(displaySize('2048')).toBe('2.00 Kb');
  });

  it('returns a non-numeric value untouched', () => {
    expect(displaySize('unknown')).toBe('unknown');
    expect(displaySize(undefined)).toBeUndefined();
  });

  it('treats null as zero, because it is not NaN', () => {
    expect(displaySize(null)).toBe('0 bytes');
  });

  it('never scales a negative value, since the loop tests for > 1024', () => {
    expect(displaySize(-2048)).toBe('-2048 bytes');
  });
});
