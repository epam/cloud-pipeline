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
import {SwitcherParameters, TitleSection} from '../index';

export default class ParameterGroup extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      parameters: props.mockData.parameters
    };
  }

  renderParameters = () => {
    const {parameters} = this.state;

    return Object.entries(parameters).map(([key, {type, value}]) => (
      <SwitcherParameters
        key={key}
        type={type}
        value={typeof value === 'boolean' ? String(value) : value}
        label={key}
        onChange={(newValue) =>
          this.setState((prevState) => ({
            parameters: {
              ...prevState.parameters,
              [key]: {
                ...prevState.parameters[key],
                value: newValue
              }
            }
          }))
        }
        disabled={false}
      />
    ));
  };

  render () {
    return (
      <div>
        <TitleSection title="Parameters" />
        {this.renderParameters()}
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
