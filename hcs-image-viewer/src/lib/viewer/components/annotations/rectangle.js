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
import { PolygonLayer } from '@deck.gl/layers';
import getAnnotationColor from './get-annotation-color';
import BaseAnnotationLayer from './base';

const RectangleAnnotationLayer = class extends BaseAnnotationLayer {
  renderLayers() {
    const {
      id,
      selectedAnnotation,
    } = this.props;
    const mapPoint = (point) => {
      const [x = 0, y = 0] = point;
      return [x, y];
    };
    const layer = new PolygonLayer({
      id: `rectangle-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: this.visibleAnnotations,
      getPolygon: (rect) => {
        const getPoint = (dx, dy) => ([
          (rect.center[0] + dx * (rect.width / 2.0)),
          (rect.center[1] + dy * (rect.height / 2.0)),
        ]);
        const correct = (point) => mapPoint(rect.pixels ? this.unproject(point) : point);
        return [
          correct(getPoint(-1, -1)),
          correct(getPoint(-1, 1)),
          correct(getPoint(1, 1)),
          correct(getPoint(1, -1)),
        ];
      },
      getLineWidth: () => 2,
      lineWidthUnits: 'pixels',
      getLineColor: (rect) => getAnnotationColor(rect, selectedAnnotation),
      getFillColor: () => ([0, 0, 0, 0]),
      filled: true,
      stroked: true,
      pickable: true,
      updateTriggers: {
        getLineColor: [selectedAnnotation],
      },
    });
    return [layer];
  }

  onHover(info) {
    const { object } = info;
    const { onHover } = this.props;
    if (onHover) {
      onHover(object);
    }
    return true;
  }

  onClick(info) {
    const { object } = info;
    const { onClick } = this.props;
    if (onClick) {
      onClick(object ? object.identifier : undefined);
    }
    return true;
  }
};

RectangleAnnotationLayer.layerName = 'RectangleAnnotationLayer';
RectangleAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'rectangle-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [{
      center: [0, 0],
      width: 0,
      height: 0,
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

export default RectangleAnnotationLayer;
