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

const EMPTY_SELECTION_CELL = { x: -1, y: -1 };

const CollageMeshLayer = class extends CompositeLayer {
  renderLayers() {
    const {
      loader,
      id,
      mesh = {},
      hoveredCell,
    } = this.props;
    const {
      cells = [],
      selected = [],
      meshSize = 1,
      notSelectedAlpha = 0.75,
    } = mesh;

    const { width = 0, height = 0 } = getImageSize(loader[0]) || {};
    const cellWidth = width / meshSize;
    const cellHeight = height / meshSize;
    const getPoint = (x, y) => [x * cellWidth, y * cellHeight];
    const getCellPolygon = (x, y) => {
      const p1 = getPoint(x, y);
      const p2 = getPoint(x + 1, y);
      const p3 = getPoint(x + 1, y + 1);
      const p4 = getPoint(x, y + 1);
      return [p1, p2, p3, p4];
    };
    const isCellHovered = (cell) => cell
      && hoveredCell
      && hoveredCell.id === cell.id;
    const cellIsNotSelected = (cell) => cell
      && !selected.some((s) => s.id === cell.id);
    const meshData = cells.slice();
    meshData.sort((a, b) => Number(isCellHovered(a)) - Number(isCellHovered(b)));
    const meshLayer = new PolygonLayer({
      id: `line-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: meshData,
      getPolygon: (cell) => getCellPolygon(cell.x, cell.y),
      getLineWidth: (cell) => (
        isCellHovered(cell) ? 2 : 1
      ),
      lineWidthUnits: 'pixels',
      getLineColor: (cell) => (
        isCellHovered(cell) ? [220, 220, 220] : [220, 220, 220, 50]
      ),
      filled: false,
      stroked: true,
      updateTriggers: {
        getLineColor: [hoveredCell || EMPTY_SELECTION_CELL],
      },
    });
    const cellsLayer = new PolygonLayer({
      id: `cell-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: cells,
      getPolygon: (cell) => getCellPolygon(cell.x, cell.y),
      filled: true,
      stroked: false,
      pickable: true,
      getFillColor: [255, 255, 255, 0],
    });
    const notSelectedCellsLayer = new PolygonLayer({
      id: `cell-not-selected-${id}`,
      coordinateSystem: COORDINATE_SYSTEM.CARTESIAN,
      data: cells.filter(cellIsNotSelected),
      getPolygon: (cell) => getCellPolygon(cell.x, cell.y),
      filled: true,
      stroked: false,
      getFillColor: [0, 0, 0, Math.max(0, Math.min(255, Math.round(255 * notSelectedAlpha)))],
    });
    return [notSelectedCellsLayer, meshLayer, cellsLayer];
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
      onClick(object);
    }
    return true;
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
  mesh: { type: 'object', value: { meshSize: 0 }, compare: true },
  onHover: { type: 'function', value: (() => {}), compare: true },
  onClick: { type: 'function', value: (() => {}), compare: true },
};

export default CollageMeshLayer;
