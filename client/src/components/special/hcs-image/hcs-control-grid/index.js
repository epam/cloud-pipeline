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
import PropTypes from 'prop-types';

const CANVAS_PADDING = 20;
const ZOOM_TICK_PERCENT = 10 / 100;
const CELL_DIAMETER_LIMITS = {
  min: 5,
  max: 50
};
const CTX_STYLE = {
  strokeStyle: '#595959',
  lineWidth: 1
};

class HcsControlGrid extends React.Component {
  canvas;

  componentWillUnmount () {
    if (this.canvas) {
      this.canvas.removeEventListener('wheel', this.handleZoom);
      this.canvas.removeEventListener('click', this.handleClick);
    }
  }

  get bounds () {
    if (this.canvas) {
      return {
        xFrom: CANVAS_PADDING + CTX_STYLE.lineWidth,
        xTo: this.canvas.width - CANVAS_PADDING,
        yFrom: CANVAS_PADDING + CTX_STYLE.lineWidth,
        yTo: this.canvas.height - CANVAS_PADDING
      };
    }
    return null;
  };

  get cellDiameter () {
    const {columns} = this.props;
    if (!this.bounds) {
      return 0;
    }
    return (this.bounds.xTo - this.bounds.xFrom) / columns;
  }

  initializeCanvas = (canvas) => {
    this.canvas = canvas;
    this.canvas.addEventListener('wheel', this.handleZoom);
    this.canvas.addEventListener('click', this.handleClick);
    this.draw();
  };

  getElementAtEvent = (event) => {
    if (event && this.canvas) {
      const rect = this.canvas.getBoundingClientRect();
      const eventX = event.clientX - rect.left;
      const eventY = event.clientY - rect.top;
      if (
        eventX < this.bounds.xFrom ||
        eventX > this.bounds.xTo ||
        eventY < this.bounds.yFrom ||
        eventY > this.bounds.yTo
      ) {
        return null;
      }
      return {
        column: Math.ceil((eventX - this.bounds.xFrom) / this.cellDiameter),
        row: Math.ceil((eventY - this.bounds.yFrom) / this.cellDiameter)
      };
    }
    return null;
  };

  handleClick = (event) => {
    const {onClick} = this.props;
    if (event && this.canvas) {
      onClick && onClick(this.getElementAtEvent(event));
    }
  };

  cleanUpCanvas = () => {
    if (this.canvas) {
      const ctx = this.canvas.getContext('2d');
      this.canvas.width = this.canvas.offsetWidth;
      this.canvas.height = this.canvas.offsetHeight;
      ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }
  };

  draw = () => {
    if (this.canvas && this.canvas.getContext) {
      const {rows, columns} = this.props;
      if (rows && columns) {
        const ctx = this.canvas.getContext('2d');
        this.cleanUpCanvas();
        this.drawCells(ctx, rows, columns);
      }
    }
  };

  drawCells = (ctx, rows, columns) => {
    if (this.bounds) {
      const radius = this.cellDiameter / 2;
      for (let row = 0; row < rows; row++) {
        for (let column = 0; column < columns; column++) {
          this.drawCell(
            ctx,
            (this.bounds.xFrom + radius) + column * this.cellDiameter,
            (this.bounds.yFrom + radius) + row * this.cellDiameter,
            this.cellDiameter,
            'circle'
          );
        }
      }
    }
  };

  drawCell = (ctx, x, y, diameter, shape = 'circle') => {
    if (shape === 'circle') {
      ctx.beginPath();
      ctx.arc(x, y, diameter / 2, 0, Math.PI * 2);
      ctx.stroke();
    }
  };

  zoomIn = () => {
    if (this.canvas) {
      const {style, offsetWidth, offsetHeight} = this.canvas;
      style.width = `${offsetWidth + (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      style.height = `${offsetHeight + (offsetHeight * ZOOM_TICK_PERCENT)}px`;
      this.draw();
    }
  };

  zoomOut = () => {
    if (this.canvas) {
      const {style, offsetWidth, offsetHeight} = this.canvas;
      style.width = `${offsetWidth - (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      style.height = `${offsetHeight - (offsetHeight * ZOOM_TICK_PERCENT)}px`;
      this.draw();
    }
  };

  handleZoom = (e) => {
    if (e && e.shiftKey && this.cellDiameter) {
      const zoomIn = e.deltaY < 0;
      const nextCellDiameter = zoomIn
        ? this.cellDiameter + (this.cellDiameter * ZOOM_TICK_PERCENT)
        : this.cellDiameter - (this.cellDiameter * ZOOM_TICK_PERCENT);
      const furtherZoomPossible = zoomIn
        ? nextCellDiameter < CELL_DIAMETER_LIMITS.max
        : nextCellDiameter > CELL_DIAMETER_LIMITS.min;
      if (!furtherZoomPossible) {
        return;
      }
      return zoomIn ? this.zoomIn() : this.zoomOut();
    }
  };

  // todo: add canvas legend components, synced up with main plot scrolling
  // todo: dynamic canvas height, based on (rows * cellDiameter) + paddings
  // todo: scroll positions synced with event(x, y)?

  render () {
    const {
      style
    } = this.props;
    return (
      <div style={Object.assign({overflow: 'auto'}, style)}>
        <canvas
          style={{
            position: 'relative',
            width: '100%',
            height: '100%',
            border: '1px solid #dfdfdf'
          }}
          ref={this.initializeCanvas}
        />
      </div>
    );
  }
}

HcsControlGrid.propTypes = {
  rows: PropTypes.number,
  columns: PropTypes.number,
  onClick: PropTypes.func
};

export default HcsControlGrid;
