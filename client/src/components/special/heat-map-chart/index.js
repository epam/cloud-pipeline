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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import Renderer, {HeatmapTimelineChartEvents} from './renderer';
import {parseDate} from './utils';
import styles from './heat-map-chart.css';

const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_LINE_COLOR = DEFAULT_TEXT_COLOR;

@inject('themes')
@observer
class HeatMapChart extends React.Component {
  state = {
    options: [],
    hover: undefined,
    colors: {
      backgroundColor: DEFAULT_BACKGROUND_COLOR,
      lineColor: DEFAULT_LINE_COLOR,
      textColor: DEFAULT_TEXT_COLOR
    }
  };

  renderer = new Renderer(this.props.options);
  hoverPosition = undefined;
  hoverContainer;

  componentDidMount () {
    const {themes} = this.props;
    this.updateDatasets();
    this.updateRange();
    if (this.renderer) {
      this.renderer.addEventListener(
        HeatmapTimelineChartEvents.scaleChanged,
        this.onRangeChanged
      );
      this.renderer.addEventListener(
        HeatmapTimelineChartEvents.onItemsHover,
        this.onHover
      );
    }
    if (themes) {
      themes.addThemeChangedListener(this.onThemesChanged);
    }
    this.onThemesChanged();
    this.startHoverPositioning();
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
    this.hoverContainer = undefined;
  }

  onThemesChanged = () => {
    this.updateColors();
  };

  updateColors = () => new Promise((resolve) => {
    const {
      themes
    } = this.props;
    const getColor = (name) => {
      if (themes && themes.currentThemeConfiguration) {
        return themes.currentThemeConfiguration[name];
      }
      return undefined;
    };
    const backgroundColor = getColor('@card-background-color') || DEFAULT_BACKGROUND_COLOR;
    const lineColor = getColor('@card-border-color') || DEFAULT_LINE_COLOR;
    const textColor = getColor('@application-color') || DEFAULT_TEXT_COLOR;
    const colors = {
      backgroundColor,
      lineColor,
      textColor
    };
    this.setState({colors}, () => {
      if (this.renderer) {
        const {
          colors
        } = this.state;
        this.renderer.backgroundColor = colors.backgroundColor;
        this.renderer.coordinatesColor = colors.textColor;
        this.renderer.coordinatesSystemColor = colors.lineColor;
        this.renderer.selectionColor = colors.lineColor;
      }
    });
  });

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
        to,
        fromZoom = false,
        fromDrag = false
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
          toDateString: toDate.format('YYYY-MM-DD HH:mm:ss'),
          fromZoom,
          fromDrag
        });
      }
    }
  };

  updateDatasets () {
    const {
      datasets = []
    } = this.props;
    if (this.renderer && datasets?.length) {
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

  renderHoveredItemsContent = () => {
    const {
      hover
    } = this.state;
    if (!hover) {
      return undefined;
    }
    const {
      hover: hoverConfig
    } = this.props;
    if (
      hoverConfig?.getHoveredElementsInfo &&
      typeof hoverConfig?.getHoveredElementsInfo === 'function'
    ) {
      if (
        typeof hoverConfig === 'object' &&
        typeof hoverConfig.getHoveredElementsInfo === 'function'
      ) {
        return hoverConfig.getHoveredElementsInfo(hover);
      }
    }
    return undefined;
  };

  startHoverPositioning = () => {
    let x, y;
    const callback = () => {
      if (this.renderer && this.hoverContainer && this.hoverPosition) {
        const width = this.renderer.width;
        const hoverWidth = this.hoverContainer.clientWidth;
        const hoverHeight = this.hoverContainer.clientHeight;
        const {
          x: positionX,
          y: positionY
        } = this.hoverPosition;
        let newX, newY;
        if (width / 2.0 > positionX) {
          newX = positionX + 10;
        } else {
          newX = positionX - 10 - hoverWidth;
        }
        newY = Math.max(
          0,
          positionY - (hoverHeight / 2.0)
        );
        if (newX !== x || newY !== y) {
          x = newX;
          y = newY;
          this.hoverContainer.style.transform = `translate(${x}px, ${y}px)`;
        }
      }
      this.hoverPositionRAF = requestAnimationFrame(callback);
    };
    callback();
  }

  onHover = (hoverInfo) => {
    if (!this.hoverContainer) {
      return;
    }
    if (!hoverInfo) {
      this.hoverContainer.style.display = 'none';
      this.hoverPosition = undefined;
      this.setState({
        hover: undefined
      });
      return;
    }
    this.hoverContainer.style.display = 'block';
    const {
      position = {},
      info
    } = hoverInfo;
    this.hoverPosition = position;
    this.setState({
      hover: info
    });
  };

  renderHoveredInfo = () => {
    const {
      hover
    } = this.props;
    if (!hover) {
      return null;
    }
    const initialize = (container) => {
      this.hoverContainer = container;
    };
    const content = this.renderHoveredItemsContent();
    return (
      <div
        key="hover-container"
        className={
          classNames(
            styles.hoverContainer,
            'cp-panel'
          )
        }
        style={{
          visibility: content ? 'visible' : 'hidden'
        }}
        ref={initialize}
      >
        {content}
      </div>
    );
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
          style={{backgroundColor: this.state.colors.backgroundColor}}
          ref={this.initializeContainer}
        />
        {this.renderHoveredInfo()}
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
        start: PropTypes.oneOfType(PropTypes.number, PropTypes.string),
        end: PropTypes.oneOfType(PropTypes.number, PropTypes.string),
        value: PropTypes.number
      }))
    }))
  })),
  onRangeChanged: PropTypes.func,
  from: PropTypes.oneOfType([PropTypes.number, PropTypes.string, PropTypes.object]),
  to: PropTypes.oneOfType([PropTypes.number, PropTypes.string, PropTypes.object])
};

export default HeatMapChart;
