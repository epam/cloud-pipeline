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

import moment from 'moment-timezone';

const dpr = window.devicePixelRatio || 1;

export function correctPixels (pixels) {
  return pixels * dpr;
}

export function percentToHexAlpha (p) {
  return `0${Math.ceil((255 / 100) * p).toString(16)}`.slice(-2).toUpperCase();
}

export function isNumber (number) {
  return number !== undefined && number !== null && !Number.isNaN(Number(number));
}

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

export function parseDate (date) {
  let dateValue, unix;
  if (moment.isMoment(date)) {
    dateValue = date;
    unix = dateValue.unix();
  } else if (isNumber(date)) {
    unix = Number(date);
    dateValue = moment.unix(unix);
  } else if (typeof date === 'string') {
    dateValue = moment.utc(date).local();
    if (!dateValue.isValid()) {
      dateValue = undefined;
    } else {
      unix = dateValue.unix();
    }
  }
  if (unix !== undefined && dateValue !== undefined) {
    return {
      unix,
      date: dateValue
    };
  }
  return undefined;
}
