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
import {computed} from 'mobx';
import {Row, Select} from 'antd';
import Base from './base';
import {AxisDataType} from './controls/utilities';
import {ChartRenderer, Plot} from './controls';

class NetworkUsageChart extends Base {
  static controlsHeight = 30;

  state = {
    selectedInterface: null
  };

  @computed
  get interfaces () {
    const {data} = this.props;
    if (data && data.groups && data.groups.length > 0) {
      return data.groups;
    }
    return [];
  }

  @computed
  get selectedInterface () {
    let {selectedInterface} = this.state;
    if (!selectedInterface && this.interfaces.length > 0) {
      selectedInterface = this.interfaces[0];
    }
    return selectedInterface;
  }

  renderControls () {
    const onSelectInterface = (selectedInterface) => {
      this.setState({selectedInterface});
    };
    return (
      <div
        style={{height: this.constructor.controlsHeight}}
      >
        <Row
          type="flex"
          justify="space-around"
        >
          <Select
            value={this.selectedInterface}
            style={{width: '50%'}}
            onChange={onSelectInterface}
          >
            {
              this.interfaces.map(i => (
                <Select.Option key={i} value={i}>{i}</Select.Option>
              ))
            }
          </Select>
        </Row>
      </div>
    );
  }

  renderPlot (data, width, height) {
    const selectedInterface = this.selectedInterface;
    if (!selectedInterface) {
      return null;
    }
    return (
      <Plot
        width={width}
        height={height}
        data={data}
        dataGroup={this.selectedInterface}
        dataType={AxisDataType.networkUsage}
        {...this.plotProperties}
        plots={[{
          name: 'rx', renderer: 'network-usage', group: selectedInterface, title: 'RX'
        }, {
          name: 'tx', renderer: 'network-usage', group: selectedInterface, title: 'TX'
        }]}
      >
        <ChartRenderer identifier={'network-usage'} />
      </Plot>
    );
  }
}

export default NetworkUsageChart;
