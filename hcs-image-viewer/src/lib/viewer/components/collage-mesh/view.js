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
import CollageMeshLayer from './layer';
import { getLayerId } from '../get-layer-id';

export class DetailViewWithMesh extends DetailView {
  constructor(props) {
    super(props);
    const { mesh } = props;
    this.mesh = mesh;
  }

  getLayers({ viewStates, props }) {
    const detailViewLayers = super.getLayers({ viewStates, props });
    const { loader } = props;
    const { id, height, width } = this;
    const layerViewState = viewStates[id];
    if (this.mesh) {
      detailViewLayers.push(
        new CollageMeshLayer(
          {
            ...props,
            loader,
            id: `mesh-${getLayerId(id)}`,
            mesh: { ...this.mesh },
            viewState: { ...layerViewState, height, width },
          },
        ),
      );
    }
    return detailViewLayers;
  }
}

export default DetailViewWithMesh;
