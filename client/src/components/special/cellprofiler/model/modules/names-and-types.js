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
  @observable mergeZPlanes = false;

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
    return this.wells.length > 1 ||
      this.timePoints.length > 1 ||
      (!this.mergeZPlanes && this.zCoordinates.length > 1) ||
      this.wellFields.some(({fields = []}) => fields.length > 1);
  }

  @computed
  get wells () {
    return [...new Set(
      this.sourceFiles
        .map(aFile => [
          aFile.sourceDirectory,
          aFile.x,
          aFile.y
        ].join('|'))
    )]
      .map(o => o.split('|'))
      .map(([sourceDirectory, x, y]) => ({
        uuid: sourceDirectory,
        x: Number(x),
        y: Number(y)
      }));
  }

  @computed
  get timePoints () {
    return [...new Set(this.sourceFiles.map(aFile => aFile.t))];
  }

  @computed
  get zCoordinates () {
    return [...new Set(this.sourceFiles.map(aFile => aFile.z))];
  }

  @computed
  get wellFields () {
    return this.wells.map(aWell => {
      const fields = [...new Set(this.sourceFiles
        .filter(aFile =>
          aFile.sourceDirectory === aWell.uuid &&
          aFile.x === aWell.x &&
          aFile.y === aWell.y
        )
        .map(aFile => aFile.fieldID)
      )];
      return ({well: aWell, fields});
    });
  }

  @computed
  get commonFields () {
    const all = new Set(
      this.wellFields
        .map(o => o.fields)
        .reduce((r, c) => ([...r, ...c]), [])
    );
    this.wellFields.forEach(({fields = []}) => {
      [...all].forEach(field => {
        if (!fields.includes(field)) {
          all.delete(field);
        }
      });
    });
    return [...all];
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
