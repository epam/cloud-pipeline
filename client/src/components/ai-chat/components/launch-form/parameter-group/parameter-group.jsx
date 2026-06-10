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
import styles from './parameter-group.module.css';
import {computed, makeObservable} from 'mobx';

@observer
export default class ParameterGroup extends React.Component {
  constructor(props) {
    super(props);
    makeObservable(this, {
      noParameters: computed,
    });
  }

  get noParameters() {
    const {formStore} = this.props;
    const {parameters} = formStore;
    return !parameters || Object.keys(parameters).length === 0;
  }

  renderParameters = () => {
    const {formStore} = this.props;
    const {parameters} = formStore;
    if (!parameters || Object.keys(parameters).length === 0) {
      return <div>No parameters found.</div>;
    }
    return (
      <table>
        <tbody>
          {Object.entries(parameters).map(([parameterName, parameter]) => {
            return (
              <tr key={parameterName}>
                <td style={{textAlign: 'end', paddingRight: 5}}>
                  <b>{parameterName}:</b>
                </td>
                <td>
                  <span>{`${parameter.value}`}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

  render() {
    if (this.noParameters) {
      return null;
    }
    return (
      <div>
        <TitleSection title="Parameters" />
        <div className={styles.indent}>{this.renderParameters()}</div>
      </div>
    );
  }
}

ParameterGroup.propTypes = {
  formStore: PropTypes.object,
};
