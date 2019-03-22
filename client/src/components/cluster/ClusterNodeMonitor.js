/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {Row, Col} from 'antd';
import styles from './ClusterNode.css';
import {
  CPUChart,
  MemoryChart,
  CurrentMemoryUsageChart,
  NetworkChart,
  FileSystemChart
} from './charts';

@inject('usage')
@observer
export default class ClusterNodeMonitor extends React.Component {
  render () {
    return (
      <div className={styles.fullHeightContainer} style={{overflowY: 'auto'}}>
        <Row type="flex" className={styles.twoChartsRow}>
          <Col xs={24} sm={12} className={styles.chartCol}>
            <CPUChart
              usage={this.props.usage}
              className={styles.chartContainer} />
          </Col>
          <Col xs={24} sm={12} className={styles.chartCol}>
            <div
              className={styles.chartContainer}
              style={{display: 'flex', flexDirection: 'column-reverse', flex: 1}}>
              <CurrentMemoryUsageChart
                usage={this.props.usage}
                className={styles.chartContainer80} />
              <MemoryChart
                usage={this.props.usage}
                style={{display: 'flex', flex: 1}} />
            </div>
          </Col>
        </Row>
        <Row type="flex" className={styles.twoChartsRow}>
          <Col xs={24} sm={12} className={styles.chartCol}>
            <NetworkChart
              usage={this.props.usage}
              className={styles.chartContainer} />
          </Col>
          <Col xs={24} sm={12} className={styles.chartCol}>
            <FileSystemChart
              usage={this.props.usage}
              className={styles.chartContainer} />
          </Col>
        </Row>
      </div>
    );
  }
}
