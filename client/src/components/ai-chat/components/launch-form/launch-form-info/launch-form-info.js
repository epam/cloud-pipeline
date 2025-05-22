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

export default class LaunchFormInfo extends React.Component {
  render () {
    const {name, version, description} = this.props;

    return (
      <div className={styles.container}>
        <div className={styles.launchFormInfo}>
          <div className={styles.infoRow}>
            <span className={styles.label}>Pipeline:</span>
            <span className={styles.value}>{name}</span>
          </div>
          <div className={styles.infoRow}>
            <span className={styles.label}>Version:</span>
            <span className={styles.value}>{version}</span>
          </div>
        </div>
        {description &&
          <p>
            {description}
          </p>
        }
      </div>
    );
  }
}

LaunchFormInfo.propTypes = {
  name: PropTypes.string.isRequired,
  version: PropTypes.string.isRequired,
  description: PropTypes.string
};
