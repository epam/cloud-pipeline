/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import Renderer, {HeatmapTimelineChartEvents} from './renderer';
import {parseDate} from './utils';
import styles from './heat-map-chart.css';

@observer
class HeatMapChart extends React.Component {
  state = {
    options: [],
    hover: undefined
  };

  renderer = new Renderer(this.props.options);

  componentDidMount () {
    this.updateDatasets();
    this.updateRange();
    if (this.renderer) {
      this.renderer.addEventListener(
        HeatmapTimelineChartEvents.scaleChanged,
        this.onRangeChanged
      );
    }
  }

  componentDidUpdate (prevProps) {
    const {datasets, from, to} = this.props;
    if (datasets !== prevProps.datasets) {
      this.updateDatasets();
    }
    if (from !== prevProps.from || to !== prevProps.to) {
      this.updateRange();
    }
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.hoverPositionRAF);
    if (this.renderer) {
      this.renderer.destroy();
      this.renderer = undefined;
    }
  }

  initializeContainer = (container) => {
    if (this.renderer) {
      this.renderer.attachToContainer(container);
    }
  };

  onRangeChanged = (range) => {
    const {
      onRangeChanged
    } = this.props;
    if (typeof onRangeChanged === 'function') {
      const {
        from,
        to
      } = range;
      const {
        unix: fromUnix,
        date: fromDate
      } = parseDate(from) || {};
      const {
        unix: toUnix,
        date: toDate
      } = parseDate(to) || {};
      if (from === this.props.from && to === this.props.to) {
        return;
      }
      if (fromUnix !== undefined && toUnix !== undefined) {
        onRangeChanged({
          from: fromUnix,
          fromDate: fromDate,
          fromDateString: fromDate.format('YYYY-MM-DD HH:mm:ss'),
          to: toUnix,
          toDate: toDate,
          toDateString: toDate.format('YYYY-MM-DD HH:mm:ss')
        });
      }
    }
  };

  updateDatasets () {
    const {
      datasets = []
    } = this.props;
    if (this.renderer) {
      this.renderer.registerDatasets(datasets);
    }
  }

  updateRange = () => {
    const {
      from,
      to
    } = this.props;
    if (this.renderer && from !== undefined && to !== undefined) {
      this.renderer.from = from;
      this.renderer.to = to;
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={classNames(className, styles.container)}
        style={style}
      >
        <div
          key="renderer"
          className={styles.renderer}
          ref={this.initializeContainer}
        />
      </div>
    );
  }
}

HeatMapChart.propTypes = {
  datasets: PropTypes.arrayOf(PropTypes.shape({
    color: PropTypes.string,
    key: PropTypes.string,
    max: PropTypes.number,
    min: PropTypes.number,
    data: PropTypes.arrayOf(PropTypes.shape({
      name: PropTypes.string,
      records: PropTypes.arrayOf(PropTypes.shape({
        start: PropTypes.string,
        end: PropTypes.string,
        value: PropTypes.number
      }))
    }))
  })),
  onRangeChanged: PropTypes.func,
  from: PropTypes.number,
  to: PropTypes.number
};

export default HeatMapChart;
