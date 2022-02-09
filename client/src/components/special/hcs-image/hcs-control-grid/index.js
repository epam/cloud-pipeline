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
import styles from './control-grid.css';

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

const Shapes = {
  circle: 'circle',
  rect: 'rect'
};

@inject('themes')
@observer
class HcsControlGrid extends React.Component {
  state = {
    minimumSize: undefined,
    cellSize: undefined,
    widthPx: undefined,
    heightPx: undefined,
    hovered: false,
    maxHeight: undefined
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
      prevProps.columns !== this.props.columns ||
      prevProps.rows !== this.props.rows
    ) {
      this.updateSize();
    } else if (
      prevProps.selectedCell !== this.props.selectedCell ||
      prevProps.dataCells !== this.props.dataCells
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
      rows,
      columns
    } = this.props;
    const {
      widthPx,
      heightPx
    } = this.state;
    if (
      rows > 0 &&
      columns > 0 &&
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
      const width = widthPx;
      const height = heightPx;
      const offset = 5;
      const columnCellSize = newCellSizeFn((width - offset) / columns);
      const rowCellSize = newCellSizeFn((height - offset) / rows);
      const newCellSize = Math.round(Math.max(columnCellSize, rowCellSize));
      const minCellSize = allowEmptySpaces
        ? Math.round(Math.min(columnCellSize, rowCellSize))
        : newCellSize;
      this.setState({
        cellSize: newCellSize,
        minimumSize: minCellSize,
        maxHeight: Math.min(
          newCellSize * rows,
          width
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
    let cell;
    if (offsetX !== undefined && offsetY !== undefined) {
      let column = Math.floor(offsetX / cellSize);
      let row = Math.floor(offsetY / cellSize);
      const {
        dataCells = [],
        flipVertical,
        flipHorizontal,
        columns,
        rows
      } = this.props;
      if (flipHorizontal) {
        column = columns - column;
      }
      if (flipVertical) {
        row = rows - row;
      }
      cell = (dataCells.find(o => o.row === row && o.column === column));
    }
    return cell;
  };

  handleMouseMove = event => {
    const {
      hovered: currentHovered
    } = this.state;
    let hovered = !!this.getCellUnderEvent(event);
    if (hovered !== currentHovered) {
      this.setState({hovered});
    }
  };

  handleClick = event => {
    const cell = this.getCellUnderEvent(event);
    const {onClick} = this.props;
    if (cell && onClick) {
      onClick(cell);
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
        cellSize
      } = this.state;
      const {
        rows = 0,
        columns = 0,
        themes,
        dataCells = [],
        selectedCell,
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
      if (rows && columns && cellSize) {
        if (this.drawHandle) {
          cancelAnimationFrame(this.drawHandle);
        }
        const correctPixels = o => o * window.devicePixelRatio;
        const getX = column =>
          correctPixels((flipHorizontal ? (columns - column) : column) * cellSize);
        const getY = row =>
          correctPixels((flipVertical ? (rows - row) : row) * cellSize);
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
          const renderCell = (column, row) => {
            if (cellShape === Shapes.circle) {
              context.arc(
                Math.round(getX(column + 0.5)),
                Math.round(getY(row + 0.5)),
                correctPixels(radius),
                0,
                Math.PI * 2
              );
            }
            if (cellShape === Shapes.rect) {
              context.rect(
                Math.round(getX(column)),
                Math.round(getY(row)),
                Math.round(correctPixels(cellSize)),
                Math.round(correctPixels(cellSize))
              );
            }
          };
          context.save();
          context.fillStyle = dataColor;
          for (let dataCell of dataCells) {
            const {
              row,
              column
            } = dataCell;
            if (selectedCell && selectedCell.column === column && selectedCell.row === row) {
              continue;
            }
            context.beginPath();
            renderCell(column, row);
            context.fill();
          }
          context.restore();
          context.save();
          context.fillStyle = selectedColor;
          if (selectedCell) {
            const {
              row,
              column
            } = selectedCell;
            context.beginPath();
            renderCell(column, row);
            context.fill();
          }
          context.restore();
          context.save();
          context.strokeStyle = color;
          context.lineWidth = 1;
          if (cellShape === Shapes.circle) {
            for (let c = 0; c < columns; c += 1) {
              for (let r = 0; r < rows; r += 1) {
                context.beginPath();
                renderCell(c, r);
                context.stroke();
              }
            }
          }
          if (cellShape === Shapes.rect) {
            context.beginPath();
            for (let c = 1; c <= columns; c += 1) {
              context.moveTo(
                getX(c),
                0
              );
              context.lineTo(
                getX(c),
                this.canvas.height
              );
            }
            for (let r = 1; r <= rows; r += 1) {
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
              Math.round(getX(columns / 2.0)),
              Math.round(getY(rows / 2.0)),
              Math.round(correctPixels(cellSize * gridRadius)),
              0,
              Math.PI * 2
            );
            context.stroke();
            context.beginPath();
            context.arc(
              getX(columns / 2.0),
              getY(rows / 2.0),
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
      rows,
      columns,
      controlledHeight,
      flipHorizontal,
      flipVertical,
      title
    } = this.props;
    const {
      cellSize,
      hovered,
      maxHeight
    } = this.state;
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
        <div className={styles.header}>
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
          className={styles.canvasContainer}
        >
          <div className={styles.placeholder}>{'\u00A0'}</div>
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
                cellSize && renderLegend(cellSize, rows, {height: cellSize})
              }
            </div>
          </div>
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
                cellSize && renderLegend(cellSize, columns, {width: cellSize})
              }
            </div>
          </div>
          <div
            className={
              classNames(
                styles.data
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
                width: cellSize ? columns * cellSize : 0,
                height: cellSize ? rows * cellSize : 0
              }}
            >
              <canvas
                width={(cellSize ? columns * cellSize : 0) * window.devicePixelRatio}
                height={(cellSize ? rows * cellSize : 0) * window.devicePixelRatio}
                style={{
                  width: '100%',
                  height: '100%',
                  cursor: hovered ? 'pointer' : 'default'
                }}
                ref={this.initializeCanvas}
                onMouseMove={this.handleMouseMove}
                onMouseLeave={this.unHover}
                onClick={this.handleClick}
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

HcsControlGrid.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  rows: PropTypes.number,
  columns: PropTypes.number,
  selectedCell: PropTypes.shape({
    row: PropTypes.number,
    column: PropTypes.number
  }),
  dataCells: PropTypes.arrayOf(PropTypes.shape({
    row: PropTypes.number,
    column: PropTypes.number
  })),
  onClick: PropTypes.func,
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
  title: PropTypes.string
};

HcsControlGrid.defaultProps = {
  cellShape: Shapes.circle,
  gridShape: Shapes.rect
};

HcsControlGrid.Shapes = Shapes;

export default HcsControlGrid;
