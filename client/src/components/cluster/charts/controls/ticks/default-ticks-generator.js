/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import valueUnSet from './value-unset';
const SIZE_PER_TICK = 50;

const buildRule = (rangeFn) => ({
  fn: range => rangeFn(range),
  display: function (o, full) {
    if (this.base >= 0) {
      return Math.floor(o);
    }
    const d = this.initialBase ** (-this.base + 1 + (full ? 1 : 0));
    return Math.round(o * d) / d;
  },
  fillRange: function (start, end, maxLevel, level = 0) {
    if (level >= maxLevel) {
      return [];
    }
    let tick = 0;
    const result = [];
    const isBase = level === 0;
    if (isBase) {
      result.push({
        tick: start,
        display: this.display(start, true),
        isBase,
        isStart: true
      });
      result.push({
        tick: end,
        display: this.display(end, true),
        isBase,
        isEnd: true
      });
    }
    while (tick <= start) {
      tick = this.addStep(tick);
    }
    while (tick < end) {
      if (result.filter(({t}) => t === tick).length === 0) {
        result.push({
          tick: tick,
          display: this.display(tick),
          isBase
        });
      }
      tick = this.addStep(tick);
    }
    if (level + 1 < maxLevel && this.nextRule) {
      result.push(...this.nextRule.fillRange(start, end, maxLevel, level + 1));
    }
    return result;
  },
  nextRule: undefined
});

const generateRules = (base, range, levels) => {
  const iterationsPerPower = Math.max(Math.ceil(Math.log2(base)), 1);
  const maxPower = Math.floor(Math.log10(range) / Math.log10(base)) + 1;
  const rules = [];
  for (let i = maxPower; i >= maxPower - Math.max(2, levels); i--) {
    for (let j = 0; j < iterationsPerPower; j++) {
      const step = base ** i / (2 ** j);
      rules.push({
        ...buildRule(o => o / step),
        addStep: o => o + step,
        base: Math.floor(Math.log10(step)),
        initialBase: base
      });
    }
  }
  for (let i = 0; i < rules.length - 1; i++) {
    rules[i].nextRule = rules[i + 1];
  }
  return rules;
};

export default function (start, end, canvasSize, base = 10, levels = 2, display) {
  if (
    !canvasSize ||
    valueUnSet(canvasSize) ||
    valueUnSet(start) ||
    valueUnSet(end) ||
    start === end
  ) {
    return [];
  }
  const baseTicksCount = Math.floor(canvasSize / SIZE_PER_TICK);
  const rules = generateRules(base, end - start, levels);
  if (rules.length === 0) {
    return [];
  }
  const diffs = rules
    .map(rule => ({
      ...rule,
      diff: rule.fn(end - start),
      display: display || rule.display
    }));
  const bestFit = diffs
    .filter(d => d.diff <= baseTicksCount).pop() || diffs[0];
  return bestFit.fillRange(start, end, levels);
}
