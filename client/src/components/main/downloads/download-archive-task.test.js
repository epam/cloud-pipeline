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

import {fn, spyOn, restoreMocks} from '@test';
import {message} from 'antd';
import downloadArchiveTask from './download-archive-task';

function asyncTasksDouble (consumeTask) {
  return {consumeTask: consumeTask || fn().mockResolvedValue('https://example.com/archive.zip')};
}

afterEach(() => {
  restoreMocks();
});

test('fetches the signed url with the task id and opens it', async () => {
  const consumeTask = fn().mockResolvedValue('https://example.com/archive.zip');
  const open = fn();

  await downloadArchiveTask(asyncTasksDouble(consumeTask), {id: 42}, open);

  expect(consumeTask).toHaveBeenCalledWith(42);
  expect(open).toHaveBeenCalledWith('https://example.com/archive.zip');
});

test('a rejecting consumeTask does not throw out, and shows an error toast', async () => {
  const consumeTask = fn().mockRejectedValue(new Error('request failed'));
  const open = fn();
  const errorToast = spyOn(message, 'error');

  await expect(
    downloadArchiveTask(asyncTasksDouble(consumeTask), {id: 42}, open)
  ).resolves.toBeUndefined();

  expect(open).not.toHaveBeenCalled();
  expect(errorToast).toHaveBeenCalledWith('request failed', 5);
});

test('a consumeTask resolving to undefined shows an error toast and opens nothing', async () => {
  const consumeTask = fn().mockResolvedValue(undefined);
  const open = fn();
  const errorToast = spyOn(message, 'error');

  await downloadArchiveTask(asyncTasksDouble(consumeTask), {id: 42}, open);

  expect(open).not.toHaveBeenCalled();
  expect(errorToast).toHaveBeenCalled();
});

test('a task with no id triggers no call at all', async () => {
  const consumeTask = fn().mockResolvedValue('https://example.com/archive.zip');
  const open = fn();

  await downloadArchiveTask(asyncTasksDouble(consumeTask), {}, open);
  await downloadArchiveTask(asyncTasksDouble(consumeTask), undefined, open);

  expect(consumeTask).not.toHaveBeenCalled();
  expect(open).not.toHaveBeenCalled();
});
