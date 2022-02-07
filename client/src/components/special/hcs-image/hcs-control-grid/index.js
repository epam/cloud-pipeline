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
const DEFAULTS = {
  strokeStyle: '#595959',
  lineWidth: 1,
  background: '#ececec'
};

class HcsControlGrid extends React.Component {
  canvasContainer;
  canvas;
  verticalLegendContainer;
  verticalLegend;
  horisontalLegendContainer;
  horisontalLegend;
  scrolling = false;

  componentWillUnmount () {
    if (this.canvas) {
      this.canvas.removeEventListener('wheel', this.handleZoom);
      this.canvas.removeEventListener('click', this.handleClick);
      this.canvasContainer.removeEventListener('scroll', this.handleScroll);
    }
  }

  get bounds () {
    if (this.canvas) {
      return {
        xFrom: CANVAS_PADDING + DEFAULTS.lineWidth,
        xTo: this.canvas.width - CANVAS_PADDING,
        yFrom: CANVAS_PADDING + DEFAULTS.lineWidth,
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

  initializeCanvasContainer = (canvas) => {
    this.canvasContainer = canvas;
    this.canvasContainer.addEventListener('scroll', this.handleScroll);
  };

  initializeLegends = (canvas, type) => {
    if (type === 'vertical') {
      this.verticalLegend = canvas;
    } else if (type === 'horisontal') {
      this.horisontalLegend = canvas;
    }
    this.drawLegends();
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

  handleScroll = () => {
    if (!this.scrolling) {
      window.requestAnimationFrame(() => {
        this.synchronizeScrolls();
        this.scrolling = false;
      });
      this.scrolling = true;
    }
  };

  synchronizeScrolls = () => {
    if (this.canvasContainer) {
      const scrollWidth = this.canvasContainer.offsetWidth - this.canvasContainer.clientWidth;
      if (this.horisontalLegendContainer) {
        this.horisontalLegendContainer.scrollLeft = this.canvasContainer.scrollLeft;
        this.horisontalLegendContainer.style.width = `${
          this.canvasContainer.offsetWidth - scrollWidth}px`;
      }
      if (this.verticalLegendContainer) {
        this.verticalLegendContainer.scrollTop = this.canvasContainer.scrollTop;
        this.verticalLegendContainer.style.height = `${
          this.canvasContainer.offsetHeight - scrollWidth}px`;
      }
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

  zoomIn = () => {
    if (this.canvas) {
      const {offsetWidth, offsetHeight} = this.canvas;
      this.canvas.style.width = `${
        offsetWidth + (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      this.canvas.style.height = `${
        offsetHeight + (offsetHeight * ZOOM_TICK_PERCENT)}px`;
      this.horisontalLegend.style.width = `${
        offsetWidth + (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      this.verticalLegend.style.height = `${
        offsetHeight + (offsetHeight * ZOOM_TICK_PERCENT)}px`;
      this.draw();
    }
  };

  zoomOut = () => {
    if (this.canvas) {
      const {offsetWidth, offsetHeight} = this.canvas;
      this.canvas.style.width = `${
        offsetWidth - (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      this.canvas.style.height = `${
        offsetHeight - (offsetHeight * ZOOM_TICK_PERCENT)}px`;
      this.horisontalLegend.style.width = `${
        offsetWidth + (offsetWidth * ZOOM_TICK_PERCENT)}px`;
      this.verticalLegend.style.height = `${
        offsetHeight + (offsetHeight * ZOOM_TICK_PERCENT)}px`;
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

  drawLegends = () => {
    const {columns, rows} = this.props;
    if (this.horisontalLegend && this.verticalLegend) {
      const verticalCtx = this.verticalLegend.getContext('2d');
      const horisontalCtx = this.horisontalLegend.getContext('2d');
      this.verticalLegend.width = this.verticalLegend.offsetWidth;
      this.verticalLegend.height = this.verticalLegend.offsetHeight;
      this.horisontalLegend.height = this.horisontalLegend.offsetHeight;
      this.horisontalLegend.width = this.horisontalLegend.offsetWidth;
      this.drawVerticalLegend(verticalCtx, rows);
      this.drawHorisontalLegend(horisontalCtx, columns);
    }
  };

  drawHorisontalLegend = (ctx, columns) => {
    ctx.clearRect(0, 0, this.horisontalLegend.width, this.horisontalLegend.height);
    ctx.fillStyle = DEFAULTS.background;
    ctx.fillRect(0, 0, this.horisontalLegend.width, this.horisontalLegend.height);
    ctx.fillStyle = DEFAULTS.strokeStyle;
    ctx.beginPath();
    ctx.moveTo(CANVAS_PADDING, CANVAS_PADDING);
    ctx.lineTo(this.horisontalLegend.width, CANVAS_PADDING);
    ctx.stroke();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    for (let column = 0; column < columns; column++) {
      ctx.fillText(
        column + 1,
        (this.bounds.xFrom + (this.cellDiameter / 2)) + column * this.cellDiameter,
        CANVAS_PADDING - 2
      );
    }
  };

  drawVerticalLegend = (ctx, rows) => {
    ctx.clearRect(0, 0, this.verticalLegend.width, this.verticalLegend.height);
    ctx.fillStyle = DEFAULTS.background;
    ctx.fillRect(0, 0, this.verticalLegend.width, this.verticalLegend.height);
    ctx.fillStyle = DEFAULTS.strokeStyle;
    ctx.beginPath();
    ctx.moveTo(CANVAS_PADDING, CANVAS_PADDING);
    ctx.lineTo(CANVAS_PADDING, this.verticalLegend.height);
    ctx.stroke();
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'center';
    for (let row = 0; row < rows; row++) {
      ctx.fillText(
        row + 1,
        CANVAS_PADDING / 2,
        (this.bounds.yFrom + (this.cellDiameter / 2)) + row * this.cellDiameter
      );
    }
  };

  draw = () => {
    if (this.canvas && this.canvas.getContext) {
      const {rows, columns} = this.props;
      if (rows && columns) {
        const ctx = this.canvas.getContext('2d');
        this.cleanUpCanvas();
        this.drawCells(ctx, rows, columns);
        this.drawLegends();
        this.synchronizeScrolls();
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

  render () {
    const {
      style
    } = this.props;
    return (
      <div style={{position: 'relative', overflow: 'hidden'}}>
        <div
          ref={this.initializeCanvasContainer}
          style={Object.assign({overflow: 'auto'}, style)}
        >
          <canvas
            style={{
              width: '100%',
              height: '100%'
            }}
            ref={this.initializeCanvas}
          />
          <div
            style={{
              width: '100%',
              height: CANVAS_PADDING,
              overflow: 'hidden',
              position: 'absolute',
              top: 0,
              left: 0
            }}
            ref={(canvas) => { this.horisontalLegendContainer = canvas; }}
          >
            <canvas
              style={{
                width: '100%',
                height: CANVAS_PADDING
              }}
              ref={(canvas) => this.initializeLegends(canvas, 'horisontal')}
            />
          </div>
          <div
            style={{
              width: CANVAS_PADDING,
              height: '100%',
              overflow: 'hidden',
              position: 'absolute',
              top: 0,
              left: 0
            }}
            ref={(canvas) => { this.verticalLegendContainer = canvas; }}
          >
            <canvas
              style={{
                width: CANVAS_PADDING,
                height: '100%'
              }}
              ref={(canvas) => this.initializeLegends(canvas, 'vertical')}
            />
          </div>
          <span
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: CANVAS_PADDING,
              height: CANVAS_PADDING,
              background: DEFAULTS.background
            }}
          />
        </div>
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
