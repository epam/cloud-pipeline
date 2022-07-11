/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon} from 'antd';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import styles from './hcs-cell-selector.css';
import {cellsArraysAreEqual} from '../utilities/cells-utilities';

const DEFAULT_MINIMUM_CELL_SIZE = 5;
const DEFAULT_MAXIMUM_CELL_SIZE = 30;
const ZOOM_BUTTON_DELTA = 4;

function renderLegend (size, count, style) {
  return (new Array(count))
    .fill('o')
    .map((o, i) => (
      <div
        key={i}
        className={
          classNames(
            styles.legendItem,
            {
              [styles.s]: size < 10,
              [styles.m]: size >= 10 && size < 14,
              [styles.l]: size >= 14
            }
          )
        }
        style={style}
      >
        <span>{i + 1}</span>
      </div>
    ));
}

function getRegionSelectionFilter (region = {}, currentSelection = []) {
  const {
    start,
    end,
    append = false
  } = region;
  const previouslySelected = aCell => currentSelection
    .some(selectedCell => selectedCell.x === aCell.x && selectedCell.y === aCell.y);
  if (!start || !end) {
    return previouslySelected;
  }
  const [minX, maxX] = [start, end]
    .map(aCell => aCell.x)
    .sort((a, b) => a - b);
  const [minY, maxY] = [start, end]
    .map(aCell => aCell.y)
    .sort((a, b) => a - b);
  return aCell => (
    aCell.x >= minX && aCell.x <= maxX &&
    aCell.y >= minY && aCell.y <= maxY
  ) || (append && previouslySelected(aCell));
}

const Shapes = {
  circle: 'circle',
  rect: 'rect'
};

@inject('themes')
@observer
class HcsCellSelector extends React.Component {
  state = {
    minimumSize: undefined,
    cellSize: undefined,
    widthPx: undefined,
    heightPx: undefined,
    hovered: false,
    maxHeight: undefined,
    regionSelection: undefined
  };

  get canZoomOut () {
    const {
      minimumSize,
      cellSize
    } = this.state;
    return cellSize && minimumSize && cellSize > minimumSize;
  }

  get canZoomIn () {
    const {
      cellSize
    } = this.state;
    return cellSize && DEFAULT_MAXIMUM_CELL_SIZE && cellSize < DEFAULT_MAXIMUM_CELL_SIZE;
  }

  get currentSelection () {
    const {
      selected = []
    } = this.props;
    return selected;
  }

  componentDidMount () {
    this.initializeSizeChecker();
    this.initializeScrollerSynchronizer();
    const {themes} = this.props;
    if (themes) {
      themes.addThemeChangedListener(this.draw);
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.width !== this.props.width ||
      prevProps.height !== this.props.height
    ) {
      this.updateSize();
    } else if (
      !cellsArraysAreEqual(prevProps.selected, this.props.selected) ||
      !cellsArraysAreEqual(prevProps.cells, this.props.cells) ||
      prevState.regionSelection !== this.state.regionSelection
    ) {
      this.draw();
    }
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.rafHandle);
    cancelAnimationFrame(this.scrollerRafHandle);
    if (this.container) {
      this.container.removeEventListener('wheel', this.handleZoom);
    }
    const {themes} = this.props;
    if (themes) {
      themes.removeThemeChangedListener(this.draw);
    }
  }

  initializeContainer = (container) => {
    if (this.container) {
      this.container.removeEventListener('wheel', this.handleZoom);
    }
    this.container = container;
    if (this.container) {
      this.container.addEventListener('wheel', this.handleZoom);
      this.setState({
        widthPx: this.container.clientWidth,
        heightPx: this.container.clientHeight
      }, () => this.updateSize());
    }
  };

  initializeLegend = (horizontal = false) => (container) => {
    if (horizontal) {
      this.horizontalLegend = container;
    } else {
      this.verticalLegend = container;
    }
  };

  initializeCanvas = (canvas) => {
    this.canvas = canvas;
    this.draw();
  };

  updateSize = () => {
    const {
      allowEmptySpaces,
      height,
      width
    } = this.props;
    const {
      widthPx,
      heightPx
    } = this.state;
    if (
      height > 0 &&
      width > 0 &&
      widthPx > 0 &&
      heightPx > 0
    ) {
      const newCellSizeFn = o => Math.max(
        DEFAULT_MINIMUM_CELL_SIZE,
        Math.min(
          DEFAULT_MAXIMUM_CELL_SIZE,
          o
        )
      );
      const offset = 5;
      const columnCellSize = newCellSizeFn((widthPx - offset) / width);
      const rowCellSize = newCellSizeFn((heightPx - offset) / height);
      const newCellSize = Math.max(columnCellSize, rowCellSize);
      const minCellSize = allowEmptySpaces
        ? Math.round(Math.min(columnCellSize, rowCellSize))
        : newCellSize;
      this.setState({
        cellSize: newCellSize,
        minimumSize: minCellSize,
        maxHeight: Math.min(
          newCellSize * height,
          widthPx
        )
      }, () => this.draw());
    }
  }

  initializeScrollerSynchronizer = () => {
    const handler = () => {
      if (
        this.container &&
        this.verticalLegend &&
        this.horizontalLegend
      ) {
        const top = this.container.scrollTop;
        const left = this.container.scrollLeft;
        this.horizontalLegend.style.left = `-${left}px`;
        this.verticalLegend.style.top = `-${top}px`;
      }
      this.scrollerRafHandle = requestAnimationFrame(handler);
    };
    handler();
  };

  initializeSizeChecker = () => {
    const handler = () => {
      const {widthPx, heightPx} = this.state;
      if (
        this.container &&
        (
          this.container.clientWidth !== widthPx ||
          this.container.clientHeight !== heightPx
        )
      ) {
        this.setState({
          widthPx: this.container.clientWidth,
          heightPx: this.container.clientHeight
        });
      }
      this.rafHandle = requestAnimationFrame(handler);
    };
    handler();
  };

  zoom = (delta) => {
    const {
      minimumSize,
      cellSize
    } = this.state;
    const newCellSize = Math.max(
      minimumSize,
      Math.min(
        DEFAULT_MAXIMUM_CELL_SIZE,
        cellSize + delta
      )
    );
    if (newCellSize !== cellSize) {
      this.setState({cellSize: newCellSize}, () => this.draw());
    }
  };

  handleZoom = (event) => {
    const {cellSize} = this.state;
    if (event && event.shiftKey && cellSize) {
      const zoomIn = event.deltaY < 0;
      const eventDelta = zoomIn ? 2 : -2;
      this.zoom(eventDelta);
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
  };

  getCellUnderEvent = event => {
    const {
      cellSize
    } = this.state;
    const {
      offsetX,
      offsetY
    } = event.nativeEvent || {};
    if (offsetX !== undefined && offsetY !== undefined) {
      let x = Math.ceil(offsetX / cellSize);
      let y = Math.ceil(offsetY / cellSize);
      const {
        cells = [],
        flipVertical,
        flipHorizontal,
        width,
        height
      } = this.props;
      if (flipHorizontal) {
        x = width - x + 1;
      }
      if (flipVertical) {
        y = height - y + 1;
      }
      const cell = (cells.find(o => o.y === y && o.x === x));
      return {
        cell,
        x,
        y
      };
    }
    return {cell: undefined};
  };

  handleMouseDown = event => {
    const {multiple} = this.props;
    const {cell} = this.getCellUnderEvent(event);
    if (multiple && cell) {
      this.setState({
        regionSelection: {
          start: cell,
          end: cell,
          append: event && event.nativeEvent && event.nativeEvent.shiftKey
        }
      });
    }
  }

  handleMouseUp = event => {
    const {cell, x, y} = this.getCellUnderEvent(event);
    const {regionSelection} = this.state;
    const {
      onChange,
      cells,
      multiple
    } = this.props;
    if (multiple && x !== undefined && y !== undefined && regionSelection) {
      const {start, append} = regionSelection;
      const selection = cells.filter(
        getRegionSelectionFilter(
          {
            start,
            end: {x, y},
            append
          },
          this.currentSelection
        )
      );
      if (typeof onChange === 'function') {
        onChange(selection);
      }
      this.setState({
        regionSelection: undefined
      });
    } else if (multiple && regionSelection) {
      this.setState({
        regionSelection: undefined
      });
    } else if (cell && typeof onChange === 'function') {
      onChange([cell]);
    }
  }

  handleMouseMove = event => {
    const {
      hovered: currentHovered,
      regionSelection
    } = this.state;
    const {
      multiple
    } = this.props;
    const {cell, x, y} = this.getCellUnderEvent(event);
    if (multiple && x !== undefined && y !== undefined && regionSelection) {
      const {start, end, append} = regionSelection;
      if (!end || end.x !== x || end.y !== y) {
        this.setState({
          regionSelection: {start, end: {x, y}, append},
          hovered: true
        });
        return;
      }
    }
    if ((!!cell) !== currentHovered) {
      this.setState({hovered: !!cell});
    }
  };

  unHover = () => {
    const {hovered} = this.state;
    if (hovered) {
      this.setState({hovered: false});
    }
  }

  draw = () => {
    if (this.canvas) {
      const context = this.canvas.getContext('2d');
      context.clearRect(
        0,
        0,
        this.canvas.width,
        this.canvas.height
      );
      const {
        cellSize,
        regionSelection
      } = this.state;
      const {
        height = 0,
        width = 0,
        themes,
        cells = [],
        gridShape = Shapes.rect,
        gridRadius = 0,
        flipVertical,
        flipHorizontal
      } = this.props;
      let {
        cellShape = Shapes.circle
      } = this.props;
      if (cellSize < 15) {
        cellShape = Shapes.rect;
      }
      let selected = [];
      if (regionSelection) {
        selected = cells.filter(getRegionSelectionFilter(regionSelection, this.currentSelection));
      } else {
        selected = this.currentSelection;
      }
      if (height && width && cellSize) {
        if (this.drawHandle) {
          cancelAnimationFrame(this.drawHandle);
        }
        const correctPixels = o => o * window.devicePixelRatio;
        const getX = xx =>
          correctPixels((flipHorizontal ? (width - xx) : xx) * cellSize);
        const getY = yy =>
          correctPixels((flipVertical ? (height - yy) : yy) * cellSize);
        this.drawHandle = requestAnimationFrame(() => {
          let color = 'rgba(0, 0, 0, 0.65)';
          let dataColor = '#108ee9';
          let selectedColor = '#ff8818';
          if (themes && themes.currentThemeConfiguration) {
            color = themes.currentThemeConfiguration['@application-color-faded'] ||
              color;
            dataColor = themes.currentThemeConfiguration['@primary-color'] ||
              dataColor;
            selectedColor = themes.currentThemeConfiguration['@color-warning'] ||
              selectedColor;
          }
          context.save();
          const radius = Math.floor(cellSize / 2) - 1;
          const renderCell = (x, y) => {
            if (cellShape === Shapes.circle) {
              context.arc(
                Math.round(getX(x - 0.5)),
                Math.round(getY(y - 0.5)),
                correctPixels(radius),
                0,
                Math.PI * 2
              );
            }
            if (cellShape === Shapes.rect) {
              const xx = Math.min(getX(x - 1), getX(x));
              const yy = Math.min(getY(y - 1), getY(y));
              context.rect(
                Math.round(xx),
                Math.round(yy),
                Math.round(correctPixels(cellSize)),
                Math.round(correctPixels(cellSize))
              );
            }
          };
          context.save();
          context.fillStyle = dataColor;
          for (let dataCell of cells) {
            const {
              y,
              x
            } = dataCell;
            if (selected.some(cell => cell.x === x && cell.y === y)) {
              continue;
            }
            context.beginPath();
            renderCell(x, y);
            context.fill();
          }
          context.restore();
          context.save();
          context.fillStyle = selectedColor;
          for (let selectedCell of selected) {
            const {
              x,
              y
            } = selectedCell;
            context.beginPath();
            renderCell(x, y);
            context.fill();
          }
          context.restore();
          context.save();
          context.strokeStyle = color;
          context.lineWidth = 1;
          if (cellShape === Shapes.circle) {
            for (let c = 0; c < width; c += 1) {
              for (let r = 0; r < height; r += 1) {
                context.beginPath();
                renderCell(c + 1, r + 1);
                context.stroke();
              }
            }
          }
          if (cellShape === Shapes.rect) {
            context.beginPath();
            for (let c = 1; c <= width; c += 1) {
              context.moveTo(
                getX(c),
                0
              );
              context.lineTo(
                getX(c),
                this.canvas.height
              );
            }
            for (let r = 1; r <= height; r += 1) {
              context.moveTo(
                0,
                getY(r)
              );
              context.lineTo(
                this.canvas.width,
                getY(r)
              );
            }
            context.stroke();
          }
          context.restore();
          if (gridShape === Shapes.circle && gridRadius) {
            context.save();
            context.beginPath();
            context.strokeStyle = color;
            context.lineWidth = 2;
            context.arc(
              Math.round(getX(width / 2.0)),
              Math.round(getY(height / 2.0)),
              Math.round(correctPixels(cellSize * gridRadius)),
              0,
              Math.PI * 2
            );
            context.stroke();
            context.beginPath();
            context.arc(
              getX(width / 2.0),
              getY(height / 2.0),
              correctPixels(3),
              0,
              Math.PI * 2
            );
            context.stroke();
            context.fill();
            context.restore();
          }
        });
      }
    }
  };

  renderZoomControls = () => {
    const zoomInAvailable = this.canZoomIn;
    const zoomOutAvailable = this.canZoomOut;
    if (!zoomInAvailable && !zoomOutAvailable) {
      return null;
    }
    return (
      <div className={styles.zoomControls}>
        <Icon
          type="minus-circle-o"
          className={classNames(
            'cp-hcs-zoom-button',
            {'cp-disabled': !zoomOutAvailable},
            styles.zoomControlBtn
          )}
          onClick={() => this.zoom(-ZOOM_BUTTON_DELTA)}
        />
        <Icon
          type="plus-circle-o"
          className={classNames(
            'cp-hcs-zoom-button',
            {'cp-disabled': !zoomInAvailable},
            styles.zoomControlBtn
          )}
          onClick={() => this.zoom(ZOOM_BUTTON_DELTA)}
        />
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      height,
      width,
      controlledHeight,
      flipHorizontal,
      flipVertical,
      title,
      showLegend
    } = this.props;
    const {
      cellSize,
      hovered,
      maxHeight
    } = this.state;
    if (!width || !height) {
      return null;
    }
    return (
      <div
        style={style}
        className={
          classNames(
            className,
            styles.container
          )
        }
      >
        <div className={classNames(
          styles.header,
          {[styles.noLegend]: !showLegend}
        )}>
          {title ? (
            <div
              className={styles.title}
            >
              {title}
            </div>
          ) : null}
          {this.renderZoomControls()}
        </div>
        <div
          className={classNames(
            styles.canvasContainer,
            {[styles.noLegend]: !showLegend}
          )}
        >
          {showLegend && (
            <div className={styles.placeholder}>
              {'\u00A0'}
            </div>
          )}
          {showLegend && (
            <div
              className={
                classNames(
                  styles.rows,
                  {
                    [styles.flip]: flipVertical
                  }
                )
              }
            >
              <div className={styles.legend} ref={this.initializeLegend()}>
                {
                  cellSize && renderLegend(cellSize, height, {height: cellSize})
                }
              </div>
            </div>
          )}
          {showLegend && (
            <div
              className={
                classNames(
                  styles.columns,
                  {
                    [styles.flip]: flipHorizontal
                  }
                )
              }
            >
              <div className={styles.legend} ref={this.initializeLegend(true)}>
                {
                  cellSize && renderLegend(cellSize, width, {width: cellSize})
                }
              </div>
            </div>
          )}
          <div
            className={
              classNames(
                styles.data,
                'cp-outline-bordered'
              )
            }
            ref={this.initializeContainer}
            style={
              Object.assign(
                cellSize || !this.container ? {} : {overflow: 'scroll'},
                controlledHeight ? {} : {height: maxHeight}
              )
            }
          >
            <div
              style={{
                width: cellSize ? width * cellSize : 0,
                height: cellSize ? height * cellSize : 0
              }}
            >
              <canvas
                width={(cellSize ? width * cellSize : 0) * window.devicePixelRatio}
                height={(cellSize ? height * cellSize : 0) * window.devicePixelRatio}
                style={{
                  width: '100%',
                  height: '100%',
                  cursor: hovered ? 'pointer' : 'default'
                }}
                ref={this.initializeCanvas}
                onMouseMove={this.handleMouseMove}
                onMouseLeave={this.unHover}
                onMouseDown={this.handleMouseDown}
                onMouseUp={this.handleMouseUp}
              >
                {'\u00A0'}
              </canvas>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

const CellPropType = PropTypes.shape({
  x: PropTypes.number,
  y: PropTypes.number
});

const arrayOf = of => PropTypes.oneOfType([PropTypes.object, PropTypes.arrayOf(of)]);

HcsCellSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  height: PropTypes.number,
  width: PropTypes.number,
  selected: arrayOf(CellPropType),
  cells: arrayOf(CellPropType),
  onChange: PropTypes.func,
  multiple: PropTypes.bool,
  cellShape: PropTypes.oneOf([
    Shapes.circle,
    Shapes.rect
  ]),
  gridShape: PropTypes.oneOf([
    Shapes.circle,
    Shapes.rect
  ]),
  gridRadius: PropTypes.number,
  controlledHeight: PropTypes.bool,
  allowEmptySpaces: PropTypes.bool,
  flipVertical: PropTypes.bool,
  flipHorizontal: PropTypes.bool,
  title: PropTypes.string,
  showLegend: PropTypes.bool
};

HcsCellSelector.defaultProps = {
  cellShape: Shapes.circle,
  gridShape: Shapes.rect,
  showLegend: true
};

HcsCellSelector.Shapes = Shapes;
function sizeCorrection (size, cells = [], selector = () => 0) {
  return Math.max(
    size || 0,
    ...(cells || []).map(selector)
  );
}
HcsCellSelector.widthCorrection = (width, cells) =>
  sizeCorrection(width, cells, cell => cell.x);
HcsCellSelector.heightCorrection = (height, cells) =>
  sizeCorrection(height, cells, cell => cell.y);

export default HcsCellSelector;
