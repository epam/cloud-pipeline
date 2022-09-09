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

const ObjectProperty = {
  all: 'all',
  numberOfObjects: 'Number of Objects',
  area: 'Area',
  totalSpotArea: 'Total Area',
  relativeSpotsIntensity: 'Relative Intensity',
  numberOfSpots: 'Number of',
  numberOfSpotsPerAreaOf: 'Number of per Area',
  meanSpotIntensity: 'Mean Intensity',
  spotBackgroundIntensity: 'Background Intensity',
  correctedSpotIntensity: 'Corrected Intensity',
  relativeSpotIntensity: 'Relative Object Intensity',
  uncorrectedSpotIntensity: 'Uncorrected Peak Intensity',
  spotContrast: 'Contrast',
  regionIntensity: 'Region Intensity',
  spotToRegionIntensity: 'To Region Intensity'
};

const ObjectPropertyName = {
  [ObjectProperty.all]: 'All properties',
  [ObjectProperty.numberOfObjects]: 'Number of Objects',
  [ObjectProperty.area]: 'Area',
  [ObjectProperty.totalSpotArea]: 'Total Spot Area',
  [ObjectProperty.relativeSpotsIntensity]: 'Relative Spot Intensity',
  [ObjectProperty.numberOfSpots]: 'Number of Spots',
  [ObjectProperty.numberOfSpotsPerAreaOf]: 'Number of Spots per Area ',
  [ObjectProperty.meanSpotIntensity]: 'Mean Spot Intensity',
  [ObjectProperty.spotBackgroundIntensity]: 'Spot Background Intensity',
  [ObjectProperty.correctedSpotIntensity]: 'Corrected Spot Intensity',
  [ObjectProperty.relativeSpotIntensity]: 'Relative Spot Intensity',
  [ObjectProperty.uncorrectedSpotIntensity]: 'Uncorrected Spot Peak Intensity',
  [ObjectProperty.spotContrast]: 'Spot Contrast',
  [ObjectProperty.regionIntensity]: 'Region Intensity',
  [ObjectProperty.spotToRegionIntensity]: 'Spot To Region Intensity'
};

/**
 * @param {{spot: boolean?, spotWithParent: boolean?, hasChild: boolean?}} objectOptions
 */
function getObjectProperties (objectOptions = {}) {
  const {
    spot = false,
    spotWithParent = false,
    hasChild = false
  } = objectOptions;
  const props = [
    ObjectProperty.numberOfObjects
  ];
  if (!spot && !hasChild) {
    props.push(...[
      ObjectProperty.area
    ]);
  }
  if (!spot && hasChild) {
    props.push(...[
      ObjectProperty.area,
      ObjectProperty.totalSpotArea,
      ObjectProperty.relativeSpotsIntensity,
      ObjectProperty.numberOfSpots,
      ObjectProperty.numberOfSpotsPerAreaOf
    ]);
  }
  if (spot) {
    props.push(...[
      ObjectProperty.area,
      ObjectProperty.meanSpotIntensity,
      ObjectProperty.spotBackgroundIntensity,
      ObjectProperty.correctedSpotIntensity,
      ObjectProperty.relativeSpotIntensity,
      ObjectProperty.uncorrectedSpotIntensity,
      ObjectProperty.spotContrast,
      spotWithParent ? ObjectProperty.regionIntensity : false,
      spotWithParent ? ObjectProperty.spotToRegionIntensity : false
    ].filter(Boolean));
  }
  return [...new Set(props)];
}

export {
  ObjectProperty,
  ObjectPropertyName,
  getObjectProperties
};
