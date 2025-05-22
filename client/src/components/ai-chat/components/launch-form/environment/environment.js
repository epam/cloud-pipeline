import React from 'react';
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {TitleSection, InputField, SelectField} from '../index';
import styles from './environment.css';

@observer
export default class Environment extends React.Component {
  handleChange = (field, value) => {
    const {formStore} = this.props;

    if (field === 'is_spot') {
      formStore.updateField('is_spot', value === 'spot'); // true для spot, false для On Demand
    } else {
      formStore.updateField(field, value);
    }
  };

  render () {
    const {formStore, data} = this.props;

    const {dockerImage, instanceType, disk, is_spot} = formStore;

    const priceOptions = [
      {value: 'spot', label: 'Spot'},
      {value: 'On Demand', label: 'On Demand'}
    ];

    const selectedOption = is_spot ? 'spot' : 'On Demand';

    const instanceOptions = data.optionsSelect || [];

    return (
      <div className={styles.stripe}>
        <TitleSection title="Environment" />
        <InputField
          label="Docker"
          value={dockerImage}
          onChange={(value) => this.handleChange('dockerImage', value)}
          disabled={false}
        />
        <div className={styles.selectContainer}>
          {instanceType && (
            <SelectField
              label="Instance"
              value={instanceType}
              options={instanceOptions}
              onChange={(value) => this.handleChange('instanceType', value)}
              placeholder="Choose instance type"
            />
          )}
          {disk && (
            <InputField
              label="Disk Size"
              value={disk}
              onChange={(value) => this.handleChange('disk', value)}
              disabled={false}
            />
          )}
          <SelectField
            label="Price Type"
            value={selectedOption}
            options={priceOptions}
            onChange={(value) => this.handleChange('is_spot', value)}
            placeholder="Choose price type"
          />
        </div>
      </div>
    );
  }
}

Environment.propTypes = {
  formStore: PropTypes.object.isRequired,
  data: PropTypes.shape({
    dockerImage: PropTypes.string.isRequired,
    optionsSelect: PropTypes.arrayOf(
      PropTypes.shape({
        value: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired
      })
    ).isRequired
  }).isRequired
};
