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

    return Object.entries(parameters).map(([key, parameter]) => (
      <LaunchFormParameterInput
        key={key}
        parameter={parameter}
        onChange={(updatedParameter) => {
          formStore.updateParameter(key, updatedParameter.value);
        }}
        label={`Parameter: ${key}`}
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
  formStore: PropTypes.object.isRequired
};
