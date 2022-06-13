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
  FileParameter,
  StringParameter,
  BooleanParameter,
  ListParameter,
  OutlineObjectsConfigurationParameter
} from '../../parameters';
import {AnalysisTypes} from '../../common/analysis-types';

const displayModes = OutlineObjectsConfigurationParameter.displayModes;

const outlineModes = {
  inner: 'Inner',
  outer: 'Outer',
  thick: 'Thick'
};

const outlineModeParameter = (options = {}) => new ListParameter({
  name: 'outlineMode',
  title: 'Outline mode',
  parameterName: 'How to outline',
  values: [
    outlineModes.inner,
    outlineModes.outer,
    outlineModes.thick
  ],
  value: outlineModes.inner,
  ...options
});

class OverlayOutlines extends AnalysisModule {
  @computed
  get objectsName () {
    return this.getParameterValue('name');
  }
  initialize () {
    super.initialize();
    this.registerParameters(
      new BooleanParameter({
        name: 'displayOnBlank',
        parameterName: 'Display outlines on a blank image?',
        title: 'Display outlines on a blank image',
        value: false
      }),
      new FileParameter({
        name: 'input',
        parameterName: 'Select image on which to display outlines',
        title: 'Image',
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('displayOnBlank') !== true
      }),
      new StringParameter({
        name: 'name',
        parameterName: 'Name the output image',
        title: 'Output image name'
      }),
      new ListParameter({
        name: OutlineObjectsConfigurationParameter.displayModeParameter,
        title: 'Display mode',
        parameterName: 'Outline display mode',
        values: [
          displayModes.color,
          displayModes.grayscale
        ],
        value: displayModes.color
      }),
      outlineModeParameter(),
      new OutlineObjectsConfigurationParameter()
    );
  }
  @computed
  get outputs () {
    const name = this.getParameterValue('name');
    if (name) {
      return [{
        type: AnalysisTypes.file,
        value: name,
        name,
        module: this
      }];
    }
    return [];
  }
}

export {OverlayOutlines, outlineModeParameter};
