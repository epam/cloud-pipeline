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

export const HeatmapTimelineChartEvents = {
  scaleChanged: 'SCALE_CHANGED',
  onItemClick: 'ON_ITEM_CLICK',
  onItemsHover: 'ON_ITEMS_HOVER'
};

const DEFAULT_OPTIONS = {
  coordinatesSystemColor: '#999999',
  coordinatesColor: '#666666'
};

class Renderer {
  datasets;
  options = {};
  container;
  canvas;
  canvasBox;
  _renderRequested = false;
  _requestReCheck = false;
  checkRAF;
  renderRAF;
  _listeners;
  _hoverInfo = {};
  timelineStart;
  yLabelsPadding = 40;
  xLabelsPadding = 40;

  _coordinatesSystemColor;
  _coordinatesColor;
  _selectionColor;

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
    this.xAxis.addScaleChangedListener(
      (fromZoom, fromDrag) => this.onScaleChange(fromZoom, fromDrag)
    );
    this.mouseMoveHandler = this.onMouseMove.bind(this);
    this.mouseUpHandler = this.onMouseUp.bind(this);
    this.mouseOutHandler = this.onMouseOut.bind(this);
    window.addEventListener('mousemove', this.mouseMoveHandler);
    window.addEventListener('mouseup', this.mouseUpHandler);
    window.addEventListener('mouseout', this.mouseOutHandler);
    this._listeners = [];
    this._hoveredItems = [];
    this._hoverEvents = true;
    this._coordinatesSystemColor = options.coordinatesSystemColor;
    this._coordinatesColor = options.coordinatesColor;
    this._selectionColor = options.selectionColor;
  }

  get coordinatesSystemColor () {
    return this._coordinatesSystemColor;
  }

  set coordinatesSystemColor (color) {
    if (color !== this._coordinatesSystemColor) {
      this._coordinatesSystemColor = color;
      this.xAxis.ticksChanged = true;
      this.requestRender();
    }
  }

  get coordinatesColor () {
    return this._coordinatesColor;
  }

  set coordinatesColor (color) {
    if (color !== this._coordinatesColor) {
      this._coordinatesColor = color;
      this.xAxis.ticksChanged = true;
      this.requestRender();
    }
  }

  get selectionColor () {
    return this._selectionColor;
  }

  set selectionColor (color) {
    if (color !== this._selectionColor) {
      this._selectionColor = color;
      this.requestRender();
    }
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

  get hoverEvents () {
    return this._hoverEvents;
  }

  set hoverEvents (hoverEvents) {
    if (hoverEvents !== this._hoverEvents) {
      this._hoverEvents = hoverEvents;
    }
  }

  get hoveredItems () {
    return this._hoveredItems;
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
          yEnd: height + CELL_HEIGHT,
          color: datasetGroup.color,
          max: datasetGroup.max,
          key: datasetGroup.key
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
    this.xAxis.setPixelsOffset(this.yLabelsPadding + 8, false);
    this.requestReCheck();
  }

  onScaleChange (fromZoom = false, fromDrag = false) {
    this.reportEvent(HeatmapTimelineChartEvents.scaleChanged, {
      from: this.from,
      to: this.to,
      fromZoom,
      fromDrag
    });
    this.requestRender();
  }

  unHoverItems () {
    this._hoverInfo = {};
    this.requestRender();
    this.reportEvent(HeatmapTimelineChartEvents.onItemsHover, undefined);
  }

  hoverItems (hoverInfo) {
    const {position = {}, ...info} = hoverInfo || {};
    const {
      gpuId,
      key,
      recordIdx
    } = info || {};
    const {x, y} = position;
    if (this.hoverEvents) {
      if (!hoverInfo) {
        return this.unHoverItems();
      }
      if (
        gpuId !== undefined &&
        key !== undefined &&
        recordIdx !== undefined && (
          gpuId !== this._hoverInfo.gpuId ||
          key !== this._hoverInfo.key ||
          x !== this._hoverInfo.position.x ||
          y !== this._hoverInfo.position.y
        )) {
        if (isNaN(hoverInfo.position.y)) {
          return this.unHoverItems();
        }
        this._hoverInfo = hoverInfo;
        this.reportEvent(HeatmapTimelineChartEvents.onItemsHover, {
          position,
          info
        });
      }
      return;
    }
    this.unHoverItems();
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
    const valueX = this.xAxis.getValueForPixel(x);
    const hitX = this.xAxis.valueFitsRange(valueX);
    return {
      x,
      y,
      valueX,
      xAxisCenter: this.xAxis.center,
      hit: !!hitX,
      hitX,
      hitCanvas: x >= 0 && x <= width && y >= 0 && y <= height,
      shiftKey
    };
  }

  getHoverInfo = (event) => {
    const {
      valueX,
      x,
      y,
      hit
    } = this.getMousePosition(event);
    if (hit && this.datasets) {
      const hoveredDataset = this.datasets.find(dataset => {
        return y >= dataset.y && y <= dataset.yEnd;
      });
      const hoveredRecordIdx = (hoveredDataset?.data || [])
        .filter(d => !d.hide)
        .findIndex(d => valueX >= d.xStart && valueX <= d.xEnd);
      if (!hoveredDataset || hoveredRecordIdx === undefined) {
        return undefined;
      }
      const position = {
        x,
        y: hoveredDataset.y + (hoveredDataset.yEnd - hoveredDataset.y)
      };
      return {
        gpuId: hoveredDataset.name,
        key: hoveredDataset.key,
        recordIdx: hoveredRecordIdx,
        color: hoveredDataset.color,
        position
      };
    }
    return undefined;
  };

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
    } else {
      this.hoverItems(this.getHoverInfo(event), event);
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
      this.xAxis.setRange(
        {from: start, to: end},
        {animate: false, fromZoom: true}
      );
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
      dataset.data.forEach((data) => {
        const from = this.xAxis.getPixelForValue(data.xStart);
        const to = this.xAxis.getPixelForValue(data.xEnd);
        const valuePercentage = data.value / dataset.max * 100;
        ctx.fillStyle = `${dataset.color}${percentToHexAlpha(valuePercentage)}`;
        const offset = Math.min(0, from - this.yLabelsPadding - 8);
        ctx.fillRect(
          correctPixels(Math.max(this.yLabelsPadding + 8, from)),
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
    ctx.strokeStyle = this.coordinatesSystemColor;
    ctx.beginPath();
    ctx.moveTo(
      correctPixels(this.yLabelsPadding + 8),
      correctPixels(this.dataHeight)
    );
    ctx.lineTo(
      correctPixels(this.canvas.width),
      correctPixels(this.dataHeight)
    );
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

  drawSelection () {
    if (this.dragEvent && this.dragEvent.shiftKey && this.dragEvent.shiftEnd) {
      const ctx = this.textCanvas.getContext('2d');
      ctx.fillStyle = this.selectionColor;
      ctx.clearRect(
        correctPixels(this.yLabelsPadding + 8),
        0,
        correctPixels(this.width - this.yLabelsPadding),
        correctPixels(this.dataHeight)
      );
      const delta = this.dragEvent.shiftEnd.x - this.dragEvent.x;
      const x = delta > 0
        ? this.dragEvent.x
        : this.dragEvent.shiftEnd.x;
      const start = Math.max(x, this.yLabelsPadding + 8);
      const offset = Math.min(
        0,
        this.dragEvent.shiftEnd.x - this.yLabelsPadding - 8
      );
      ctx.beginPath();
      ctx.fillRect(
        correctPixels(start),
        0,
        correctPixels(Math.abs(delta) + offset),
        correctPixels(this.dataHeight)
      );
      ctx.stroke();
    }
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
    this.datasets.forEach(dataset => {
      context.save();
      context.fillStyle = this.coordinatesColor;
      context.font = `${this.xAxis.fontSize * dpr}pt ${this.xAxis.fontFamily}`;
      context.fillText(
        dataset.name,
        correctPixels(this.yLabelsPadding - 5),
        correctPixels(dataset.yEnd)
      );
      context.restore();
    });
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
        color: this.coordinatesColor
      }
    );
    context.restore();
  }

  draw (ctx) {
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    this.drawDatasets(ctx);
    this.drawGridLines(ctx);
    this.drawText();
    this.drawSelection();
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
      container.style.minHeight = `50px`;
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
      canvas.height = dpr * 50;
      canvas.style.height = `${50}px`;
      canvas.addEventListener('wheel', this.onMouseWheel.bind(this));
      canvas.addEventListener('mousedown', this.onMouseDown.bind(this));
      window.addEventListener('mouseup', this.onMouseUp.bind(this));
      canvas.addEventListener('mousemove', this.onMouseMove.bind(this));
      this.textCanvas = textCanvas;
      const textContext = textCanvas.getContext('2d');
      this.xAxis.attachTextContext(textContext);
      this.xAxis.setPixelsOffset(this.yLabelsPadding + 8, false);
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
          this.width = Math.max(width || 0, 1);
          canvas.width = dpr * width;
          canvas.height = dpr * (this.dataHeight + this.xLabelsPadding);
          canvas.style.width = `${width}px`;
          canvas.style.height = `${(this.dataHeight || 0) + this.xLabelsPadding}px`;
          container.style.minHeight = canvas.style.height;
          width = container.clientWidth;
          height = container.clientHeight;
          textCanvas.width = dpr * width;
          textCanvas.height = dpr * (this.dataHeight + this.xLabelsPadding);
          textCanvas.style.width = `${width}px`;
          textCanvas.style.height = `${(this.dataHeight || 0) + this.xLabelsPadding}px`;
          this.canvasBox = canvas.getBoundingClientRect();
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
