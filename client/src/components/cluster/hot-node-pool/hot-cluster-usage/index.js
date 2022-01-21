/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import moment from 'moment-timezone';
import OverallClusterChart from './charts/overall-cluster-chart';
import ClusterChart from './charts/cluster-chart';
import ControlRow from './controls/control-row';
import {PERIOD_TYPES} from './controls/period-picker';
import {colors} from './charts/utils';
import styles from './hot-cluster-usage.css';

const MOCKED_POOLS_AMOUNT = 4;

const randomNumber = (from, to) => {
  return Math.floor(Math.random() * (to - from + 1) + from);
};

const getDaylyMock = (day) => {
  const hours = Array.from(
    {length: 24},
    (_, i) => moment(i, 'HH').format('HH:mm')
  );
  const pools = Array.from(
    {length: MOCKED_POOLS_AMOUNT},
    (_, i) => `pool-${i + 1}`
  );
  let rndLimit = randomNumber(10, 20);
  let rnd = randomNumber(0, rndLimit);
  const mockedData = pools.reduce((acc, poolName) => {
    if (!acc[poolName]) {
      acc[poolName] = hours.map(hr => {
        if (randomNumber(0, 10) > 8) {
          rndLimit = randomNumber(10, 20);
          rnd = randomNumber(10, rndLimit);
        } else if (randomNumber(0, 10) > 7) {
          rnd = randomNumber(0, rndLimit);
        }
        return {
          poolLimit: rndLimit,
          poolUsage: rnd,
          measureTime: moment(`${day} ${hr}`).format('YYYY-MM-DD HH:mm')
        };
      });
    }
    return acc;
  }, {});
  return mockedData;
};

const getMonthlyMock = (date) => {
  const month = moment(date).format('YYYY-MM');
  const days = Array.from(
    {length: 30},
    (_, i) => moment(i + 1, 'DD').format('DD')
  );
  const pools = Array.from(
    {length: MOCKED_POOLS_AMOUNT},
    (_, i) => `pool-${i + 1}`
  );
  let rndLimit = randomNumber(10, 20);
  let rnd = randomNumber(0, rndLimit);
  const mockedData = pools.reduce((acc, poolName) => {
    if (!acc[poolName]) {
      acc[poolName] = days.map(day => {
        if (randomNumber(0, 10) > 8) {
          rndLimit = randomNumber(10, 20);
          rnd = randomNumber(10, rndLimit);
        } else if (randomNumber(0, 10) > 7) {
          rnd = randomNumber(0, rndLimit);
        }
        return {
          poolLimit: rndLimit,
          poolUsage: rnd,
          measureTime: moment(`${month}-${day}`).format('YYYY-MM-DD')
        };
      });
    }
    return acc;
  }, {});
  return mockedData;
};

class HotClusterUsage extends React.Component {
  state = {
    filters: {
      periodType: PERIOD_TYPES.day,
      period: moment().format('YYYY-MM-DD')
    },
    hiddenPools: [],
    currentCluster: null
  }

  componentDidMount () {
    this.updateMock(true);
  }

  updateMock = (updateCurrentCluster = false) => {
    const {period, periodType} = this.state.filters;
    const mock = periodType === PERIOD_TYPES.day
      ? getDaylyMock(period)
      : getMonthlyMock(period, 'YYYY-MM-DD');
    if (updateCurrentCluster) {
      return this.setState({
        mockedData: mock,
        currentCluster: Object.keys(mock)[0]
      });
    }
    return this.setState({
      mockedData: mock
    });
  };

  get clusterNames () {
    const {mockedData} = this.state;
    return Object.keys(mockedData);
  }

  get clusterChartColors () {
    const {currentCluster} = this.state;
    return {
      limit: '#ff4d4f',
      usage: colors[this.clusterNames.indexOf(currentCluster)]
    };
  }

  onPeriodTypeChange = (key) => {
    const {filters} = this.state;
    if (!key || filters.periodType === key) {
      return null;
    }
    return this.setState({
      filters: {
        ...filters,
        periodType: key
      }
    }, () => this.updateMock(true));
  };

  onPeriodChange = (period) => {
    const {filters} = this.state;
    return this.setState({
      filters: {
        ...filters,
        period
      }
    }, () => this.updateMock());
  };

  onCurrentClusterChange = (key) => {
    const {currentCluster} = this.state;
    if (currentCluster !== key) {
      return this.setState({currentCluster: key});
    }
    return null;
  };

  toggleHiddenPools = (event, {text}) => {
    const {hiddenPools} = this.state;
    if (!text) {
      return null;
    }
    if (!hiddenPools.includes(text)) {
      return this.setState({hiddenPools: [...hiddenPools, text]});
    }
    return this.setState({hiddenPools: hiddenPools.filter(pool => pool !== text)});
  };

  render () {
    const {
      filters,
      currentCluster,
      mockedData,
      hiddenPools
    } = this.state;
    if (!mockedData) {
      return null;
    }
    return (
      <div>
        <ControlRow
          onPeriodTypeChange={this.onPeriodTypeChange}
          filters={filters}
          currentCluster={currentCluster}
          onPeriodChange={this.onPeriodChange}
        />
        <div className={styles.chartsContainer}>
          <OverallClusterChart
            rawData={mockedData}
            filters={filters}
            onClick={this.onCurrentClusterChange}
            currentCluster={currentCluster}
            title="All hot node pools usage, %"
            units="%"
            colors={colors}
            onToggleDataset={this.toggleHiddenPools}
            hiddenDatasets={hiddenPools}
          />
          <ClusterChart
            rawData={mockedData}
            currentCluster={currentCluster}
            onCurrentClusterChange={this.onCurrentClusterChange}
            clusterNames={this.clusterNames}
            filters={filters}
            displayEmptyTitleRow
            units=" active nodes"
            description="M5.LARGE ON-DEMAND 50GB"
            colorOptions={this.clusterChartColors}
          />
        </div>
      </div>
    );
  }
}

export default HotClusterUsage;
