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

import { DetailView } from '@hms-dbmi/viv';
import CollageMeshLayer from './collage-mesh/layer';
import ImageOverlayLayer from './image/layer';
import { getLayerId } from './get-layer-id';

class DetailViewWithMesh extends DetailView {
  constructor(props) {
    super(props);
    const {
      mesh,
      overlayImages = [],
      onCellHover,
      hoveredCell,
      onCellClick,
    } = props;
    this.onCellHover = onCellHover;
    this.hoveredCell = hoveredCell;
    this.onCellClick = onCellClick;
    this.mesh = mesh;
    this.overlayImages = overlayImages;
  }

  getLayers({ viewStates, props }) {
    const detailViewLayers = super.getLayers({ viewStates, props });
    const { loader } = props;
    const { id, height, width } = this;
    const layerViewState = viewStates[id];
    if (this.overlayImages) {
      this.overlayImages.forEach((image, idx) => {
        let url;
        let ignoreColor;
        let ignoreColorAccuracy;
        let color;
        if (typeof image === 'string') {
          url = image;
          ignoreColor = true;
          ignoreColorAccuracy = 0.1;
        } else if (typeof image === 'object' && image.url) {
          url = image.url;
          color = image.color;
          ignoreColor = !!color || !!image.ignoreColor;
          ignoreColorAccuracy = image.ignoreColorAccuracy || 0.1;
        }
        if (url) {
          detailViewLayers.push(
            new ImageOverlayLayer({
              loader,
              url,
              color,
              ignoreColor,
              ignoreColorAccuracy,
              id: `image-${idx}-${getLayerId(id)}`,
              viewState: { ...layerViewState, height, width },
            }),
          );
        }
      });
    }
    if (this.mesh) {
      detailViewLayers.push(
        new CollageMeshLayer(
          {
            ...props,
            loader,
            id: `mesh-${getLayerId(id)}`,
            mesh: { ...this.mesh },
            viewState: { ...layerViewState, height, width },
            onHover: this.onCellHover,
            onClick: this.onCellClick,
            hoveredCell: this.hoveredCell,
          },
        ),
      );
    }
    return detailViewLayers;
  }
}

export default DetailViewWithMesh;
