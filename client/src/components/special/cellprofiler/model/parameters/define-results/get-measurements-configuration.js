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

import {getObjectProperties, ObjectProperty, ObjectPropertyName} from './object-properties';
import {AllStats} from './property-functions';

function expandProperties (properties, pipeline, object) {
  if (!pipeline || !object) {
    return [];
  }
  const spot = pipeline.getObjectIsSpot(object);
  const spotWithParent = spot && pipeline.getObjectIsSpotWithParent(object);
  const hasChild = pipeline.getObjectHasSpots(object);
  return (properties || [])
    .map(aProp => aProp.name === ObjectProperty.all
      ? getObjectProperties({hasChild, spotWithParent, spot})
        .map(o => ({name: o, stats: AllStats}))
      : [aProp]
    )
    .reduce((r, c) => ([...r, ...c]));
}

/**
 * @typedef {Object} SpotInfo
 * @property {string} name
 * @property {string} image
 * @property {string} parent
 */

/**
 * @typedef {Object} ConfigurationProperty
 * @property {string} image
 * @property {string} object
 * @property {SpotInfo[]} spots
 * @property {string[]} parents
 * @property {string} property
 * @property {string[]} [stats]
 * @property {string} [variable]
 * @property {string} [customName]
 */

/**
 * @typedef {Object} ConfigurationFormula
 * @property {ConfigurationProperty[]} variables
 * @property {string[]} expression
 * @property {boolean} isFormula
 * @property {string} name
 */

/**
 * @param configuration
 * @param pipeline
 * @returns {(ConfigurationProperty | ConfigurationFormula)[]}
 */
function getConfigurationProperties (configuration, pipeline) {
  if (!pipeline || !configuration) {
    return [];
  }
  const spots = pipeline.spots.slice();
  if (configuration.isFormula) {
    const {
      name,
      expression = [],
      variables = {}
    } = configuration;
    const variableConfigurations = Object
      .entries(variables || {})
      .map(([variableName, variableValue]) => {
        const {object, property, function: stat} = variableValue;
        return {
          object,
          image: pipeline.getSourceImageForObject(object),
          spots: spots.filter(aSpot => aSpot.parent === object),
          parents: spots.filter(aSpot => aSpot.name === object)
            .map(aSpot => aSpot.parent),
          property,
          stats: stat ? [stat] : AllStats,
          variable: variableName
        };
      });
    return [{
      isFormula: true,
      name,
      variables: variableConfigurations,
      expression
    }];
  }
  const {
    object,
    properties: props = []
  } = configuration;
  return expandProperties(props, pipeline, object)
    .map(property => ({
      object,
      image: pipeline.getSourceImageForObject(object),
      spots: spots.filter(aSpot => aSpot.parent === object),
      parents: spots.filter(aSpot => aSpot.name === object).map(aSpot => aSpot.parent),
      property: property.name,
      stats: property.stats && property.stats.length ? property.stats : AllStats
    }));
}

/**
 * @param {AnalysisPipeline} pipeline
 * @param {*[]} configurations
 * @returns {{image: string, objects: string[]}[]}
 */
export function getMeasureObjectIntensityTargets (pipeline, configurations) {
  if (!pipeline) {
    return [];
  }
  const intensitiesConfiguration = configurations
    .slice()
    .map(aConfiguration => {
      return getConfigurationProperties(aConfiguration, pipeline)
        .reduce((result, current) => {
          if (current.isFormula) {
            return [...result, ...current.variables];
          }
          return [...result, current];
        }, [])
        .map((aProperty) => {
          switch (aProperty.property) {
            case ObjectProperty.meanSpotIntensity:
            case ObjectProperty.spotBackgroundIntensity:
            case ObjectProperty.correctedSpotIntensity:
            case ObjectProperty.uncorrectedSpotIntensity:
            case ObjectProperty.relativeSpotIntensity:
            case ObjectProperty.spotContrast:
              return [{object: aProperty.object, image: aProperty.image}];
            case ObjectProperty.relativeSpotsIntensity:
              return aProperty.spots
                .map(childSpot => [
                  {object: aProperty.object, image: childSpot.image},
                  {object: childSpot.name, image: childSpot.image}
                ])
                .reduce((r, c) => ([...r, ...c]), []);
            case ObjectProperty.spotToRegionIntensity:
            case ObjectProperty.regionIntensity:
              return aProperty.parents
                .map(parent => [
                  {object: parent, image: aProperty.image},
                  {object: aProperty.object, image: aProperty.image}
                ])
                .reduce((r, c) => ([...r, ...c]), []);
            default:
              return [];
          }
        }).reduce((r, c) => ([...r, ...c]), []);
    })
    .reduce((r, c) => ([...r, ...c]), []);
  const uniqueImages = [...(new Set(intensitiesConfiguration.map(config => config.image)))];
  return uniqueImages.map(image => ({
    image,
    objects: [...new Set(
      intensitiesConfiguration
        .filter(config => config.image === image)
        .map(config => config.object)
    )]
  }));
}

/**
 * @param {AnalysisPipeline} pipeline
 * @param {{object: string}[]} configurations
 * @returns {string[]}
 */
export function getMeasureObjectSizeTargets (pipeline, configurations) {
  if (!pipeline) {
    return [];
  }
  /**
   * @type {string[]}
   */
  const sizesConfiguration = configurations
    .slice()
    .map(aConfiguration => {
      return getConfigurationProperties(aConfiguration, pipeline)
        .reduce((result, current) => {
          if (current.isFormula) {
            return [...result, ...current.variables];
          }
          return [...result, current];
        }, [])
        .map((aProperty) => {
          switch (aProperty.property) {
            case ObjectProperty.area:
              return [aProperty.object];
            case ObjectProperty.totalSpotArea:
            case ObjectProperty.numberOfSpotsPerAreaOf:
              return [aProperty.object, ...aProperty.spots.map(childSpot => childSpot.name)];
            case ObjectProperty.spotToRegionIntensity:
            case ObjectProperty.regionIntensity:
              return [aProperty.object, ...aProperty.parents];
            default:
              return [];
          }
        }).reduce((r, c) => ([...r, ...c]), []);
    })
    .reduce((r, c) => ([...r, ...c]), []);
  return [...(new Set(sizesConfiguration))];
}

/**
 * @typedef {Object} SpecItem
 * @property {string} primary
 * @property {string} [secondary]
 * @property {string} operation
 * @property {string[]} [stat_functions]
 * @property {string} [column_operation_name]
 */

/**
 * @param {ConfigurationProperty|ConfigurationFormula} aProperty
 * @returns {SpecItem[]}
 */
function processConfigurationProperty (aProperty) {
  switch (aProperty.property) {
    case ObjectProperty.numberOfObjects:
      return [{
        primary: aProperty.object,
        operation: aProperty.property,
        stat_functions: []
      }];
    case ObjectProperty.meanSpotIntensity:
    case ObjectProperty.spotBackgroundIntensity:
    case ObjectProperty.correctedSpotIntensity:
    case ObjectProperty.uncorrectedSpotIntensity:
    case ObjectProperty.relativeSpotIntensity:
    case ObjectProperty.spotContrast:
    case ObjectProperty.area:
      return [{
        primary: aProperty.object,
        operation: aProperty.property,
        stat_functions: aProperty.stats || AllStats
      }];
    case ObjectProperty.relativeSpotsIntensity:
    case ObjectProperty.totalSpotArea:
    case ObjectProperty.numberOfSpots:
    case ObjectProperty.numberOfSpotsPerAreaOf:
      return aProperty.spots.map(childSpot => ({
        primary: aProperty.object,
        secondary: childSpot.name,
        operation: aProperty.property,
        stat_functions: aProperty.stats || AllStats
      }));
    case ObjectProperty.regionIntensity:
      return aProperty.parents.map(primary => ({
        primary: primary,
        secondary: aProperty.object,
        operation: aProperty.property,
        stat_functions: aProperty.stats || AllStats,
        column_operation_name:
          `${aProperty.object} - ${primary} ${ObjectPropertyName[aProperty.property]}`
      }));
    case ObjectProperty.spotToRegionIntensity:
      return aProperty.parents.map(primary => ({
        primary: primary,
        secondary: aProperty.object,
        operation: aProperty.property,
        stat_functions: aProperty.stats || AllStats
      }));
    default:
      return [{
        primary: aProperty.object,
        operation: aProperty.property,
        stat_functions: aProperty.stats || AllStats
      }];
  }
}

/**
 * @param {AnalysisPipeline} pipeline
 * @param {*[]} configurations
 * @returns {*[]}
 */
export function getSpecs (pipeline, configurations) {
  if (!pipeline) {
    return [];
  }
  return configurations
    .slice()
    .map(aConfiguration => {
      return getConfigurationProperties(aConfiguration, pipeline)
        .map((aProperty) => {
          if (aProperty.isFormula) {
            return [{
              properties: aProperty.variables
                .map(aVariable => ({
                  ...(processConfigurationProperty(aVariable) || [])[0],
                  column_operation_name: aVariable.variable
                })),
              expression: aProperty.expression,
              formula: true,
              column_operation_name: aProperty.name
            }];
          }
          return processConfigurationProperty(aProperty);
        }).reduce((r, c) => ([...r, ...c]), []);
    })
    .reduce((r, c) => ([...r, ...c]), []);
}
