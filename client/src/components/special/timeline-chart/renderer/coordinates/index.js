import moment from 'moment-timezone';
import TimelineConfigurations from './timeline-steps';
import {getLabelSizer} from '../text-utilities';

/**
 * @typedef {Object} CoordinatesOptions
 * @property {boolean} [isTimeline=false]
 * @property {number} [valueShift=0]
 * @property {RendererAxis} axis
 * @property {number} [stepPixelSize=20]
 * @property {number} [minimumStepPixelSize=5]
 * @property {CanvasRenderingContext2D} [textCanvasContext]
 * @property {string} [fontSize]
 * @property {string} [fontFamily]
 * @property {boolean} [verticalAxis=false]
 * @property {boolean} [includeStart=true]
 * @property {boolean} [includeEnd=true]
 * @property {boolean} [includeZero=true]
 */

/**
 * @typedef {Object} CoordinatesStep
 * @property {number} value
 * @property {boolean} main
 * @property {boolean} start
 * @property {boolean} end
 * @property {string} label
 * @property {number} size
 */

/**
 * @param {CoordinatesStep} tick
 * @returns {number}
 */
function getTickPriority (tick) {
  if (tick.start || tick.end) {
    return 2;
  }
  if (tick.main) {
    return 1;
  }
  return 0;
}

/**
 *
 * @param {CoordinatesStep} a
 * @param {CoordinatesStep} b
 * @returns {number}
 */
function ticksSorter (a, b) {
  return (getTickPriority(b) - getTickPriority(a)) || (a.value - b.value);
}

/**
 * @param {CoordinatesOptions} options
 * @returns {CoordinatesStep[]}
 */
function buildTimelineCoordinates (options) {
  const {
    axis,
    valueShift = 0,
    stepPixelSize = 40,
    minimumStepPixelSize = 5.0,
    includeStart = true,
    includeEnd = true
  } = options;
  if (!axis || !axis.initialized || axis.pixelsSize <= 0) {
    return [];
  }
  const result = [];
  const {
    getLabelSize,
    releaseContext
  } = getLabelSizer(options);
  const expandConfig = (config) => {
    const {
      step,
      variations = [],
      unit,
      ...rest
    } = config;
    return {
      ...rest,
      unit,
      step,
      getNextStepDate: (currentDate, variation = 1) => moment(currentDate).add(
        variation,
        unit === 'date' ? 'day' : unit
      ),
      variations: [...new Set([1, ...variations])]
        .sort((a, b) => a - b)
        .map((variation) => ({
          variation,
          stepPxSize: axis.getPixelSizeForValueSize(step * variation)
        })),
      stepPxSize: axis.getPixelSizeForValueSize(step),
      labelSize: getLabelSize(config.maxLabel)
    };
  };
  const configs = TimelineConfigurations
    .map(expandConfig)
    .reverse();
  let mainIndex = configs.findIndex(
    (config) => config.stepPxSize > minimumStepPixelSize &&
      config.labelSize * 1.1 <= config.stepPxSize
  );
  if (mainIndex === -1) {
    mainIndex = configs.length - 1;
  }
  const smallIndex = Math.max(0, mainIndex - 1);
  const mainConfig = configs[mainIndex];
  let smallConfig;
  if (smallIndex !== mainIndex) {
    smallConfig = configs[smallIndex];
  }
  mainConfig.main = true;
  const significantUnitChangesPriority = ['year', 'month', 'date', 'hour', 'minute', 'second'];
  const getUnitsToCheck = (config) => significantUnitChangesPriority.slice(
    0,
    significantUnitChangesPriority.indexOf(config.unit)
  );

  const significantUnitChange = (date, previousDate, units = significantUnitChangesPriority) => {
    if (!previousDate || !date) {
      return undefined;
    }
    for (let i = 0; i < units.length; i += 1) {
      const unit = units[i];
      if (previousDate.get(unit) !== date.get(unit)) {
        return unit;
      }
    }
    return undefined;
  };

  const minValue = axis.correctActualValue(axis.from);
  const maxValue = axis.correctActualValue(axis.to);

  const addTick = (tickDate, tickOptions) => {
    const tickValue = tickDate.unix();
    const {
      config,
      start = false,
      end = false,
      change
    } = tickOptions || {};
    if (
      (!start && !end && tickValue - valueShift < minValue) ||
      result.find((tick) => tick.value === tickValue - valueShift)
    ) {
      return;
    }
    const {
      format: mainFormat,
      smallFormat = mainFormat,
      main = start || end
    } = config || {};
    const format = main ? mainFormat : smallFormat;
    let label = tickDate.format(format);
    if (change && main) {
      let newFormat = format;
      if (config && config.change && config.change[change]) {
        newFormat = config.change[change];
      } else if (config && config.change && config.change.default) {
        newFormat = config.change.default;
      }
      label = tickDate.format(newFormat);
    }
    const size = getLabelSize(label);
    result.push({
      value: tickValue - valueShift,
      main,
      start,
      end,
      config,
      label,
      size
    });
  };
  if (includeStart) {
    addTick(moment.unix(minValue + valueShift), {
      config: {
        format: 'D MMM YYYY, HH:mm:ss',
        unit: 'second'
      },
      start: true
    });
  }
  if (includeEnd) {
    addTick(moment.unix(maxValue + valueShift), {
      config: {
        format: 'D MMM YYYY, HH:mm:ss',
        unit: 'second'
      },
      end: true
    });
  }
  const iterateWithConfig = (config) => {
    if (config) {
      const {
        variation,
        stepPxSize
      } = config.variations
        .find((aVariation) => aVariation.stepPxSize >= stepPixelSize) ||
      config.variations[config.variations.length - 1];
      if (stepPxSize < minimumStepPixelSize) {
        return;
      }
      let tick = config.getNearest(minValue + valueShift);
      let tickDate = moment.unix(tick);
      let previous;
      let change;
      let iteration = 0;
      while (tick <= maxValue + valueShift && iteration < 500) {
        iteration += 1;
        addTick(tickDate, {config, change});
        previous = tickDate;
        tickDate = config.getNextStepDate(tickDate, variation);
        change = significantUnitChange(tickDate, previous, getUnitsToCheck(config));
        if (change) {
          tickDate = tickDate.startOf(change);
        }
        tick = tickDate.unix();
      }
    }
  };
  iterateWithConfig(mainConfig, true);
  iterateWithConfig(smallConfig, false);
  releaseContext();
  return result.sort(ticksSorter);
}

/**
 * @param {CoordinatesOptions} options
 * @returns {CoordinatesStep[]}
 */
function buildDefaultCoordinates (options) {
  const {
    axis,
    valueShift = 0,
    stepPixelSize = 40,
    minimumStepPixelSize = 5.0,
    includeStart = true,
    includeEnd = true,
    includeZero = true
  } = options;
  if (!axis || !axis.initialized || axis.pixelsSize <= 0) {
    return [];
  }
  const result = [];
  const {
    getLabelSize,
    releaseContext
  } = getLabelSizer(options);
  const createConfig = (base) => {
    const step = 10 ** base;
    const roundBasePow = base - 2;
    const negative = roundBasePow < 0;
    const roundBase = negative ? (10 ** Math.abs(roundBasePow)) : (10 ** roundBasePow);
    const round = (value) => negative
      ? (Math.round(value * roundBase) / roundBase)
      : (Math.round(value / roundBase) * roundBase);
    return {
      step,
      base,
      roundBase,
      getLabel: (value) => `${round(value)}`,
      getNextStep: (currentStep, variation = 1) => round(currentStep + step * variation),
      getNearest: (value) => round(value),
      variations: [1, 1.25, 2, 2.5, 5]
        .sort((a, b) => a - b)
        .map((variation) => ({
          variation,
          stepPxSize: axis.getPixelSizeForValueSize(step * variation)
        })),
      stepPxSize: axis.getPixelSizeForValueSize(step),
      labelSize: getLabelSize(`${step}`)
    };
  };
  const minValue = axis.correctActualValue(axis.from);
  const maxValue = axis.correctActualValue(axis.to);
  const getBase = (value) => Math.floor(Math.log10(Math.abs(value) || 10));
  let base = getBase(maxValue);
  let mainConfig = createConfig(base);
  while (mainConfig.stepPxSize > minimumStepPixelSize) {
    base -= 1;
    mainConfig = createConfig(base);
  }
  mainConfig = createConfig(base + 1);
  mainConfig.main = true;
  base -= 1;
  const smallConfig = createConfig(base);
  const addTick = (tickValue, tickOptions) => {
    const {
      config,
      start = false,
      end = false
    } = tickOptions || {};
    if (
      (!start && !end && tickValue - valueShift < minValue) ||
      result.find((tick) => tick.value === tickValue - valueShift)
    ) {
      return;
    }
    const {
      main = start || end,
      getLabel = ((o) => `${o}`)
    } = config || {};
    let label = getLabel(tickValue);
    const size = getLabelSize(label);
    result.push({
      value: tickValue - valueShift,
      main,
      start,
      end,
      label,
      size
    });
  };
  if (includeStart) {
    addTick(minValue + valueShift, {start: true});
  }
  if (includeEnd) {
    addTick(maxValue + valueShift, {end: true});
  }
  if (includeZero) {
    addTick(0, {main: true});
  }
  const iterateWithConfig = (config) => {
    if (config) {
      const {
        variation,
        stepPxSize
      } = config.variations
        .find((aVariation) => aVariation.stepPxSize >= stepPixelSize) ||
      config.variations[config.variations.length - 1];
      if (stepPxSize < minimumStepPixelSize) {
        return;
      }
      let tick = config.getNearest(axis.correctActualValue(axis.from) + valueShift);
      let iteration = 0;
      while (tick <= axis.correctActualValue(axis.to) + valueShift && iteration < 500) {
        iteration += 1;
        addTick(tick, {config});
        tick = config.getNextStep(tick, variation);
      }
    }
  };
  iterateWithConfig(mainConfig, true);
  iterateWithConfig(smallConfig, false);
  releaseContext();
  return result.sort(ticksSorter);
}

/**
 * @param {CoordinatesOptions} options
 * @returns {CoordinatesStep[]}
 */
function buildCoordinates (options) {
  const {
    isTimeline = false
  } = options;
  if (isTimeline) {
    return buildTimelineCoordinates(options);
  }
  return buildDefaultCoordinates(options);
}

export default buildCoordinates;
