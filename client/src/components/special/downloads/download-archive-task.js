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

import {message} from 'antd';

/**
 * Fetches the signed URL for a finished archive-download task and opens it.
 * The `open` parameter defaults to the same `window.open(url, '_blank')`
 * mechanism `download-storage-items.js` uses for every pre-signed storage URL
 * today, and is injectable so this is testable in jsdom without module
 * mocking.
 *
 * `/async-task/{id}/consume` is not consuming in the sense of being used up:
 * the task itself is unchanged, and a second call answers afresh with a
 * newly-signed URL rather than failing or exhausting anything. So calling
 * this a second time for the same task is harmless.
 *
 * @param {{consumeTask: function(*): Promise<string|undefined>}} asyncTasks
 * @param {{id: *}} task
 * @param {function(string): void} [open]
 * @returns {Promise<void>}
 */
export default async function downloadArchiveTask (
  asyncTasks,
  task,
  open = (url) => window.open(url, '_blank')
) {
  if (!task || !task.id) {
    return;
  }
  const hide = message.loading('Preparing download...', 0);
  try {
    const url = await asyncTasks.consumeTask(task.id);
    if (!url) {
      throw new Error('The archive download link is not available');
    }
    open(url);
  } catch (error) {
    message.error(error.message, 5);
  } finally {
    hide();
  }
}
