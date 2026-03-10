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
import {withRouter} from '../../../../utils/with-router';
import {computed, makeObservable} from 'mobx';
import {Alert} from 'antd';
import classNames from 'classnames';
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
import PoolsHardwareChart from './charts/pools-hardware-chart';
import {HARDWARE_MAPPING} from './charts/utils/hardware-chart-utils';
import ResourseSharingPoolTable from './resourse-sharing-pool-table';

@inject('themes')
@inject((stores, params) => {
  const {
    location = {}
  } = params || {};
  const {search = ''} = location;
  const poolString = new URLSearchParams(search).get('pool');
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

  constructor (props) {
    super(props);
    makeObservable(this, {
      colors: computed,
      pendingBarColor: computed,
      totalBarColor: computed,
      runsColors: computed,
      cpuColors: computed,
      ramColors: computed,
      gpuColors: computed,
      limitColor: computed,
      backgroundColor: computed,
      lineColor: computed,
      textColor: computed
    });
  }

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

  get pendingBarColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@application-background-color'];
    }
    return getColor(1);
  }

  get totalBarColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@color-pink'];
    }
    return getColor(2);
  }

  get runsColors () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return {
        active: themes.currentThemeConfiguration['@primary-color'],
        pending: this.pendingBarColor,
        total: this.totalBarColor
      };
    }
    return {
      active: getColor(0),
      pending: this.pendingBarColor,
      total: this.totalBarColor
    };
  }

  get cpuColors () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return {
        active: themes.currentThemeConfiguration['@color-green'],
        pending: this.pendingBarColor,
        total: this.totalBarColor
      };
    }
    return {
      active: getColor(0),
      pending: this.pendingBarColor,
      total: this.totalBarColor
    };
  }

  get ramColors () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return {
        active: themes.currentThemeConfiguration['@color-blue-dimmed'],
        pending: this.pendingBarColor,
        total: this.totalBarColor
      };
    }
    return {
      active: getColor(0),
      pending: this.pendingBarColor,
      total: this.totalBarColor
    };
  }

  get gpuColors () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return {
        active: themes.currentThemeConfiguration['@color-violet'],
        pending: this.pendingBarColor,
        total: this.totalBarColor
      };
    }
    return {
      active: getColor(0),
      pending: this.pendingBarColor,
      total: this.totalBarColor
    };
  }

  get limitColor () {
    const {themes} = this.props;
    const defaultLimitColor = '#ff4d4f';
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@color-pink'] || defaultLimitColor;
    }
    return defaultLimitColor;
  }

  get backgroundColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-background-color'] ||
        backgroundColor;
    }
    return backgroundColor;
  }

  get lineColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-border-color'] || lineColor;
    }
    return lineColor;
  }

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
          name: item.poolName,
          resourceSharingPool: item.resourceSharingPool || false
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
    const currentPoolData = (data || [])
      .find(({poolId}) => poolId === currentPoolId);
    const {poolName, resourceSharingPool = false} = currentPoolData || {};
    const onlyResourceSharingPools = !(data || []).some((p) => !p.resourceSharingPool);
    // CSS Grid controls the placement and order of elements.
    return (
      <div>
        <ControlRow
          onChange={this.onPeriodChange}
          period={period}
          periodType={periodType}
          pending={pending}
        />
        {
          error && (
            <div>
              <Alert type="error" title={error} />
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
            <div
              className={classNames(
                styles.chartsContainer, {
                  [styles.onlySharing]: onlyResourceSharingPools,
                  [styles.withSharing]: resourceSharingPool,
                  [styles.noSharing]: !resourceSharingPool
                }
              )}
            >
              {resourceSharingPool ? (
                <ResourseSharingPoolTable
                  data={currentPoolData}
                  className={styles.poolTableArea}
                  periodType={periodType}
                />
              ) : null}
              {!onlyResourceSharingPools && (
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
                  className={styles.overallPoolChartArea}
                />
              )}
              {!onlyResourceSharingPools && (
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
                  className={styles.poolChartArea}
                />
              )}
              {resourceSharingPool && (
                <PoolsHardwareChart
                  currentPoolId={currentPoolId}
                  onCurrentPoolChange={this.onCurrentPoolChange}
                  pools={pools}
                  showPoolSelector={onlyResourceSharingPools}
                  rawData={currentPoolData}
                  mappings={[
                    HARDWARE_MAPPING.runs,
                    HARDWARE_MAPPING.runsPending
                  ]}
                  title={
                    !onlyResourceSharingPools && poolName
                      ? `${poolName} - jobs status`
                      : 'Jobs status'
                  }
                  units=""
                  colors={this.runsColors}
                  textColor={this.textColor}
                  backgroundColor={this.backgroundColor}
                  lineColor={this.lineColor}
                  period={period}
                  periodType={periodType}
                  className={styles.jobsArea}
                />
              )}
              {resourceSharingPool && (
                <PoolsHardwareChart
                  currentPoolId={currentPoolId}
                  onCurrentPoolChange={this.onCurrentPoolChange}
                  pools={pools}
                  showPoolSelector={onlyResourceSharingPools}
                  rawData={currentPoolData}
                  mappings={[
                    HARDWARE_MAPPING.gpu,
                    HARDWARE_MAPPING.gpuPending,
                    HARDWARE_MAPPING.gpuLimit
                  ]}
                  title={`${poolName} - GPU status`}
                  units=""
                  colors={this.gpuColors}
                  textColor={this.textColor}
                  backgroundColor={this.backgroundColor}
                  lineColor={this.lineColor}
                  period={period}
                  periodType={periodType}
                  className={styles.gpuArea}
                />
              )}
              {resourceSharingPool && (
                <PoolsHardwareChart
                  currentPoolId={currentPoolId}
                  onCurrentPoolChange={this.onCurrentPoolChange}
                  pools={pools}
                  showPoolSelector={onlyResourceSharingPools}
                  rawData={currentPoolData}
                  mappings={[
                    HARDWARE_MAPPING.cpu,
                    HARDWARE_MAPPING.cpuPending,
                    HARDWARE_MAPPING.cpuLimit
                  ]}
                  title={`${poolName} - CPU status`}
                  units=""
                  colors={this.cpuColors}
                  textColor={this.textColor}
                  backgroundColor={this.backgroundColor}
                  lineColor={this.lineColor}
                  period={period}
                  periodType={periodType}
                  className={styles.cpuArea}
                />
              )}
              {resourceSharingPool && (
                <PoolsHardwareChart
                  currentPoolId={currentPoolId}
                  onCurrentPoolChange={this.onCurrentPoolChange}
                  pools={pools}
                  showPoolSelector={onlyResourceSharingPools}
                  rawData={currentPoolData}
                  mappings={[
                    HARDWARE_MAPPING.ram,
                    HARDWARE_MAPPING.ramPending,
                    HARDWARE_MAPPING.ramLimit
                  ]}
                  title={`${poolName} - memory status`}
                  units="GiB"
                  colors={this.ramColors}
                  textColor={this.textColor}
                  backgroundColor={this.backgroundColor}
                  lineColor={this.lineColor}
                  period={period}
                  periodType={periodType}
                  className={styles.ramArea}
                />
              )}
            </div>
          )
        }
      </div>
    );
  }
}

export default withRouter(HotClusterUsage);
