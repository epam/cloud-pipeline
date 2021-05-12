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

import Branches, {HeadBranch, Merged, RemoteBranch} from './branches';
import States from './line-states';

export default class ConflictedFileLine {
  static keyIncrement = 0;

  /**
   * Next HEAD line
   * @type {ConflictedFileLine}
   */
  [HeadBranch] = undefined;
  /**
   * Next REMOTE line
   * @type {ConflictedFileLine}
   */
  [RemoteBranch] = undefined;
  /**
   * Next MERGED line
   * @type {ConflictedFileLine}
   */
  [Merged] = undefined;

  /**
   * @type ConflictedFile
   */
  file;

  constructor (line, meta = {}) {
    ConflictedFileLine.keyIncrement += 1;
    this.key = ConflictedFileLine.keyIncrement;
    this.line = line;
    this.text = {
      [HeadBranch]: line,
      [Merged]: line,
      [RemoteBranch]: line
    };
    this.meta = meta;
    this.previous = {};
    this.lineNumber = {};
    this.changesBefore = {};
    this.change = {};
    this.isChangeMarker = {};
    this.state = {
      [HeadBranch]: States.original,
      [RemoteBranch]: States.original,
      [Merged]: States.original
    };
    this.file = undefined;
  }

  copy () {
    const copy = new ConflictedFileLine(this.line, this.meta);
    copy.previous = {
      ...this.previous
    };
    copy.changesBefore = {
      ...this.changesBefore
    };
    copy.lineNumber = {
      ...this.lineNumber
    };
    copy.text = {
      ...this.text
    };
    copy.state = {
      ...this.state
    };
    copy.change = this.change;
    copy.file = this.file;
    Branches.forEach(branch => {
      copy[branch] = this[branch];
    });
    return copy;
  }

  getBranchState (branch) {
    return this.state[branch];
  }

  get isConflict () {
    return this.meta && this.meta.conflict;
  }

  isParentFor (line) {
    if (!line || line === this) {
      return false;
    }
    const parents = new Set([
      line.previous[HeadBranch],
      line.previous[RemoteBranch],
      line.previous[Merged]
    ]);
    for (let parent of parents) {
      if (parent === this) {
        return true;
      }
    }
    for (let parent of parents) {
      if (parent && this.isParentFor(parent)) {
        return true;
      }
    }
    return false;
  }

  changeParent (parent, ...branches) {
    if (parent) {
      const currentParentLinks = this.previous;
      branches.forEach(branch => {
        if (currentParentLinks[branch]) {
          parent[branch] = this;
          this.previous[branch] = parent;
        }
      });
    }
  }
}
