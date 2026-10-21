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

import {
  alphabeticalSorter,
  alphabeticalSorterDesc,
  numericalSorter,
  numericalSorterDesc,
  booleanSorter,
  booleanSorterDesc,
  defaultSorter,
  defaultSorterDesc
} from './sorting';

// The sorters return a sign, not a magnitude — localeCompare's is
// implementation-defined — so assertions are on the sign.
const before = (sorter, a, b) => expect(sorter(a, b)).toBeLessThan(0);
const after = (sorter, a, b) => expect(sorter(a, b)).toBeGreaterThan(0);
const tied = (sorter, a, b) => expect(sorter(a, b)).toBe(0);

describe('alphabeticalSorter', () => {
  it('orders strings ascending', () => {
    before(alphabeticalSorter, 'apple', 'banana');
    after(alphabeticalSorter, 'banana', 'apple');
    tied(alphabeticalSorter, 'apple', 'apple');
  });

  it('sorts a list', () => {
    expect(['c', 'a', 'b'].sort(alphabeticalSorter)).toEqual(['a', 'b', 'c']);
  });

  it('pushes a non-string to the end, whichever side it is on', () => {
    after(alphabeticalSorter, 1, 'a');
    before(alphabeticalSorter, 'a', 1);
  });

  it('leaves two non-strings tied', () => {
    tied(alphabeticalSorter, 1, 2);
    tied(alphabeticalSorter, null, undefined);
  });
});

describe('alphabeticalSorterDesc', () => {
  it('orders strings descending', () => {
    after(alphabeticalSorterDesc, 'apple', 'banana');
    before(alphabeticalSorterDesc, 'banana', 'apple');
  });

  it('still pushes a non-string to the end', () => {
    after(alphabeticalSorterDesc, 1, 'a');
    before(alphabeticalSorterDesc, 'a', 1);
  });
});

describe('numericalSorter', () => {
  it('orders numbers ascending', () => {
    before(numericalSorter, 1, 2);
    after(numericalSorter, 2, 1);
    tied(numericalSorter, 2, 2);
    before(numericalSorter, -5, 0);
  });

  it('pushes a non-number to the end', () => {
    after(numericalSorter, 'a', 1);
    before(numericalSorter, 1, 'a');
    tied(numericalSorter, 'a', 'b');
  });
});

describe('numericalSorterDesc', () => {
  it('orders numbers descending', () => {
    after(numericalSorterDesc, 1, 2);
    before(numericalSorterDesc, 2, 1);
  });

  it('still pushes a non-number to the end', () => {
    after(numericalSorterDesc, 'a', 1);
  });
});

describe('booleanSorter', () => {
  it('orders false before true', () => {
    before(booleanSorter, false, true);
    after(booleanSorter, true, false);
    tied(booleanSorter, true, true);
  });

  it('pushes a non-boolean to the end', () => {
    after(booleanSorter, 1, true);
    before(booleanSorter, true, 1);
    tied(booleanSorter, 1, 'a');
  });
});

describe('booleanSorterDesc', () => {
  it('orders true before false', () => {
    before(booleanSorterDesc, true, false);
    after(booleanSorterDesc, false, true);
  });
});

describe('defaultSorter', () => {
  it('orders within a type', () => {
    before(defaultSorter, 'apple', 'banana');
    before(defaultSorter, 1, 2);
    before(defaultSorter, false, true);
  });

  it('orders strings, then numbers, then booleans', () => {
    before(defaultSorter, 'a', 1);
    before(defaultSorter, 1, true);
    before(defaultSorter, 'a', true);
  });

  it('pushes anything it has no order for to the end', () => {
    after(defaultSorter, {}, 'a');
    after(defaultSorter, [], 1);
    before(defaultSorter, true, () => {});
  });

  it('leaves two unordered values tied', () => {
    tied(defaultSorter, {}, []);
    tied(defaultSorter, undefined, null);
  });

  it('sorts a mixed list', () => {
    expect([true, 2, 'b', 1, 'a', false].sort(defaultSorter))
      .toEqual(['a', 'b', 1, 2, false, true]);
  });
});

describe('defaultSorterDesc', () => {
  it('reverses the order within a type', () => {
    after(defaultSorterDesc, 'apple', 'banana');
    after(defaultSorterDesc, 1, 2);
    after(defaultSorterDesc, false, true);
  });

  it('reverses the order between types, booleans first', () => {
    after(defaultSorterDesc, 'a', 1);
    after(defaultSorterDesc, 1, true);
  });

  it('still pushes anything it has no order for to the end', () => {
    after(defaultSorterDesc, {}, 'a');
    before(defaultSorterDesc, true, {});
  });

  it('sorts a mixed list', () => {
    expect([true, 2, 'b', 1, 'a', false].sort(defaultSorterDesc))
      .toEqual([true, false, 2, 1, 'b', 'a']);
  });
});
