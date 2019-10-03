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

import {computed, observable} from 'mobx';
import AxisPosition from './axis-position';
import AxisDefaultSize from './axis-default-size';

class PlotContext {
  @observable padding = {
    top: 10,
    left: AxisDefaultSize.vertical,
    right: AxisDefaultSize.vertical,
    bottom: AxisDefaultSize.horizontal
  };
  @observable data = {
    data: [],
    ranges: {}
  };
  @observable axises = [];
  @observable plots = [];
  @observable container;
  @observable hoveredItem;

  constructor (container) {
    this.container = container;
  }

  setData = (data) => {
    if (this.data && this.data.unRegisterListener) {
      this.data.unRegisterListener(this.dataUpdated);
    }
    this.data = data;
    if (this.data && this.data.registerListener) {
      this.data.registerListener(this.dataUpdated);
    }
  };

  dataUpdated = () => {
    this.hoveredItem = null;
  };

  registerAxis (axis) {
    if (this.axises.indexOf(axis) === -1) {
      this.axises.push(axis);
    }
  }

  unRegisterAxis (axis) {
    const index = this.axises.indexOf(axis);
    if (index >= 0) {
      this.axises.splice(index, 1);
    }
  }

  registerPlot (plot) {
    let index = this.plots.indexOf(plot);
    if (index === -1) {
      this.plots.push(plot);
      index = this.plots.length - 1;
    }
    return index;
  }

  unRegisterPlot (plot) {
    const index = this.axises.indexOf(plot);
    if (index >= 0) {
      this.plots.splice(plot, 1);
    }
  }

  getAxis (identifier) {
    const [result] = this.axises.filter(axis => axis.props.identifier === identifier);
    return result;
  }

  getPlot (identifier) {
    const [result] = this.plots.filter(plot => plot.props.identifier === identifier);
    return result;
  }

  get series () {
    const mapDataFieldFn = (fn) => {
      if (typeof fn === 'string') {
        return obj => obj[fn];
      }
      return fn;
    };
    return this.plots
      .map((plot, index) => ({
        dataField: mapDataFieldFn(plot.props.dataField),
        name: plot.props.name || plot.props.identifier,
        index
      }));
  }

  getSeriesForYAxis (axis) {
    return this.plots
      .filter(plot => plot.props.yAxis === axis.props.identifier)
      .reduce((result, current) => {
        const {dataField} = current.props;
        return [...result, dataField];
      }, [])
      .filter(Boolean);
  }

  getAxisesSize = (position) => {
    return this.axises
      .filter(axis => axis.props.position === position)
      .reduce((size, axis) => size + (axis.size || 0), 0);
  };

  @computed
  get xAxis () {
    const [axis] = this.axises
      .filter(axis => [AxisPosition.bottom, AxisPosition.top].indexOf(axis.props.position) >= 0);
    return axis;
  }

  @computed
  get yAxis () {
    const [axis] = this.axises
      .filter(axis => [AxisPosition.right, AxisPosition.left].indexOf(axis.props.position) >= 0);
    return axis;
  }

  @computed
  get identifier () {
    if (this.container) {
      return this.container.props.identifier;
    }
    return '';
  }

  @computed
  get left () {
    return this.getAxisesSize(AxisPosition.left) || this.padding.left;
  }

  @computed
  get right () {
    return this.getAxisesSize(AxisPosition.right) || this.padding.right;
  }

  @computed
  get top () {
    return this.getAxisesSize(AxisPosition.top) || this.padding.top;
  }

  @computed
  get bottom () {
    return this.getAxisesSize(AxisPosition.bottom) || this.padding.bottom;
  }

  getAxisOffset (axis) {
    const samePositionAxes = this.axises.filter(a => a.props.position === axis.props.position);
    const index = samePositionAxes.indexOf(axis);
    return samePositionAxes
      .filter((a, i) => i < index)
      .reduce((size, axis) => size + (axis.size || 0), 0);
  }

  @computed
  get width () {
    if (this.container) {
      return this.container.width;
    }
    return 0;
  }

  @computed
  get height () {
    if (this.container) {
      return this.container.height;
    }
    return 0;
  }

  @computed
  get dataXRange () {
    if (!this.data || !this.data.ranges || !this.data.ranges.x) {
      return {
        min: Infinity,
        max: -Infinity
      };
    }
    return this.data.ranges.x;
  }

  getDataRange (axis) {
    const series = this.getSeriesForYAxis(axis).map(s => {
      if (typeof s === 'string') {
        return obj => obj[s];
      }
      return s;
    });
    return series.map(fn => fn(this.data.ranges || {}))
      .reduce((result, current) => ({
        min: Math.min(result.min, current?.min),
        max: Math.max(result.max, current?.max)
      }), {min: Infinity, max: -Infinity});
  }

  getNearestItem = ({x, y}, xAxisIdentifier = undefined) => {
    let xAxis = this.xAxis;
    if (xAxisIdentifier) {
      [xAxis] = this.axises.filter(axis => axis.props.identifier === xAxisIdentifier);
    }
    if (
      xAxis &&
      x > this.left &&
      x < this.width - this.right &&
      y > this.top &&
      y < this.height - this.bottom
    ) {
      const dataX = xAxis.getPlotCoordinate(x);
      let min = Infinity;
      let nearestItem = null;
      for (let i = 0; i < this.data.data.length; i++) {
        const item = this.data.data[i];
        if (min > Math.abs(item.x - dataX)) {
          min = Math.abs(item.x - dataX);
          nearestItem = item;
        }
      }
      return nearestItem;
    }
    return null;
  }
}

export default PlotContext;
