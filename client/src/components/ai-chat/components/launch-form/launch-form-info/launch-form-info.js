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
import styles from './launch-form-info.css';
import {observer} from 'mobx-react';

@observer
export default class LaunchFormInfo extends React.Component {
  render () {
    const {formStore} = this.props;

    const {toolInfo, toolVersion} = formStore;

    return (
      <div className={styles.container}>
        <div className={styles.launchFormInfo}>
          {toolInfo.image && (
            <div className={styles.infoRow}>
              <span className={styles.label}>Tool:</span>
              <span className={styles.value}>{toolInfo.image}</span>
            </div>
          )}
          {toolVersion.version && (
            <div className={styles.infoRow}>
              <span className={styles.label}>Version:</span>
              <span className={styles.value}>{toolVersion.version}</span>
            </div>
          )}
        </div>
        {toolInfo?.description &&
          <p>
            {toolInfo?.shortDescription}
          </p>
        }
      </div>
    );
  }
}

LaunchFormInfo.propTypes = {
  formStore: PropTypes.shape({
    toolInfo: PropTypes.shape({
      description: PropTypes.string
    }),
    toolVersion: PropTypes.shape({
      version: PropTypes.string
    }),
    configuration: PropTypes.shape({
      cmd_template: PropTypes.string
    })
  }).isRequired
};
