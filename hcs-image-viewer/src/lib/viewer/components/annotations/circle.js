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
import { ScatterplotLayer } from '@deck.gl/layers';
import getAnnotationColor from './get-annotation-color';
import BaseAnnotationLayer from './base';

const CircleAnnotationLayer = class extends BaseAnnotationLayer {
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
    const layer = new ScatterplotLayer({
      id: `circles-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: this.visibleAnnotations,
      getPosition: (circle) => mapPoint(
        circle.pixels ? this.unproject(circle.center) : circle.center,
      ),
      getRadius: (circle) => (circle.pixels ? measure(circle.radius) : circle.radius),
      getLineWidth: () => 2,
      lineWidthUnits: 'pixels',
      getLineColor: (annotation) => getAnnotationColor(annotation, selectedAnnotation),
      filled: true,
      getFillColor: [255, 255, 255, 0],
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

CircleAnnotationLayer.layerName = 'CircleAnnotationLayer';
CircleAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'circle-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [{
      center: [0, 0],
      radius: 0,
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
  onEdit: { type: 'function', value: (() => {}), compare: true },
};

export default CircleAnnotationLayer;
