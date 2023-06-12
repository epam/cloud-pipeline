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
    const result = annotations
      .filter((annotation) => annotation.visible === undefined || annotation.visible)
      .slice();
    const {
      dragInfo,
    } = this.state;
    if (dragInfo && dragInfo.object) {
      const idx = result.findIndex((o) => o.identifier === dragInfo.object.identifier);
      if (idx >= 0) {
        result.splice(idx, 1, dragInfo.object);
      }
    }
    return result;
  }

  // eslint-disable-next-line class-methods-use-this
  moveObject(object, delta) {
    if (object.center) {
      return {
        ...object,
        center: [
          object.center[0] + delta[0],
          object.center[1] + delta[1],
        ],
      };
    }
    if (object.points) {
      return {
        ...object,
        points: object.points.map((point) => ([
          point[0] + delta[0],
          point[1] + delta[1],
        ])),
      };
    }
    return { ...object };
  }

  // eslint-disable-next-line class-methods-use-this
  getObjectCenter(object) {
    if (object.center) {
      return object.center.slice();
    }
    if (object.points && object.points.length > 0) {
      const xx = object.points.map((point) => point[0]);
      const yy = object.points.map((point) => point[1]);
      const xMin = Math.min(...xx);
      const xMax = Math.max(...xx);
      const yMin = Math.min(...yy);
      const yMax = Math.max(...yy);
      return [
        (xMin + xMax) / 2.0,
        (yMin + yMax) / 2.0,
      ];
    }
    return [0, 0];
  }

  resizeObject(object, anchor, delta) {
    const center = this.getObjectCenter(object);
    const d1 = [
      anchor[0] - center[0],
      anchor[1] - center[1],
    ];
    const d2 = [
      d1[0] + delta[0],
      d1[1] + delta[1],
    ];
    const getS = (index) => ((d1[index] === 0) ? 1 : (d2[index] / d1[index]));
    const s = [
      getS(0),
      getS(1),
    ];
    let {
      width,
      height,
      radius,
      points,
    } = object;
    if (width !== undefined) {
      width *= s[0];
    }
    if (height !== undefined) {
      height *= s[1];
    }
    if (radius !== undefined) {
      radius *= Math.max(s[0], s[1]);
    }
    if (points !== undefined && points.length > 0) {
      points = points.map((point) => ([
        center[0] + (point[0] - center[0]) * s[0],
        center[1] + (point[1] - center[1]) * s[1],
      ]));
    }
    const result = {
      ...object,
      width: width ? Math.abs(width) : width,
      height: height ? Math.abs(height) : height,
      radius: radius ? Math.abs(radius) : radius,
      points,
    };
    if (width === undefined) {
      delete result.width;
    }
    if (height === undefined) {
      delete result.height;
    }
    if (radius === undefined) {
      delete result.radius;
    }
    if (points === undefined) {
      delete result.points;
    }
    return result;
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
    if (
      typeof onClick === 'function'
      && object
    ) {
      onClick(object.selectable ? object.identifier : undefined);
    }
    return true;
  }

  onDragStart(info, event) {
    const { object, coordinate } = info;
    if (
      object.identifier
      && object.editable
      && object.identifier === this.props.selectedAnnotation
    ) {
      this.setState({
        dragInfo: {
          object: { ...object },
          coordinate,
          resize: event.srcEvent && event.srcEvent.shiftKey,
        },
      });
      event.stopPropagation();
      return true;
    }
    return false;
  }

  onDrag(info, event) {
    const { object, coordinate } = info;
    const {
      dragInfo,
    } = this.state;
    if (
      object
      && dragInfo
      && coordinate
      && dragInfo.object
      && dragInfo.object.identifier === object.identifier
      && dragInfo.coordinate
    ) {
      const delta = [
        coordinate[0] - dragInfo.coordinate[0],
        coordinate[1] - dragInfo.coordinate[1],
      ];
      this.setState({
        dragInfo: {
          ...dragInfo,
          object: dragInfo.resize
            ? this.resizeObject(object, dragInfo.coordinate, delta)
            : this.moveObject(object, delta),
        },
      });
      event.stopPropagation();
      return true;
    }
    return false;
  }

  onDragEnd(info, event) {
    const { dragInfo } = this.state;
    if (dragInfo) {
      const { onEdit } = this.props;
      if (typeof onEdit === 'function' && dragInfo.object) {
        onEdit(dragInfo.object);
      }
      this.setState({
        dragInfo: undefined,
      });
      event.stopPropagation();
      return true;
    }
    return false;
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
  onEdit: { type: 'function', value: (() => {}), compare: true },
};

export default BaseAnnotationLayer;
