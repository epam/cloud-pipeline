import RendererAxis from './axis';
import {
  initializeProgram,
  buildPathVAO,
  buildLineVAO,
  buildCircleVAO,
  buildStrokeCircleVAO,
  buildOrthoMatrix,
  buildRectangleVAO,
  useCircleProgram,
  useDefaultProgram,
  usePathProgram,
  drawCircle,
  drawPath,
  drawRectangle,
  IDENTITY_MATRIX,
  pathProgram,
  circleProgram,
  defaultProgram
} from './utilities';
import {
  drawVisibleLabels,
  buildLineHelpers
} from './draw-utilities';
import {parseColor} from '../../../../themes/utilities/color-utilities';
import {isDate, isNumber, parseDate} from './date-utilities';

const dpr = window.devicePixelRatio || 1;

const SEARCH_MODE = {
  closest: 'closest',
  vertical: 'vertical'
};

const DEFAULT_LINE_WIDTH = 2.0;
const DEFAULT_CHART_LINE_COLOR = '#108ee9';
const SEARCH_AREA_PX = 100;
const DEFAULT_SEARCH_MODE = SEARCH_MODE.closest;

const TimelineChartEvents = {
  scaleChanged: 'SCALE_CHANGED',
  onItemClick: 'ON_ITEM_CLICK',
  onItemsHover: 'ON_ITEMS_HOVER'
};

function hoveredItemsEqual (a, b) {
  const {
    dataset: aDataset,
    item: aItem
  } = a;
  const {
    dataset: bDataset,
    item: bItem
  } = b;
  return aDataset === bDataset && aItem === bItem;
}

function hoveredItemsSetsAreEqual (aSet, bSet) {
  return ((aSet || []).length === 0 && (bSet || []).length === 0) ||
    (
      !aSet.some((a) => !bSet.find((b) => hoveredItemsEqual(a, b))) &&
      !bSet.some((b) => !aSet.find((a) => hoveredItemsEqual(a, b)))
    );
}

class TimelineChartRenderer {
  /**
   * @typedef {Object} TimelineChartOptions
   * @property {SEARCH_MODE} [searchMode=closest]
   * @property {string} [coordinatesSystemColor=#999999]
   * @property {string} [coordinatesColor=#666666]
   * @property {string} [backgroundColor=#333333]
   * @property {string} [selectionColor=#666666]
   * @property {boolean} [adaptValueAxis=true]
   * @property {boolean} [showHoveredCoordinateLine=false]
   * @property {boolean} [showHorizontalLines=false]
   */
  /**
   * @param {TimelineChartOptions} [options]
   */
  constructor (options = {}) {
    const {
      searchMode = DEFAULT_SEARCH_MODE,
      coordinatesSystemColor = '#999999',
      coordinatesColor = '#666666',
      backgroundColor = '#ffffff',
      selectionColor = '#666666',
      adaptValueAxis = true,
      showHoveredCoordinateLine = false
    } = options || {};
    this.options = options || {};
    this.container = undefined;
    this.canvas = undefined;
    this.textCanvas = undefined;
    this.pathProgram = undefined;
    this.defaultProgram = undefined;
    this.circleProgram = undefined;
    this._hoveredItems = [];
    this.width = 1;
    this.height = 1;
    this.datasets = [];
    this.datasetsOptions = [];
    this.timelineStart = undefined;
    this.datasetBuffers = [];
    this.circleBuffer = undefined;
    this.lineBuffer = undefined;
    this.circleStrokeBuffer = undefined;
    this._searchMode = searchMode;
    this._coordinatesSystemColor = coordinatesSystemColor;
    this._coordinatesColor = coordinatesColor;
    this._selectionColor = selectionColor;
    this._backgroundColor = backgroundColor;
    this._adaptValueAxis = adaptValueAxis;
    this._showHoveredCoordinateLine = showHoveredCoordinateLine;
    this._hoverEvents = true;
    this.xAxis = new RendererAxis(
      (item) => item.x,
      {
        extendArea: 0.1,
        timeline: true,
        minimumRatio: 50.0 // 50 pixels for 1 second,
      }
    );
    this.yAxis = new RendererAxis(
      (item) => item.y,
      {
        reverted: true,
        extendArea: true,
        stickToZero: true,
        vertical: true
      });
    this.xAxis.addScaleChangedListener(this.onScaleChange.bind(this, true));
    this.yAxis.addScaleChangedListener(this.onScaleChange.bind(this));
    this.mouseMoveHandler = this.onMouseMove.bind(this);
    this.mouseUpHandler = this.onMouseUp.bind(this);
    this.mouseOutHandler = this.onMouseOut.bind(this);
    window.addEventListener('mousemove', this.mouseMoveHandler);
    window.addEventListener('mouseup', this.mouseUpHandler);
    window.addEventListener('mouseout', this.mouseOutHandler);
    this._listeners = [];
  }

  get searchMode () {
    return this._searchMode;
  }

  set searchMode (searchMode) {
    if (searchMode !== this._searchMode) {
      this._searchMode = searchMode;
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

  get backgroundColor () {
    return this._backgroundColor;
  }

  set backgroundColor (color) {
    if (color !== this._backgroundColor) {
      this._backgroundColor = color;
      this.xAxis.ticksChanged = true;
      this.yAxis.ticksChanged = true;
      this.requestRender();
    }
  }

  get coordinatesSystemColor () {
    return this._coordinatesSystemColor;
  }

  set coordinatesSystemColor (color) {
    if (color !== this._coordinatesSystemColor) {
      this._coordinatesSystemColor = color;
      this.xAxis.ticksChanged = true;
      this.yAxis.ticksChanged = true;
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
      this.yAxis.ticksChanged = true;
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

  get adaptValueAxis () {
    return this._adaptValueAxis;
  }

  set adaptValueAxis (adapt) {
    if (adapt !== this._adaptValueAxis) {
      this._adaptValueAxis = adapt;
      this.adaptYAxisForVisibleElements();
    }
  }

  get showHoveredCoordinateLine () {
    return this._showHoveredCoordinateLine;
  }

  set showHoveredCoordinateLine (showHoveredCoordinateLine) {
    if (showHoveredCoordinateLine !== this._showHoveredCoordinateLine) {
      this._showHoveredCoordinateLine = showHoveredCoordinateLine;
      this.requestRender();
    }
  }

  get hoveredItems () {
    return this._hoveredItems;
  }

  attachToContainer (container) {
    this.detachFromContainer();
    this.container = container;
    if (container) {
      container.style.position = 'relative';
      container.style.overflow = 'hidden';
      const attachCanvas = (z = 0) => {
        const canvas = document.createElement('canvas');
        canvas.style.position = 'absolute';
        canvas.style.top = '0px';
        canvas.style.bottom = '0px';
        canvas.style.zIndex = `${z}`;
        container.appendChild(canvas);
        return canvas;
      };
      const canvas = attachCanvas();
      canvas.addEventListener('wheel', this.onMouseWheel.bind(this));
      canvas.addEventListener('mousedown', this.onMouseDown.bind(this));
      canvas.addEventListener('mouseout', this.onMouseOut.bind(this));
      const textCanvas = attachCanvas(1);
      textCanvas.style.pointerEvents = 'none';
      this.canvas = canvas;
      this.textCanvas = textCanvas;
      const context = canvas.getContext('webgl2', {antialias: true});
      const textContext = textCanvas.getContext('2d');
      this.pathProgram = initializeProgram(context, pathProgram);
      this.defaultProgram = initializeProgram(context, defaultProgram);
      this.circleProgram = initializeProgram(context, circleProgram);
      this.lineBuffer = buildLineVAO(this.pathProgram);
      this.rectangleBuffer = buildRectangleVAO(this.defaultProgram);
      this.circleBuffer = buildCircleVAO(this.circleProgram, 10);
      this.circleStrokeBuffer = buildStrokeCircleVAO(this.circleProgram, 10);
      const {
        verticalLine,
        horizontalLine
      } = buildLineHelpers(this.pathProgram, this.lineBuffer);
      this.verticalLine = verticalLine;
      this.horizontalLine = horizontalLine;
      this.xAxis.attachTextContext(textContext);
      this.yAxis.attachTextContext(textContext);
      this.xAxis.updateLargestLabelSize();
      this.yAxis.updateLargestLabelSize();
      this.xAxis.setPixelsOffset(20 + this.yAxis.largestLabelSize, false);
      this.yAxis.setPixelsOffset(20 + this.xAxis.largestLabelSize, false);
      let width, height;
      const check = () => {
        if (container.clientWidth !== width || container.clientHeight !== height) {
          width = container.clientWidth;
          height = container.clientHeight;
          this.width = Math.max(width || 0, 1);
          this.height = Math.max(height || 0, 1);
          canvas.width = dpr * width;
          canvas.height = dpr * height;
          canvas.style.width = `${width}px`;
          canvas.style.height = `${height}px`;
          textCanvas.width = dpr * width;
          textCanvas.height = dpr * height;
          textCanvas.style.width = `${width}px`;
          textCanvas.style.height = `${height}px`;
          this.canvasBox = canvas.getBoundingClientRect();
          this.onResize();
        }
        this.checkRAF = requestAnimationFrame(check);
      };
      check();
      const render = () => {
        if (this._renderRequested) {
          this._renderRequested = false;
          this.draw();
        }
        this.renderRAF = requestAnimationFrame(render);
      };
      render();
    }
    return this;
  }

  detachFromContainer () {
    cancelAnimationFrame(this.checkRAF);
    cancelAnimationFrame(this.renderRAF);
    this.width = 1;
    this.height = 1;
    this.xAxis.detachTextContext();
    this.yAxis.detachTextContext();
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
    this.pathProgram = undefined;
    this.defaultProgram = undefined;
    this.circleProgram = undefined;
    this.datasetBuffers = undefined;
    this.circleBuffer = undefined;
    this.lineBuffer = undefined;
    this.rectangleBuffer = undefined;
    this.circleStrokeBuffer = undefined;
    this.verticalLine = undefined;
    this.horizontalLine = undefined;
    return this;
  }

  requestRender () {
    this._renderRequested = true;
  }

  /**
   * @param {string} event
   * @param {function} listener
   */
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

  destroy () {
    this.detachFromContainer();
    this._listeners = [];
    window.removeEventListener('mousemove', this.mouseMoveHandler);
    window.removeEventListener('mouseup', this.mouseUpHandler);
    window.removeEventListener('mouseout', this.mouseOutHandler);
  }

  adaptYAxisForVisibleElements () {
    if (this._adaptValueAxis) {
      let previous;
      let min, max;
      const checkValue = (value) => {
        if (min === undefined || min > value) {
          min = value;
        }
        if (max === undefined || max < value) {
          max = value;
        }
      };
      const checkInterpolatedValue = (pointA, pointB, x) => {
        if (!pointA || !pointB) {
          return [];
        }
        let {
          x: aX,
          y: aY
        } = pointA;
        let {
          x: bX,
          y: bY
        } = pointB;
        if (aX > bX) {
          const tmpX = aX;
          const tmpY = aY;
          aX = bX;
          aY = bY;
          bX = tmpX;
          bY = tmpY;
        }
        if (aX === bX && aX === x) {
          checkValue(aY);
          checkValue(bY);
        } else if (aX <= x && x <= bX) {
          const ratio = (x - aX) / (bX - aX);
          checkValue(aY + ratio * (bY - aY));
        }
      };
      this.datasets.forEach((dataset) => {
        previous = undefined;
        const {
          data = []
        } = dataset;
        data.forEach((item) => {
          if (item) {
            if (this.xAxis.valueFitsRange(item.x)) {
              checkValue(item.y);
            }
            if (previous) {
              checkInterpolatedValue(previous, item, this.xAxis.from);
              checkInterpolatedValue(previous, item, this.xAxis.to);
            }
          }
          previous = item;
        });
      });
      if (min !== undefined && max !== undefined) {
        this.yAxis.setRange({from: min, to: max}, {extend: true, animate: false});
      }
    } else {
      this.yAxis.clearRange();
    }
  }

  unHoverItems () {
    if (!hoveredItemsSetsAreEqual([], this._hoveredItems)) {
      this._hoveredItems = [];
      this.requestRender();
      this.reportEvent(TimelineChartEvents.onItemsHover, undefined);
    }
  }

  hoverItems (items) {
    if (this.hoverEvents) {
      if (!hoveredItemsSetsAreEqual(items, this._hoveredItems)) {
        this._hoveredItems = (items || []).slice();
        if (items && items.length > 0) {
          const xValues = [...new Set(items.map((item) => item.item.x))]
            .map((x) => this.xAxis.getPixelForValue(x));
          const yValues = [...new Set(items.map((item) => item.item.y))]
            .map((y) => this.yAxis.getPixelForValue(y));
          const x = xValues.reduce((r, c) => (r + c), 0) / xValues.length;
          const y = yValues.reduce((r, c) => (r + c), 0) / yValues.length;
          this.reportEvent(TimelineChartEvents.onItemsHover, {
            position: {
              x,
              y
            },
            items
          });
        } else {
          this._hoveredItems = [];
          this.reportEvent(TimelineChartEvents.onItemsHover, undefined);
        }
        this.requestRender();
      }
      return;
    }
    this.unHoverItems();
  }

  onScaleChange (changeYAxis = false) {
    if (changeYAxis) {
      this.adaptYAxisForVisibleElements();
    }
    this.unHoverItems();
    this.reportEvent(TimelineChartEvents.scaleChanged, {
      from: this.from,
      to: this.to
    });
    this.requestRender();
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
    const valueY = this.yAxis.getValueForPixel(y);
    const hitX = this.xAxis.valueFitsRange(valueX);
    const hitY = this.yAxis.valueFitsRange(valueY);
    return {
      x,
      y,
      valueX,
      valueY,
      xAxisCenter: this.xAxis.center,
      yAxisCenter: this.yAxis.center,
      hit: hitX && hitY,
      hitX,
      hitY,
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
    if (info.hit) {
      this.dragEvent = {
        ...info
      };
      event.preventDefault();
    }
  }

  onMouseMove (event) {
    if (this.dragEvent) {
      const info = this.getMousePosition(event);
      const dx = info.x - this.dragEvent.x;
      const dy = info.y - this.dragEvent.y;
      this.dragEvent.moved = this.dragEvent.moved || false || dx > 0 || dy > 0;
      const valueDX = this.xAxis.getValueSizeForPixelSize(-dx);
      if (this.dragEvent.moved) {
        this.unHoverItems();
      }
      if (this.dragEvent.shiftKey) {
        this.dragEvent.shiftEnd = info;
        this.requestRender();
        event.preventDefault();
      } else {
        this.xAxis.center = this.dragEvent.xAxisCenter + valueDX;
      }
    } else {
      this.hoverItems(this.getItemsByMouseEvent(event), event);
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
    } else if (this.dragEvent) {
      this.unHoverItems();
      const {
        x,
        y
      } = this.dragEvent;
      const item = this.getItemsByMouseEvent(event)[0];
      if (item) {
        this.reportEvent(
          TimelineChartEvents.onItemClick,
          {
            item,
            x,
            y
          }
        );
      }
    }
    this.dragEvent = undefined;
  }

  onMouseOut (event) {
    this.onMouseUp(event);
    this.unHoverItems();
  }

  searchClosestItem (pixelX, pixelY) {
    const getItemDistance = (item) => {
      const xx = this.xAxis.getPixelForValue(item.x);
      const yy = this.yAxis.getPixelForValue(item.y);
      return Math.sqrt((xx - pixelX) ** 2 + (yy - pixelY) ** 2);
    };
    const candidates = [];
    (this.datasets || []).forEach((dataset) => {
      const {
        data = []
      } = dataset;
      for (let i = 0; i < data.length; i += 1) {
        const item = data[i];
        if (item) {
          const xDistance = Math.abs(this.xAxis.getPixelForValue(item.x) - pixelX);
          if (
            xDistance < SEARCH_AREA_PX && this.xAxis.valueFitsRange(item.x)
          ) {
            candidates.push({
              item,
              dataset,
              distance: getItemDistance(item)
            });
          }
        }
      }
    });
    candidates.sort((a, b) => a.distance - b.distance);
    if (candidates.length > 0) {
      return {
        dataset: candidates[0].dataset,
        item: candidates[0].item
      };
    }
    return undefined;
  }

  searchItemsByXValue (xValue) {
    const candidates = [];
    const SEARCH_AREA = this.xAxis.getValueSizeForPixelSize(SEARCH_AREA_PX);
    const getItemDistance = (item) => {
      return Math.abs(item.x - xValue);
    };
    (this.datasets || []).forEach((dataset) => {
      const {
        data = []
      } = dataset;
      for (let i = 0; i < data.length; i += 1) {
        const item = data[i];
        if (item) {
          const distance = getItemDistance(item);
          if (distance < SEARCH_AREA && this.xAxis.valueFitsRange(item.x)) {
            candidates.push({
              item,
              dataset,
              distance: getItemDistance(item)
            });
          }
        }
      }
    });
    candidates.sort((a, b) => a.distance - b.distance);
    if (candidates.length > 0) {
      const x = candidates[0].item.x;
      return candidates
        .filter((item) => item.item.x === x)
        .map(({item, dataset}) => ({
          item,
          dataset
        }));
    }
    return [];
  }

  getItemsByMouseEvent = (event) => {
    const {
      x,
      valueX,
      y,
      hit
    } = this.getMousePosition(event);
    const result = [];
    if (hit) {
      switch (this.searchMode) {
        case SEARCH_MODE.closest:
          const item = this.searchClosestItem(x, y);
          if (item) {
            result.push(item);
          }
          break;
        case SEARCH_MODE.vertical:
        default:
          const items = this.searchItemsByXValue(valueX);
          result.push(...items);
          break;
      }
    }
    return result;
  };

  onResize () {
    this.xAxis.resize(this.width, false);
    this.yAxis.resize(this.height, false);
    this.requestRender();
  }

  registerDatasets (datasets = []) {
    const processed = datasets
      .slice()
      .map((dataset, index) => {
        const {
          data = [],
          key = index,
          ...rest
        } = dataset;
        return {
          ...rest,
          index,
          key,
          data: data
            .map((item) => {
              if (item === undefined || !isDate(item.date) || !isNumber(item.value)) {
                return undefined;
              }
              const {
                value,
                date,
                ...itemRest
              } = item;
              const {
                unix: x,
                date: dateValue
              } = parseDate(date) || {};
              return {
                ...itemRest,
                value,
                y: Number(value),
                date,
                x,
                dateValue
              };
            })
            .filter((item) => item === undefined || item.x !== undefined)
        };
      });
    let minimum = Infinity;
    for (let processedDataset of processed) {
      for (let item of processedDataset.data) {
        if (item && minimum > item.x) {
          minimum = item.x;
        }
      }
    }
    processed.forEach((dataset) => {
      dataset.data.forEach((item, index, array) => {
        if (item) {
          item.x = item.x - minimum;
        }
      });
    });
    this.timelineStart = minimum;
    this.datasets = processed;
    this.xAxis.update(
      this.datasets,
      {
        shift: this.timelineStart
      }
    );
    this.yAxis.update(this.datasets);
    this.xAxis.setPixelsOffset(20 + this.yAxis.largestLabelSize, false);
    this.yAxis.setPixelsOffset(20 + this.xAxis.largestLabelSize, false);
    this.datasetBuffers = [];
    this.datasets.forEach((dataset) => {
      this.datasetBuffers.push({
        dataset,
        buffers: this.buildDatasetBuffers(dataset)
      });
    });
    this.coordinatesChanged = true;
    this.requestRender();
    this.adaptYAxisForVisibleElements();
    return this;
  }

  registerDatasetsOptions (datasetsOptions = []) {
    this.datasetsOptions = datasetsOptions.slice().map((datasetOptions, index) => {
      const {
        key = index,
        ...rest
      } = datasetOptions || {};
      return {
        ...rest,
        key,
        index
      };
    });
    this.requestRender();
    return this;
  }

  getDatasetOptions (dataset) {
    const {
      key
    } = dataset;
    return this.datasetsOptions.find((options) => options.key === key) || {};
  }

  buildDatasetBuffers (dataset) {
    const {
      data = []
    } = dataset;
    const sliced = data.reduce((result, item) => {
      let last = result.length > 0 ? result[result.length - 1] : undefined;
      if (item === undefined && last && last.length > 0) {
        return [...result, []];
      }
      if (item && !last) {
        last = [];
        result.push(last);
      }
      if (item) {
        last.push(item);
      }
      return result;
    }, []);
    const blocks = [];
    sliced.forEach((sliceItem) => {
      const maxItemsPerBlock = 1000;
      for (let i = 0; i < sliceItem.length; i++) {
        const item = sliceItem[i];
        let current = blocks.length > 0 ? blocks[blocks.length - 1] : undefined;
        if (!current || current.length >= maxItemsPerBlock) {
          const last = current ? current[current.length - 1] : undefined;
          current = [];
          if (last) {
            current.push({...last});
          }
          blocks.push(current);
        }
        current.push({...item});
      }
    });
    blocks.forEach((block) => {
      for (let i = 0; i < block.length; i++) {
        const currentIndex = (i < block.length - 1) ? (i) : (block.length - 2);
        const nextIndex = currentIndex + 1;
        const current = block[currentIndex];
        const next = block[nextIndex];
        block[i].vector = {
          x: next.x - current.x,
          y: next.y - current.y
        };
      }
    });
    return blocks.map((block) => buildPathVAO(block, this.pathProgram));
  }

  draw () {
    if (!this.canvas) {
      return;
    }
    const gl = this.canvas.getContext('webgl2', {antialias: true});
    const {
      r, g, b
    } = parseColor(this.backgroundColor);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.clearColor(r / 255.0, g / 255.0, b / 255.0, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    this.drawCoordinates(gl);
    this.drawSelection(gl);
    this.drawLines(gl);
    this.drawPoints(gl);
    this.drawText();
  }

  prepareChartContext (gl) {
    if (!gl) {
      return undefined;
    }
    gl.viewport(
      this.xAxis.pixelsOffset * dpr,
      this.yAxis.pixelsOffset * dpr,
      this.xAxis.pixelsSize * dpr,
      this.yAxis.pixelsSize * dpr
    );
    const projection = new Float32Array(buildOrthoMatrix({
      top: this.yAxis.to,
      bottom: this.yAxis.from,
      left: this.xAxis.from,
      right: this.xAxis.to
    }));
    const pixelResolution = new Float32Array([
      2.0 / this.xAxis.pixelsSize,
      2.0 / this.yAxis.pixelsSize
    ]);
    return {
      projection,
      pixelResolution
    };
  }

  drawLines (gl) {
    const drawingContext = this.prepareChartContext(gl);
    if (!drawingContext) {
      return;
    }
    const {
      projection,
      pixelResolution
    } = drawingContext;
    // eslint-disable-next-line react-hooks/rules-of-hooks
    usePathProgram(this.pathProgram, projection, IDENTITY_MATRIX, pixelResolution);
    (this.datasetBuffers || []).forEach((datasetBuffer) => {
      const {
        dataset,
        buffers
      } = datasetBuffer;
      const {
        width = DEFAULT_LINE_WIDTH,
        color = DEFAULT_CHART_LINE_COLOR
      } = this.getDatasetOptions(dataset);
      buffers.forEach((buffer) => {
        drawPath(
          this.pathProgram,
          buffer,
          {
            width,
            color
          }
        );
      });
    });
  }

  drawPoints (gl) {
    const drawingContext = this.prepareChartContext(gl);
    if (!drawingContext) {
      return;
    }
    const {
      projection,
      pixelResolution
    } = drawingContext;
    // eslint-disable-next-line react-hooks/rules-of-hooks
    usePathProgram(this.pathProgram, projection, IDENTITY_MATRIX, pixelResolution);
    const pointsToDraw = [];
    const maxItemsToDrawPoints = this.xAxis.pixelsSize / 5.0;
    let total = 0;
    for (let d = 0; d < (this.datasetBuffers || []).length; d += 1) {
      const {
        dataset,
        buffers = []
      } = this.datasetBuffers[d];
      const datasetItemsToDraw = [];
      for (let i = 0; i < buffers.length; i += 1) {
        const {
          items = []
        } = buffers[i];
        const filtered = items.filter((item) => this.xAxis.valueFitsRange(item.x));
        total += filtered.length;
        if (total > maxItemsToDrawPoints) {
          break;
        }
        datasetItemsToDraw.push(...filtered);
      }
      if (total > maxItemsToDrawPoints) {
        break;
      }
      pointsToDraw.push({
        dataset,
        items: datasetItemsToDraw
      });
    }
    if ((this.hoveredItems || []).length || (total > 0 && total < maxItemsToDrawPoints)) {
      // eslint-disable-next-line react-hooks/rules-of-hooks
      useCircleProgram(this.circleProgram, projection, pixelResolution);
    }
    if (total > 0 && total < maxItemsToDrawPoints) {
      pointsToDraw.forEach((datasetPoints) => {
        const {
          dataset,
          items
        } = datasetPoints;
        const {
          width = DEFAULT_LINE_WIDTH,
          color: fill = DEFAULT_CHART_LINE_COLOR,
          pointRadius = width
        } = this.getDatasetOptions(dataset);
        if (pointRadius > 0) {
          items.forEach((item) => {
            drawCircle(
              this.circleProgram,
              this.circleBuffer,
              this.circleStrokeBuffer,
              [item.x, item.y],
              pointRadius,
              {
                fill
              }
            );
          });
        }
      });
    }
    if ((this.hoveredItems || []).length) {
      this.hoveredItems.forEach((point) => {
        const {
          dataset,
          item
        } = point;
        const {
          width = DEFAULT_LINE_WIDTH,
          color: stroke = DEFAULT_CHART_LINE_COLOR,
          pointRadius = width
        } = this.getDatasetOptions(dataset);
        drawCircle(
          this.circleProgram,
          this.circleBuffer,
          this.circleStrokeBuffer,
          [item.x, item.y],
          pointRadius + width / 2.0,
          {
            fill: this.backgroundColor,
            stroke,
            strokeWidth: width
          }
        );
      });
    }
  }

  drawCoordinates (gl) {
    if (
      !gl ||
      !this.datasets ||
      !this.datasets.length ||
      typeof this.horizontalLine !== 'function' ||
      typeof this.verticalLine !== 'function'
    ) {
      return;
    }
    const projection = new Float32Array(buildOrthoMatrix({
      top: 0,
      bottom: this.canvas.height,
      left: 0,
      right: this.canvas.width
    }));
    const pixelResolution = new Float32Array([
      2.0 / this.canvas.width,
      2.0 / this.canvas.height
    ]);
    gl.viewport(
      0,
      0,
      this.canvas.width,
      this.canvas.height
    );
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    // eslint-disable-next-line react-hooks/rules-of-hooks
    usePathProgram(this.pathProgram, projection, IDENTITY_MATRIX, pixelResolution);
    this.verticalLine({
      x: this.xAxis.pixelsOffset,
      y1: 0,
      y2: this.height - this.yAxis.pixelsOffset + 10,
      color: this.coordinatesSystemColor
    });
    this.horizontalLine({
      x1: this.xAxis.pixelsOffset - 10,
      x2: this.width,
      y: this.height - this.yAxis.pixelsOffset,
      color: this.coordinatesSystemColor
    });
    if (this.hoveredItems && this.hoveredItems.length && this.showHoveredCoordinateLine) {
      const hoveredLines = [
        ...new Set(this.hoveredItems.map((item) => this.xAxis.getPixelForValue(item.item.x)))
      ];
      hoveredLines.forEach((x) => this.verticalLine({
        x: Math.round(x),
        y1: 0,
        y2: this.height - this.yAxis.pixelsOffset,
        color: this.coordinatesSystemColor
      }));
    }
    this.xAxis.ticks.forEach((tick) => {
      if (this.xAxis.valueFitsRange(tick.value)) {
        this.verticalLine({
          x: this.xAxis.getPixelForValue(tick.value),
          y1: this.height - this.yAxis.pixelsOffset,
          y2: this.height - this.yAxis.pixelsOffset + (tick.main ? 10 : 5),
          color: this.coordinatesSystemColor
        });
      }
    });
    this.yAxis.ticks.forEach((tick) => {
      if (this.yAxis.valueFitsRange(tick.value)) {
        this.horizontalLine({
          y: this.yAxis.getPixelForValue(tick.value),
          x1: this.xAxis.pixelsOffset - (tick.main ? 10 : 5),
          x2: this.xAxis.pixelsOffset,
          color: this.coordinatesSystemColor
        });
        if (this.options?.showHorizontalLines) {
          this.horizontalLine({
            y: this.yAxis.getPixelForValue(tick.value),
            x1: this.xAxis.pixelsOffset,
            x2: this.width,
            color: this.coordinatesSystemColor
          });
        }
      }
    });
  }

  drawSelection (gl) {
    if (
      !gl ||
      !this.dragEvent ||
      !this.dragEvent.shiftKey ||
      !this.dragEvent.shiftEnd
    ) {
      return;
    }
    const {
      valueX: start,
      shiftEnd = {}
    } = this.dragEvent;
    const {
      valueX: end
    } = shiftEnd;
    if (start === undefined || end === undefined) {
      return;
    }
    const {
      projection
    } = this.prepareChartContext(gl);
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useDefaultProgram(this.defaultProgram, projection);
    drawRectangle(
      this.defaultProgram,
      this.rectangleBuffer,
      start,
      this.yAxis.from,
      (end - start),
      this.yAxis.currentSize,
      this.selectionColor
    );
  }

  drawText () {
    if (
      !this.textCanvas ||
      (!this.xAxis.ticksChanged && !this.yAxis.ticksChanged)
    ) {
      return;
    }
    this.xAxis.ticksChanged = false;
    this.yAxis.ticksChanged = false;
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
          y: this.height - this.yAxis.pixelsOffset + 12
        }),
        color: this.coordinatesColor
      }
    );
    drawVisibleLabels(
      (this.yAxis.ticks || []),
      {
        context,
        axis: this.yAxis,
        getElementPosition: (tick) => ({
          y: this.yAxis.getPixelForValue(tick.value),
          x: this.xAxis.pixelsOffset - 12
        }),
        textAlign: 'right',
        textAlignStart: 'right',
        textAlignEnd: 'right',
        textBaseline: 'middle',
        textBaselineStart: 'bottom',
        textBaselineEnd: 'top',
        color: this.coordinatesColor
      }
    );
    context.restore();
  }
}

export {TimelineChartEvents};
export default TimelineChartRenderer;
