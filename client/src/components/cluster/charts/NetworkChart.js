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
import NodeChart from './NodeChart';
import {Select} from 'antd';

export default class NetworkChart extends NodeChart {
  state = {
    interfaces: [],
    selectedInterface: null
  };
  static title = 'Network';
  static noDataAvailableMessage = 'Network usage is unavailable';
  static chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    title: {
      display: false
    },
    tooltips: {
      mode: 'index',
      intersect: false
    },
    hover: {
      mode: 'nearest',
      intersect: true
    },
    scales: {
      yAxes: [{
        display: true,
        gridLines: {
          display: true
        },
        scaleLabel: {
          display: true,
          labelString: 'Bytes per second'
        },
        ticks: {
          beginAtZero: true,
          maxTicksLimit: 10
        }
      }]
    },
    legend: {
      display: true
    }
  };

  updateInterfaces (entries) {
    const interfaceKeys = entries.map(
      entry => {
        if (entry.networkUsage && entry.networkUsage.statsByInterface) {
          const statsByInterface = entry.networkUsage.statsByInterface;
          const keys = [];
          for (let key in statsByInterface) {
            if (statsByInterface.hasOwnProperty(key)) {
              keys.push(key);
            }
          }
          return keys;
        } else {
          return [];
        }
      }
    ).reduce((keys, currentKeys) => {
      for (let i = 0; i < currentKeys.length; i++) {
        if (keys.indexOf(currentKeys[i]) === -1) {
          keys.push(currentKeys[i]);
        }
      }
      return keys;
    }, []);
    const arraysMatch = (arr1, arr2) => {
      if (!!arr1 !== !!arr2 || !arr1 || !arr2) {
        return false;
      }
      if (arr1.length === arr2.length) {
        for (let i = 0; i < arr1.length; i++) {
          if (arr2.indexOf(arr1[i]) === -1) {
            return false;
          }
        }
        return true;
      }
      return false;
    };
    if (!arraysMatch(interfaceKeys, this.state.interfaces)) {
      if (!this.state.selectedInterface ||
        interfaceKeys.indexOf(this.state.selectedInterface) === -1) {
        this.setState({interfaces: interfaceKeys, selectedInterface: interfaceKeys[0]});
      } else {
        this.setState({interfaces: interfaceKeys});
      }
    }
  }

  renderChart (entries) {
    if (entries && this.state.selectedInterface && this.chart) {
      const items = entries
        .filter(entry =>
          entry.networkUsage &&
          entry.networkUsage.statsByInterface[this.state.selectedInterface]
        )
        .map(entry => {
          const stats = entry.networkUsage.statsByInterface[this.state.selectedInterface];
          return {
            rx: stats.rxBytes,
            tx: stats.txBytes,
            label: this.getDate(entry)
          };
        });
      const data = {
        labels: items.map(i => i.label),
        datasets: [{
          type: 'line',
          label: 'RX',
          fill: false,
          data: items.map(i => i.rx),
          borderColor: '#2282BF',
          backgroundColor: 'rgba(34, 130, 191, 0.25)'
        }, {
          type: 'line',
          label: 'TX',
          fill: false,
          data: items.map(i => i.tx),
          borderColor: '#bf2b3e',
          backgroundColor: 'rgba(191, 43, 62, 0.25)'
        }]
      };
      this.updateChartData(data);
    }
  };

  onInterfaceChange = (newInterface) => {
    this.setState({selectedInterface: newInterface});
  };

  renderContent () {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          width: '100%',
          height: '100%'
        }}>
        <div
          style={{
            paddingLeft: 55,
            paddingRight: 30
          }}>
          <Select
            style={{width: '100%'}}
            value={this.state.selectedInterface}
            onChange={this.onInterfaceChange}>
            {this.state.interfaces.map(i => {
              return (<Select.Option key={i} value={i}>{i}</Select.Option>);
            })}
          </Select>
        </div>
        <div style={{flex: 1, display: 'flex', position: 'relative', height: '100%'}}>
          {super.renderContent()}
        </div>
      </div>
    );
  };

  componentDidUpdate (prevProps, prevState) {
    if (!this.props.usage.pending) {
      this.updateInterfaces((this.props.usage.value || []).map(e => e));
    }
    super.componentDidUpdate();
  }
}
