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

import {computed} from 'mobx';
import {AnalysisModule} from '../base';
import {
  ListParameter,
  FileParameter
} from '../../parameters';
import {AnalysisTypes} from '../../common/analysis-types';

class SaveImages extends AnalysisModule {
  initialize () {
    super.initialize();
    this.registerParameters(
      new FileParameter({
        name: 'source',
        title: 'Image to save',
        parameterName: 'Select the image to save'
      }),
      new FileParameter({
        name: 'filePrefix',
        title: 'File prefix',
        parameterName: 'Select image name for file prefix'
      }),
      new ListParameter({
        name: 'format',
        title: 'Format',
        parameterName: 'Saved file format',
        values: ['tiff', 'png'],
        value: 'png'
      })
    );
  }
  @computed
  get outputs () {
    const name = this.getParameterValue('source');
    if (name) {
      return [{
        type: AnalysisTypes.object,
        value: `${name}_saved`,
        name: `${name}_saved`,
        module: this
      }];
    }
    return [];
  }
}

export {SaveImages};
