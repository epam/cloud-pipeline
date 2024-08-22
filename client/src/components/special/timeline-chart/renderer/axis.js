import {
  DEFAULT_FONT_FAMILY,
  DEFAULT_FONT_SIZE_PT,
  getLabelSizer
} from './text-utilities';
import buildCoordinates from './coordinates';
import { parseDate } from '../../heat-map-chart/utils';

const dpr = window.devicePixelRatio;

function calculateRange (data, accessor) {
  let min = 0;
  let max = 1;
  const filtered = data.filter(Boolean);
  if (filtered.length > 0) {
    const items = (filtered || []).map(accessor);
    min = Math.min(...items);
    max = Math.max(...items);
  }
  return {
    min: Math.min(min, max),
    max: Math.max(min, max)
  };
}

function calculateRangeFromDatasets (datasets, accessor) {
  const ranges = datasets.map((dataset) => calculateRange(dataset.data || [], accessor));
  let min = 0;
  let max = 1;
  if (ranges.length > 0) {
    min = Math.min(...ranges.map((range) => range.min));
    max = Math.max(...ranges.map((range) => range.max));
  }
  return {
    from: Math.min(min, max),
    to: Math.max(min, max)
  };
}

class RendererAxis {
  /**
   * @typedef {Object} RendererAxisOptions
   * @property {string} [identifier]
   * @property {boolean} [reverted=false]
   * @property {boolean|number} [extendArea=false]
   * @property {boolean} [stickToZero=false]
   * @property {number} [minimumRatio]
   * @property {number} [offset]
   * @property {number} [fontSize]
   * @property {string} [fontFamily]
   * @property {boolean} [vertical=false]
   * @property {boolean} [timeline=false]
   */
  /**
   * @param {function(*):number} accessor
   * @param {RendererAxisOptions} [options]
   */
  constructor (accessor, options) {
    if (typeof accessor !== 'function') {
      throw new Error('Axis should be initialized with accessor');
    }
    const {
      identifier,
      reverted = false,
      extendArea = false,
      stickToZero = false,
      minimumRatio,
      fontSize = DEFAULT_FONT_SIZE_PT,
      fontFamily = DEFAULT_FONT_FAMILY,
      vertical = false,
      timeline = false,
      offset = 0
    } = options || {};
    this.identifier = identifier;
    this.accessor = accessor;
    this.reverted = reverted;
    this.extendArea = extendArea;
    this.stickToZero = stickToZero;
    this._minimumRatio = minimumRatio;
    this._min = 0;
    this._max = 1;
    this._from = undefined;
    this._to = undefined;
    this._actualMin = undefined;
    this._actualMax = undefined;
    this._containerSize = 1;
    this._offset = offset;
    this._listeners = [];
    this._fontSize = fontSize;
    this._fontFamily = fontFamily;
    this._ticks = [];
    this._initialized = true;
    this._vertical = vertical;
    this._timeline = timeline;
    this._valueShift = 0;
    this._largestLabelSize = 0;
    this._ticksChanged = false;
  }

  get initialized () {
    return this._initialized;
  }

  get min () {
    return this._min || 0;
  }

  get max () {
    return this._max === undefined ? 1 : this._max;
  }

  get from () {
    return this._from === undefined ? this.min : this._from;
  }

  get to () {
    return this._to === undefined ? this.max : this._to;
  }

  get actualMin () {
    return this._actualMin === undefined ? this.min : this._actualMin;
  }

  get actualMax () {
    return this._actualMax === undefined ? this.max : this._actualMax;
  }

  get center () {
    return (this.from + this.to) / 2.0;
  }

  set center (value) {
    const diff = value - this.center;
    const size = this.currentSize;
    let to = this.correctValue(this.to + diff);
    const from = this.correctValue(to - size);
    to = from + size;
    if (this._from !== from || this._to !== to) {
      this.stopSetRangeAnimation();
      this._from = from;
      this._to = to;
      this.updateTicks();
      this.reportScaleChanged(false, true);
    }
  }

  get currentSize () {
    return this.to - this.from;
  }

  get size () {
    return this.max - this.min;
  }

  get pixelsOffset () {
    return this._offset || 0;
  }

  get pixelsSize () {
    return Math.max(this._containerSize - this.pixelsOffset, 0);
  }

  get minimumRatio () {
    if (this._minimumRatio !== undefined) {
      return this._minimumRatio;
    }
  }

  set minimumRatio (minimumRatio) {
    if (this._minimumRatio !== minimumRatio) {
      this._minimumRatio = minimumRatio;
      this.zoom(this.ratio);
    }
  }

  get ratio () {
    if (this.currentSize === 0) {
      return 0;
    }
    return this.pixelsSize / this.currentSize;
  }

  get minRatio () {
    return this.pixelsSize / this.size;
  }

  get maxRatio () {
    return Math.min(this.pixelsSize, this.minimumRatio || Infinity);
  }

  get ticks () {
    return this._ticks || [];
  }

  get ticksChanged () {
    return this._ticksChanged;
  }

  set ticksChanged (changed) {
    this._ticksChanged = changed;
  }

  get largestLabelSize () {
    return this._largestLabelSize || 0;
  }

  get fontSize () {
    return this._fontSize;
  }

  get fontFamily () {
    return this._fontFamily;
  }

  addScaleChangedListener (listener) {
    this.removeScaleChangedListener(listener);
    if (typeof listener === 'function') {
      this._listeners.push(listener);
    }
  }

  removeScaleChangedListener (listener) {
    this._listeners = this._listeners.filter((aListener) => aListener !== listener);
  }

  reportScaleChanged (fromZoom = false, fromDrag = false) {
    this._listeners.forEach((listener) => {
      if (typeof listener === 'function') {
        listener(fromZoom, fromDrag);
      }
    });
  }

  attachTextContext (textContext) {
    this.detachTextContext();
    this._textContext = textContext;
  }

  detachTextContext () {
    this._textContext = undefined;
  }

  setPixelsOffset (offset = 0, report = true) {
    const newOffset = Math.max(offset, 0);
    if (newOffset !== this._offset) {
      this._offset = newOffset;
      this.updateTicks();
      if (report) {
        this.reportScaleChanged();
      }
    }
  }

  correctValue (value) {
    return Math.max(this.min, Math.min(this.max, value || 0));
  }

  correctActualValue (value) {
    return Math.max(this.actualMin, Math.min(this.actualMax, value || 0));
  }

  /**
   * @param {{from: number?, to: number?}} range
   * @param {{extend: boolean?, stickToZero: boolean?}|boolean} [options]
   * @returns {{from: number, to: number}}
   */
  extendRange (range, options) {
    let {
      from,
      to
    } = range || {};
    const {
      extend = this.extendArea,
      stickToZero = this.stickToZero
    } = typeof options === 'boolean'
      ? {extend: options}
      : (options || {});
    if (from !== undefined && to !== undefined) {
      const tmp = from;
      from = Math.min(from, to);
      to = Math.max(tmp, to);
      const correctStickToZero = () => {
        if (stickToZero) {
          from = 0;
          to = Math.max(from, to);
          if (to === 0) {
            to = 1;
          }
        }
      };
      correctStickToZero();
      if (extend) {
        const extendValue = typeof extend === 'number' ? extend : 0.2; // 20% by default
        let rangeSize = (to - from) * extendValue;
        if (rangeSize === 0) {
          rangeSize = Math.abs(from) * extendValue;
        }
        if (rangeSize === 0) {
          rangeSize = 1.0;
        }
        from -= rangeSize / 2.0;
        to += rangeSize / 2.0;
        correctStickToZero();
      }
    }
    return {
      from,
      to
    };
  }

  /**
   * @param {*[]} datasets
   * @param {{shift: number, report: boolean}} options
   */
  update (datasets = [], options) {
    this.stopSetRangeAnimation();
    const {
      shift = 0,
      report = true
    } = options || {};
    this._valueShift = shift;
    let {
      from: actualMin,
      to: actualMax
    } = calculateRangeFromDatasets(datasets, this.accessor);
    if (this.stickToZero) {
      actualMin = Math.min(actualMin, 0);
    }
    const {
      from: min,
      to: max
    } = this.extendRange({
      from: actualMin,
      to: actualMax
    });
    this._min = min;
    this._max = max;
    this._actualMin = actualMin;
    this._actualMax = actualMax;
    const fitRange = (value) => value >= min && value <= max;
    if (!fitRange(this.from) || !fitRange(this.to)) {
      this._from = undefined;
      this._to = undefined;
    }
    this.updateLargestLabelSize(false);
    this.updateTicks();
    if (report) {
      this.reportScaleChanged();
    }
  }

  updateLargestLabelSize (report = true) {
    if (this._textContext) {
      const {
        getLabelSize,
        releaseContext
      } = getLabelSizer({
        textCanvasContext: this._textContext,
        fontSize: this._fontSize,
        fontFamily: this._fontFamily,
        verticalAxis: !this._vertical
      });
      if (this._timeline) {
        this._largestLabelSize = getLabelSize(`WWWWWWWWW WWWW`) / dpr; // largest: September YYYY
      } else {
        const range = Math.max(
          Math.abs(this.max),
          Math.abs(this.min),
          0
        );
        const getBase = (o) => {
          if (o === 0) {
            return 1;
          }
          const b = Math.log10(o);
          if (b < 0) {
            return Math.abs(Math.floor(b));
          }
          return Math.ceil(b);
        };
        const maxDigits = Math.max(
          getBase(range),
          getBase(range / 10)
        ) + 1;
        let s = '0';
        for (let i = 0; i < maxDigits; i++) {
          s += '0';
        }
        this._largestLabelSize = getLabelSize(s) / dpr;
      }
      releaseContext();
      if (report) {
        this.reportScaleChanged();
      }
      return true;
    }
    return false;
  }

  updateTicks () {
    this._ticks = [];
    if (this._textContext) {
      this._ticks = buildCoordinates({
        axis: this,
        isTimeline: this._timeline,
        valueShift: this._valueShift,
        textCanvasContext: this._textContext,
        fontSize: this._fontSize,
        fontFamily: this._fontFamily,
        verticalAxis: this._vertical,
        includeStart: this._timeline,
        includeEnd: this._timeline
      });
      this.ticksChanged = true;
      return true;
    }
    return false;
  }

  resize (containerSize, report = true) {
    const newContainerSize = Math.max(containerSize || 0, 1);
    if (this._containerSize !== newContainerSize) {
      this._containerSize = newContainerSize;
      this.updateTicks();
      if (report) {
        this.reportScaleChanged();
      }
      return true;
    }
    return false;
  }

  getPixelForValue (value, ratio = this.ratio) {
    if (this.reverted) {
      return this._containerSize - (
        this.pixelsOffset + this.getPixelSizeForValueSize(value - this.from, ratio)
      );
    }
    return this.pixelsOffset + this.getPixelSizeForValueSize(value - this.from, ratio);
  }

  getValueForPixel (pixel, ratio = this.ratio) {
    if (ratio === 0) {
      return this.from;
    }
    if (this.reverted) {
      return this.from + this.getValueSizeForPixelSize(
        this._containerSize - pixel - this.pixelsOffset,
        ratio
      );
    }
    return this.from + this.getValueSizeForPixelSize(pixel - this.pixelsOffset, ratio);
  }

  getPixelSizeForValueSize (valueSize, ratio = this.ratio) {
    return valueSize * ratio;
  }

  getValueSizeForPixelSize (pixelSize, ratio = this.ratio) {
    if (ratio === 0) {
      return 0;
    }
    return pixelSize / ratio;
  }

  valueFitsRange (value) {
    return value >= this.from && value <= this.to;
  }

  zoom (scale, anchor = this.center) {
    const scaleCorrected = Math.max(
      this.minRatio,
      Math.min(this.maxRatio, scale)
    );
    if (scaleCorrected <= 0) {
      return false;
    }
    const anchorPoint = this.correctValue(anchor);
    const newFrom = this.correctValue(
      anchorPoint - this.getPixelSizeForValueSize(anchorPoint - this.from) / scaleCorrected
    );
    const newTo = this.correctValue(
      anchorPoint + this.getPixelSizeForValueSize(this.to - anchorPoint) / scaleCorrected
    );
    return this.setRange({
      from: newFrom,
      to: newTo
    }, {fromZoom: true});
  }

  zoomBy (factor = 0, anchor = this.center) {
    return this.zoom(this.ratio * (1.0 + factor), anchor);
  }

  /**
   * @param {{from: number?, to: number?}} range
   * @param {{extend: boolean?, animate: boolean?}} [options]
   * @returns {boolean}
   */
  setRange (range, options = {}) {
    const {
      extend = false,
      animate = false,
      fromZoom = false
    } = options || {};
    const {
      from: _from = this._from,
      to: _to = this._to
    } = range || {};
    let {
      from: newFrom,
      to: newTo
    } = this.extendRange({from: _from, to: _to}, extend);
    if (newFrom !== this._from || newTo !== this._to) {
      this.stopSetRangeAnimation();
      if (animate) {
        this.startSetRangeAnimation(newFrom, newTo, fromZoom);
      } else {
        this._from = newFrom;
        this._to = newTo;
        this.updateTicks();
        this.reportScaleChanged(fromZoom);
      }
      return true;
    }
    return false;
  }

  clearRange () {
    if (this._from !== undefined || this._to !== undefined) {
      this.stopSetRangeAnimation();
      this._from = undefined;
      this._to = undefined;
      this.updateTicks();
      this.reportScaleChanged();
      return true;
    }
    return false;
  }

  startSetRangeAnimation (from, to, fromZoom) {
    this.stopSetRangeAnimation();
    let currentFrom = this.from;
    let currentTo = this.to;
    if (
      from === undefined ||
      to === undefined ||
      currentFrom === undefined ||
      currentTo === undefined
    ) {
      this._from = from;
      this._to = to;
      this.updateTicks();
      this.reportScaleChanged(fromZoom);
      return;
    }
    const DURATION_MS = 250;
    const step = 1000.0 / 60.0;
    let duration = 0.0;
    let prev;
    const easeInOut = (x) => (x * x) / (2.0 * (x * x - x) + 1.0);
    const frame = (tick) => {
      let request = true;
      if (tick === undefined || prev === undefined) {
        duration += step;
      } else {
        duration += (tick - prev);
      }
      prev = tick;
      if (duration > DURATION_MS) {
        request = false;
        this._from = from;
        this._to = to;
        this.reportScaleChanged(fromZoom);
      } else {
        const ratio = easeInOut(Math.max(0, Math.min(1.0, duration / DURATION_MS)));
        this._from = currentFrom + (from - currentFrom) * ratio;
        this._to = currentTo + (to - currentTo) * ratio;
      }
      this.updateTicks();
      this.reportScaleChanged();
      if (request) {
        this.animationRAF = requestAnimationFrame(frame);
      }
    };
    frame();
  }

  stopSetRangeAnimation () {
    cancelAnimationFrame(this.animationRAF);
  }
}

export default RendererAxis;
