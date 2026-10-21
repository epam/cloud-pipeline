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

const PROMPT_PLACEHOLDER = '{user_prompt}';

function resolvePromptTemplate (query, template) {
  return template.replace(PROMPT_PLACEHOLDER, query);
}

export function getSearchPrompt (
  query = '',
  template = '',
  isAdvanced = false,
  omitWildcardWrapper = false
) {
  let result = query;
  const hasTemplate = template?.length > 0 && template.includes(PROMPT_PLACEHOLDER);
  if (!isAdvanced && hasTemplate) {
    result = resolvePromptTemplate(query, template);
  } else if (!isAdvanced && !hasTemplate) {
    result = query && !omitWildcardWrapper
      ? `*${query}*`
      : query;
  } else {
    result = query || '';
  }
  return result;
}
