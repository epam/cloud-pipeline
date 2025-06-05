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

export const LAUNCH_PLACEHOLDER_START = '<<<LAUNCH:';
export const PLACEHOLDER_END = '>>>';

function extractJSON (message, regexp) {
  try {
    const matches = regexp.exec(message.text);
    if (matches && matches[1]?.length) {
      return {
        text: matches[0],
        payload: JSON.parse(matches[1].trim())
      };
    }
  } catch (e) {
    console.error('Error parsing message payload:', e);
    return null;
  }
}

function extractPlainJSON (message) {
  const launchIndex = message.text.indexOf('LAUNCH:');
  if (launchIndex === -1) {
    return null;
  }
  const jsonStart = message.text.indexOf('{', launchIndex);
  const jsonEnd = message.text.lastIndexOf('}');
  if (jsonStart < 0 || jsonEnd < 0) {
    return null;
  }
  const substring = message.text.slice(jsonStart, jsonEnd + 1);
  let json;
  try {
    json = JSON.parse(substring);
  } catch (e) {}
  if (json) {
    return {
      text: message.text.slice(launchIndex, jsonEnd + 1),
      payload: json
    };
  }
  return null;
}

function extractJsonFromText (message) {
  let payload;
  const variants = [
    new RegExp(`\`\`\`\\s*${LAUNCH_PLACEHOLDER_START}(.*?)${PLACEHOLDER_END}\\s*\`\`\``, 'gi'),
    new RegExp(`${LAUNCH_PLACEHOLDER_START}(.*?)${PLACEHOLDER_END}`, 'gi')
  ];
  payload = variants
    .map((regexp) => extractJSON(message, regexp))
    .filter(Boolean)[0];
  if (!payload) {
    payload = extractPlainJSON(message);
  }
  return payload;
}

export function processMessage (message) {
  if (message.fromUser) {
    return message;
  }

  const {text, payload} = extractJsonFromText(message) || {};

  if (!payload) {
    return {...message, parts: []};
  }

  const textParts = message.text.split(text);

  const parts = textParts.flatMap((part, index) => {
    if (textParts.length > 1 && index === 0) {
      return [
        {isText: true, value: part, hasText: part.trim().length > 0},
        {isPayload: true, value: payload}
      ];
    }

    return [{isText: true, value: part, hasText: part.trim().length > 0}];
  });

  return {...message, parts};
}
