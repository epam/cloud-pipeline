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

/**
 * Finds change for line
 * @param line
 * @param changes
 * @param preferBranch
 * @returns {*}
 */
export function findModification (line, changes, preferBranch) {
  const filtered = changes.filter(change =>
    change.items && change.items.indexOf(line) >= 0
  );
  if (preferBranch) {
    const prefer = filtered.find(b => b.branch === preferBranch);
    if (prefer) {
      return prefer;
    }
  }
  return filtered[0];
}

/**
 * Find change that has passed line as a first visible (for specified branch) after change starts.
 * Examples:
 *  For insertion, edition, conflict area: the first line of insertion/edition/conflict area
 *  For deletion: the first line after deletion
 * @param line
 * @param changes
 * @param branch
 * @returns {*}
 */
export function findModificationMarker (line, changes, branch) {
  const filtered = changes.filter(modification =>
    modification.lineIndex(branch) === line.lineNumber[branch]
  );
  return filtered[0];
}
