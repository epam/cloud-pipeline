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
import {toJS} from 'mobx';
import {inject, observer} from 'mobx-react';
import moment from 'moment-timezone';
import {message, Spin} from 'antd';
import ClusterNetworkUsageFilters from '../../../../models/cluster/ClusterNetworkUsageFilters';
import {
  ClusterNetworkUsageFilter,
  HISTOGRAM_TYPES
} from '../../../../models/cluster/ClusterNetworkUsageFilter';
import ProxyStateChart from './charts/proxy-state-chart';
import ThemedReport from '../../../billing/reports/themed-report';
import Filters from './components/filters';
import {getDatasetOptions, getDatasetStyles, formatLabel} from './utils';
import copyTextToClipboard from '../../../special/copy-text-to-clipboard';

const FETCH_DELAY = 500;
const TOP_ITEMS_COUNT = 10;

@inject('reportThemes', 'usersInfo')
@observer
class ProxyState extends React.Component {
  fetchDataTimeout;

  defaultFilters = {
    reporter: [],
    hostname: undefined,
    hostIp: undefined,
    runId: undefined,
    resourceHost: undefined,
    method: [],
    from: moment().subtract(24, 'hours'),
    to: moment()
  };

  state = {
    data: undefined,
    availableFilters: {
      method: [],
      reporter: []
    },
    filters: this.defaultFilters,
    pending: true
  };

  componentDidMount () {
    const init = async () => {
      await this.loadFilters();
      this.fetchData(true);
    };
    void init();
  }

  loadFilters = async () => {
    const request = new ClusterNetworkUsageFilters();
    await request.fetch();
    if (request.error) {
      message.error(request.error, 5);
      return;
    }
    this.setState({
      availableFilters: toJS(request.value)
    });
  };

  fetchData = (immediately = false) => {
    const {filters} = this.state;
    const {from, to} = filters;
    const formatStringFilter = (filter) => {
      if (!filter) {
        return [];
      }
      return filter.split(',').map((item) => item.trim());
    };
    const fetchDataCallback = () => {
      this.setState({pending: true}, async () => {
        const requests = [
          HISTOGRAM_TYPES.time,
          HISTOGRAM_TYPES.run,
          HISTOGRAM_TYPES.resource
        ].map((histogramType) => {
          return new Promise(async (resolve) => {
            const request = new ClusterNetworkUsageFilter(
              histogramType,
              moment(from).format('YYYY-MM-DD HH:mm:ss'),
              moment(to).format('YYYY-MM-DD HH:mm:ss')
            );
            await request.send({
              runId: formatStringFilter(filters.runId),
              hostname: formatStringFilter(filters.hostname),
              hostIp: formatStringFilter(filters.hostIp),
              resourceHost: formatStringFilter(filters.resourceHost),
              reporter: filters.reporter,
              method: filters.method
            });
            if (request.error) {
              resolve({
                type: histogramType,
                value: undefined,
                error: request.error
              });
            }
            if (request.loaded && request.value) {
              resolve({
                type: histogramType,
                value: request.value
              });
            } else {
              resolve({
                type: histogramType,
                value: undefined
              });
            }
          });
        });
        const results = await Promise.all(requests);
        this.setState({data: results}, () => {
          this.setState({pending: false});
        });
      });
    };
    if (this.fetchDataTimeout) {
      clearTimeout(this.fetchDataTimeout);
    }
    this.fetchDataTimeout = setTimeout(() => {
      fetchDataCallback();
    }, immediately ? 0 : FETCH_DELAY);
  };

  onChangeFilters = (filters) => {
    this.setState({filters}, () => {
      this.fetchData();
    });
  };

  onEntryClick = (chartEntry, dataEntry, scaleHovered) => {
    if (!chartEntry) {
      return;
    }
    if (dataEntry.type === HISTOGRAM_TYPES.run) {
      this.setState({
        filters: {
          ...this.state.filters,
          runId: chartEntry
        }
      }, () => this.fetchData(true));
    }
    if (dataEntry.type === HISTOGRAM_TYPES.resource) {
      if (scaleHovered) {
        return copyTextToClipboard(chartEntry).then(() => {
          message.info(
            <span>
              <b>{chartEntry}</b> copied to clipboard.
            </span>, 5);
        }).catch((error) => {
          console.error(error.message);
        });
      }
      this.setState({
        filters: {
          ...this.state.filters,
          resourceHost: chartEntry
        }
      }, () => this.fetchData(true));
    }
  };

  render () {
    const {data, pending, filters, availableFilters} = this.state;
    const {reportThemes} = this.props;
    const extractData = (dataEntry) => {
      const {value = []} = dataEntry;
      const sorted =
        dataEntry.type === HISTOGRAM_TYPES.time
          ? value
          : [...value]
            .sort((a, b) => b.count - a.count)
            .slice(0, TOP_ITEMS_COUNT);
      const labels = sorted.map((item) => formatLabel(dataEntry.type, item.value, filters));
      const datasets = [
        {
          label: dataEntry.type,
          data: sorted.map((item) => item.count),
          ...getDatasetStyles(dataEntry.type, reportThemes)
        }
      ];
      return {
        labels,
        datasets,
        entries: labels
      };
    };
    if (!data && pending) {
      return <Spin style={{width: '100%', height: '100%'}} />;
    }
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          flexWrap: 'wrap',
          height: '100%'
        }}
      >
        <Filters
          onChange={this.onChangeFilters}
          availableFilters={availableFilters}
          defaultFilters={this.defaultFilters}
          filters={filters}
        />
        {(data ?? []).map((dataEntry) => {
          const dynamicHeight = Math.max(
            (dataEntry.value ?? []).length * 30 + 50,
            200
          );
          return (
            <ProxyStateChart
              type={dataEntry.type}
              key={dataEntry.type}
              loading={pending}
              title={dataEntry.type}
              data={extractData(dataEntry)}
              style={{
                width: '100%',
                maxHeight: '50%',
                height: dataEntry.type === HISTOGRAM_TYPES.resource
                  ? `${dynamicHeight}px`
                  : '300px',
                marginBottom: 10
              }}
              options={getDatasetOptions(dataEntry.type)}
              onEntryClick={(entry, _i, scaleHovered) => {
                this.onEntryClick(entry, dataEntry, scaleHovered);
              }}
            />
          );
        })}
      </div>
    );
  }
}

const ProxyStateWithThemes = (props) => {
  return (
    <ThemedReport>
      <ProxyState {...props} />
    </ThemedReport>
  );
};

export default ProxyStateWithThemes;
