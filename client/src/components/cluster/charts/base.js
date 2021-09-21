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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {Icon} from 'antd';
import styles from './chart.css';

const TITLE_HEIGHT = 26;
const MINUTE = 60;

@observer
class Chart extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    data: PropTypes.object,
    disableTooltips: PropTypes.bool,
    title: PropTypes.string,
    width: PropTypes.number.isRequired,
    height: PropTypes.number.isRequired,
    onRangeChanged: PropTypes.func,
    rangeChangeEnabled: PropTypes.bool,
    start: PropTypes.number,
    end: PropTypes.number,
    followCommonScale: PropTypes.bool
  };

  static controlsHeight = 0;

  static defaultProps = {
    rangeChangeEnabled: true
  };

  state = {};

  @computed
  get canZoomIn () {
    let {start, end} = this.state;
    if (!start || !end) {
      return true;
    }
    return (end - start) > MINUTE;
  }

  @computed
  get canZoomOut () {
    const {data} = this.props;
    let {start, end} = this.state;
    if (data) {
      return (start && start > data.instanceFrom) || (end && end < data.instanceFrom);
    }
    return false;
  }

  get plotProperties () {
    const {rangeChangeEnabled} = this.props;
    const {start, end} = this.state;
    const properties = {
      onRangeChanged: this.onChangeRange,
      rangeChangeEnabled,
      from: start,
      to: end
    };
    const {data} = this.props;
    if (data) {
      properties.instanceFrom = data.instanceFrom;
      properties.instanceTo = data.instanceTo;
    }
    return properties;
  }

  componentDidMount () {
    this.updateRange(this.props);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    const {start, end} = this.state;
    if (
      nextProps.followCommonScale &&
      (
        nextProps.start !== start ||
        nextProps.end !== end
      )
    ) {
      this.updateRange(nextProps);
    }
  }

  updateRange = (props) => {
    const {start, end} = props;
    this.setState({start, end});
  };

  renderTitle = (height) => {
    const {
      data,
      title
    } = this.props;
    if (!title) {
      return null;
    }
    return (
      <div
        className={styles.title}
        style={{height}}
      >
        <Icon
          type={'loading'}
          style={{opacity: data && data.pending ? 1 : 0, marginRight: 5}}
        />
        {title}
      </div>
    );
  };

  onChangeRange = (start, end, loadData) => {
    const {data, followCommonScale} = this.props;
    const range = Math.max(end - start, MINUTE);
    if (data) {
      start = data.correctDateToFixRange(start);
      end = data.correctDateToFixRange(start + range);
      start = data.correctDateToFixRange(end - range);
    }
    this.setState({start, end}, () => {
      if (loadData && !followCommonScale) {
        const {data} = this.props;
        data.from = start;
        data.to = end;
        return data.loadData();
      }
    });
    const {onRangeChanged} = this.props;
    if (onRangeChanged) {
      onRangeChanged(start, end, loadData);
    }
  };

  renderControls () {
    return null;
  }

  renderZoomControls () {
    const zoomInClassNames = [styles.zoomControl];
    const zoomOutClassNames = [styles.zoomControl];
    if (!this.canZoomIn) {
      zoomInClassNames.push(styles.disabled);
    }
    if (!this.canZoomOut) {
      zoomOutClassNames.push(styles.disabled);
    }
    const zoomIn = () => {
      if (!this.canZoomIn) {
        return;
      }
      const {data} = this.props;
      if (!data) {
        return;
      }
      let {start, end} = this.state;
      if (!start) {
        start = data.instanceFrom;
      }
      if (!end) {
        end = data.instanceTo;
      }
      const newRange = (end - start) / 2.0;
      const center = (start + end) / 2.0;
      this.onChangeRange(
        center - newRange / 2.0,
        center + newRange / 2.0,
        true
      );
    };
    const zoomOut = () => {
      if (!this.canZoomOut) {
        return;
      }
      const {data} = this.props;
      if (!data) {
        return;
      }
      let {start, end} = this.state;
      if (!start) {
        start = data.instanceFrom;
      }
      if (!end) {
        end = data.instanceTo;
      }
      const newRange = (end - start) * 2.0;
      const center = (start + end) / 2.0;
      this.onChangeRange(
        center - newRange / 2.0,
        center + newRange / 2.0,
        true
      );
    };
    return (
      <div
        className={styles.zoomControls}
      >
        <Icon
          className={zoomInClassNames.join(' ')}
          type={'plus-circle-o'}
          onClick={zoomIn}
        />
        <Icon
          className={zoomOutClassNames.join(' ')}
          type={'minus-circle-o'}
          onClick={zoomOut}
        />
      </div>
    );
  }

  renderPlot (data, width, height, disableTooltips) {
    return null;
  }

  render () {
    const {
      className,
      data,
      disableTooltips,
      width,
      height,
      rangeChangeEnabled
    } = this.props;
    if (!data) {
      return null;
    }
    return (
      <div
        className={
          [
            styles.chartContainer,
            className
          ].filter(Boolean).join(' ')
        }
        style={{width, height}}
      >
        {this.renderTitle(TITLE_HEIGHT)}
        {this.renderControls()}
        {
          this.renderPlot(
            data,
            width,
            height - TITLE_HEIGHT - this.constructor.controlsHeight,
            disableTooltips
          )
        }
        {rangeChangeEnabled && this.renderZoomControls()}
      </div>
    );
  }
}

export default Chart;
