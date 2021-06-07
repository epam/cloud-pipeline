import React from 'react';
import PropTypes from 'prop-types';
import {Select} from 'antd';
import {booleanParameterIsSetToValue} from './parameter-utilities';
import {
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_SINGULARITY,
  CP_CAP_DESKTOP_NM,
  CP_CAP_MODULES, CP_DISABLE_HYPER_THREADING
} from './parameters';

export const RUN_CAPABILITIES = {
  dinD: 'DinD',
  singularity: 'Singularity',
  systemD: 'SystemD',
  noMachine: 'NoMachine',
  module: 'Module',
  disableHyperThreading: 'Disable Hyper-Threading'
};

export default class RunCapabilities extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    values: PropTypes.arrayOf(PropTypes.string),
    onChange: PropTypes.func
  };

  static defaultProps = {
    values: null,
    onChange: null
  };

  onSelectionChanged = (values) => {
    const {onChange} = this.props;

    onChange && onChange(values || []);
  };

  render () {
    const {disabled, values} = this.props;

    return (
      <Select
        allowClear
        disabled={disabled}
        mode="multiple"
        onChange={this.onSelectionChanged}
        placeholder="None selected"
        size="large"
        value={values || []}
        filterOption={
          (input, option) => option.props.children.toLowerCase().includes(input.toLowerCase())
        }
      >
        {Object.values(RUN_CAPABILITIES).map(o => (<Select.Option key={o}>{o}</Select.Option>))}
      </Select>
    );
  }
}

export function getRunCapabilitiesSkippedParameters () {
  return [
    CP_CAP_DIND_CONTAINER,
    CP_CAP_SINGULARITY,
    CP_CAP_SYSTEMD_CONTAINER,
    CP_CAP_DESKTOP_NM,
    CP_CAP_MODULES
  ];
}

export function dinDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DIND_CONTAINER);
}

export function singularityEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SINGULARITY);
}

export function systemDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SYSTEMD_CONTAINER);
}

export function noMachineEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DESKTOP_NM);
}

export function moduleEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_MODULES);
}

export function disableHyperThreadingEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_DISABLE_HYPER_THREADING);
}
