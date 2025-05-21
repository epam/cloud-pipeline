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

export default class LaunchForm extends React.Component {
  render () {
    const {mockData} = this.props;

    return (
      <Form className={classNames(styles.launchForm, 'cp-panel')}>
        <LaunchFormInfo
          name={mockData.configurationName}
          version={mockData.version}
          description={mockData.description}
        />
        <Environment mockData={mockData} />
        <ParameterGroup mockData={mockData} />
        <div className={styles.controlBtn}>
          <Button type="primary" onClick={() => {
          }}>SUBMIT</Button>
        </div>
      </Form>
    );
  }
}

LaunchForm.propTypes = {
  mockData: PropTypes.shape({
    toolId: PropTypes.number,
    pipelineId: PropTypes.number,
    configurationName: PropTypes.string,
    version: PropTypes.string,
    image: PropTypes.string,
    registry: PropTypes.string,
    dockerImage: PropTypes.string,
    instanceType: PropTypes.string,
    disk: PropTypes.number,
    is_spot: PropTypes.bool,
    description: PropTypes.string,
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string,
        value: PropTypes.any
      })
    )
  })
};
