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

import {computed, observable, observe} from 'mobx';
import {AnalysisModule} from '../base';
import {
  FileParameter,
  IntegerRangeParameter,
  StringParameter,
  BooleanParameter,
  ListParameter,
  FloatParameter,
  IntegerParameter,
  ColorParameter
} from '../../parameters';
import {AnalysisTypes} from '../../common/analysis-types';
import colorList from '../../common/color-list';
import {thresholdConfiguration} from './thresholding-methods';
import {outlineModeParameter, OverlayOutlines} from './overlay-outlines';
import {SaveImages} from './save-images';

const clumpedObjectsMethods = {
  none: 'None',
  intensity: 'Intensity',
  shape: 'Shape',
  propagate: 'Propagate'
};

const fillHolesMethod = {
  thresholdAndDeclumping: 'After both thresholding and declumping',
  declumping: 'After declumping only',
  never: 'Never'
};

const excessiveHandlers = {
  continue: 'Continue',
  erase: 'Erase'
};

class IdentifyPrimaryObjects extends AnalysisModule {
  @observable overlayOutlinesModule;
  @observable saveImagesModule;
  @computed
  get objectsName () {
    return this.getParameterValue('name');
  }
  @computed
  get inputImage () {
    return this.getParameterValue('input');
  }
  @computed
  get outlineColor () {
    return this.getParameterValue('color');
  }
  @computed
  get outlineMode () {
    return this.getParameterValue('outlineMode');
  }
  initialize () {
    super.initialize();
    this.registerParameters(
      new FileParameter({
        name: 'input',
        parameterName: 'Select the input image',
        title: 'Input image'
      }),
      new StringParameter({
        name: 'name',
        parameterName: 'Name the primary objects to be identified',
        title: 'Primary objects name'
      }),
      new IntegerRangeParameter({
        name: 'diameterRange',
        parameterName: 'Typical diameter of objects, in pixel units (Min,Max)',
        title: 'Typical diameter of object, in pixels',
        range: {min: 0}
      }),
      new BooleanParameter({
        name: 'discardObjectsOutside',
        parameterName: 'Discard objects outside the diameter range?',
        title: 'Discard objects outside the diameter range',
        value: true
      }),
      new BooleanParameter({
        name: 'discardObjectsTouchingBorder',
        parameterName: 'Discard objects touching the border of the image?',
        title: 'Discard objects touching the border of the image',
        value: true
      }),
      new ColorParameter({
        name: 'color',
        local: true,
        title: 'Outline color',
        parameterName: 'Outline display color',
        value: colorList[0]
      }),
      outlineModeParameter({name: 'outlineMode', local: true, advanced: true}),
      ...thresholdConfiguration(),
      new ListParameter({
        advanced: true,
        name: 'clumpedObjectsMethod',
        title: 'Method to distinguish clumped objects',
        parameterName: 'Method to distinguish clumped objects',
        values: [
          clumpedObjectsMethods.intensity,
          clumpedObjectsMethods.shape,
          clumpedObjectsMethods.none
        ],
        value: clumpedObjectsMethods.intensity
      }),
      new ListParameter({
        advanced: true,
        name: 'clumpedObjectsDrawMethod',
        title: 'Method to draw dividing lines between clumped objects',
        parameterName: 'Method to draw dividing lines between clumped objects',
        values: [
          clumpedObjectsMethods.intensity,
          clumpedObjectsMethods.shape,
          clumpedObjectsMethods.propagate,
          clumpedObjectsMethods.none
        ],
        value: clumpedObjectsMethods.intensity,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('clumpedObjectsMethod') !== clumpedObjectsMethods.none
      }),
      new BooleanParameter({
        advanced: true,
        name: 'declumpingAutoSize',
        title: 'Automatically calculate size of smoothing filter for declumping',
        parameterName: 'Automatically calculate size of smoothing filter for declumping?',
        value: true,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('clumpedObjectsMethod') !== clumpedObjectsMethods.none &&
          module.getParameterValue('clumpedObjectsDrawMethod') !== clumpedObjectsMethods.none
      }),
      new FloatParameter({
        advanced: true,
        name: 'declumpingSmoothFilter',
        title: 'Size of smoothing filter',
        parameterName: 'Size of smoothing filter',
        value: 10,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('declumpingAutoSize') === false
      }),
      new BooleanParameter({
        advanced: true,
        name: 'declumpingAutoMinDistance',
        title: 'Automatically calculate minimum allowed distance between local maxima',
        parameterName: 'Automatically calculate minimum allowed distance between local maxima?',
        value: true,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('clumpedObjectsMethod') !== clumpedObjectsMethods.none &&
          module.getParameterValue('clumpedObjectsDrawMethod') !== clumpedObjectsMethods.none
      }),
      new FloatParameter({
        advanced: true,
        name: 'suppressLocalMaximaDistance',
        title: 'Suppress local maxima that are closer than this minimum allowed distance',
        parameterName: 'Suppress local maxima that are closer than this minimum allowed distance',
        value: 7,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('declumpingAutoMinDistance') === false
      }),
      new BooleanParameter({
        advanced: true,
        name: 'speedUp',
        title: 'Speed up by using lower-resolution image to find local maxima',
        parameterName: 'Speed up by using lower-resolution image to find local maxima?',
        value: true,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('clumpedObjectsMethod') !== clumpedObjectsMethods.none &&
          module.getParameterValue('clumpedObjectsDrawMethod') !== clumpedObjectsMethods.none
      }),
      new BooleanParameter({
        advanced: true,
        name: 'displayLocalMaxima',
        title: 'Display accepted local maxima',
        parameterName: 'Display accepted local maxima?',
        value: false,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('clumpedObjectsMethod') !== clumpedObjectsMethods.none &&
          module.getParameterValue('clumpedObjectsDrawMethod') !== clumpedObjectsMethods.none
      }),
      new ColorParameter({
        advanced: true,
        name: 'maximaColor',
        title: 'Maxima color',
        parameterName: 'Select maxima color',
        value: colorList[0] || '#0000FF',
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('displayLocalMaxima') === true
      }),
      new FloatParameter({
        advanced: true,
        name: 'maximaSize',
        title: 'Maxima size',
        parameterName: 'Select maxima size',
        value: 1,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('displayLocalMaxima') === true
      }),
      new ListParameter({
        advanced: true,
        name: 'fillHoles',
        parameterName: 'Fill holes in identified objects?',
        title: 'Fill holes in identified objects',
        values: [
          fillHolesMethod.thresholdAndDeclumping,
          fillHolesMethod.declumping,
          fillHolesMethod.never
        ],
        value: fillHolesMethod.thresholdAndDeclumping
      }),
      new ListParameter({
        advanced: true,
        name: 'excessiveMethod',
        parameterName: 'Handling of objects if excessive number of objects identified',
        title: 'Handling of objects if excessive number of objects identified',
        values: [
          excessiveHandlers.continue,
          excessiveHandlers.erase
        ],
        value: excessiveHandlers.continue
      }),
      new IntegerParameter({
        advanced: true,
        name: 'maximumObjects',
        title: 'Maximum number of objects',
        parameterName: 'Maximum number of objects',
        value: 500,
        /**
         * @param {AnalysisModule} module
         */
        visibilityHandler: (module) =>
          module.getParameterValue('excessiveMethod') === excessiveHandlers.erase
      })
    );
    this.overlayOutlinesModule = new OverlayOutlines(this.analysis, true);
    this.saveImagesModule = new SaveImages(this.analysis, true);
    this.hiddenModules = [this.overlayOutlinesModule, this.saveImagesModule];
    observe(this, 'objectsName', this.update.bind(this));
    observe(this, 'inputImage', this.update.bind(this));
    observe(this, 'outlineColor', this.update.bind(this));
    observe(this, 'outlineMode', this.update.bind(this));
    observe(this.overlayOutlinesModule, 'objectsName', this.updateSaveImagesModule.bind(this));
    this.updateInput();
  }

  applySettings (settings) {
    super.applySettings(settings);
    this.updateInput();
  };

  @computed
  get outputs () {
    const name = this.getParameterValue('name');
    if (name) {
      return [{
        type: AnalysisTypes.object,
        value: name,
        name,
        module: this
      }];
    }
    return [];
  }

  updateInput () {
    const configuration = this.getParameterConfiguration('input');
    const value = this.inputImage;
    if ((!value || /^none$/i.test(value)) && configuration && configuration.defaultValue) {
      this.setParameterValue('input', configuration.defaultValue);
    }
  }

  update () {
    this.overlayOutlinesModule.setParameterValue('displayOnBlank', true);
    this.overlayOutlinesModule.setParameterValue('name', `${this.objectsName} overlay`);
    this.overlayOutlinesModule.setParameterValue('input', this.inputImage);
    this.overlayOutlinesModule.setParameterValue('outlineMode', this.outlineMode);
    this.overlayOutlinesModule.setParameterValue('output', [{
      name: this.objectsName,
      color: this.outlineColor
    }]);
    this.updateSaveImagesModule();
  }

  updateSaveImagesModule () {
    this.saveImagesModule.setParameterValue('source', this.overlayOutlinesModule.objectsName);
    this.saveImagesModule.setParameterValue('filePrefix', this.inputImage);
    this.saveImagesModule.setParameterValue('format', 'png');
  }
}

export {IdentifyPrimaryObjects};
