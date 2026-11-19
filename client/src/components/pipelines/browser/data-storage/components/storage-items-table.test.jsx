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

import React from 'react';
import {render, screen} from '@test';
import StorageItemsTable from './storage-items-table';

// jsdom has no layout, so stickiness itself cannot be seen here. These are the classes the
// sticky rules (storage-items-table.css) and the themed colors (cp-storage-items-table) match.
test('wraps the title row in the sticky and the themed classes, and keeps the given one', () => {
  render(
    <StorageItemsTable
      className="caller-class"
      title={() => 'Controls'}
      dataSource={[{key: 'a', name: 'file.txt'}]}
      columns={[{key: 'name', dataIndex: 'name'}]}
      pagination={false}
    />
  );

  const title = screen.getByText('Controls');
  expect(title.closest('.table')).not.toBeNull();
  expect(title.closest('.cp-storage-items-table')).not.toBeNull();
  expect(title.closest('.caller-class')).not.toBeNull();
  expect(screen.getByText('file.txt')).toBeInTheDocument();
});
