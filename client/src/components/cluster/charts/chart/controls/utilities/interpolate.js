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

export default function (points) {
  if (points.length < 3) {
    return points;
  }
  const xPoints = points.map(p => p.x);
  const yPoints = points.map(p => p.y);
  const derivatives = buildDerivatives(xPoints, yPoints);
  const result = [];
  for (let i = 1; i < xPoints.length; i++) {
    const xStart = xPoints[i - 1];
    const xEnd = xPoints[i];
    const range = xEnd - xStart;
    const step = Math.min(1, 2.0 / Math.abs(range));
    for (let t = 0; i === xPoints.length - 1 ? t < 1 + step : t < 1; t += step) {
      const x = xStart + Math.min(t, 1.0) * range;
      const y = getPoint(Math.min(t, 1.0), i, xPoints, yPoints, derivatives);
      result.push({x, y});
    }
  }
  return result;
}

function getPoint (t, index, xPoints, yPoints, derivatives) {
  const xDiff = xPoints[index] - xPoints[index - 1];
  const yDiff = yPoints[index] - yPoints[index - 1];
  const a = derivatives[index - 1] * xDiff - yDiff;
  const b = -derivatives[index] * xDiff + yDiff;
  return (1 - t) * yPoints[index - 1] +
    t * yPoints[index] +
    t * (1 - t) * (a * (1 - t) + b * t);
}

function matrix (size) {
  const result = [];
  for (let i = 0; i < size; i++) {
    result.push([]);
    for (let j = 0; j < size + 1; j++) {
      result[i].push(0);
    }
  }
  result.prepare = function prepare () {
    const size = this.length;
    const swap = (rowA, rowB) => {
      let p = this[rowA];
      this[rowA] = this[rowB];
      this[rowB] = p;
    };
    for (let r = 0; r < size; r++) {
      let maximumIndex = 0;
      let maximum = -Infinity;
      for (let i = r; i < size; i++) {
        if (this[i][r] > maximum) {
          maximumIndex = i;
          maximum = this[i][r];
        }
      }
      swap(r, maximumIndex);
      for (let i = r + 1; i < size; i++) {
        for (let j = r + 1; j < size + 1; j++) {
          this[i][j] = this[i][j] - this[r][j] * (this[i][r] / this[r][r]);
        }
        this[i][r] = 0;
      }
    }
  };
  result.solve = function solve (derivatives) {
    this.prepare();
    const rowsCount = this.length;
    for (let i = rowsCount - 1; i >= 0; i--) {
      const v = this[i][rowsCount] / this[i][i];
      derivatives[i] = v;
      for (let j = i - 1; j >= 0; j--) {
        this[j][rowsCount] -= this[j][i] * v;
        this[j][i] = 0;
      }
    }
    return derivatives;
  };
  return result;
}

function buildDerivatives (xPoints, yPoints) {
  const size = xPoints.length - 1;
  const _matrix = matrix(xPoints.length);
  function xDiffAt (index) {
    return xPoints[index] - xPoints[index - 1];
  }
  function yDiffAt (index) {
    return yPoints[index] - yPoints[index - 1];
  }
  for (let i = 1; i < size; i++) {
    _matrix[i][i - 1] = 1.0 / xDiffAt(i);
    _matrix[i][i] = 2.0 * (1.0 / xDiffAt(i) + 1 / xDiffAt(i + 1));
    _matrix[i][i + 1] = 1.0 / xDiffAt(i + 1);
    _matrix[i][size + 1] = 3.0 * (
      yDiffAt(i) / (xDiffAt(i) ** 2) + yDiffAt(i + 1) / (xDiffAt(i + 1) ** 2)
    );
  }
  _matrix[0][0] = 2.0 / xDiffAt(1);
  _matrix[0][1] = 1.0 / xDiffAt(1);
  _matrix[0][size + 1] = 3.0 * yDiffAt(1) / (xDiffAt(1) ** 2);
  _matrix[size][size - 1] = 1.0 / xDiffAt(size);
  _matrix[size][size] = 2.0 / xDiffAt(size);
  _matrix[size][size + 1] = 3.0 * yDiffAt(size) / (xDiffAt(size) ** 2);

  const derivatives = [];
  for (let i = 0; i < xPoints.length; i++) {
    derivatives.push(0);
  }
  return _matrix.solve(derivatives);
}
