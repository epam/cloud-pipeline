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
import {Link} from 'react-router-dom';
import {LAUNCH_MODES} from '../utils';

@observer
export default class LaunchFormInfo extends React.Component {
  render () {
    const {formStore} = this.props;
    const {toolInfo, toolVersion, mode, pipeline, pipelineVersion} = formStore;
    const name = mode === LAUNCH_MODES.tool
      ? toolInfo?.image
      : pipeline.name;
    const version = mode === LAUNCH_MODES.tool
      ? toolVersion?.version
      : pipelineVersion.name;
    const url = mode === LAUNCH_MODES.tool
      ? `/tool/${toolInfo?.id}/description`
      : `/${pipeline.id}/${version}`;
    const description = LAUNCH_MODES.tool
      ? toolInfo?.shortDescription || toolInfo?.description
      : '';
    return (
      <div className={styles.container}>
        <div className={styles.launchFormInfo}>
          <div className={styles.infoRow}>
            <span className={styles.label}>
              {mode === LAUNCH_MODES.tool ? 'Tool:' : 'Pipeline:'}
            </span>
            <Link to={url}>
              {name}
            </Link>
          </div>
          <div className={styles.infoRow}>
            <span className={styles.label}>Version:</span>
            <span className={styles.value}>{version}</span>
          </div>
        </div>
        {!!description &&
          <p>
            {description}
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
  })
};
