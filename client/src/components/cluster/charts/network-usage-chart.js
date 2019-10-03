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
import {Legend, LinePlot, Plot, XAxis, YAxis} from './controls';
import {AxisDataType, formatters} from './controls/utilities';

class NetworkUsageChart extends Base {
  static controlsHeight = 30;

  state = {
    selectedInterface: null
  };

  @computed
  get interfaces () {
    const {data} = this.props;
    if (data && data.data && data.data.length > 0) {
      const {stats} = data.data[0];
      return Object.keys(stats);
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
        identifier={'network-usage'}
        data={data}
        width={width}
        height={height}
        {...this.plotProperties}
      >
        <XAxis
          axisDataType={AxisDataType.date}
          {...this.xAxisProperties}
        />
        <YAxis
          axisDataType={AxisDataType.networkUsage}
          min={0}
          start={0}
          size={100}
        />
        <LinePlot
          identifier={'rx'}
          name={'RX'}
          dataField={obj => obj?.stats[selectedInterface]?.rxBytes}
          tooltip={item => formatters.networkUsage(item.stats[selectedInterface]?.rxBytes)}
        />
        <LinePlot
          identifier={'tx'}
          name={'TX'}
          dataField={obj => obj?.stats[selectedInterface]?.txBytes}
          tooltip={item => formatters.networkUsage(item.stats[selectedInterface]?.txBytes)}
        />
        <Legend />
      </Plot>
    );
  }
}

export default NetworkUsageChart;
