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

const PolylineAnnotationLayer = class extends BaseAnnotationLayer {
  renderLayers() {
    const {
      id,
      selectedAnnotation,
    } = this.props;
    const mapPoint = (point) => {
      const [x = 0, y = 0] = point;
      return [x, y];
    };
    const layerConfiguration = {
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: this.visibleAnnotations,
      getPath: (polyline) => {
        const map = (p) => mapPoint(polyline.pixels ? this.unproject(p) : p);
        const [first, ...rest] = polyline.points;
        if (polyline.closed) {
          return [first, ...rest, first].map(map);
        }
        return [first, ...rest].map(map);
      },
      getWidth: () => 2,
      widthUnits: 'pixels',
      getColor: (rect) => getAnnotationColor(rect, selectedAnnotation),
      filled: false,
      stroked: true,
      pickable: false,
      updateTriggers: {
        getColor: [selectedAnnotation],
      },
    };
    const layer = new PathLayer({
      id: `polylines-${id}`,
      ...layerConfiguration,
    });
    const pickableLayer = new PathLayer({
      id: `polylines-pickable-${id}`,
      ...layerConfiguration,
      getWidth: () => 6,
      getColor: () => [0, 0, 0, 0],
      pickable: true,
    });
    return [layer, pickableLayer];
  }
};

PolylineAnnotationLayer.layerName = 'PolylineAnnotationLayer';
PolylineAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'polyline-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [{
      points: [[0, 0]],
      pixels: false,
      visible: true,
      lineColor: [0, 0, 0],
      closed: false,
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
  onEdit: { type: 'function', value: (() => {}), compare: true },
};

export default PolylineAnnotationLayer;
