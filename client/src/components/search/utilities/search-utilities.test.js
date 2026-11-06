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

import {getScrollingDocumentId} from './search-utilities';

describe('getScrollingDocumentId', () => {
  it('returns the document id when it differs from the Elasticsearch id', () => {
    const document = {
      id: 'folder/file.txt',
      elasticId: 'sha256:02425c0f5b0dabf3d2b9115f3f7723a02ad8bcfb1534a0d231614fd42b8188f6'
    };
    expect(getScrollingDocumentId(document)).toBe('folder/file.txt');
  });

  it('returns the document id when it matches the Elasticsearch id', () => {
    expect(getScrollingDocumentId({id: '42', elasticId: '42'})).toBe('42');
  });

  it('falls back to the Elasticsearch id when the document has no id', () => {
    expect(getScrollingDocumentId({elasticId: 'generated-id'})).toBe('generated-id');
  });
});
