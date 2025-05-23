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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {TitleSection, InputField, SelectField} from '../index';
import styles from './environment.css';

const PRICE_TYPES = {
  spot: 'Spot',
  onDemand: 'On demand'
};

@inject('allowedInstanceTypes')
@observer
export default class Environment extends React.Component {
  @computed
  get allowedInstanceTypes () {
    if (this.props.allowedInstanceTypes.loaded) {
      return this.props.allowedInstanceTypes.value;
    }
    return [];
  }

  handleChange = (field) => (value) => {
    const {formStore} = this.props;
    formStore.updateField(field, value);
  };

  onChangePriceType = (value) => {
    this.handleChange('isSpot')(value === PRICE_TYPES.spot);
  };

  render () {
    console.log('allowedInstanceTypes', this.allowedInstanceTypes)
    const {formStore} = this.props;
    const {dockerImage, instanceType, disk, isSpot} = formStore;
    const priceOptions = [
      {value: PRICE_TYPES.spot, label: PRICE_TYPES.spot},
      {value: PRICE_TYPES.onDemand, label: PRICE_TYPES.onDemand}
    ];
    const priceOption = isSpot ? PRICE_TYPES.spot : PRICE_TYPES.onDemand;
    return (
      <div className={styles.stripe}>
        <TitleSection title="Environment" />
        <InputField
          label="Docker"
          value={dockerImage}
          onChange={this.handleChange('dockerImage')}
          disabled={false}
        />
        <div className={styles.selectContainer}>
          {instanceType && (
            <SelectField
              label="Instance"
              value={instanceType}
              options={[]}
              onChange={this.handleChange('instanceType')}
              placeholder="Choose instance type"
            />
          )}
          {disk && (
            <InputField
              label="Disk Size"
              value={disk}
              onChange={this.handleChange('disk')}
              disabled={false}
            />
          )}
          <SelectField
            label="Price Type"
            value={priceOption}
            options={priceOptions}
            onChange={this.onChangePriceType}
            placeholder="Choose price type"
          />
        </div>
      </div>
    );
  }
}

Environment.propTypes = {
  formStore: PropTypes.object
};
