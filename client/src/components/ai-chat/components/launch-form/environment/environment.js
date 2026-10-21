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
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {TitleSection} from '../index';
import styles from './environment.css';

const PRICE_TYPES = {
  spot: 'Spot',
  onDemand: 'On demand'
};

@observer
export default class Environment extends React.Component {
  renderParameter = (title, value) => (
    <div className={styles.parameterContainer}>
      <b>{title}:</b>
      <span>{value}</span>
    </div>
  );

  render () {
    const {environment} = this.props.formStore;
    const {dockerImage, instanceType, disk, isSpot} = environment;
    const priceOption = isSpot ? PRICE_TYPES.spot : PRICE_TYPES.onDemand;
    let clusterDescription;
    if (environment.cluster) {
      const parts = [
        environment.sge ? 'GridEngine' : false,
        environment.slurm ? 'Slurm' : false,
        environment.spark ? 'Apache Spark' : false,
        environment.kube ? 'Kubernetes' : false
      ].filter(Boolean).join(', ');
      clusterDescription = parts.concat(
        environment.autoScaled
          ? ' auto-scaled cluster'
          : ' cluster'
      );
      if (environment.autoScaled && environment.defaultNodesCount > 1) {
        clusterDescription = clusterDescription
          .concat(` (${environment.defaultNodesCount} - ${environment.nodeCount} nodes)`);
      } else {
        clusterDescription = clusterDescription
          .concat(` (${environment.nodeCount} nodes)`);
      }
    }
    return (
      <div className={styles.stripe}>
        <TitleSection title="Environment" />
        <div className={styles.row}>
          {this.renderParameter('Docker image', dockerImage)}
          {this.renderParameter('Instance type', instanceType)}
          {this.renderParameter('Disk (Gb)', disk)}
          {this.renderParameter('Price type', priceOption)}
          {clusterDescription && (
            <div className={styles.parameterContainer}>
              <b>
                {clusterDescription}
              </b>
            </div>
          )}
        </div>
      </div>
    );
  }
}

Environment.propTypes = {
  formStore: PropTypes.object
};
