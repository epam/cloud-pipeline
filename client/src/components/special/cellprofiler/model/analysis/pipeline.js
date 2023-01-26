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

import {action, computed, observable} from 'mobx';
import moment from 'moment-timezone';
import {AnalysisModule} from '../modules/base';
import {AnalysisTypes} from '../common/analysis-types';
import {DefineResultsModuleName} from '../modules/implementation/define-results';
import GraphicsOutputConfiguration from './graphics-output-configuration';
import generateUUID from '../common/generate-uuid';

const CLOUD_PIPELINE_CELL_PROFILER_PIPELINE_TYPE = 'CloudPipeline CellProfiler pipeline';

class AnalysisPipeline {
  @observable uuid;
  @observable name;
  @observable author;
  @observable description;
  @observable createdDate;
  @observable modifiedDate;
  @observable path;
  @observable version;
  @observable isNew = true;
  usedChannels = [];
  /**
   * @type {Analysis}
   */
  @observable analysis;
  /**
   * @type {AnalysisModule[]}
   */
  @observable modules = [];
  @observable changed = false;
  /**
   * @type {AnalysisModule}
   */
  @observable defineResults;
  @observable graphicsOutput = new GraphicsOutputConfiguration();

  constructor (analysis) {
    this.analysis = analysis;
    this.createdDate = moment.utc();
    this.modifiedDate = moment.utc();
    this.uuid = generateUUID();
    this.defineResults = AnalysisModule.createModule(
      DefineResultsModuleName,
      {},
      {pipeline: this}
    );
  }

  @computed
  get namesAndTypes () {
    if (!this.analysis) {
      return undefined;
    }
    return this.analysis.namesAndTypes;
  }

  @computed
  get channels () {
    if (!this.namesAndTypes) {
      return [];
    }
    return this.namesAndTypes.outputs.map(output => output.name);
  }

  @computed
  get physicalSize () {
    if (!this.analysis) {
      return undefined;
    }
    return this.analysis.physicalSize;
  }

  /**
   * @returns {{parent: string, name: string, image:string}[]}
   */
  @computed
  get spots () {
    // Spots are objects that are used as "child" within "RelateObjects"/"FindSpots" modules
    // "RelateObjects":
    const spots = [];// an array of the {spot, parent}
    const relateObjectsModules = this.modules
      .filter(cpModule => /^RelateObjects$/i.test(cpModule.name));
    const appendSpots = (parent, child) => {
      const image = this.getSourceImageForObject(child);
      if (child && image) {
        spots.push({
          parent: parent,
          name: child,
          image
        });
      }
    };
    relateObjectsModules.forEach(relateObjectsModule => {
      const parent = relateObjectsModule.getParameterValue('parent');
      const saveAsNew = relateObjectsModule.getBooleanParameterValue('saveAsNew');
      const child = saveAsNew
        ? relateObjectsModule.getParameterValue('name')
        : relateObjectsModule.getParameterValue('child');
      appendSpots(saveAsNew ? undefined : parent, child);
    });
    const findSpotsModules = this.modules
      .filter(cpModule => /^FindSpots$/i.test(cpModule.name));
    findSpotsModules.forEach(findSpotsModule => {
      const parent = findSpotsModule.getParameterValue('parentObject');
      const child = findSpotsModule.getParameterValue('output');
      appendSpots(parent === 'None' ? undefined : parent, child);
    });
    return spots.filter((aSpot, index, array) => {
      return array.slice(0, index)
        .filter(o => o.name === aSpot.name && o.parent === aSpot.parent)
        .length === 0;
    });
  }

  @computed
  get populations () {
    // Populations are objects that found using FindNuclei / IdentifyPrimaryObjects methods
    // and NOT used as children within the RelateObjects methods
    const populations = [];
    this.modules
      .filter(cpModule => /^(FindNuclei|IdentifyPrimaryObjects)$/i.test(cpModule.name))
      .forEach(cpModule => {
        const name = cpModule.getParameterValue('name');
        const image = cpModule.getParameterValue('input');
        if (name && image) {
          populations.push({name, image});
        }
      });
    const populationNames = new Set(populations.map(o => o.name));
    const exclude = new Set();
    this.modules
      .filter(cpModule => /^RelateObjects$/i.test(cpModule.name))
      .forEach(relateObjectsModule => {
        const saveAsNew = relateObjectsModule.getBooleanParameterValue('saveAsNew');
        const child = saveAsNew
          ? relateObjectsModule.getParameterValue('name')
          : relateObjectsModule.getParameterValue('child');
        if (child && populationNames.has(child)) {
          exclude.add(child);
        }
      });
    return populations.filter(population => !exclude.has(population.name));
  }

  @computed
  get regionsOfInterest () {
    // ROI are objects found using FindCells, FindCytoplasm, IdentifySecondaryObjects,
    // IdentifyTertiaryObjects modules
    const rois = []; // an array of the {name, parent}
    this.modules
      .filter(cpModule => /^FindCells$/i.test(cpModule.name))
      .forEach(cpModule => {
        const name = cpModule.getParameterValue('name');
        const parent = cpModule.getParameterValue('nuclei');
        if (name && parent) {
          rois.push({name, parent});
        }
      });
    this.modules
      .filter(cpModule => /^FindCytoplasm$/i.test(cpModule.name))
      .forEach(cpModule => {
        const name = cpModule.getParameterValue('output');
        const nuclei = cpModule.getParameterValue('nuclei');
        const cells = cpModule.getParameterValue('cells');
        if (name && nuclei && cells) {
          rois.push({name, parent: nuclei});
          rois.push({name, parent: cells});
        }
      });
    this.modules
      .filter(cpModule => /^IdentifySecondaryObjects$/i.test(cpModule.name))
      .forEach(cpModule => {
        const name = cpModule.getParameterValue('name');
        const nuclei = cpModule.getParameterValue('inputObjects');
        if (name && nuclei) {
          rois.push({name, parent: nuclei});
        }
      });
    this.modules
      .filter(cpModule => /^IdentifyTertiaryObjects$/i.test(cpModule.name))
      .forEach(cpModule => {
        const name = cpModule.getParameterValue('output');
        const nuclei = cpModule.getParameterValue('large');
        const cells = cpModule.getParameterValue('small');
        if (name && nuclei && cells) {
          rois.push({name, parent: nuclei});
          rois.push({name, parent: cells});
        }
      });
    return rois;
  }

  @computed
  get objects () {
    // All objects found
    const all = this.modules
      .map(cpModule => cpModule.outputs
        .filter(output => output.type === AnalysisTypes.object)
        .map(output => output.name)
      )
      .reduce((r, c) => ([...r, ...c]), []);
    return [...new Set(all)];
  }

  @computed
  get defineResultsAreEmpty () {
    if (!this.defineResults) {
      return true;
    }
    const configuration = this.defineResults.getParameterValue('configuration');
    return !configuration ||
      configuration.filter(o => o.object).length === 0;
  }

  @computed
  get parametersWithErrors () {
    return this.modules
      .reduce((acc, current) => ([
        ...acc,
        ...(current.parameters || [])
      ]), [])
      .filter(param => param.isInvalid);
  }

  getObjectIsSpot = (object) => {
    return this.spots.some(aSpot => aSpot.name === object);
  }

  getObjectIsSpotWithParent = (object) => {
    return this.spots.some(aSpot => aSpot.name === object && !!aSpot.parent);
  }

  getObjectHasSpots = (object) => {
    return this.spots.some(aSpot => aSpot.parent === object);
  }

  getAllObjectParents = (object) => {
    const result = [object];
    if (object) {
      const rois = [...new Set(
        this.regionsOfInterest
          .filter(roi => roi.name === object)
          .map(roi => roi.parent)
      )];
      result.push(...rois
        .map(roi => this.getAllObjectParents(roi)).reduce((r, c) => ([...r, ...c]), [])
      );
    }
    return result.filter(Boolean);
  };

  getSourceImageForObject = (object) => {
    if (!object) {
      return undefined;
    }
    const cpModule = this.modules
      .find(aModule =>
        aModule.outputs
          .some(output => output.name === object && output.type === AnalysisTypes.object)
      );
    if (cpModule) {
      if (
        /^RelateObjects$/i.test(cpModule.name) &&
        cpModule.getBooleanParameterValue('saveAsNew')
      ) {
        return this.getSourceImageForObject(cpModule.getParameterValue('child'));
      }
      return cpModule.sourceImage;
    }
    return undefined;
  };

  /**
   * @param {object} analysisModuleConfiguration
   * @returns {AnalysisModule}
   */
  @action
  add = async (analysisModuleConfiguration) => {
    const newModule = new AnalysisModule(this, analysisModuleConfiguration);
    this.modules.push(newModule);
    newModule.setParameterDefaultValues();
    return newModule;
  };

  exportPipeline = (json = false) => {
    let author = this.author;
    if (json) {
      return {
        uuid: this.uuid,
        path: this.path,
        version: this.version,
        name: this.name,
        description: this.description,
        channels: this.channels,
        modules: this.modules.map(module => module.exportModule(json)),
        defineResults: this.defineResults.exportModule(json)
      };
    }
    const header = [
      CLOUD_PIPELINE_CELL_PROFILER_PIPELINE_TYPE,
      this.uuid ? `Identifier:${this.uuid}` : false,
      this.name ? `Name:${this.name}` : false,
      this.description ? `Description:${this.description}` : false,
      author ? `Author:${author}` : false,
      this.createdDate
        ? `Created:${moment.utc(this.createdDate).format('YYYY-MM-DD HH:mm:ss')}`
        : false,
      this.modifiedDate
        ? `Modified:${moment.utc(this.modifiedDate).format('YYYY-MM-DD HH:mm:ss')}`
        : false,
      this.channels.length > 0 ? `Channels:${JSON.stringify(this.channels)}` : false,
      `Outlines:${this.graphicsOutput.exportConfigurations()}`
    ].filter(Boolean).join('\n');
    return [
      header,
      ...this.modules.map(module => module.exportModule()),
      this.defineResults.exportModule()
    ].join('\n\n');
  }

  static importPipeline (content) {
    if (!content) {
      return undefined;
    }
    try {
      const [
        header,
        ...modulesContent
      ] = content.split(/[\r]?\n[\r]?\n/);
      const pipeline = new AnalysisPipeline();
      pipeline.isNew = false;
      const [
        pipelineType,
        ...pipelineInfos
      ] = header.split(/[\r]?\n/);
      pipelineInfos.forEach(info => {
        const [key, ...valueParts] = info.split(':');
        const value = valueParts.join(':');
        if (/^identifier$/i.test(key)) {
          pipeline.uuid = value;
        } else if (/^name$/i.test(key)) {
          pipeline.name = value;
        } else if (/^version$/i.test(key)) {
          pipeline.version = value;
        } else if (/^description$/i.test(key)) {
          pipeline.description = value;
        } else if (/^author$/i.test(key)) {
          pipeline.author = value;
        } else if (/^created$/i.test(key)) {
          pipeline.createdDate = moment.utc(value);
        } else if (/^modified$/i.test(key)) {
          pipeline.modifiedDate = moment.utc(value);
        } else if (/^channels$/i.test(key)) {
          try {
            const channels = JSON.parse(value);
            if (!Array.isArray(channels)) {
              throw new Error(`Unknown channels format: ${value}`);
            }
            pipeline.usedChannels = channels;
          } catch (e) {
            console.warn(e.message);
          }
        } else if (/^outlines$/i.test(key)) {
          try {
            pipeline.graphicsOutput = GraphicsOutputConfiguration.importConfigurations(value);
          } catch (e) {
            console.warn(e.message);
          }
        }
      });
      if (pipelineType !== CLOUD_PIPELINE_CELL_PROFILER_PIPELINE_TYPE) {
        console.warn('Unsupported pipeline type:', pipelineType);
      }
      const modules = modulesContent
        .map(moduleContent => AnalysisModule.importModule(
          moduleContent,
          {pipeline, throwError: true}
        ));
      pipeline.modules = modules.filter(o => o.name !== DefineResultsModuleName);
      pipeline.defineResults = modules.find(o => o.name === DefineResultsModuleName) ||
        pipeline.defineResults;
      return pipeline;
    } catch (error) {
      console.warn(`Error importing pipeline: ${error.message}`);
    }
    return undefined;
  }

  getMissingInputs = () => (this.modules || [])
    .map((aModule) => aModule.getMissingInputs())
    .reduce((r, c) => ([...r, ...c]), []);

  correctInputsForModules = (correctedInputs = {}) => {
    let corrected = false;
    this.modules.forEach((aModule) => {
      corrected = aModule.correctInputsForModules(correctedInputs) || corrected;
    });
    return corrected;
  };
}

export {AnalysisPipeline};
