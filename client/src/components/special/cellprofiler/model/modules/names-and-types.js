/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import {computed, observable} from 'mobx';
import {AnalysisModule} from './base';
import {HCSSourceFile, sourceFileOptionsSetsEqual} from '../common/analysis-file';
import {AnalysisTypes} from '../common/analysis-types';

class NamesAndTypes extends AnalysisModule {
  static predefined = true;
  path;
  /**
   * @type {HCSSourceFileOptions[]}
   */
  @observable sourceFiles = [];

  /**
   * @param {HCSSourceFileOptions[]} sources
   */
  constructor (sources) {
    super(undefined);
    this.title = 'Input';
    this.changeFiles(sources);
  }

  @computed
  get available () {
    return this.sourceFiles.length > 0 && HCSSourceFile.check(...this.sourceFiles);
  }

  @computed
  get sourceDirectory () {
    return [...(new Set(this.sourceFiles.map(aFile => aFile.sourceDirectory)))].pop();
  }

  @computed
  get multipleFields () {
    return new Set(
      this.sourceFiles
        .map(aFile => [
          aFile.sourceDirectory,
          aFile.x,
          aFile.y,
          aFile.z,
          aFile.t,
          aFile.fieldID
        ].join('|'))
    ).size > 1;
  }

  @computed
  get outputs () {
    if (this.sourceFiles && HCSSourceFile.check(...this.sourceFiles)) {
      let channels = [...new Set(this.sourceFiles.map(a => a.channel))];
      if (!channels || !channels.length) {
        channels = ['input'];
      }
      return channels.map(name => ({
        type: AnalysisTypes.file,
        name,
        cpModule: this
      }));
    }
    return [];
  }

  /**
   * @param {HCSSourceFileOptions[]} sourceFilesOptions
   * @returns {boolean} true if changed
   */
  changeFiles (sourceFilesOptions = []) {
    if (sourceFileOptionsSetsEqual(this.sourceFiles, sourceFilesOptions)) {
      return false;
    }
    this.sourceFiles = sourceFilesOptions.slice();
    return true;
  }
}

export {NamesAndTypes};
