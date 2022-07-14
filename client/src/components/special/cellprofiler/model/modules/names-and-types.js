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
import {HCSSourceFile, sourceFileOptionsEqual} from '../common/analysis-file';
import {AnalysisTypes} from '../common/analysis-types';

class NamesAndTypes extends AnalysisModule {
  static predefined = true;
  path;
  /**
   * @type {HCSSourceFileOptions}
   */
  @observable sourceFile;

  /**
   * @param {HCSSourceFileOptions} source
   */
  constructor (source) {
    super(undefined);
    this.title = 'Input';
    this.changeFile(source);
  }

  @computed
  get available () {
    return HCSSourceFile.check(this.sourceFile);
  }

  @computed
  get outputs () {
    if (this.sourceFile && HCSSourceFile.check(this.sourceFile)) {
      let channels = this.sourceFile.channels;
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
   * @param {HCSSourceFileOptions} sourceFileOptions
   * @returns {boolean} true if changed
   */
  changeFile (sourceFileOptions) {
    if (sourceFileOptionsEqual(this.sourceFile, sourceFileOptions)) {
      return false;
    }
    this.sourceFile = sourceFileOptions;
    return true;
  }
}

export {NamesAndTypes};
