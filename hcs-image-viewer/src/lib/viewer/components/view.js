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
import ArrowAnnotationLayer from './annotations/arrow';
import RectangleAnnotationLayer from './annotations/rectangle';
import CircleAnnotationLayer from './annotations/circle';
import PolylineAnnotationLayer from './annotations/polyline';
import AnnotationBackgroundLayer from './annotations/background';
import TextAnnotationLayer from './annotations/text';

class DetailViewWithMesh extends DetailView {
  constructor(props) {
    super(props);
    const {
      mesh,
      overlayImages = [],
      annotations = [],
      selectedAnnotation,
      onCellHover,
      hoveredCell,
      onCellClick,
      onEditAnnotation,
      onSelectAnnotation,
    } = props;
    this.onCellHover = onCellHover;
    this.hoveredCell = hoveredCell;
    this.onCellClick = onCellClick;
    this.mesh = mesh;
    this.overlayImages = overlayImages;
    this.annotations = annotations;
    this.selectedAnnotation = selectedAnnotation;
    this.onSelectAnnotation = onSelectAnnotation;
    this.onEditAnnotation = onEditAnnotation;
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
    const rectangleAnnotations = this.annotations
      .filter((annotation) => /^rectangle$/i.test(annotation.type));
    const circleAnnotations = this.annotations
      .filter((annotation) => /^circle$/i.test(annotation.type));
    const polylineAnnotations = this.annotations
      .filter((annotation) => /^polyline$/i.test(annotation.type));
    const arrowAnnotations = this.annotations
      .filter((annotation) => /^arrow$/i.test(annotation.type));
    const textAnnotations = this.annotations
      .filter((annotation) => /^text$/i.test(annotation.type));
    if (this.annotations.length > 0) {
      detailViewLayers.push(
        new AnnotationBackgroundLayer(
          {
            ...this.props,
            loader,
            id: `annotations-background-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    if (rectangleAnnotations.length > 0) {
      detailViewLayers.push(
        new RectangleAnnotationLayer(
          {
            ...this.props,
            id: `rectangle-annotation-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            annotations: rectangleAnnotations,
            selectedAnnotation: this.selectedAnnotation,
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    if (circleAnnotations.length > 0) {
      detailViewLayers.push(
        new CircleAnnotationLayer(
          {
            ...this.props,
            id: `circle-annotation-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            annotations: circleAnnotations,
            selectedAnnotation: this.selectedAnnotation,
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    if (polylineAnnotations.length > 0) {
      detailViewLayers.push(
        new PolylineAnnotationLayer(
          {
            ...this.props,
            id: `polyline-annotation-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            annotations: polylineAnnotations,
            selectedAnnotation: this.selectedAnnotation,
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    if (arrowAnnotations.length > 0) {
      detailViewLayers.push(
        new ArrowAnnotationLayer(
          {
            ...this.props,
            id: `arrow-annotation-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            annotations: arrowAnnotations,
            selectedAnnotation: this.selectedAnnotation,
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    if (textAnnotations.length > 0) {
      detailViewLayers.push(
        new TextAnnotationLayer(
          {
            ...this.props,
            id: `text-annotation-${getLayerId(id)}`,
            viewState: { ...layerViewState, height, width },
            annotations: textAnnotations,
            selectedAnnotation: this.selectedAnnotation,
            onClick: this.onSelectAnnotation,
            onEdit: this.onEditAnnotation,
          },
        ),
      );
    }
    return detailViewLayers;
  }
}

export default DetailViewWithMesh;
