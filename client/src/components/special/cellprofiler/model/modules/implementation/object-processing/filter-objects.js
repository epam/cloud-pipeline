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
/* eslint-disable max-len */
import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {Button, Checkbox, Icon, Input} from 'antd';
import {AnalysisTypes} from '../../../common/analysis-types';
import MeasurementSelector, {
  AreaShapeMeasurements,
  buildMeasurements,
  Groups,
  isSquareUnitsMeasurement,
  isUnitsMeasurement,
  parseMeasurements
} from '../../../parameters/measurements';

const DEFAULT_MEASUREMENT = {
  use_min_filter: true,
  use_max_filter: true,
  min_value: 0,
  max_value: 1,
  measurement: undefined
};

@observer
class Measurements extends React.Component {
  /**
   * @returns {ModuleParameter}
   */
  @computed
  get parameter () {
    const {
      parameterValue
    } = this.props;
    if (parameterValue) {
      return parameterValue.parameter;
    }
    return undefined;
  }
  @computed
  get cpModule () {
    return this.parameter ? this.parameter.cpModule : undefined;
  }
  @computed
  get physicalSize () {
    if (
      this.cpModule &&
      this.cpModule.pipeline
    ) {
      return this.cpModule.pipeline.physicalSize;
    }
    return undefined;
  }
  @computed
  get units () {
    if (this.physicalSize) {
      return this.physicalSize.unit;
    }
    return 'px';
  }
  @computed
  get method () {
    if (!this.cpModule) {
      return false;
    }
    return this.cpModule.getParameterValue('method');
  }
  @computed
  get multiple () {
    if (!this.cpModule) {
      return false;
    }
    return this.cpModule.getParameterValue('mode') === 'Measurements' &&
      this.method === 'Limits';
  }
  @computed
  get measurements () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue) {
      return [];
    }
    const value = parameterValue.value || [];
    if (value.length === 0) {
      return [{...DEFAULT_MEASUREMENT}];
    }
    return value;
  }
  setValue (newValue = []) {
    const {
      parameterValue
    } = this.props;
    if (parameterValue) {
      parameterValue.value = [...newValue];
      parameterValue.reportChanged();
    }
  }
  renderMeasurementConfiguration = (measurement, index) => {
    const {
      measurement: value
    } = measurement;
    const isUnits = isUnitsMeasurement(value);
    const isSquareUnits = isSquareUnitsMeasurement(value);
    const onChangeValue = (newValue) => {
      const measurements = this.measurements;
      measurements[index].measurement = newValue;
      this.setValue(measurements);
    };
    const renderMinMaxFilter = (useProperty, valueProperty, title) => {
      const {
        [useProperty]: use,
        [valueProperty]: filter
      } = measurement;
      const onChangeUse = (e) => {
        const measurements = this.measurements;
        measurements[index][useProperty] = e.target.checked;
        this.setValue(measurements);
      };
      const onChangeMinMax = (e) => {
        const measurements = this.measurements;
        measurements[index][valueProperty] = e.target.value;
        this.setValue(measurements);
      };
      return (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            marginTop: 5
          }}
        >
          <div
            style={{width: 100}}
          >
            <Checkbox
              checked={use}
              onChange={onChangeUse}
              style={{marginRight: 5}}
            >
              {title}
            </Checkbox>
          </div>
          {
            use && (
              <Input
                style={{flex: 1}}
                value={filter}
                onChange={onChangeMinMax}
              />
            )
          }
          {
            use && isUnits && (
              <span style={{marginLeft: 5}}>
                {this.units}
              </span>
            )
          }
          {
            use && isSquareUnits && (
              <span style={{marginLeft: 5}}>
                {this.units}
                <sup>{'2'}</sup>
              </span>
            )
          }
        </div>
      );
    };
    const onRemove = () => {
      const measurements = this.measurements;
      measurements.splice(index, 1);
      this.setValue(measurements);
    };
    return (
      <div
        key={`measurement-${index}`}
        className="cp-even-odd-element"
        style={{padding: 5}}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center'
          }}
        >
          <MeasurementSelector
            value={value}
            style={{flex: 1}}
            onChange={onChangeValue}
          />
          {
            this.multiple && this.measurements.length > 1 && (
              <Button
                style={{marginLeft: 5}}
                onClick={onRemove}
                size="small"
                type="danger"
              >
                <Icon type="delete" />
              </Button>
            )
          }
        </div>
        {
          this.method === 'Limits' && (
            <div>
              {
                renderMinMaxFilter(
                  'use_min_filter',
                  'min_value',
                  'Minimum'
                )
              }
              {
                renderMinMaxFilter(
                  'use_max_filter',
                  'max_value',
                  'Maximum'
                )
              }
            </div>
          )
        }
      </div>
    );
  };
  onAddMeasurement = () => {
    this.setValue([...this.measurements, {...DEFAULT_MEASUREMENT}]);
  };
  render () {
    const {
      className,
      style
    } = this.props;
    const measurements = this.measurements;
    return (
      <div
        className={className}
        style={style}
      >
        <div>
          Measurements:
        </div>
        <div>
          {
            measurements.map(this.renderMeasurementConfiguration)
          }
        </div>
        {
          this.multiple && (
            <div
              style={{marginTop: 5}}
            >
              <Button
                onClick={this.onAddMeasurement}
                size="small"
              >
                <Icon type="plus" />
                Add measurement
              </Button>
            </div>
          )
        }
      </div>
    );
  }
}

Measurements.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object
};

const methods = [
  AreaShapeMeasurements.area,
  AreaShapeMeasurements.boundingBoxArea,
  AreaShapeMeasurements.convexArea,
  AreaShapeMeasurements.maximumRadius,
  AreaShapeMeasurements.medianRadius,
  AreaShapeMeasurements.meanRadius,
  AreaShapeMeasurements.maxFeretDiameter,
  AreaShapeMeasurements.minFeretDiameter,
  AreaShapeMeasurements.perimeter,
  AreaShapeMeasurements.majorAxisLength,
  AreaShapeMeasurements.minorAxisLength
];

const areaMethods = [
  AreaShapeMeasurements.area,
  AreaShapeMeasurements.boundingBoxArea,
  AreaShapeMeasurements.convexArea
];
const areaIf = `IF (filterObjects==true AND (${areaMethods.map(method => `filterObjectsMethod=="${method}"`).join(' OR ')}))`;
const linearIf = `IF (filterObjects==true AND (${areaMethods.map(method => `filterObjectsMethod!=="${method}"`).join(' AND ')}))`;

const filterObjectsBySizeParameters = [
  'Filter objects|flag|false|ALIAS filterObjects',
  `Filter objects by property|[${methods.join(',')}]|${AreaShapeMeasurements.maximumRadius}|IF filterObjects==true|ALIAS filterObjectsMethod`,
  `Filter objects, property min|units|0|ALIAS filterMinimumValue|${linearIf}`,
  `Filter objects, property max|units|20|ALIAS filterMaximumValue|${linearIf}`,
  `Filter objects, property min|units2|0|ALIAS filterMinimumValue2|${areaIf}`,
  `Filter objects, property max|units2|20|ALIAS filterMaximumValue2|${areaIf}`
];

/**
 *
 * @param {AnalysisModule} baseModule
 * @param {Object} moduleConfiguration
 * @param {string} [outputProperty=output]
 */
function wrapLastModuleWithFilterObjectsModule (baseModule, moduleConfiguration, outputProperty = 'output') {
  const filterObjects = baseModule ? baseModule.getBooleanParameterValue('filterObjects') : false;
  if (!baseModule || !filterObjects || !moduleConfiguration) {
    return [moduleConfiguration].filter(Boolean);
  }
  const newOutputPropertyValue = '{this.id}_before_filter|COMPUTED';
  const realOutput = (moduleConfiguration.values || {})[outputProperty];
  const moduleAlias = moduleConfiguration.alias || moduleConfiguration.module;
  const correctedModule = {
    ...moduleConfiguration,
    values: {
      ...(moduleConfiguration.values || {}),
      [outputProperty]: newOutputPropertyValue
    }
  };
  const method = baseModule.getParameterValue('filterObjectsMethod');
  let min, max;
  if (areaMethods.includes(method)) {
    min = baseModule.getParameterValue('filterMinimumValue2');
    max = baseModule.getParameterValue('filterMaximumValue2');
  } else {
    min = baseModule.getParameterValue('filterMinimumValue');
    max = baseModule.getParameterValue('filterMaximumValue');
  }
  return [
    correctedModule,
    {
      module: 'FilterObjectsBySize',
      alias: 'filterObjects',
      values: {
        input: `{${moduleAlias}.${outputProperty}}|COMPUTED`,
        output: realOutput,
        measurement: `${Groups.shape}_${method}`,
        minimum: min,
        maximum: max
      }
    }
  ];
}

const FilterObjectsBySize = {
  name: 'FilterObjectsBySize',
  composed: true,
  hidden: true,
  predefined: true,
  output: 'output|object',
  parameters: [
    'Select the objects to filter|object|ALIAS input|REQUIRED',
    'Name the output object|string|FilteredObject|ALIAS output|REQUIRED',
    'Measurement|string|ALIAS measurement',
    'Minimum diameter|units|ALIAS minimum',
    'Maximum diameter|units|ALIAS maximum'
  ],
  subModules: [
    {
      module: 'FilterObjects',
      alias: 'filterObjects',
      values: {
        input: '{parent.input}|COMPUTED',
        output: '{parent.output}|COMPUTED',
        mode: 'Measurements',
        method: 'Limits',
        keep: false,
        measurements: (cpModule, modules) => {
          const {
            parent
          } = modules || {};
          if (!parent) {
            return [];
          }
          const measurement = parent.getParameterValue('measurement');
          const min = parent.getParameterValue('minimum');
          const max = parent.getParameterValue('maximum');
          const isValid = o => o !== undefined &&
            o !== '' &&
            !Number.isNaN(Number(o)) &&
            `${o}`.trim().length > 0;
          const useMin = isValid(min);
          const useMax = isValid(max);
          return [
            {
              measurement,
              use_min_filter: useMin,
              use_max_filter: useMax,
              min_value: isValid(min) ? Number(min) : 0,
              max_value: isValid(max) ? Number(max) : 0
            }
          ];
        }
      }
    }
  ]
};

export {
  FilterObjectsBySize,
  filterObjectsBySizeParameters,
  wrapLastModuleWithFilterObjectsModule
};
export default {
  name: 'FilterObjects',
  group: 'Object Processing',
  output: 'output|object',
  parameters: [
    'Select the objects to filter|object|ALIAS input|REQUIRED',
    'Name the output objects|string|FilteredObjects|ALIAS output|REQUIRED',
    'Select the filtering mode|[Measurements,Image or mask border]|Measurements|ALIAS mode|REQUIRED',
    'Select the filtering method|[Minimal,Maximal,Minimal per object,Maximal per object,Limits]|Limits|IF mode==Measurements|ALIAS method|REQUIRED',

    'Assign overlapping child to|[Both parents, Parent with most overlap]|Both parents|IF (mode=="Measurements") AND (method=="Minimal per object" OR method=="Maximal per object")|ALIAS assignChildTo',
    {
      name: 'Measurements',
      parameterName: 'measurements',
      type: AnalysisTypes.custom,
      showTitle: false,
      visibilityHandler: (cpModule) => cpModule.getParameterValue('mode') === 'Measurements',
      valueParser: (value, cpModule) => parseMeasurements(cpModule, value),
      valueFormatter: (value, cpModule) => buildMeasurements(cpModule, value),
      renderer: (moduleParameterValue, className, style) => (
        <Measurements
          parameterValue={moduleParameterValue}
          className={className}
          style={style}
        />
      )
    },
    'Select the objects that contain the filtered objects|object|ALIAS parentObject|IF (mode==Measurements) AND (method=="Minimal per object" OR method=="Maximal per object")',

    'Keep removed objects as a separate set|flag|false|PARAMETER "Keep removed objects as a seperate set?"|ADVANCED|ALIAS keep',
    'Name the objects removed by the filter|string|RemovedObjects|IF keep==true|ALIAS removed|ADVANCED'
  ]
};
