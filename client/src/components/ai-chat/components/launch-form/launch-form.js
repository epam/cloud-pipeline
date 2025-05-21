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
import classNames from 'classnames';
import {Button, Form} from 'antd';
import {Environment, LaunchFormInfo, ParameterGroup} from './index';
import styles from './launch-form.css';
import {observer} from 'mobx-react';
import LaunchFormStore from './launch-form-store';

@observer
export default class LaunchForm extends React.Component {
  componentDidMount () {
    this.formStore = new LaunchFormStore();
    const {mockData} = this.props;
    this.formStore.initializeParameters(mockData.parameters);
  }

  render () {
    const {mockData} = this.props;

    return (
      <div className={classNames(styles.launchForm, 'cp-panel')}>
        <LaunchFormInfo
          name={mockData.configurationName}
          version={mockData.version}
          description={mockData.description}
        />
        <Environment mockData={mockData} />
        <ParameterGroup mockData={mockData} />
        <div className={styles.controlBtn}>
          <Button
            type="primary"
            onClick={() => {
              console.log('LaunchForm]', this.formStore.parameters);
            }}
          >
            SUBMIT
          </Button>
        </div>
      </div>
    );
  }
}

LaunchForm.propTypes = {
  mockData: PropTypes.shape({
    toolId: PropTypes.number.isRequired,
    pipelineId: PropTypes.number.isRequired,
    configurationName: PropTypes.string.isRequired,
    version: PropTypes.string.isRequired,
    image: PropTypes.string.isRequired,
    registry: PropTypes.string.isRequired,
    dockerImage: PropTypes.string.isRequired,
    instanceType: PropTypes.string.isRequired,
    disk: PropTypes.number.isRequired,
    is_spot: PropTypes.bool.isRequired,
    description: PropTypes.string.isRequired,
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string.isRequired,
        value: PropTypes.any.isRequired
      })
    ).isRequired,
    optionsSelect: PropTypes.array.isRequired
  }).isRequired
};
