/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

export default {
  'generic-file-path': `
<span class="d2h-file-name-wrapper">
  <i class="anticon anticon-file-text d2h-icon"></i>
  <span class="d2h-file-name">{{fileDiffName}}</span>
  {{>fileTag}}
</span>
  `,
  'line-by-line-file-diff': `
<div id="{{fileHtmlId}}" class="d2h-file-wrapper" data-lang="{{file.language}}">
    <div class="d2h-file-diff">
        <div class="d2h-code-wrapper">
            <table class="d2h-diff-table">
                <tbody class="d2h-diff-tbody">
                {{{diffs}}}
                </tbody>
            </table>
        </div>
    </div>
</div>
  `
};
