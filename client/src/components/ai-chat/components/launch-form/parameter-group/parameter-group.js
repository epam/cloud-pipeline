import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {TitleSection} from '../index';
import LaunchFormParameterInput
from '../../../../pipelines/launch/form/parameters/parameter/inputs';
import styles from './parameter-group.css';

@observer
export default class ParameterGroup extends React.Component {
  renderParameters = () => {
    const {formStore} = this.props;
    const {parameters} = formStore;

    if (!parameters || Object.keys(parameters).length === 0) {
      return <div>No parameters found.</div>;
    }

    return Object.entries(parameters).map(([parameterName, parameter]) => {
      const isBoolean = typeof parameter.value === 'boolean';

      const parameterClass = isBoolean
        ? styles.checkboxField
        : styles.parameterField;

      return (
        <div key={parameterName} className={parameterClass}>
          <span>{parameterName}</span>
          <LaunchFormParameterInput
            parameter={parameter}
            onChange={(updatedParameter) => {
              formStore.updateParameter(parameterName, updatedParameter.value);
            }}
            style={{flex: 1}}
          />
        </div>
      );
    });
  };

  render () {
    return (
      <div>
        <TitleSection title="Parameters" />
        <div className={styles.indent}>
          {this.renderParameters()}
        </div>
      </div>
    );
  }
}

ParameterGroup.propTypes = {
  formStore: PropTypes.object
};
