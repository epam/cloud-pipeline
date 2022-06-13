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
  StringParameter,
  BooleanParameter,
  ObjectParameter,
  ListParameter
} from '../../parameters';
import {AnalysisTypes} from '../../common/analysis-types';

const childParentDistances = {
  none: 'None',
  centroid: 'Centroid',
  minimum: 'Minimum',
  both: 'Both'
};

class RelateObjects extends AnalysisModule {
  initialize () {
    super.initialize();
    this.registerParameters(
      new ObjectParameter({
        name: 'parent',
        title: 'Parent objects',
        parameterName: 'Parent objects'
      }),
      new ObjectParameter({
        name: 'child',
        title: 'Child objects',
        parameterName: 'Child objects'
      }),
      new BooleanParameter({
        name: 'calculateMeans',
        title: 'Calculate per-parent means for all child measurements',
        parameterName: 'Calculate per-parent means for all child measurements?',
        value: true
      }),
      new ListParameter({
        name: 'childParentDistances',
        title: 'Calculate child-parent distances',
        parameterName: 'Calculate child-parent distances?',
        values: [
          childParentDistances.none,
          childParentDistances.centroid,
          childParentDistances.minimum,
          childParentDistances.both
        ],
        value: childParentDistances.none
      }),
      new BooleanParameter({
        name: 'saveAsNew',
        title: 'Save as new objects',
        parameterName: 'Do you want to save the children with parents as a new object set?',
        value: false
      }),
      new StringParameter({
        name: 'name',
        title: 'Name the objects',
        parameterName: 'Name the output object',
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) => {
          return module.getParameterValue('saveAsNew') === true;
        }
      })
    );
  }
  @computed
  get outputs () {
    const saveAsNew = this.getParameterValue('saveAsNew') === true;
    const name = this.getParameterValue('name');
    if (saveAsNew && name) {
      return [{
        type: AnalysisTypes.object,
        value: name,
        name,
        module: this
      }];
    }
    return [];
  }
}

export {RelateObjects};
