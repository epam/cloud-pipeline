/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import { COORDINATE_SYSTEM } from '@deck.gl/core';
import { PathLayer } from '@deck.gl/layers';
import getAnnotationColor from './get-annotation-color';
import BaseAnnotationLayer from './base';

const ArrowAnnotationLayer = class extends BaseAnnotationLayer {
  renderLayers() {
    const {
      id,
      selectedAnnotation,
    } = this.props;
    const measure = (s) => {
      const [x1, y1] = this.unproject([0, 0]);
      const [x2, y2] = this.unproject([s, 0]);
      return Math.sqrt((x1 - x2) ** 2 + (y1 - y2) ** 2);
    };
    const mapPoint = (point) => {
      const [x = 0, y = 0] = point;
      return [x, y];
    };
    const layer = new PathLayer({
      id: `arrows-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: this.visibleAnnotations,
      getPath: (arrow) => {
        const [a, b] = arrow.points;
        const start = arrow.pixels ? this.unproject(a) : a;
        const end = arrow.pixels ? this.unproject(b) : b;
        const length = Math.sqrt((start[0] - end[0]) ** 2 + (start[1] - end[1]) ** 2);
        const arrowSize = Math.min(
          length,
          measure(10.0),
        );
        const v = [
          end[0] - start[0],
          end[1] - start[1],
        ];
        const o = [
          -v[1],
          v[0],
        ];
        const getVectorLength = (vector) => Math.sqrt(vector[0] ** 2 + vector[1] ** 2);
        const getNormal = (vector) => {
          const l = getVectorLength(vector);
          if (l === 0) {
            return [0, 0];
          }
          return [
            vector[0] / l,
            vector[1] / l,
          ];
        };
        const normalV = getNormal(v);
        const normalO = getNormal(o);
        const p = [
          end[0] - normalV[0] * arrowSize,
          end[1] - normalV[1] * arrowSize,
        ];
        const p1 = [
          p[0] + normalO[0] * (arrowSize / 2.0),
          p[1] + normalO[1] * (arrowSize / 2.0),
        ];
        const p2 = [
          p[0] - normalO[0] * (arrowSize / 2.0),
          p[1] - normalO[1] * (arrowSize / 2.0),
        ];
        return [start, p, p2, end, p1, p].map(mapPoint);
      },
      getWidth: () => 2,
      widthUnits: 'pixels',
      getColor: (arrow) => getAnnotationColor(arrow, selectedAnnotation),
      filled: false,
      stroked: true,
      pickable: true,
      updateTriggers: {
        getColor: [selectedAnnotation],
      },
    });
    return [layer];
  }
};

ArrowAnnotationLayer.layerName = 'ArrowAnnotationLayer';
ArrowAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'arrow-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [{
      points: [[0, 0], [0, 0]],
      pixels: false,
      visible: true,
      lineColor: [0, 0, 0],
    }],
    compare: true,
  },
  selectedAnnotation: { type: 'string', value: undefined, compare: true },
  pickable: { type: 'boolean', value: true, compare: true },
  viewState: {
    type: 'object',
    value: { zoom: 0, target: [0, 0, 0] },
    compare: true,
  },
  onClick: { type: 'function', value: (() => {}), compare: true },
};

export default ArrowAnnotationLayer;
