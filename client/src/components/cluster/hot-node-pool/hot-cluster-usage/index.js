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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Alert} from 'antd';
import OverallPoolChart from './charts/overall-pool-chart';
import PoolChart from './charts/pool-chart';
import LoadingView from '../../../special/LoadingView';
import fetchData from './fetch-data';
import ControlRow from './controls/control-row';
import {Period} from '../../../special/periods';
import colors, {
  getColor,
  backgroundColor,
  lineColor,
  textColor
} from './charts/utils/colors';
import styles from './hot-cluster-usage.css';

@inject('themes')
@inject((stores, params) => {
  const {
    location = {}
  } = params || {};
  const {query} = location;
  const {pool: poolString} = query;
  return {
    currentPoolId: Number.isNaN(Number(poolString))
      ? undefined
      : Number(poolString)
  };
})
@observer
class HotClusterUsage extends React.Component {
  state = {
    error: undefined,
    pending: true,
    periodType: Period.day,
    period: undefined,
    data: undefined,
    pools: [],
    hiddenPools: [],
    currentPoolId: undefined
  }

  @computed
  get colors () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return [
        '@primary-color',
        '@color-green',
        '@color-yellow',
        '@color-violet',
        '@color-red',
        '@color-aqua',
        '@color-grey',
        '@color-blue-dimmed',
        '@color-aqua-light'
      ]
        .map(color => themes.currentThemeConfiguration[color])
        .filter(Boolean);
    }
    return colors;
  }

  @computed
  get limitColor () {
    const {themes} = this.props;
    const defaultLimitColor = '#ff4d4f';
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@color-pink'] || defaultLimitColor;
    }
    return defaultLimitColor;
  }

  @computed
  get backgroundColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-background-color'] ||
        backgroundColor;
    }
    return backgroundColor;
  }

  @computed
  get lineColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-border-color'] || lineColor;
    }
    return lineColor;
  }

  @computed
  get textColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@application-color'] || textColor;
    }
    return textColor;
  }

  componentDidMount () {
    this.loadData(this.props.currentPoolId);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.currentPoolId !== this.props.currentPoolId) {
      this.onCurrentPoolChange(this.props.currentPoolId);
    }
  }

  loadData = (setPoolId) => {
    const {
      period,
      periodType
    } = this.state;
    let {
      currentPoolId
    } = this.state;
    if (setPoolId) {
      currentPoolId = setPoolId;
    }
    this.setState({
      pending: true,
      error: undefined
    }, async () => {
      const state = {
        pending: false,
        error: undefined,
        currentPoolId
      };
      try {
        state.data = await fetchData(periodType, period);
        state.pools = state.data.map(item => ({
          ...(item.pool || {}),
          id: Number(item.poolId),
          name: item.poolName
        }));
        if (!state.data || !state.pools.find(o => o.id === currentPoolId)) {
          state.currentPoolId = (state.pools[0] || {}).id;
        }
      } catch (e) {
        state.error = e.message;
      } finally {
        this.setState(state);
      }
    });
  }

  get clusterChartColors () {
    const {currentPoolId, pools = []} = this.state;
    let currentClusterIndex = pools.findIndex(o => o.id === currentPoolId);
    if (currentClusterIndex === -1) {
      currentClusterIndex = 0;
    }
    return {
      limit: this.limitColor,
      usage: getColor(currentClusterIndex, this.colors)
    };
  }

  onPeriodChange = (periodType, period) => {
    return this.setState({
      periodType,
      period
    }, () => this.loadData());
  };

  onCurrentPoolChange = (identifier) => {
    const {currentPoolId} = this.state;
    if (currentPoolId !== identifier) {
      return this.setState({currentPoolId: identifier});
    }
    return null;
  };

  toggleHiddenPools = (event, {datasetIndex}) => {
    const {
      hiddenPools,
      pools = []
    } = this.state;
    const pool = pools[datasetIndex];
    if (!pool) {
      return null;
    }
    const poolId = Number(pool.id);
    if (!hiddenPools.includes(poolId)) {
      return this.setState({hiddenPools: [...hiddenPools, poolId]});
    }
    return this.setState({hiddenPools: hiddenPools.filter(id => id !== poolId)});
  };

  render () {
    const {
      periodType,
      period,
      currentPoolId,
      hiddenPools,
      data,
      pools = [],
      error,
      pending
    } = this.state;
    return (
      <div>
        <ControlRow
          onChange={this.onPeriodChange}
          period={period}
          periodType={periodType}
        />
        {
          error && (
            <div>
              <Alert type="error" message={error} />
            </div>
          )
        }
        {
          pending && !data && (
            <LoadingView />
          )
        }
        {
          data && (
            <div className={styles.chartsContainer}>
              <OverallPoolChart
                rawData={data}
                onClick={this.onCurrentPoolChange}
                currentPoolId={currentPoolId}
                title="All hot node pools usage, %"
                units="%"
                colors={this.colors}
                onToggleDataset={this.toggleHiddenPools}
                hiddenDatasets={hiddenPools}
                backgroundColor={this.backgroundColor}
                lineColor={this.lineColor}
                textColor={this.textColor}
                period={period}
                periodType={periodType}
              />
              <PoolChart
                rawData={data}
                currentPoolId={currentPoolId}
                onCurrentPoolChange={this.onCurrentPoolChange}
                pools={pools}
                displayEmptyTitleRow
                units=" active nodes"
                colorOptions={this.clusterChartColors}
                backgroundColor={this.backgroundColor}
                lineColor={this.lineColor}
                textColor={this.textColor}
                period={period}
                periodType={periodType}
              />
            </div>
          )
        }
      </div>
    );
  }
}

export default HotClusterUsage;
