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

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Icon, Select} from 'antd';
import {isObservableArray} from 'mobx';

const DEBUG = process.env.DEVELOPMENT;

const AreaShapeMeasurements = {
  area: 'Area',
  boundingBoxArea: 'BoundingBoxArea',
  boundingBoxMaximumX: 'BoundingBoxMaximum_X',
  boundingBoxMaximumY: 'BoundingBoxMaximum_Y',
  boundingBoxMinimumX: 'BoundingBoxMinimum_X',
  boundingBoxMinimumY: 'BoundingBoxMinimum_Y',
  centerX: 'Center_X',
  centerY: 'Center_Y',
  compactness: 'Compactness',
  convexArea: 'ConvexArea',
  eccentricity: 'Eccentricity',
  equivalentDiameter: 'EquivalentDiameter',
  eulerNumber: 'EulerNumber',
  extent: 'Extent',
  formFactor: 'FormFactor',
  majorAxisLength: 'MajorAxisLength',
  minorAxisLength: 'MinorAxisLength',
  maxFeretDiameter: 'MaxFeretDiameter',
  minFeretDiameter: 'MinFeretDiameter',
  maximumRadius: 'MaximumRadius',
  meanRadius: 'MeanRadius',
  medianRadius: 'MedianRadius',
  orientation: 'Orientation',
  perimeter: 'Perimeter',
  solidity: 'Solidity',
  zernike00: 'Zernike_0_0',
  zernike11: 'Zernike_1_1',
  zernike20: 'Zernike_2_0',
  zernike22: 'Zernike_2_2',
  zernike31: 'Zernike_3_1',
  zernike33: 'Zernike_3_3',
  zernike40: 'Zernike_4_0',
  zernike42: 'Zernike_4_2',
  zernike44: 'Zernike_4_4',
  zernike51: 'Zernike_5_1',
  zernike53: 'Zernike_5_3',
  zernike55: 'Zernike_5_5',
  zernike60: 'Zernike_6_0',
  zernike62: 'Zernike_6_2',
  zernike64: 'Zernike_6_4',
  zernike66: 'Zernike_6_6',
  zernike71: 'Zernike_7_1',
  zernike73: 'Zernike_7_3',
  zernike75: 'Zernike_7_5',
  zernike77: 'Zernike_7_7',
  zernike80: 'Zernike_8_0',
  zernike82: 'Zernike_8_2',
  zernike84: 'Zernike_8_4',
  zernike86: 'Zernike_8_6',
  zernike88: 'Zernike_8_8',
  zernike91: 'Zernike_9_1',
  zernike93: 'Zernike_9_3',
  zernike95: 'Zernike_9_5',
  zernike97: 'Zernike_9_7',
  zernike99: 'Zernike_9_9'
};

const IntensityMeasurements = {
  integratedIntensityEdge: 'IntegratedIntensityEdge',
  integratedIntensity: 'IntegratedIntensity',
  lowerQuartileIntensity: 'LowerQuartileIntensity',
  MADIntensity: 'MADIntensity',
  massDisplacement: 'MassDisplacement',
  maxIntensityEdge: 'MaxIntensityEdge',
  maxIntensity: 'MaxIntensity',
  meanIntensityEdge: 'MeanIntensityEdge',
  meanIntensity: 'MeanIntensity',
  medianIntensity: 'MedianIntensity',
  minIntensityEdge: 'MinIntensityEdge',
  minIntensity: 'MinIntensity',
  stdIntensityEdge: 'StdIntensityEdge',
  stdIntensity: 'StdIntensity',
  upperQuartileIntensity: 'UpperQuartileIntensity'
};

const LocationMeasurements = {
  centerX: 'Center_X',
  centerY: 'Center_Y',
  centerZ: 'Center_Z',
  centerMassIntensityX: 'CenterMassIntensity_X',
  centerMassIntensityY: 'CenterMassIntensity_Y',
  centerMassIntensityZ: 'CenterMassIntensity_Z',
  maxIntensityX: 'MaxIntensity_X',
  maxIntensityY: 'MaxIntensity_Y',
  maxIntensityZ: 'MaxIntensity_Z'
};

const NumberMeasurements = {
  number: 'Object_Number'
};

const unitsToPixelsMeasurements = new Set([
  AreaShapeMeasurements.boundingBoxMaximumX,
  AreaShapeMeasurements.boundingBoxMaximumY,
  AreaShapeMeasurements.boundingBoxMinimumX,
  AreaShapeMeasurements.boundingBoxMinimumY,
  AreaShapeMeasurements.centerX,
  AreaShapeMeasurements.centerY,
  AreaShapeMeasurements.equivalentDiameter,
  AreaShapeMeasurements.majorAxisLength,
  AreaShapeMeasurements.minorAxisLength,
  AreaShapeMeasurements.maxFeretDiameter,
  AreaShapeMeasurements.minFeretDiameter,
  AreaShapeMeasurements.maximumRadius,
  AreaShapeMeasurements.meanRadius,
  AreaShapeMeasurements.medianRadius,
  AreaShapeMeasurements.perimeter
]);

const squareUnitsToSquarePixelsMeasurements = new Set([
  AreaShapeMeasurements.area,
  AreaShapeMeasurements.boundingBoxArea,
  AreaShapeMeasurements.convexArea
]);

const Groups = {
  shape: 'AreaShape',
  intensity: 'Intensity',
  location: 'Location',
  number: 'Number'
};

const GroupNames = {
  [Groups.shape]: 'Shape',
  [Groups.intensity]: 'Intensity',
  [Groups.location]: 'Location',
  [Groups.number]: 'Other'
};

function parseMeasurement (measurement) {
  if (!measurement) {
    return {group: undefined, measurement: undefined};
  }
  const [group, ...names] = measurement.split('_');
  const name = names.join('_');
  return {
    group,
    measurement: name
  };
}

function isUnitsMeasurement (measurement) {
  const {
    group,
    measurement: measurementName
  } = parseMeasurement(measurement);
  switch (group) {
    case Groups.shape:
      return unitsToPixelsMeasurements.has(measurementName);
    case Groups.location:
      return true;
    default:
      return false;
  }
}

function isSquareUnitsMeasurement (measurement) {
  const {
    group,
    measurement: measurementName
  } = parseMeasurement(measurement);
  switch (group) {
    case Groups.shape:
      return squareUnitsToSquarePixelsMeasurements.has(measurementName);
    default:
      return false;
  }
}

function isShapeMeasurement (measurement) {
  return parseMeasurement(measurement).group === Groups.shape;
}

function isIntensityMeasurement (measurement) {
  const {
    group,
    measurement: name
  } = parseMeasurement(measurement);
  switch (group) {
    case Groups.intensity:
      return true;
    case Groups.location:
      return [
        LocationMeasurements.centerMassIntensityX,
        LocationMeasurements.centerMassIntensityY,
        LocationMeasurements.centerMassIntensityZ,
        LocationMeasurements.maxIntensityX,
        LocationMeasurements.maxIntensityY,
        LocationMeasurements.maxIntensityZ
      ].includes(name);
    default:
      return false;
  }
}

/**
 * Converts measurement value from pixels to units
 * @param {AnalysisModule} cpModule
 * @param {string} measurement
 * @param {number|string} value
 * @returns {number} pixels
 */
function parseMeasurementValue (cpModule, measurement, value) {
  const isUnits = isUnitsMeasurement(measurement);
  const isSquareUnits = isSquareUnitsMeasurement(measurement);
  const physicalSize = cpModule && cpModule.pipeline ? cpModule.pipeline.physicalSize : undefined;
  if (isUnits && physicalSize) {
    DEBUG &&
    console.log('converting', value, 'px to', physicalSize.getPhysicalSize(value), 'units');
    return physicalSize.getPhysicalSize(value);
  }
  if (isSquareUnits && physicalSize) {
    DEBUG &&
    console.log('converting', value, 'px2 to', physicalSize.getSquarePhysicalSize(value), 'units2');
    return physicalSize.getSquarePhysicalSize(value);
  }
  if (!Number.isNaN(Number(value))) {
    return Number(value);
  }
  return value;
}

/**
 * @typedef {Object} FilterObjectsMeasurement
 * @property {string} measurement
 * @property {boolean} use_min_filter
 * @property {number} min_value
 * @property {boolean} use_max_filter
 * @property {number} max_value
 */

/**
 * Parses FilterObjects' module `measurements`
 * @param {AnalysisModule} cpModule
 * @param {FilterObjectsMeasurement[]} measurements
 * @returns {FilterObjectsMeasurement[]}
 */
function parseMeasurements (cpModule, measurements = []) {
  if (!Array.isArray(measurements) && !isObservableArray(measurements)) {
    return [];
  }
  return measurements.map((obj) => {
    const {
      measurement,
      use_min_filter: useMin,
      use_max_filter: useMax,
      min_value: minValue,
      max_value: maxValue
    } = obj;
    return {
      measurement,
      use_max_filter: useMax,
      use_min_filter: useMin,
      min_value: parseMeasurementValue(cpModule, measurement, minValue),
      max_value: parseMeasurementValue(cpModule, measurement, maxValue)
    };
  });
}

/**
 * Converts measurement value from units to pixels
 * @param {AnalysisModule} cpModule
 * @param {string} measurement
 * @param {number|string} value
 * @returns {number} pixels
 */
function buildMeasurementValue (cpModule, measurement, value) {
  const isUnits = isUnitsMeasurement(measurement);
  const isSquareUnits = isSquareUnitsMeasurement(measurement);
  const physicalSize = cpModule && cpModule.pipeline ? cpModule.pipeline.physicalSize : undefined;
  if (isUnits && physicalSize) {
    DEBUG &&
    console.log('converting', value, 'units to', physicalSize.getPixels(value), 'px');
    return physicalSize.getPixels(value);
  }
  if (isSquareUnits && physicalSize) {
    DEBUG &&
    console.log('converting', value, 'units2 to', physicalSize.getSquarePixels(value), 'px2');
    return physicalSize.getSquarePixels(value);
  }
  if (!Number.isNaN(Number(value))) {
    return Number(value);
  }
  return value;
}

/**
 * Builds FilterObjects' module `measurements`
 * @param {AnalysisModule} cpModule
 * @param {FilterObjectsMeasurement[]} measurements
 * @returns {FilterObjectsMeasurement[]}
 */
function buildMeasurements (cpModule, measurements = []) {
  if (!Array.isArray(measurements) && !isObservableArray(measurements)) {
    return [];
  }
  return measurements.map((obj) => {
    const wrapNumber = o => o !== undefined &&
    o !== '' &&
    !Number.isNaN(Number(o))
      ? Number(o)
      : undefined;
    const {
      measurement,
      use_min_filter: useMin,
      use_max_filter: useMax,
      min_value: minValue,
      max_value: maxValue
    } = obj;
    return {
      measurement,
      use_max_filter: useMax,
      use_min_filter: useMin,
      min_value: buildMeasurementValue(cpModule, measurement, wrapNumber(minValue)),
      max_value: buildMeasurementValue(cpModule, measurement, wrapNumber(maxValue))
    };
  });
}

const MeasurementsList = [
  ...Object.values(LocationMeasurements).map(measurement => ({
    name: measurement,
    group: Groups.location
  })),
  ...Object.values(AreaShapeMeasurements).map(measurement => ({
    name: measurement,
    group: Groups.shape
  })),
  ...Object.values(IntensityMeasurements).map(measurement => ({
    name: measurement,
    group: Groups.intensity
  })),
  ...Object.values(NumberMeasurements).map(measurement => ({
    name: measurement,
    group: Groups.number
  }))
];

function MeasurementSelector (props) {
  const {
    className,
    style,
    value,
    onChange
  } = props;
  const groups = [...new Set(MeasurementsList.map(o => o.group))].filter(Boolean);
  return (
    <Select
      showSearch
      className={className}
      style={style}
      value={value}
      onChange={onChange}
      filterOption={
        (input, option) => option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
      }
    >
      {
        groups.map(aGroup => (
          <Select.OptGroup
            key={aGroup}
            label={GroupNames[aGroup] || aGroup}
          >
            {
              MeasurementsList
                .filter(measurement => measurement.group === aGroup)
                .map(measurement => (
                  <Select.Option
                    key={`${aGroup}_${measurement.name}`}
                    value={`${aGroup}_${measurement.name}`}
                    title={measurement.name}
                  >
                    {GroupNames[aGroup] || aGroup}
                    <Icon type="right" />
                    {measurement.name}
                  </Select.Option>
                ))
            }
          </Select.OptGroup>
        ))
      }
    </Select>
  );
}

MeasurementSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func
};

export {
  AreaShapeMeasurements,
  Groups,
  isUnitsMeasurement,
  isSquareUnitsMeasurement,
  isShapeMeasurement,
  isIntensityMeasurement,
  parseMeasurements,
  buildMeasurements,
  parseMeasurement,
  MeasurementsList
};
export default observer(MeasurementSelector);
