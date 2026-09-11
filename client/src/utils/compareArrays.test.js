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

import compareArrays from './compareArrays';

describe('compareArrays', () => {
  it('treats absent, null and empty as the same emptiness', () => {
    expect(compareArrays(undefined, [])).toBe(true);
    expect(compareArrays(null, undefined)).toBe(true);
    expect(compareArrays([], [])).toBe(true);
  });

  it('reports one empty side as different', () => {
    expect(compareArrays([], ['a'])).toBe(false);
    expect(compareArrays(['a'], null)).toBe(false);
  });

  it('reports differing lengths as different without looking at elements', () => {
    expect(compareArrays(['a'], ['a', 'b'])).toBe(false);
  });

  it('ignores order', () => {
    expect(compareArrays([1, 2, 3], [3, 1, 2])).toBe(true);
  });

  it('compares membership, so it is asymmetric on duplicates', () => {
    // Every element of the first array is looked up in the second; nothing
    // counts occurrences. [1, 1] is therefore "equal to" [1, 2] but not the
    // other way round.
    expect(compareArrays([1, 1], [1, 2])).toBe(true);
    expect(compareArrays([1, 2], [1, 1])).toBe(false);
  });

  it('uses strict membership without a comparer, so equal objects differ', () => {
    expect(compareArrays([{id: 1}], [{id: 1}])).toBe(false);
  });

  it('uses the comparer when one is given', () => {
    const byId = (a, b) => a.id === b.id;
    expect(compareArrays([{id: 1}, {id: 2}], [{id: 2}, {id: 1}], byId)).toBe(true);
    expect(compareArrays([{id: 1}], [{id: 3}], byId)).toBe(false);
  });

  it('reports a comparer that never matches as different', () => {
    expect(compareArrays(['a'], ['a'], () => false)).toBe(false);
  });
});
