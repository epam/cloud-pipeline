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
import classNames from 'classnames';
import {isObservableArray} from 'mobx';
import {observer} from 'mobx-react';
import {
  Checkbox,
  Input,
  Select,
  Slider
} from 'antd';
import {AnalysisTypes} from '../model/common/analysis-types';
import styles from './cell-profiler.css';
import ColorPicker from '../../color-picker';

class CellProfilerParameter extends React.Component {
  reportChanged = () => {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue) {
      return;
    }
    parameterValue.reportChanged();
  };
  renderStringControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue) {
      return null;
    }
    const onChange = (e) => {
      parameterValue.value = e.target.value;
      this.reportChanged();
    };
    const correctedValue = parameterValue.value &&
      (isObservableArray(parameterValue.value) || Array.isArray(parameterValue.valaue))
      ? parameterValue.value.join(', ')
      : parameterValue.value;
    return (
      <Input
        className={
          classNames(
            styles.control,
            {
              'cp-error': parameterValue.isInvalid
            }
          )
        }
        value={correctedValue}
        onChange={onChange}
      />
    );
  }
  renderSliderControl () {
    const {
      parameterValue
    } = this.props;
    if (
      !parameterValue ||
      !parameterValue.parameter ||
      !parameterValue.parameter.range
    ) {
      return null;
    }
    const {min, max} = parameterValue.parameter.range;
    const onChange = (value) => {
      parameterValue.value = value;
      this.reportChanged();
    };
    const step = 10 ** (Math.log10((max - min) / 100));
    return (
      <Slider
        className={styles.control}
        value={parameterValue.value}
        min={min}
        max={max}
        step={step}
        onChange={onChange}
      />
    );
  }

  renderBooleanControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue) {
      return null;
    }
    const onChange = (e) => {
      parameterValue.value = e.target.checked;
      this.reportChanged();
    };
    return (
      <Checkbox
        className={styles.control}
        checked={!!parameterValue.value}
        onChange={onChange}
      />
    );
  }

  renderListControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue || !parameterValue.parameter) {
      return null;
    }
    const values = parameterValue.parameter.values;
    const multiple = !!(parameterValue.parameter.multiple);
    const onChange = (changedValue) => {
      if (multiple) {
        parameterValue.value = values
          .map(o => o.id)
          .filter(value => (changedValue || []).includes(value));
      } else {
        const valueId = changedValue;
        const newValue = values.find(value => value.id === valueId);
        parameterValue.value = newValue ? newValue.value : undefined;
      }
      this.reportChanged();
    };
    let selected;
    if (multiple) {
      selected = values.filter(value =>
        parameterValue.value &&
        typeof parameterValue.value.includes === 'function' &&
        (parameterValue.value || []).includes(value.value)
      ).map(o => o.id);
    } else {
      const selectedValue = values.find(value => value.value === parameterValue.value);
      selected = selectedValue ? selectedValue.id : undefined;
    }
    return (
      <Select
        allowClear
        mode={multiple ? 'multiple' : 'default'}
        className={
          classNames(
            styles.control,
            {
              'cp-error': parameterValue.isInvalid
            }
          )
        }
        value={selected}
        onChange={onChange}
      >
        {
          values.map((value) => (
            <Select.Option key={value.id} value={value.id}>
              {value.title || value.id}
            </Select.Option>
          ))
        }
      </Select>
    );
  }

  renderColorControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue) {
      return null;
    }
    const onChange = (color) => {
      parameterValue.value = color;
      this.reportChanged();
    };
    return (
      <ColorPicker
        className={styles.control}
        color={parameterValue.value}
        onChange={onChange}
        hex
        ignoreAlpha
      />
    );
  }

  renderRangeControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue || !parameterValue.parameter) {
      return null;
    }
    const [from, to] = parameterValue.value && parameterValue.value.length
      ? parameterValue.value
      : [];
    const onChangeFrom = (e) => {
      parameterValue.value = [e.target.value, to];
      this.reportChanged();
    };
    const onChangeTo = (e) => {
      parameterValue.value = [from, e.target.value];
      this.reportChanged();
    };
    return (
      <div
        className={styles.control}
        style={{display: 'flex'}}
      >
        <span style={{whiteSpace: 'pre'}}>from </span>
        <Input
          style={{flex: 1}}
          value={from}
          onChange={onChangeFrom}
        />
        <span style={{whiteSpace: 'pre'}}> to </span>
        <Input
          style={{flex: 1}}
          value={to}
          onChange={onChangeTo}
        />
      </div>
    );
  }

  renderValueControl () {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue || !parameterValue.parameter) {
      return null;
    }
    if (parameterValue.parameter.isList) {
      return this.renderListControl();
    }
    if (parameterValue.parameter.isRange) {
      return this.renderRangeControl();
    }
    const type = parameterValue.parameter.type;
    const renderer = parameterValue.parameter.renderer;
    switch (type) {
      case AnalysisTypes.boolean:
        return this.renderBooleanControl();
      case AnalysisTypes.string:
        return this.renderStringControl();
      case AnalysisTypes.integer:
      case AnalysisTypes.float:
      case AnalysisTypes.units:
      case AnalysisTypes.units2: {
        if (
          parameterValue.parameter.range &&
          parameterValue.parameter.range.min !== undefined &&
          parameterValue.parameter.range.max !== undefined &&
          Number.isFinite(Number(parameterValue.parameter.range.min)) &&
          Number.isFinite(Number(parameterValue.parameter.range.max))
        ) {
          return this.renderSliderControl();
        }
        return this.renderStringControl();
      }
      case AnalysisTypes.color:
        return this.renderColorControl();
      case AnalysisTypes.custom:
      default: {
        if (typeof renderer === 'function') {
          return renderer(parameterValue, styles.control);
        }
        return this.renderStringControl();
      }
    }
  }

  renderUnits = () => {
    const {
      parameterValue
    } = this.props;
    if (!parameterValue || !parameterValue.parameter) {
      return null;
    }
    if (
      [AnalysisTypes.units, AnalysisTypes.units2].includes(parameterValue.parameter.type) &&
      parameterValue.parameter.cpModule &&
      parameterValue.parameter.cpModule.pipeline &&
      parameterValue.parameter.cpModule.pipeline.physicalSize
    ) {
      return (
        <span className={styles.units}>
          {parameterValue.parameter.cpModule.pipeline.physicalSize.unit}
          {
            parameterValue.parameter.type === AnalysisTypes.units2 && (
              <sup>2</sup>
            )
          }
        </span>
      );
    }
    return null;
  };

  render () {
    const {
      parameterValue,
      className,
      style
    } = this.props;
    if (!parameterValue || !parameterValue.parameter) {
      return null;
    }
    const {showTitle} = parameterValue.parameter;
    return (
      <div
        className={
          classNames(
            styles.cellProfilerParameter,
            className
          )
        }
        style={style}
      >
        <div
          className={
            classNames(
              styles.cellProfilerParameterTitle,
              {
                [styles.hidden]: !showTitle
              }
            )
          }
        >
          {parameterValue.parameter.title}
          {this.renderUnits()}
        </div>
        <div
          className={styles.cellProfilerParameterValue}
        >
          {
            this.renderValueControl()
          }
        </div>
      </div>
    );
  }
}

CellProfilerParameter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameterValue: PropTypes.object
};

export default observer(CellProfilerParameter);
