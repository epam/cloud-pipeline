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

import RendererAxis from '../timeline-chart/renderer/axis';
import {
  parseDate,
  drawVisibleLabels,
  percentToHexAlpha,
  correctPixels
} from './utils';

const dpr = window.devicePixelRatio || 1;
const CELL_HEIGHT = 10;
const ROW_MARGIN = 5;
const LEFT_PADDING = 90;

export const HeatmapTimelineChartEvents = {
  scaleChanged: 'SCALE_CHANGED'
};

const DEFAULT_OPTIONS = {
  coordinatesSystemColor: '#999999',
  coordinatesColor: '#666666',
  backgroundColor: '#ffffff'
};

class Renderer {
  datasets;
  grid;
  options = {};
  container;
  canvas;
  canvasBox;
  _renderRequested = false;
  _requestReCheck = false;
  checkRAF;
  renderRAF;
  _listeners;

  scaleFactor = 1;

  constructor (options = {}) {
    this.options = Object.assign(DEFAULT_OPTIONS, options);
    this.xAxis = new RendererAxis(
      (item) => {
        return item.xStart + ((item.xEnd - item.xStart) / 2);
      },
      {
        extendArea: 0.1,
        timeline: true,
        minimumRatio: 50.0 // 50 pixels for 1 second,
      }
    );
    this.xAxis.addScaleChangedListener(this.onScaleChange.bind(this, true));
    this.mouseMoveHandler = this.onMouseMove.bind(this);
    this.mouseUpHandler = this.onMouseUp.bind(this);
    this.mouseOutHandler = this.onMouseOut.bind(this);
    window.addEventListener('mousemove', this.mouseMoveHandler);
    window.addEventListener('mouseup', this.mouseUpHandler);
    window.addEventListener('mouseout', this.mouseOutHandler);
    this._listeners = [];
  }

  get from () {
    return this.xAxis.from + this.timelineStart;
  }

  set from (from) {
    const {
      unix
    } = parseDate(from) || {};
    if (
      this.xAxis.setRange({
        from: unix !== undefined ? unix - this.timelineStart : undefined
      })
    ) {
      this.requestRender();
    }
  }

  get to () {
    return this.xAxis.to + this.timelineStart;
  }

  set to (to) {
    const {
      unix
    } = parseDate(to) || {};
    if (
      this.xAxis.setRange({
        to: unix !== undefined ? unix - this.timelineStart : undefined
      })
    ) {
      this.requestRender();
    }
  }

  registerDatasets (datasets) {
    if (!datasets) {
      return;
    }
    let flatData = [];
    let height = 0;
    datasets.forEach((datasetGroup) => {
      datasetGroup.data.forEach((data) => {
        flatData.push({
          ...data,
          y: height,
          color: datasetGroup.color,
          max: datasetGroup.max
        });
        height = height + CELL_HEIGHT + ROW_MARGIN;
      });
    });
    const processed = flatData
      .slice()
      .map((dataset, index) => {
        const {
          key = index,
          records,
          ...rest
        } = dataset;
        return {
          ...rest,
          index,
          key,
          data: (dataset.records || [])
            .map((item) => {
              if (item === undefined) {
                return undefined;
              }
              const {
                value,
                start,
                end,
                ...itemRest
              } = item;
              const {
                unix: xStart,
                date: dateValueStart
              } = parseDate(start) || {};
              const {
                unix: xEnd,
                date: dateValueEnd
              } = parseDate(end) || {};
              return {
                ...itemRest,
                xStart,
                xEnd,
                dateValueStart,
                dateValueEnd,
                value,
                start,
                end
              };
            })
            .filter((item) => item === undefined ||
              (item.xStart !== undefined && item.xEnd !== undefined)
            )
        };
      });
    let minimum = Infinity;
    for (let processedDataset of processed) {
      for (let data of processedDataset.data) {
        if (data && minimum > data.xStart) {
          minimum = data.xStart;
        }
      }
    }
    processed.forEach((dataset) => {
      dataset.data.forEach((data, index, array) => {
        if (data) {
          data.xStart = data.xStart - minimum;
          data.xEnd = data.xEnd - minimum;
        }
      });
    });
    this.timelineStart = minimum;
    this.datasets = processed;
    this.dataHeight = height;
    this.xAxis.update(
      this.datasets,
      {
        shift: this.timelineStart
      }
    );
    this.xAxis.setPixelsOffset(50, false);
    this.requestReCheck();
  }

  onScaleChange () {
    this.reportEvent(HeatmapTimelineChartEvents.scaleChanged, {
      from: this.from,
      to: this.to
    });
    this.requestRender();
  }

  addEventListener (event, listener) {
    this.removeEventListener(event, listener);
    this._listeners.push({event, listener});
  }

  removeEventListener (event, listener) {
    this._listeners = this._listeners
      .filter((config) => config.event !== event || config.listener !== listener);
  }

  reportEvent (event, payload) {
    this._listeners
      .filter((config) => config.event === event && typeof config.listener === 'function')
      .forEach((config) => {
        config.listener(payload);
      });
  }

  detachFromContainer () {
    cancelAnimationFrame(this.checkRAF);
    cancelAnimationFrame(this.renderRAF);
    this.width = 1;
    this.height = 1;
    this.xAxis.detachTextContext();
    if (this.canvas && this.container) {
      this.container.removeChild(this.canvas);
    }
    if (this.textCanvas && this.container) {
      this.container.removeChild(this.textCanvas);
    }
    this.container = undefined;
    this.canvas = undefined;
    this.textCanvas = undefined;
    this.canvasBox = undefined;
  }

  destroy () {
    this.detachFromContainer();
    this._listeners = [];
    window.removeEventListener('mousemove', this.mouseMoveHandler);
    window.removeEventListener('mouseup', this.mouseUpHandler);
    window.removeEventListener('mouseout', this.mouseOutHandler);
  }

  getMousePosition (event) {
    const {
      clientX,
      clientY,
      shiftKey
    } = event;
    let x = clientX;
    let y = clientY;
    let width = this.width;
    let height = this.height;
    if (this.canvasBox) {
      const {
        x: canvasX,
        y: canvasY,
        width: canvasWidth,
        height: canvasHeight
      } = this.canvasBox;
      x = clientX - canvasX;
      y = clientY - canvasY;
      width = canvasWidth;
      height = canvasHeight;
    }
    return {
      x,
      y,
      xAxisCenter: this.xAxis.center,
      yAxisCenter: this.height / 2,
      hitCanvas: x >= 0 && x <= width && y >= 0 && y <= height,
      shiftKey
    };
  }

  onMouseWheel (event) {
    const info = this.getMousePosition(event);
    if (this.xAxis.zoomBy(-event.deltaY / 100, info.valueX)) {
      event.stopPropagation();
      event.preventDefault();
    }
  }

  onMouseDown (event) {
    if (!this.canvas) {
      return;
    }
    this.canvasBox = this.canvas.getBoundingClientRect();
    const info = this.getMousePosition(event);
    this.dragEvent = {
      ...info
    };
    event.preventDefault();
  }

  onMouseMove (event) {
    if (this.dragEvent) {
      const info = this.getMousePosition(event);
      const dx = info.x - this.dragEvent.x;
      const dy = info.y - this.dragEvent.y;
      this.dragEvent.moved = this.dragEvent.moved || false || dx > 0 || dy > 0;
      const valueDX = this.xAxis.getValueSizeForPixelSize(-dx);
      if (this.dragEvent.shiftKey) {
        this.dragEvent.shiftEnd = info;
        this.requestRender();
        event.preventDefault();
      } else {
        this.xAxis.center = this.dragEvent.xAxisCenter + valueDX;
      }
    }
  }

  onMouseUp (event) {
    if (this.dragEvent && this.dragEvent.shiftKey && this.dragEvent.shiftEnd) {
      let {
        valueX: start,
        shiftEnd = {}
      } = this.dragEvent;
      let {
        valueX: end
      } = shiftEnd;
      if (start > end) {
        const tmp = end;
        end = start;
        start = tmp;
      }
      this.xAxis.setRange({from: start, to: end}, {animate: true});
    } else if (this.dragEvent && this.dragEvent.moved) {
      this.onMouseMove(event);
    }
    this.dragEvent = undefined;
  }

  onMouseOut (event) {
    this.onMouseUp(event);
  }

  onResize () {
    this.xAxis.resize(this.width, false);
    this.requestRender();
  }

  drawDatasets (ctx) {
    if (!this.datasets?.length) {
      return;
    }
    ctx.save();
    this.datasets.forEach((dataset) => {
      dataset.data.forEach((data, dataIdx) => {
        const from = this.xAxis.getPixelForValue(data.xStart);
        const to = this.xAxis.getPixelForValue(data.xEnd);
        const valuePercentage = data.value / dataset.max * 100;
        ctx.fillStyle = `${dataset.color}${percentToHexAlpha(valuePercentage)}`;
        const offset = Math.min(0, from - 50);
        ctx.fillRect(
          correctPixels(Math.max(50, from)),
          correctPixels(dataset.y + 0.5),
          correctPixels(Math.max(0, (to - from + offset))),
          correctPixels(CELL_HEIGHT)
        );
      });
    });
    ctx.restore();
  }

  drawGridLines (ctx) {
    ctx.save();
    ctx.strokeStyle = this.options.coordinatesSystemColor;
    ctx.beginPath();
    // TODO: refactor leftPadding
    ctx.moveTo(correctPixels(LEFT_PADDING - 40), correctPixels(this.dataHeight));
    ctx.lineTo(correctPixels(this.canvas.width), correctPixels(this.dataHeight));
    ctx.stroke();
    this.xAxis.ticks.forEach((tick) => {
      if (this.xAxis.valueFitsRange(tick.value)) {
        ctx.beginPath();
        const x = this.xAxis.getPixelForValue(tick.value);
        const size = tick.main ? 5 : 3;
        ctx.moveTo(correctPixels(x + 0.5), correctPixels(this.dataHeight + 0.5));
        ctx.lineTo(correctPixels(x + 0.5), correctPixels(this.dataHeight + (size * 2) + 0.5));
        ctx.stroke();
      }
    });
    ctx.restore();
  }

  drawText () {
    if (!this.textCanvas || !this.xAxis.ticksChanged) {
      return;
    }
    this.xAxis.ticksChanged = false;
    const context = this.textCanvas.getContext('2d');
    context.clearRect(0, 0, this.textCanvas.width, this.textCanvas.height);
    if (
      !this.datasets ||
      !this.datasets.length
    ) {
      return;
    }
    context.save();
    drawVisibleLabels(
      (this.xAxis.ticks || []),
      {
        context,
        axis: this.xAxis,
        getElementPosition: (tick) => ({
          x: this.xAxis.getPixelForValue(tick.value),
          y: this.dataHeight + 15
        }),
        color: this.options.coordinatesColor
      }
    );
    context.restore();
  }

  draw (ctx) {
    ctx.clearRect(-0.5, -0.5, this.canvas.width, this.canvas.height);
    this.drawDatasets(ctx);
    this.drawGridLines(ctx);
    this.drawText();
  }

  requestRender () {
    this._renderRequested = true;
  }

  requestReCheck () {
    this._requestReCheck = true;
  }

  attachToContainer (container) {
    this.detachFromContainer();
    this.container = container;
    if (container) {
      container.style.position = 'relative';
      container.style.overflow = 'hidden';
      container.style.minHeight = `300px`;
      const attachCanvas = (z = 0) => {
        const canvas = document.createElement('canvas');
        canvas.style.position = 'absolute';
        canvas.style.top = '0px';
        canvas.style.bottom = '0px';
        canvas.style.zIndex = `${z}`;
        container.appendChild(canvas);
        return canvas;
      };
      const textCanvas = attachCanvas();
      const canvas = attachCanvas(1);
      this.canvas = canvas;
      canvas.height = dpr * 300;
      canvas.style.height = `${300}px`;
      canvas.addEventListener('wheel', this.onMouseWheel.bind(this));
      canvas.addEventListener('mousedown', this.onMouseDown.bind(this));
      window.addEventListener('mouseup', this.onMouseUp.bind(this));
      canvas.addEventListener('mousemove', this.onMouseMove.bind(this));
      this.textCanvas = textCanvas;
      const textContext = textCanvas.getContext('2d');
      this.xAxis.attachTextContext(textContext);
      this.xAxis.setPixelsOffset(50, false);
      const ctx = canvas.getContext('2d');
      // ctx.scale(dpr, dpr);
      let width, height;
      const check = () => {
        if (
          this._requestReCheck ||
          container.clientHeight !== height ||
          container.clientWidth !== width
        ) {
          this._requestReCheck = false;
          width = container.clientWidth;
          height = container.clientHeight;
          this.width = Math.max(width || 0, 1);
          canvas.width = dpr * width;
          canvas.height = dpr * (this.dataHeight + 40);
          canvas.style.width = `${width}px`;
          canvas.style.height = `${(this.dataHeight || 0) + 40}px`;
          textCanvas.width = dpr * width;
          textCanvas.height = dpr * (this.dataHeight + 40);
          textCanvas.style.width = `${width}px`;
          textCanvas.style.height = `${(this.dataHeight || 0) + 40}px`;
          this.canvasBox = canvas.getBoundingClientRect();
          // ctx.setTransform(1, 0, 0, 1, 0, 0);
          ctx.translate(0.5, 0.5);
          this.onResize();
        }
        this.checkRAF = requestAnimationFrame(check);
      };
      check();
      const render = () => {
        if (this._renderRequested) {
          this._renderRequested = false;
          this.draw(ctx);
        }
        this.renderRAF = requestAnimationFrame(render);
      };
      render();
    }
    return this;
  }
}

export default Renderer;
