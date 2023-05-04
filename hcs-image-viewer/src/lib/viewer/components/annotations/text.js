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
import { TextLayer } from '@deck.gl/layers';
import getAnnotationColor from './get-annotation-color';
import BaseAnnotationLayer from './base';

const TextAnnotationLayer = class extends BaseAnnotationLayer {
  renderLayers() {
    const {
      id,
      selectedAnnotation,
    } = this.props;
    const mapPoint = (point) => {
      const [x = 0, y = 0] = point;
      return [x, y];
    };
    const visibleLabels = this.visibleAnnotations
      .filter((label) => label.label && label.label.value);
    const layer = new TextLayer({
      id: `labels-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: visibleLabels,
      getPosition: (label) => mapPoint(
        label.pixels ? this.unproject(label.center) : label.center,
      ),
      getText: (label) => (label.label ? label.label.value : undefined),
      getSize: (label) => (label.label ? label.label.fontSize : 16),
      getColor: (label) => getAnnotationColor(
        label,
        selectedAnnotation,
        (o) => (o.label ? o.label.color : undefined),
      ),
      getTextAnchor: 'middle',
      getAlignmentBaseline: 'center',
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

TextAnnotationLayer.layerName = 'TextAnnotationLayer';
TextAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'text-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [{
      center: [0, 0],
      label: {
        color: '#FFFFFF',
        fontSize: 16,
        value: '',
      },
      visible: true,
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

export default TextAnnotationLayer;
