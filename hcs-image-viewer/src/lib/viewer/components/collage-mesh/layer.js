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

import { CompositeLayer, COORDINATE_SYSTEM } from '@deck.gl/core';
import { PolygonLayer } from '@deck.gl/layers';
import getImageSize from '../../../state/utilities/get-image-size';

const CollageMeshLayer = class extends CompositeLayer {
  renderLayers() {
    const {
      loader,
      id,
      mesh = {},
    } = this.props;
    const {
      rows = 0,
      columns = 0,
    } = mesh;

    const { width = 0, height = 0 } = getImageSize(loader[0]) || {};
    const meshData = [];
    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        const cellWidth = width / columns;
        const cellHeight = height / rows;
        const p1 = [column * cellWidth, row * cellHeight];
        const p2 = [(column + 1) * cellWidth, row * cellHeight];
        const p3 = [(column + 1) * cellWidth, (row + 1) * cellHeight];
        const p4 = [column * cellWidth, (row + 1) * cellHeight];
        meshData.push([p1, p2, p3, p4]);
      }
    }
    const meshLayer = new PolygonLayer({
      id: `line-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: [meshData],
      getPolygon: (f) => f,
      getLineWidth: 1,
      lineWidthUnits: 'pixels',
      getLineColor: [220, 220, 220],
      filled: false,
      stroked: true,
    });
    return [meshLayer];
  }
};

CollageMeshLayer.layerName = 'CollageMeshLayer';
CollageMeshLayer.defaultProps = {
  loader: {
    type: 'object',
    value: {
      getRaster: async () => ({ data: [], height: 0, width: 0 }),
      getRasterSize: () => ({ height: 0, width: 0 }),
      dtype: '<u2',
    },
    compare: true,
  },
  id: { type: 'string', value: 'collage-mesh-layer', compare: true },
  pickable: { type: 'boolean', value: true, compare: true },
  viewState: {
    type: 'object',
    value: { zoom: 0, target: [0, 0, 0] },
    compare: true,
  },
  mesh: { type: 'object', value: { rows: 0, columns: 0 }, compare: true },
};

export default CollageMeshLayer;
