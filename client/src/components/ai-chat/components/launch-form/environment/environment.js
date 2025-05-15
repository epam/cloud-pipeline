/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {TitleSection, InputDocker, SelectField} from '../index';
import styles from './environment.css';

export default class Environment extends React.Component {
  state = {
    selectedInstance: 'm5.xlarge(CPU 4, RAM 16)',
    selectedDisk: '250',
    selectedPriceType: 'on-demand'
  };

  handleChange = (field, value) => {
    this.setState({[field]: value});
  };

  render () {
    const {mockData} = this.props;
    const {selectedInstance, selectedDisk, selectedPriceType} = this.state;

    return (
      <div className={styles.stripe}>
        <TitleSection title="Environment" />
        <InputDocker
          label="Docker"
          value={mockData.registry}
          onChange={() => {
          }}
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
