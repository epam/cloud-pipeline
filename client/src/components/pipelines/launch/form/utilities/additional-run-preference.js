import React from 'react';
import PropTypes from 'prop-types';
import {Checkbox, Row} from 'antd';
import {booleanParameterIsSetToValue} from './parameter-utilities';

export default class AdditionalRunPreference extends React.Component {
  static propTypes = {
    readOnly: PropTypes.bool,
    preference: PropTypes.string.isRequired,
    value: PropTypes.bool,
    onChange: PropTypes.func
  };

  static defaultProps = {
    readOnly: false,
    value: false,
    onChange: null
  };

  onChange = (e) => {
    const {onChange} = this.props;

    onChange && e && onChange(e.target.checked);
  };

  render () {
    const {preference, readOnly, value} = this.props;

    return (
      <Row type="flex" style={{flex: 1}}>
        <Checkbox
          disabled={readOnly}
          onChange={this.onChange}
          checked={value}
        >
          {preference}
        </Checkbox>
      </Row>
    );
  }
}

export const CP_CAP_DIND_CONTAINER = 'CP_CAP_DIND_CONTAINER';
export function dinDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DIND_CONTAINER);
}

export const CP_CAP_SINGULARITY = 'CP_CAP_SINGULARITY';
export function singularityEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SINGULARITY);
}

export const CP_CAP_SYSTEMD_CONTAINER = 'CP_CAP_SYSTEMD_CONTAINER';
export function systemDEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_SYSTEMD_CONTAINER);
}

export const CP_CAP_DESKTOP_NM = 'CP_CAP_DESKTOP_NM';
export function noMachineEnabled (parameters) {
  return booleanParameterIsSetToValue(parameters, CP_CAP_DESKTOP_NM);
}
