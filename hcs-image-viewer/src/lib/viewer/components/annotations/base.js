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

import { CompositeLayer } from '@deck.gl/core';

const BaseAnnotationLayer = class extends CompositeLayer {
  get visibleAnnotations() {
    const {
      annotations = [],
    } = this.props;
    return annotations
      .filter((annotation) => annotation.visible === undefined || annotation.visible);
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

BaseAnnotationLayer.layerName = 'BaseAnnotationLayer';
BaseAnnotationLayer.defaultProps = {
  id: { type: 'string', value: 'base-annotation-layer', compare: true },
  annotations: {
    type: 'array',
    value: [],
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

export default BaseAnnotationLayer;
