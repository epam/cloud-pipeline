/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import {LAUNCH_PLACEHOLDER_START, PLACEHOLDER_END} from '../../ai-chat-engine';

export function processMessage (message) {
  if (message.fromUser) {
    return message;
  }
  let payload;
  let parts = [];
  const regexp = new RegExp(`${LAUNCH_PLACEHOLDER_START}(.*?)${PLACEHOLDER_END}`, 'gi');
  try {
    const matches = regexp.exec(message.text);
    if (matches && matches[1]?.length) {
      payload = JSON.parse(matches[1].trim());
      const textParts = message.text.split(matches[0]);
      parts = [
        {isText: true, value: textParts[0]},
        {isPayload: true, value: payload},
        {isText: true, value: textParts[1]}
      ];
    }
  } catch (e) {
    console.error('Error parsing message payload:', e);
    return message;
  }
  return {...message, parts};
}
