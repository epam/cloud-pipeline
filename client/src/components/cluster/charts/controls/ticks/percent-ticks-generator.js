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

const SIZE_PER_TICK = 100;

const buildRule = (rangeFn) => ({
  fn: range => rangeFn(range),
  fillRange: function (start, end, isBase = true) {
    let tick = 0;
    const result = [];
    if (isBase) {
      result.push({
        tick: start,
        display: `${start}%`,
        isBase,
        isStart: true
      });
      result.push({
        tick: end,
        display: `${end}%`,
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
          display: `${tick}%`,
          isBase
        });
      }
      tick = this.addStep(tick);
    }
    if (isBase && this.nextRule) {
      result.push(...this.nextRule.fillRange(start, end, false));
    }
    return result;
  },
  nextRule: undefined
});

const rules = [
  {
    ...buildRule(o => o / 25),
    addStep: o => o + 25
  },
  {
    ...buildRule(o => o / 5),
    addStep: o => o + 5
  }
];

for (let i = 0; i < rules.length - 1; i++) {
  rules[i].nextRule = rules[i + 1];
}

export default function (start, end, canvasSize) {
  if (
    !canvasSize ||
    Math.abs(canvasSize) === Infinity ||
    isNaN(canvasSize) ||
    start === end
  ) {
    return [];
  }
  const baseTicksCount = Math.floor(canvasSize / SIZE_PER_TICK);
  const diffs = rules
    .map(rule => ({
      ...rule,
      diff: rule.fn(end - start)
    }));
  const bestFit = diffs
    .filter(d => d.diff <= baseTicksCount).pop() || diffs[0];
  return bestFit.fillRange(start, end);
}
