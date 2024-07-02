/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import { BitmapLayer } from '@deck.gl/layers';
import getImageSize from '../../../state/utilities/get-image-size';
import { IgnoreColorExtension, IgnoreColorAndTintExtension } from '../extensions/ignore-color';

const ImageOverlayLayer = class extends CompositeLayer {
  renderLayers() {
    const {
      loader,
      id,
      url,
      color,
      ignoreColor,
      ignoreColorAccuracy,
    } = this.props;
    const { width = 0, height = 0 } = getImageSize(loader[0]) || {};
    const imageLayer = new BitmapLayer({
      id: `image-layer-${id}`,
      image: url,
      bounds: [0, height, width, 0],
      ignoreColorAccuracy,
      color,
      extensions: [
        ignoreColor && !color ? new IgnoreColorExtension() : false,
        ignoreColor && color ? new IgnoreColorAndTintExtension() : false,
      ].filter(Boolean),
    });
    return [imageLayer];
  }
};

ImageOverlayLayer.layerName = 'ImageOverlayLayer';
ImageOverlayLayer.defaultProps = {
  loader: {
    type: 'object',
    value: {
      getRaster: async () => ({ data: [], height: 0, width: 0 }),
      getRasterSize: () => ({ height: 0, width: 0 }),
      dtype: '<u2',
    },
    compare: true,
  },
  id: { type: 'string', value: 'image-overlay-layer', compare: true },
  url: { type: 'string', value: undefined, compare: true },
  color: { type: 'string', value: undefined, compare: true },
  ignoreColor: { type: 'boolean', value: false, compare: true },
  ignoreColorAccuracy: { type: 'number', value: 0.1, compare: true },
  pickable: { type: 'boolean', value: true, compare: true },
  viewState: {
    type: 'object',
    value: { zoom: 0, target: [0, 0, 0] },
    compare: true,
  },
  mesh: { type: 'object', value: { rows: 0, columns: 0 }, compare: true },
  onHover: { type: 'function', value: (() => {}), compare: true },
  onClick: { type: 'function', value: (() => {}), compare: true },
};

export default ImageOverlayLayer;
