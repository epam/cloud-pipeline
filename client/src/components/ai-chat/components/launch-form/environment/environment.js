import React from 'react';
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {TitleSection, InputDocker, SelectField} from '../index';
import styles from './environment.css';
import LaunchFormStore from '../launch-form-store';

@observer
export default class Environment extends React.Component {
  constructor (props) {
    super(props);
    this.formStore = new LaunchFormStore();
  }

  componentDidMount () {
    const {mockData} = this.props;
    this.formStore.updateField('inputDocker', mockData.registry);
  }

  handleChange = (field, value) => {
    const actionMap = {
      selectedInstance: 'selectedInstance',
      selectedDisk: 'selectedDisk',
      selectedPriceType: 'selectedPriceType',
    };

    const key = actionMap[field];

    if (key) {
      this.formStore.updateField(key, value);
    } else {
      console.error(`Unknown field '${field}'`);
    }
  };

  render () {
    const {mockData} = this.props;
    const {
      selectedInstance,
      selectedDisk,
      selectedPriceType,
      inputDocker
    } = this.formStore;

    if (!mockData.optionsSelect || mockData.optionsSelect.length === 0) {
      return (
        <div className={styles.stripe}>
          <TitleSection title="Environment" />
          <div className={styles.errorMessage}>
            Options for Instance, Disk, and Price Type are missing.
          </div>
        </div>
      );
    }

    return (
      <div className={styles.stripe}>
        <TitleSection title="Environment" />
        <InputDocker
          label="Docker"
          value={inputDocker}
          onChange={(value) => this.formStore.updateField('inputDocker', value)}
          disabled={false}
        />
        <div className={styles.selectContainer}>
          <SelectField
            label="Instance"
            value={selectedInstance}
            options={mockData.optionsSelect}
            onChange={(value) => this.handleChange('selectedInstance', value)}
            placeholder="Choose"
          />
          <SelectField
            label="Disk, GB"
            value={selectedDisk}
            options={mockData.optionsSelect}
            onChange={(value) => this.handleChange('selectedDisk', value)}
            placeholder="Choose"
          />
          <SelectField
            label="Price type"
            value={selectedPriceType}
            options={mockData.optionsSelect}
            onChange={(value) => this.handleChange('selectedPriceType', value)}
            placeholder="Choose"
          />
        </div>
      </div>
    );
  }
}

Environment.propTypes = {
  mockData: PropTypes.shape({
    registry: PropTypes.string.isRequired,
    optionsSelect: PropTypes.arrayOf(
      PropTypes.shape({
        value: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired
      })
    ).isRequired
  }).isRequired
};
