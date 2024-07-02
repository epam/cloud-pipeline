/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import TimelineChartRenderer, {TimelineChartEvents} from './renderer';
import getHoveredElementsInfo from './default-hover-info';
import styles from './timeline-chart.css';
import {parseDate} from './renderer/date-utilities';

const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_LINE_COLOR = DEFAULT_TEXT_COLOR;

@inject('themes')
@observer
class TimelineChart extends React.Component {
  state = {
    options: [],
    hover: undefined,
    colors: {
      primary: '#108ee9',
      green: '#09ab5a',
      red: '#f04134',
      yellow: '#ff8818',
      violet: '#8d09ab',
      pink: '#dd1144',
      backgroundColor: DEFAULT_BACKGROUND_COLOR,
      lineColor: DEFAULT_LINE_COLOR,
      textColor: DEFAULT_TEXT_COLOR
    }
  };

  renderer = new TimelineChartRenderer();
  hoverPosition = undefined;
  hoverContainer;

  componentDidMount () {
    this.updateColors()
      .then(() => {
        this.updateDatasets();
        this.updateColorsConfig();
        this.updateOtherConfig();
      });
    if (this.renderer) {
      this.renderer.addEventListener(TimelineChartEvents.onItemClick, this.onItemClick);
      this.renderer.addEventListener(TimelineChartEvents.onItemsHover, this.onHover);
      this.renderer.addEventListener(TimelineChartEvents.scaleChanged, this.onRangeChanged);
    }
    const {
      themes
    } = this.props;
    if (themes) {
      themes.addThemeChangedListener(this.onThemesChanged);
    }
    this.startHoverPositioning();
    this.updateRange();
  }

  componentDidUpdate (prevProps) {
    const {
      datasets,
      datasetOptions,
      from,
      to
    } = this.props;
    const themeColorsChanged = () => {
      const {options = []} = this.state;
      const currentDatasetsOptions = this.getDefaultDatasetsOptions();
      if (currentDatasetsOptions.length !== options.length) {
        return true;
      }
      for (let i = 0; i < currentDatasetsOptions.length; i++) {
        if (options[i].color !== currentDatasetsOptions[i].color) {
          return true;
        }
      }
      return false;
    };
    if (datasets !== prevProps.datasets) {
      this.updateDatasets();
    } else if (datasetOptions !== prevProps.datasetOptions) {
      this.updateDatasetsOptions();
    } else if (themeColorsChanged()) {
      this.updateDefaultDatasetsOptions();
    }
    this.updateOtherConfig();
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
    const {
      themes
    } = this.props;
    if (themes) {
      themes.removeThemeChangedListener(this.onThemesChanged);
    }
  }

  startHoverPositioning = () => {
    let x, y;
    const callback = () => {
      if (this.renderer && this.hoverContainer && this.hoverPosition) {
        const width = this.renderer.width;
        const height = this.renderer.height;
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
          10,
          Math.min(height - hoverHeight - 10, positionY - hoverHeight / 2.0)
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
  };

  onThemesChanged = () => {
    this.updateColors()
      .then(() => {
        this.updateDatasetsOptions();
        this.updateColorsConfig();
      });
  };

  getDefaultDatasetsOptions = () => {
    const {
      colors
    } = this.state;
    const defaultColors = [
      colors.primary,
      colors.green,
      colors.red,
      colors.yellow,
      colors.violet,
      colors.pink
    ];
    const getColorForIndex = (index) => defaultColors[index] || colors.primary;
    const {
      datasets = []
    } = this.props;
    return datasets.map((d, index) => ({
      color: getColorForIndex(index),
      width: 2.0,
      pointRadius: 3.0
    }));
  };

  updateDatasets () {
    const {
      datasets = []
    } = this.props;
    if (this.renderer) {
      this.renderer.registerDatasets(datasets);
      this.updateDatasetsOptions();
    }
  }

  updateDatasetsOptions = () => {
    const {
      datasetOptions = []
    } = this.props;
    if (this.renderer && datasetOptions && datasetOptions.length) {
      this.renderer.registerDatasetsOptions(datasetOptions);
    } else {
      this.updateDefaultDatasetsOptions();
    }
  };

  updateDefaultDatasetsOptions = () => {
    const defaultOptions = this.getDefaultDatasetsOptions();
    if (this.renderer) {
      this.renderer.registerDatasetsOptions(defaultOptions);
    }
    this.setState({
      options: defaultOptions
    });
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
    const primary = getColor('@primary-color') || '#108ee9';
    const green = getColor('@color-green') || '#09ab5a';
    const red = getColor('@color-red') || '#f04134';
    const yellow = getColor('@color-yellow') || '#ff8818';
    const violet = getColor('@color-violet') || '#8d09ab';
    const pink = getColor('@color-pink') || '#dd1144';
    const backgroundColor = getColor('@card-background-color') || DEFAULT_BACKGROUND_COLOR;
    const lineColor = getColor('@card-border-color') || DEFAULT_LINE_COLOR;
    const textColor = getColor('@application-color') || DEFAULT_TEXT_COLOR;
    const colors = {
      primary,
      green,
      red,
      yellow,
      violet,
      pink,
      backgroundColor,
      lineColor,
      textColor
    };
    this.setState({colors}, () => resolve(colors));
  });

  updateColorsConfig = () => {
    if (this.renderer) {
      const {
        colors
      } = this.state;
      this.renderer.backgroundColor = colors.backgroundColor;
      this.renderer.coordinatesColor = colors.textColor;
      this.renderer.coordinatesSystemColor = colors.lineColor;
      this.renderer.selectionColor = colors.lineColor;
    }
  };

  updateOtherConfig = () => {
    const {
      hover = true
    } = this.props;
    if (this.renderer) {
      this.renderer.hoverEvents = !!hover;
    }
  };

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

  initializeContainer = (container) => {
    if (this.renderer) {
      this.renderer.attachToContainer(container);
    }
  };

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
      items = []
    } = hoverInfo;
    this.hoverPosition = position;
    this.setState({
      hover: items
    });
  };

  onItemClick = (payload) => {
    if (!payload) {
      return;
    }
    const {
      onItemClick
    } = this.props;
    const {
      x,
      y,
      item
    } = payload;
    if (typeof onItemClick === 'function') {
      onItemClick(item, {x, y});
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
    const getColorForDataset = (dataset) => {
      if (this.renderer) {
        const {
          color
        } = this.renderer.getDatasetOptions(dataset) || {};
        return color;
      }
      return undefined;
    };
    const hoveredItems = hover.map((item) => ({
      ...item,
      color: getColorForDataset(item.dataset)
    }));
    const cssStyles = {
      row: styles.hoverItemRow,
      color: styles.hoverItemColor,
      dataset: styles.hoverItemDataset,
      value: styles.hoverItemValue
    };
    if (
      typeof hoverConfig === 'object' &&
      typeof hoverConfig.getHoveredElementsInfo === 'function'
    ) {
      return hoverConfig.getHoveredElementsInfo(hoveredItems, cssStyles);
    }
    return getHoveredElementsInfo(
      hoveredItems,
      cssStyles
    );
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
    return (
      <div
        key="hover-container"
        className={
          classNames(
            styles.hoverContainer,
            'cp-panel',
            'cp-dark-background',
            'semi-transparent'
          )
        }
        ref={initialize}
      >
        {
          this.renderHoveredItemsContent()
        }
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
        className={classNames(className, styles.timelineChartContainer)}
        style={style}
      >
        <div
          key="renderer"
          className={styles.renderer}
          ref={this.initializeContainer}
        />
        {this.renderHoveredInfo()}
      </div>
    );
  }
}

TimelineChart.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  datasets: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  datasetOptions: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onItemClick: PropTypes.func,
  from: PropTypes.oneOfType([PropTypes.number, PropTypes.string, PropTypes.object]),
  to: PropTypes.oneOfType([PropTypes.number, PropTypes.string, PropTypes.object]),
  onRangeChanged: PropTypes.func,
  hover: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.shape({
      getHoveredElementsInfo: PropTypes.func
    })
  ])
};

TimelineChart.defaultProps = {
  hover: true
};

export default TimelineChart;
