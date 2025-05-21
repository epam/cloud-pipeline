import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {TitleSection} from '../index';
import LaunchFormParameterInput
from '../../../../pipelines/launch/form/parameters/parameter/inputs';
import styles from './parameter-group.css';
import LaunchFormStore from '../launch-form-store';

@observer
export default class ParameterGroup extends React.Component {
  constructor (props) {
    super(props);
    this.formStore = new LaunchFormStore();
  }

  componentDidMount () {
    const {mockData} = this.props;
    if (mockData && mockData.parameters) {
      this.formStore.initializeParameters(mockData.parameters);
    }
  }

  renderParameters = () => {
    const {parameters} = this.formStore;

    if (!parameters || Object.keys(parameters).length === 0) {
      return <div>No parameters found.</div>;
    }

    return Object.entries(parameters).map(([key, parameter]) => (
      <LaunchFormParameterInput
        key={key}
        parameter={parameter}
        onChange={(updatedParameter) => {
          this.formStore.updateParameter(key, updatedParameter.value);
        }}
        className="form-parameter-input"
        label={`Parameter: ${key}`}
        disabled={false}
      />
    ));
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
  mockData: PropTypes.shape({
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string.isRequired,
        value: PropTypes.any.isRequired
      })
    ).isRequired
  }).isRequired
};
