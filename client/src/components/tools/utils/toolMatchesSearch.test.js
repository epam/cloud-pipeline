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

import toolMatchesSearch from './toolMatchesSearch';

describe('toolMatchesSearch', () => {
  it('matches everything when the search is absent or empty', () => {
    const tool = {image: 'library/samtools', labels: ['bio']};
    expect(toolMatchesSearch(tool, undefined)).toBe(true);
    expect(toolMatchesSearch(tool, null)).toBe(true);
    expect(toolMatchesSearch(tool, '')).toBe(true);
  });

  it('matches by image, case-insensitively', () => {
    const tool = {image: 'library/SAMtools'};
    expect(toolMatchesSearch(tool, 'samtools')).toBe(true);
    expect(toolMatchesSearch(tool, 'library')).toBe(true);
    expect(toolMatchesSearch(tool, 'bowtie')).toBe(false);
  });

  it('matches by label, case-insensitively', () => {
    const tool = {image: 'library/samtools', labels: ['Bioinformatics', 'CLI']};
    expect(toolMatchesSearch(tool, 'bioinformatics')).toBe(true);
    expect(toolMatchesSearch(tool, 'cli')).toBe(true);
    expect(toolMatchesSearch(tool, 'gpu')).toBe(false);
  });

  it('treats a missing image or labels as simply not matching that field', () => {
    expect(toolMatchesSearch({labels: ['bio']}, 'bio')).toBe(true);
    expect(toolMatchesSearch({image: 'library/samtools'}, 'bio')).toBe(false);
    expect(toolMatchesSearch({}, 'bio')).toBe(false);
  });

  it('reports no match for a missing tool', () => {
    expect(toolMatchesSearch(undefined, 'bio')).toBe(false);
    expect(toolMatchesSearch(null, 'bio')).toBe(false);
  });
});
