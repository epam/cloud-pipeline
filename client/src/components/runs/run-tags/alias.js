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

/**
 * Sets the run name alias as the payload's `alias` tag, keeping every other tag, and removes the
 * payload's `runNameAlias` field, which `POST /run` does not accept. Mutates the payload in place.
 * An empty alias leaves the tags as they are - unless the payload carried a `runNameAlias`, which
 * means the user cleared the run name before launch, so the `alias` tag is removed as well.
 * @param {object} payload
 * @param {string} [alias] the run name to launch with
 * @returns {object} the same payload
 */
export function applyRunNameAliasTag (payload, alias) {
  if (!payload) {
    return payload;
  }
  const cleared = !alias && !!payload.runNameAlias;
  delete payload.runNameAlias;
  if (alias) {
    payload.tags = {
      ...(payload.tags || {}),
      alias
    };
  } else if (cleared && payload.tags && payload.tags.alias !== undefined) {
    const {alias: clearedAlias, ...tags} = payload.tags;
    payload.tags = tags;
  }
  return payload;
}
