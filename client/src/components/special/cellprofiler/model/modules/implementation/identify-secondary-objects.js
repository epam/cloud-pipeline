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
  FloatParameter,
  IntegerParameter,
  StringParameter,
  BooleanParameter,
  ListParameter,
  ObjectParameter, ColorParameter
} from '../../parameters';
import {AnalysisTypes} from '../../common/analysis-types';
import {thresholdConfiguration} from './thresholding-methods';
import {outlineModeParameter, OverlayOutlines} from './overlay-outlines';
import {SaveImages} from './save-images';
import colorList from '../../common/color-list';

const methods = {
  propagation: 'Propagation',
  watershedGradient: 'Watershed - Gradient',
  watershedImage: 'Watershed - Image',
  distanceN: 'Distance - N',
  distanceB: 'Distance - B'
};

class IdentifySecondaryObjects extends AnalysisModule {
  static get identifier () {
    return 'IdentifySecondaryObjects';
  }
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
        title: 'Input image',
        parameterName: 'Select the input image'
      }),
      new ObjectParameter({
        name: 'inputObjects',
        title: 'Input objects',
        parameterName: 'Select the input objects'
      }),
      new StringParameter({
        name: 'name',
        title: 'Name the objects to be identified',
        parameterName: 'Name the objects to be identified'
      }),
      new ListParameter({
        name: 'method',
        title: 'Method to identify the secondary objects',
        parameterName: 'Select the method to identify the secondary objects',
        values: [
          methods.propagation,
          methods.watershedGradient,
          methods.watershedImage,
          methods.distanceN,
          methods.distanceB
        ],
        value: methods.distanceN
      }),
      new ColorParameter({
        name: 'color',
        local: true,
        title: 'Outline color',
        parameterName: 'Outline display color',
        value: colorList[0]
      }),
      outlineModeParameter({name: 'outlineMode', local: true, advanced: true}),
      ...thresholdConfiguration(
        false,
        (cpModule) => cpModule.getParameterValue('method') !== methods.distanceN
      ),
      new FloatParameter({
        name: 'regularizationFactor',
        title: 'Regularization factor',
        parameterName: 'Regularization factor',
        value: 0.05,
        /**
         * @param {AnalysisModule} cpModule
         */
        visibilityHandler: (cpModule) => {
          return cpModule.getParameterValue('method') === methods.propagation;
        }
      }),
      new IntegerParameter({
        name: 'distanceN',
        title: 'Number of pixels by which to expand the primary objects',
        parameterName: 'Number of pixels by which to expand the primary objects',
        /**
         * @param {AnalysisModule} cpModule
         */
        visibilityHandler: (cpModule) => {
          return cpModule.getParameterValue('method') === methods.distanceN;
        }
      }),
      new BooleanParameter({
        name: 'fillHoles',
        title: 'Fill holes in identified objects',
        parameterName: 'Fill holes in identified objects?',
        value: true
      }),
      new BooleanParameter({
        name: 'discardObjectsTouchingBorder',
        title: 'Discard secondary objects touching the border of the image?',
        value: false
      }),
      new BooleanParameter({
        name: 'discardPrimaryObjects',
        title: 'Discard the associated primary objects?',
        value: false,
        /**
         * @param {AnalysisModule} cpModule
         */
        visibilityHandler: (cpModule) =>
          cpModule.getParameterValue('discardObjectsTouchingBorder') === true
      }),
      new StringParameter({
        name: 'newName',
        title: 'Name the new primary objects',
        parameterName: 'Name the new primary objects',
        /**
         * @param {AnalysisModule} cpModule
         */
        visibilityHandler: (cpModule) =>
          cpModule.getParameterValue('discardObjectsTouchingBorder') === true &&
          cpModule.getParameterValue('discardPrimaryObjects') === true
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
  @computed
  get outputs () {
    const name = this.getParameterValue('name');
    const discardObjectsTouchingBorder = this.getParameterValue('discardObjectsTouchingBorder');
    const discardPrimaryObjects = this.getParameterValue('discardPrimaryObjects');
    const newName = this.getParameterValue('newName');
    const outputs = [];
    if (name) {
      outputs.push({
        type: AnalysisTypes.object,
        value: name,
        name,
        cpModule: this
      });
    }
    if (discardObjectsTouchingBorder && discardPrimaryObjects && newName) {
      outputs.push({
        type: AnalysisTypes.object,
        value: newName,
        name: newName,
        cpModule: this
      });
    }
    return outputs;
  }

  applySettings (settings) {
    super.applySettings(settings);
    this.updateInput();
  };

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

export {IdentifySecondaryObjects};
