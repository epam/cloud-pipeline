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

import {observable} from 'mobx';
import {ModuleParameter} from './base';
import {AnalysisTypes} from '../common/analysis-types';

class RangeParameter extends ModuleParameter {
  @observable min;
  @observable max;

  /**
   * @param {ModuleParameterOptions & {range: {min: number?, max: number?}?}} options
   */
  constructor (options = {name: 'range'}) {
    const {
      range = {}
    } = options;
    super({
      ...options,
      isRange: true
    });
    const {
      min = -Infinity,
      max = Infinity
    } = range;
    this.min = min;
    this.max = max;
  }
}

class FloatRangeParameter extends RangeParameter {
  /**
   * @param {ModuleParameterOptions & {range: {min: number?, max: number?}?}} options
   */
  constructor (options = {name: 'floatRange'}) {
    super({...options, type: AnalysisTypes.float});
  }
}

class IntegerRangeParameter extends RangeParameter {
  /**
   * @param {ModuleParameterOptions & {range: {min: number?, max: number?}?}} options
   */
  constructor (options = {name: 'integerRange'}) {
    super({...options, type: AnalysisTypes.integer});
  }
}

export {
  RangeParameter,
  FloatRangeParameter,
  IntegerRangeParameter
};
