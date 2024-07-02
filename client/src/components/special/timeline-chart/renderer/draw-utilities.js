import {drawLine} from './utilities';

const dpr = window.devicePixelRatio;

export function drawVisibleLabels (
  ticks,
  options = {}
) {
  const {
    context,
    axis,
    margin = 3,
    textAlign = 'center',
    textBaseline = 'top',
    textAlignStart = 'left',
    textAlignEnd = 'right',
    textBaselineStart = textBaseline,
    textBaselineEnd = textBaseline,
    getElementPosition = () => ({x: 0, y: 0}),
    color = '#333333'
  } = options;
  if (!context || !axis) {
    return;
  }
  context.save();
  context.font = `${axis.fontSize * dpr}pt ${axis.fontFamily}`;
  context.fillStyle = color;
  const mainTicks = ticks
    .filter((tick) => tick.main)
    .map((tick) => ({
      ...tick
    }));
  const otherTicks = ticks
    .filter((tick) => !tick.main)
    .map((tick) => ({
      ...tick
    }));
  const zones = [];
  const zonesConflicts = (a, b) => {
    const {
      s: aStart,
      e: aEnd
    } = a;
    const {
      s: bStart,
      e: bEnd
    } = b;
    return (aEnd - aStart) + (bEnd - bStart) > Math.max(aEnd, bEnd) - Math.min(aStart, bStart);
  };
  const conflicts = (s, e, testZones = zones) =>
    testZones.some((zone) => zonesConflicts(zone, {s, e}));
  const processTick = (tick) => {
    const size = (tick.size || 0) + 2.0 * margin;
    const position = axis.getPixelForValue(tick.value) * dpr;
    let tickStart = position - size / 2.0;
    let tickEnd = position + size / 2.0;
    if (tick.start) {
      tickStart = position;
      tickEnd = position + size;
    } else if (tick.end) {
      tickStart = position - size;
      tickEnd = position;
    }
    tick.visible = !conflicts(tickStart, tickEnd, zones);
    tick.zone = {
      s: tickStart,
      e: tickEnd
    };
    if (tick.visible) {
      zones.push(tick.zone);
    }
  };
  const renderTick = (tick) => {
    if (tick.visible && (tick.start || tick.end || axis.valueFitsRange(tick.value))) {
      const {
        x,
        y
      } = getElementPosition(tick);
      if (tick.start) {
        context.textAlign = textAlignStart;
        context.textBaseline = textBaselineStart;
      } else if (tick.end) {
        context.textAlign = textAlignEnd;
        context.textBaseline = textBaselineEnd;
      } else {
        context.textAlign = textAlign;
        context.textBaseline = textBaseline;
      }
      context.fillText(
        tick.label,
        Math.round(x * dpr),
        Math.round(y * dpr)
      );
    }
  };
  mainTicks.forEach(processTick);
  mainTicks.forEach(renderTick);
  otherTicks.forEach(processTick);
  otherTicks.forEach(renderTick);
  context.restore();
}

function line (from, to, options) {
  const {
    color = '#000000',
    width = 1,
    units = false,
    program,
    buffer
  } = options || {};
  drawLine(
    program,
    buffer,
    {
      x: (from.x || 0) * (units ? 1 : dpr),
      y: (from.y || 0) * (units ? 1 : dpr)
    },
    {
      x: (to.x || 0) * (units ? 1 : dpr),
      y: (to.y || 0) * (units ? 1 : dpr)
    },
    {
      color,
      width
    }
  );
}

/**
 * @typedef {Object} LineOptions
 * @property {boolean} units
 * @property {string} [color]
 * @property {number} [alpha]
 * @property {number} [width]
 */

export function buildLineHelpers (program, buffer) {
  /**
   * @param {LineOptions & {y: number, x1: number, x2: number}} options
   */
  function horizontalLine (options) {
    const {
      x1 = 0,
      x2 = 0,
      y = 0,
      width = 1,
      units = false
    } = options || {};
    const round = (o) => units ? o : Math.round(o);
    line(
      {x: round(x1), y: round(y) - width / 2.0},
      {x: round(x2), y: round(y) - width / 2.0},
      {
        ...(options || {}),
        program,
        buffer
      }
    );
  }

  /**
   * @param {LineOptions & {x: number, y1: number, y2: number}} options
   */
  function verticalLine (options) {
    const {
      y1 = 0,
      y2 = 0,
      x = 0,
      width = 1,
      units = false
    } = options || {};
    const round = (o) => units ? o : Math.round(o);
    line(
      {x: round(x) - width / 2.0, y: round(y1)},
      {x: round(x) - width / 2.0, y: round(y2)},
      {
        ...(options || {}),
        program,
        buffer
      }
    );
  }
  return {
    horizontalLine,
    verticalLine
  };
}
