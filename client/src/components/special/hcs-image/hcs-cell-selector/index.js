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
/* eslint-disable max-len */
import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import {Icon, Select, Tooltip} from 'antd';
import createBuffers from './buffers';
import {mat4translate, mat4scale, mat4identity} from './matrix-functions';
import {createGLProgram, resizeCanvas, getLinesToDraw} from './canvas-utilities';
import {getWellRowName} from './utilities';
import styles from './hcs-cell-selector.css';
import {parseColor} from '../../../../themes/utilities/color-utilities';
import {cellsArraysAreEqual} from '../utilities/cells-utilities';

const MESH_MODES = {
  LINES: 'LINES',
  CROSS: 'CROSS',
  NONE: 'NONE',
  CIRCLES: 'CIRCLES'
};

const FONT_SIZE_PX = 12;
const SCROLL_BAR_SIZE = 6;
const UNIT_MAX_SCALE = 26;

export function colorToVec4 (aColor) {
  const {
    r = 255,
    g = 255,
    b = 255,
    a = 1
  } = parseColor(aColor || 'white') || {};
  return [r / 255.0, g / 255.0, b / 255.0, a];
}

function buildTagValue (tag, value) {
  return `${tag}|${value}`;
}

function parseTagValue (tagValue) {
  const [tag, ...valueParts] = (tagValue || '').split('|');
  return {
    tag,
    value: valueParts.join('|')
  };
}

function ElementHint ({element}) {
  if (!element) {
    return undefined;
  }
  const {
    tags = {},
    info = {},
    x,
    y
  } = element;
  const infos = [
    `${getWellRowName(y)}${x + 1}`,
    ...Object.entries(tags)
      .map(([key, values]) => `${key}: ${values.join(', ')}`),
    ...Object.entries(info)
      .map(([key, value]) => `${key}: ${value}`)
  ];
  return (
    <div>
      {
        infos.map((info, idx) => (
          <div key={`hint-${idx}`} style={{margin: '5px 0'}}>
            {info}
          </div>
        ))
      }
    </div>
  );
}

function elementIsSelectable (element) {
  return typeof element.selectable === 'undefined' || element.selectable;
}

@inject('themes')
@observer
class HcsCellSelector extends React.Component {
  width = 1;
  height = 1;
  needRender = true;
  elements = {};
  mouseEvent;
  _center = {x: 0, y: 0};
  _unitScale = 1;
  unitMinScale = 1;

  totalWidth = 0;
  totalHeight = 0;

  maxColumnLabelSize = 20;
  maxRowLabelSize = 20;

  @observable _hoveredElement = undefined;
  _scrollBarHovered = {vertical: false, horizontal: false};

  @observable zoomOutAvailable;
  @observable zoomInAvailable;
  @observable fitScale;
  @observable fitCenter;

  backgroundColor = [1.0, 1.0, 1.0, 1.0];
  textColor = [0, 0, 0, 0.65];
  defaultColor = [0, 0, 0, 0.65];
  primaryColor = [0, 0, 1, 1.0];
  primaryHoverColor = [0, 0, 1, 1.0];
  selectionColor = [0, 1, 1, 1.0];
  selectedColor = [0, 1, 1, 1.0];
  selectedHoverColor = [0, 1, 1, 1.0];

  state = {
    selectedTags: [],
    tags: []
  };

  setNeedRedraw = () => {
    this.needRender = true;
  }

  updateColors = () => {
    const {themes} = this.props;
    let backgroundColor = 'rgba(255, 255, 255, 1)';
    let textColor = 'rgba(0, 0, 0, 0.65)';
    let primaryColor = '#108ee9';
    let primaryHoverColor = '#108ee9';
    let selectedColor = '#ff8818';
    let selectedHoverColor = '#ff8818';
    if (themes && themes.currentThemeConfiguration) {
      backgroundColor = themes.currentThemeConfiguration['@card-background-color'] || backgroundColor;
      textColor = themes.currentThemeConfiguration['@application-color'] || textColor;
      primaryColor = themes.currentThemeConfiguration['@primary-color'] || primaryColor;
      primaryHoverColor = themes.currentThemeConfiguration['@primary-hover-color'] || primaryHoverColor;
      selectedColor = themes.currentThemeConfiguration['@color-warning'] || selectedColor;
      selectedHoverColor = themes.currentThemeConfiguration['@color-sensitive'] || selectedHoverColor;
    }
    this.backgroundColor = colorToVec4(backgroundColor);
    this.textColor = textColor;
    this.defaultColor = colorToVec4(textColor);
    this.primaryColor = colorToVec4(primaryColor);
    this.primaryHoverColor = colorToVec4(primaryHoverColor);
    this.selectionColor = [...this.primaryColor.slice(0, 3), this.primaryColor[3] / 4.0];
    this.selectedColor = colorToVec4(selectedColor);
    this.selectedHoverColor = colorToVec4(selectedHoverColor);
    this.setNeedRedraw();
  };

  get center () {
    return this._center;
  }

  set center (value) {
    if (value) {
      const restrictions = this.centerRestrictions;
      if (restrictions) {
        this._center = {
          x: Math.min(restrictions.x2, Math.max(restrictions.x1, value.x)),
          y: Math.min(restrictions.y2, Math.max(restrictions.y1, value.y))
        };
      } else {
        this._center = value;
      }
      this.setNeedRedraw();
    }
  }

  get centerRestrictions () {
    if (!this.unitScale || !this.height || !this.width || !this.drawingArea) {
      return undefined;
    }
    const {showRulers} = this.props;
    const pxWidth = this.width * this.unitScale;
    const pxHeight = this.height * this.unitScale;
    const diff = {
      x: (pxWidth - this.drawingArea.width) / this.unitScale,
      y: (pxHeight - this.drawingArea.height) / this.unitScale
    };
    const center = {
      x: this.width / 2.0,
      y: this.height / 2.0
    };
    const getRestrictions = (c, delta) => {
      if (delta < 0 && showRulers) {
        return [c - delta / 2.0, c - delta / 2.0];
      }
      return [
        c - Math.max(0, delta / 2.0),
        c + Math.max(0, delta / 2.0)
      ];
    };
    const [x1, x2] = getRestrictions(center.x, diff.x);
    const [y1, y2] = getRestrictions(center.y, diff.y);
    return {x1, x2, y1, y2};
  }

  get unitScale () {
    return this._unitScale;
  }

  set unitScale (value) {
    const newUnitScale = Math.min(UNIT_MAX_SCALE, Math.max(this.unitMinScale, value));
    if (newUnitScale !== this._unitScale) {
      this._unitScale = newUnitScale;
      this.setNeedRedraw();
      this.zoomOutAvailable = newUnitScale > this.unitMinScale;
      this.zoomInAvailable = newUnitScale < UNIT_MAX_SCALE;
    }
  }

  get offsets () {
    const {
      showRulers
    } = this.props;
    const common = Math.max(
      20,
      FONT_SIZE_PX * 2,
      this.maxRowLabelSize + 4
    );
    return {
      top: showRulers ? common : 0,
      left: showRulers ? common : 0,
      bottom: SCROLL_BAR_SIZE * 2,
      right: SCROLL_BAR_SIZE * 2
    };
  }

  get drawingArea () {
    if (this.totalWidth && this.totalHeight) {
      return {
        width: this.totalWidth - this.offsets.left - this.offsets.right,
        height: this.totalHeight - this.offsets.top - this.offsets.bottom
      };
    }
    return {
      width: 1,
      height: 1
    };
  }

  get hoveredElement () {
    return this._hoveredElement;
  }

  set hoveredElement (element) {
    if (
      !!this._hoveredElement !== !!element ||
      (
        this._hoveredElement &&
        element &&
        (
          this._hoveredElement.x !== element.x ||
          this._hoveredElement.y !== element.y ||
          this._hoveredElement.width !== element.width ||
          this._hoveredElement.height !== element.height
        )
      )
    ) {
      // we're setting this._hoveredElement to undefined first
      // to request re-render (as it is observable) WITHOUT tooltip,
      this._hoveredElement = undefined;
      // and then setting it to the actual value to display a hint
      // at the renewed position
      this._hoveredElement = element;
      this.setNeedRedraw();
    }
  }

  get scrollBarHovered () {
    return this._scrollBarHovered;
  }

  set scrollBarHovered (value) {
    const {
      vertical = false,
      horizontal = false
    } = value || {};
    if (
      this._scrollBarHovered.horizontal !== !!horizontal ||
      this._scrollBarHovered.vertical !== !!vertical
    ) {
      this._scrollBarHovered = {
        horizontal: !!horizontal,
        vertical: !!vertical
      };
      this.setNeedRedraw();
    }
  }

  get elementsMode () {
    let {
      gridMode: meshMode = MESH_MODES.CIRCLES
    } = this.props;
    const MINIMUM_CIRCLE_DIAMETER = 10;
    if (meshMode === MESH_MODES.CIRCLES && this.unitScale < MINIMUM_CIRCLE_DIAMETER) {
      return MESH_MODES.LINES;
    }
    return meshMode;
  }

  componentDidMount () {
    this.renderRAF = requestAnimationFrame(this.draw);
    this.resizerRAF = requestAnimationFrame(this.resize);
    this.initializeViewport();
    const {themes} = this.props;
    if (themes) {
      themes.addThemeChangedListener(this.updateColors);
    }
    this.updateColors();
    this.updateTags();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.width !== this.props.width ||
      prevProps.height !== this.props.height ||
      !cellsArraysAreEqual(prevProps.cells, this.props.cells) ||
      prevProps.showRulers !== this.props.showRulers
    ) {
      this.initializeViewport();
      this.updateTags();
    }
    this.setNeedRedraw();
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.renderRAF);
    cancelAnimationFrame(this.resizerRAF);
    this.removeEventListeners();
    const {themes} = this.props;
    if (themes) {
      themes.removeThemeChangedListener(this.updateColors);
    }
    this.canvas = undefined;
    this.textCanvas = undefined;
    this.ctx = undefined;
    this.textCtx = undefined;
    this.buffers = undefined;
    this.defaultGlProgram = undefined;
  }

  cellIsFiltered = (aCell) => {
    if (!aCell) {
      return false;
    }
    const {tags = []} = aCell;
    const {
      selectedTags = []
    } = this.state;
    if (selectedTags.length === 0) {
      return true;
    }
    const selected = selectedTags.map(parseTagValue);
    const groups = [...new Set(selected.map(o => o.tag))];
    for (let g = 0; g < groups.length; g++) {
      const aGroup = groups[g];
      const values = new Set(selected.filter(o => o.tag === aGroup).map(o => o.value));
      const tagValues = tags[aGroup] || [];
      if (tagValues.length === 0) {
        return false;
      }
      let tagMatches = false;
      for (let i = 0; i < tagValues.length; i++) {
        tagMatches = tagMatches || values.has(tagValues[i]);
      }
      if (!tagMatches) {
        return false;
      }
    }
    return true;
  };

  updateTags = () => {
    const {
      cells = []
    } = this.props;
    const tagGroups = [];
    cells.forEach(aCell => {
      const {
        tags = {}
      } = aCell;
      Object.entries(tags)
        .forEach(([tagName, tagValues = []]) => {
          if (tagValues.length === 0) {
            return;
          }
          let tagGroup = tagGroups.find(group => group.name === tagName);
          if (!tagGroup) {
            tagGroup = {
              name: tagName,
              values: []
            };
            tagGroups.push(tagGroup);
          }
          tagValues.forEach(tagValue => {
            if (!tagGroup.values.includes(tagValue)) {
              tagGroup.values.push(tagValue);
            }
          });
        });
    });
    tagGroups.forEach(tagGroup => {
      tagGroup.values.sort();
    });
    this.setState({
      tags: tagGroups,
      selectedTags: []
    });
  };

  initializeViewport = () => {
    const {
      width = 1,
      height = 1,
      cells = []
    } = this.props;
    this.width = width;
    this.height = height;
    this.elements = cells.map((aCell) => {
      const {
        x,
        y,
        fieldWidth: aCellWidth = 1,
        fieldHeight: aCellHeight = 1,
        ...rest
      } = aCell;
      return {
        ...rest,
        x,
        y,
        width: aCellWidth,
        height: aCellHeight,
        cell: aCell
      };
    });
    this.buildDefaultScale();
    this.hoveredElement = undefined;
    this.setNeedRedraw();
  };

  buildDefaultScale = () => {
    if (this.textCtx) {
      this.textCtx.font = `${FONT_SIZE_PX}px sans-serif`;
      this.maxRowLabelSize = this.textCtx.measureText(
        getWellRowName(this.height).replace(/./g, 'W')
      ).width + 4;
      this.maxColumnLabelSize = this.textCtx.measureText(
        `${this.width}`.replace(/./g, '0')
      ).width + 4;
    }
    if (this.ctx) {
      const getScaleFor = (width, height) => {
        const ratio = width / height;
        const drawingRatio = this.drawingArea.width / this.drawingArea.height;
        let scale = this.drawingArea.width / width;
        if (ratio < drawingRatio) {
          scale = this.drawingArea.height / height;
        }
        return Math.min(scale, UNIT_MAX_SCALE);
      };
      const minScale = getScaleFor(this.width, this.height);
      this.unitMinScale = minScale;
      this.unitScale = minScale;
      this.fitScale = minScale;
      this.center = {
        x: this.width / 2.0,
        y: this.height / 2.0
      };
      this.fitCenter = this.center;
      if (this.elements.length > 0 && this.props.scaleToROI) {
        const minX = Math.min(...this.elements.map(o => o.x));
        const maxX = Math.max(...this.elements.map(o => o.x + (o.width || 1)));
        const minY = Math.min(...this.elements.map(o => o.y));
        const maxY = Math.max(...this.elements.map(o => o.y + (o.height || 1)));
        const roiWidth = Math.min(this.width, (maxX - minX) * 1.5);
        const roiHeight = Math.min(this.height, (maxY - minY) * 1.5);
        const shouldScaleToROIThreshold = 0.25;
        if (
          roiWidth / this.width < shouldScaleToROIThreshold ||
          roiHeight / this.height < shouldScaleToROIThreshold
        ) {
          this.unitScale = getScaleFor(roiWidth, roiHeight);
          this.fitScale = this.unitScale;
          this.center = {
            x: (minX + maxX) / 2.0,
            y: (minY + maxY) / 2.0
          };
          this.fitCenter = this.center;
        }
      }
      this.setNeedRedraw();
    }
  };

  initializeEventListeners = () => {
    if (this.canvas) {
      this.canvas.addEventListener('wheel', this.onWheel);
      this.canvas.addEventListener('mousedown', this.onMouseDown);
      window.addEventListener('mousemove', this.onMouseMove);
      window.addEventListener('mouseup', this.onMouseUp);
      window.addEventListener('visibilitychange', this.setNeedRedraw);
    }
  };

  removeEventListeners = () => {
    if (this.canvas) {
      this.canvas.removeEventListener('wheel', this.onWheel);
      this.canvas.removeEventListener('mousedown', this.onMouseDown);
      window.removeEventListener('mousemove', this.onMouseMove);
      window.removeEventListener('mouseup', this.onMouseUp);
      window.removeEventListener('visibilitychange', this.setNeedRedraw);
    }
  };

  pxPointToUnitPoint = (point, round = false) => {
    if (!this.center) {
      return {};
    }
    const {
      x = 0,
      y = 0
    } = point || {};
    const pxCenter = {
      x: this.offsets.left + this.drawingArea.width / 2.0,
      y: this.offsets.top + this.drawingArea.height / 2.0
    };
    const {
      x: xx,
      y: yy
    } = this.pxSizeToUnitSize({x: x - pxCenter.x, y: y - pxCenter.y});
    const format = (o) => round ? Math.floor(o) : o;
    return {
      x: format(this.center.x + xx),
      y: format(this.center.y + yy)
    };
  };

  pxSizeToUnitSize = (vector) => {
    const {
      x = 0,
      y = 0
    } = vector || {};
    return {
      x: x / this.unitScale,
      y: y / this.unitScale
    };
  };

  unitPointToPxPoint = (point) => {
    if (!this.center) {
      return {};
    }
    const {
      x = 0,
      y = 0
    } = point || {};
    const pxCenter = {
      x: this.offsets.left + this.drawingArea.width / 2.0,
      y: this.offsets.top + this.drawingArea.height / 2.0
    };
    const {
      x: xx,
      y: yy
    } = this.unitSizeToPxSize({x: x - this.center.x, y: y - this.center.y});
    return {
      x: pxCenter.x + xx,
      y: pxCenter.y + yy
    };
  };

  unitSizeToPxSize = (vector) => {
    const {
      x = 0,
      y = 0
    } = vector || {};
    return {
      x: x * this.unitScale,
      y: y * this.unitScale
    };
  };

  getHoveredElementPointStyle = () => {
    if (this.hoveredElement) {
      const {
        x: left,
        y: top
      } = this.unitPointToPxPoint({
        x: this.hoveredElement.x + (this.hoveredElement.width || 1) / 2.0,
        y: this.hoveredElement.y + (this.hoveredElement.height || 1) / 2.0
      });
      return {
        left,
        top
      };
    }
    return {};
  };

  getElementsByPosition = (unitPosition, options = {}) => {
    if (!unitPosition) {
      return [];
    }
    const {
      unitError = 2.0 / this.unitScale
    } = options;
    const filterElement = (element) => {
      const {
        x,
        y,
        width = 1,
        height = 1
      } = element;
      const x1 = x - unitError;
      const y1 = y - unitError;
      const x2 = x + width + unitError;
      const y2 = y + height + unitError;
      return x1 <= unitPosition.x && x2 >= unitPosition.x &&
        y1 <= unitPosition.y && y2 >= unitPosition.y;
    };
    const getDistanceForElement = (element) => {
      const center = {
        x: element.x + (element.width || 1) / 2.0,
        y: element.y + (element.height || 1) / 2.0
      };
      return Math.sqrt((center.x - unitPosition.x) ** 2 + (center.y - unitPosition.y) ** 2);
    };
    return (this.elements || [])
      .filter(filterElement)
      .map((element) => ({element, distance: getDistanceForElement(element)}))
      .sort((a, b) => a.distance - b.distance)
      .map(o => o.element);
  };

  getElementsWithinArea = (unitsStart, unitsEnd) => {
    if (!unitsStart || !unitsEnd) {
      return [];
    }
    const [x1, x2 = x1] = [unitsStart.x, unitsEnd.x].sort((a, b) => a - b);
    const [y1, y2 = y1] = [unitsStart.y, unitsEnd.y].sort((a, b) => a - b);
    const elementWithinArea = (element) => {
      const {
        x,
        y,
        width = 1,
        height = 1
      } = element;
      const [minX, , , maxX] = [x1, x2, x, x + width].sort((a, b) => a - b);
      const [minY, , , maxY] = [y1, y2, y, y + height].sort((a, b) => a - b);
      return (maxX - minX) <= width + (x2 - x1) &&
        (maxY - minY) <= height + (y2 - y1);
    };
    return (this.elements || []).filter(elementWithinArea);
  };

  getScrollBars = () => {
    const getScrollBar = (options = {}) => {
      const {
        unitSize,
        totalPxSize,
        position
      } = options;
      const pxSize = unitSize * this.unitScale;
      if (pxSize <= totalPxSize) {
        return undefined;
      }
      const ratio = totalPxSize / pxSize;
      const size = totalPxSize * ratio;
      return {
        ratio,
        size,
        center: (position / unitSize) * totalPxSize
      };
    };
    return {
      vertical: getScrollBar({
        unitSize: this.height,
        totalPxSize: this.drawingArea.height,
        position: this.center.y
      }),
      horizontal: getScrollBar({
        unitSize: this.width,
        totalPxSize: this.drawingArea.width,
        position: this.center.x
      })
    };
  };

  getScrollBarUnderPosition = (point) => {
    const {
      x,
      y
    } = point;
    const {
      vertical,
      horizontal
    } = this.getScrollBars();
    if (vertical) {
      const {
        size,
        center
      } = vertical;
      const x1 = this.totalWidth - this.offsets.right / 2.0 - SCROLL_BAR_SIZE / 2.0;
      const y1 = this.offsets.top + center - size / 2.0;
      const x2 = x1 + SCROLL_BAR_SIZE;
      const y2 = y1 + size;
      if (x1 <= x && x <= x2 && y1 <= y && y <= y2) {
        return {
          vertical
        };
      }
    }
    if (horizontal) {
      const {
        size,
        center
      } = horizontal;
      const x1 = this.offsets.left + center - size / 2.0;
      const y1 = this.totalHeight - this.offsets.bottom / 2.0 - SCROLL_BAR_SIZE / 2.0;
      const x2 = x1 + size;
      const y2 = y1 + SCROLL_BAR_SIZE;
      if (x1 <= x && x <= x2 && y1 <= y && y <= y2) {
        return {
          horizontal
        };
      }
    }
    return {};
  };

  isCellSelected = (aCell) => {
    const {selected = []} = this.props;
    return !!aCell && selected.some(s => s.id === aCell.id);
  };

  zoom = (direction) => {
    this.unitScale *= (1.0 + 0.1 * direction);
  };

  fit = () => {
    this.unitScale = this.fitScale;
    this.center = this.fitCenter;
    this.setNeedRedraw();
  };

  onWheel = (event) => {
    if (!this.center) {
      return;
    }
    if (event.shiftKey) {
      const {
        offsetX: x,
        offsetY: y
      } = event;
      const wheelUnitPoint = this.pxPointToUnitPoint({x, y});
      const prevScale = this.unitScale;
      this.zoom(Math.sign(event.wheelDelta) / 2.0);
      const scaleRatio = prevScale / this.unitScale;
      this.center = {
        x: wheelUnitPoint.x - (wheelUnitPoint.x - this.center.x) * scaleRatio,
        y: wheelUnitPoint.y - (wheelUnitPoint.y - this.center.y) * scaleRatio
      };
      event.preventDefault();
      return;
    }
    const pxWidth = this.unitScale * this.width;
    const pxHeight = this.unitScale * this.height;
    if (
      pxHeight > this.drawingArea.height ||
      pxWidth > this.drawingArea.width
    ) {
      const {deltaY = 0, deltaX = 0} = event;
      const deltaUnitX = deltaX / this.unitScale;
      const deltaUnitY = deltaY / this.unitScale;
      this.center = {
        x: this.center.x + deltaUnitX,
        y: this.center.y + deltaUnitY
      };
      event.preventDefault();
    }
  };

  onMouseDown = (event) => {
    if (!event || !this.center) {
      return;
    }
    const {
      offsetX,
      offsetY,
      clientX,
      clientY
    } = event;
    const {
      vertical,
      horizontal
    } = this.getScrollBarUnderPosition({x: offsetX, y: offsetY});
    this.scrollBarHovered = {
      vertical,
      horizontal
    };
    this.mouseEvent = {
      offsetX,
      offsetY,
      clientX,
      clientY,
      startPx: {x: offsetX, y: offsetY},
      startUnits: this.pxPointToUnitPoint({x: offsetX, y: offsetY}),
      verticalScrollBar: vertical,
      horizontalScrollBar: horizontal,
      shift: event.shiftKey,
      center: {...(this.center || {x: 0, y: 0})}
    };
  };

  onMouseMove = (event) => {
    if (
      this.mouseEvent &&
      (
        this.mouseEvent.verticalScrollBar ||
        this.mouseEvent.horizontalScrollBar
      )
    ) {
      const {
        ratio: yRatio = 0
      } = this.mouseEvent.verticalScrollBar || {};
      const {
        ratio: xRatio = 0
      } = this.mouseEvent.horizontalScrollBar || {};
      const {
        clientX,
        clientY
      } = event;
      const delta = {
        x: xRatio ? -(clientX - this.mouseEvent.clientX) / xRatio : 0,
        y: yRatio ? -(clientY - this.mouseEvent.clientY) / yRatio : 0
      };
      const unitsDelta = this.pxSizeToUnitSize(delta);
      this.center = {
        x: this.mouseEvent.center.x - unitsDelta.x,
        y: this.mouseEvent.center.y - unitsDelta.y
      };
      this.hoveredElement = undefined;
      this.scrollBarHovered = {
        vertical: this.mouseEvent.verticalScrollBar,
        horizontal: this.mouseEvent.horizontalScrollBar
      };
      this.setNeedRedraw();
      return;
    }
    const {
      selectionEnabled
    } = this.props;
    if (this.mouseEvent && selectionEnabled) {
      this.hoveredElement = undefined;
      const {
        clientX,
        clientY
      } = event;
      const delta = {
        x: clientX - this.mouseEvent.clientX,
        y: clientY - this.mouseEvent.clientY
      };
      const endPx = {
        x: Math.max(
          this.offsets.left,
          Math.min(this.totalWidth - this.offsets.right, this.mouseEvent.startPx.x + delta.x)
        ),
        y: Math.max(
          this.offsets.top,
          Math.min(this.totalHeight - this.offsets.bottom, this.mouseEvent.startPx.y + delta.y)
        )
      };
      this.mouseEvent.moved = true;
      this.mouseEvent.endPx = endPx;
      this.mouseEvent.endUnits = this.pxPointToUnitPoint(endPx);
      this.mouseEvent.elements = this.getElementsWithinArea(
        this.mouseEvent.startUnits,
        this.mouseEvent.endUnits
      )
        .filter(elementIsSelectable);
      this.setNeedRedraw();
      return;
    }
    if (event.target === this.canvas) {
      // hover
      const {
        offsetX: x,
        offsetY: y
      } = event;
      const {
        vertical,
        horizontal
      } = this.getScrollBarUnderPosition({x, y});
      this.scrollBarHovered = {
        vertical,
        horizontal
      };
      if (!vertical && !horizontal) {
        const hovered = this.getElementsByPosition(this.pxPointToUnitPoint({x, y}));
        this.hoveredElement = hovered[0];
      } else {
        this.hoveredElement = undefined;
      }
    }
  };

  onMouseUp = () => {
    this.scrollBarHovered = undefined;
    const {
      selected = [],
      onChange
    } = this.props;
    if (
      this.mouseEvent &&
      !this.mouseEvent.moved &&
      !this.mouseEvent.verticalScrollBar &&
      !this.mouseEvent.horizontalScrollBar
    ) {
      const {
        offsetX: x,
        offsetY: y
      } = this.mouseEvent;
      const click = this.getElementsByPosition(this.pxPointToUnitPoint({x, y}))[0];
      if (!this.mouseEvent.shift && click) {
        onChange && onChange([click.cell]);
      } else if (this.mouseEvent.shift && this.isCellSelected(click)) {
        onChange && onChange(
          selected.filter(o => o.id !== click.id)
        );
      } else if (this.mouseEvent.shift && click) {
        onChange && onChange([
          ...selected,
          click.cell
        ]);
      } else {
        onChange && onChange([]);
      }
    } else if (
      this.mouseEvent &&
      this.mouseEvent.moved &&
      this.mouseEvent.elements &&
      this.mouseEvent.elements.length
    ) {
      if (this.mouseEvent.shift) {
        const newSelectionIds = new Set(this.mouseEvent.elements.map(o => o.id));
        onChange && onChange([
          ...selected.filter(o => !newSelectionIds.has(o.id)),
          ...this.mouseEvent.elements.map(o => o.cell)
        ]);
      } else {
        onChange && onChange(
          this.mouseEvent.elements.map(o => o.cell)
        );
      }
    }
    this.hoveredElement = undefined;
    this.mouseEvent = undefined;
    this.setNeedRedraw();
  };

  canvasInitializer = (aCanvas) => {
    if (aCanvas && this.canvas !== aCanvas) {
      this.removeEventListeners();
      this.canvas = aCanvas;
      this.ctx = this.canvas.getContext('webgl2', {antialias: true});
      this.buffers = createBuffers(this.ctx);
      this.defaultGlProgram = createGLProgram(this.ctx);
      this.initializeEventListeners();
      this.resizeCanvas();
      this.setNeedRedraw();
    } else if (!aCanvas) {
      this.canvas = undefined;
      this.ctx = undefined;
      this.buffers = undefined;
      this.defaultGlProgram = undefined;
    }
  };

  textCanvasInitializer = (textCanvas) => {
    if (textCanvas && this.textCanvas !== textCanvas) {
      this.textCanvas = textCanvas;
      this.textCtx = this.textCanvas.getContext('2d');
      this.resizeCanvas();
      this.setNeedRedraw();
    } else if (!textCanvas) {
      this.textCanvas = undefined;
      this.textCtx = undefined;
    }
  };

  initializeContainer = (container) => {
    this.container = container;
  };

  setUnitsDrawingUniforms = () => {
    const gl = this.ctx;
    if (!gl) {
      return;
    }
    const diffPx = {
      x: (this.offsets.left - this.offsets.right) / 2.0,
      y: -(this.offsets.top - this.offsets.bottom) / 2.0
    };
    gl.uniformMatrix4fv(this.defaultGlProgram.projection, false, mat4scale(
      [2.0 / this.totalWidth, 2.0 / this.totalHeight, 1]
    ));
    gl.uniformMatrix4fv(this.defaultGlProgram.viewScale, false, mat4scale(
      [this.unitScale, -this.unitScale, 1]
    ));
    gl.uniformMatrix4fv(this.defaultGlProgram.viewTranslate, false, mat4translate(
      diffPx.x, diffPx.y, 0
    ));
  };

  setPixelDrawingUniforms = () => {
    const gl = this.ctx;
    if (!gl) {
      return;
    }
    gl.uniformMatrix4fv(this.defaultGlProgram.projection, false, mat4scale(
      [2.0 / this.totalWidth, 2.0 / this.totalHeight, 1]
    ));
    gl.uniformMatrix4fv(this.defaultGlProgram.modelScale, false, mat4identity());
    gl.uniformMatrix4fv(this.defaultGlProgram.modelTranslate, false, mat4identity());
  };

  setViewUniforms = (x, y, width, height) => {
    const gl = this.ctx;
    if (!gl) {
      return;
    }
    gl.uniformMatrix4fv(this.defaultGlProgram.viewScale, false, mat4scale([
      width,
      height,
      1
    ]));
    gl.uniformMatrix4fv(this.defaultGlProgram.viewTranslate, false, mat4translate(
      -this.totalWidth / 2.0 + x,
      this.totalHeight / 2.0 - y,
      0
    ));
  };

  drawMesh = () => {
    const gl = this.ctx;
    const mode = this.elementsMode;
    if (gl && this.buffers && this.defaultGlProgram && mode !== MESH_MODES.NONE) {
      const options = {
        borders: mode !== MESH_MODES.CROSS,
        threshold: mode === MESH_MODES.CROSS ? 20 : 10,
        unitScale: this.unitScale
      };
      const horizontalLines = getLinesToDraw(
        this.height,
        {
          ...options,
          from: this.pxPointToUnitPoint({x: 0, y: this.offsets.top}).y - 1,
          to: this.pxPointToUnitPoint({x: 0, y: this.totalHeight - this.offsets.bottom}).y + 1
        }
      );
      const verticalLines = getLinesToDraw(
        this.width,
        {
          ...options,
          from: this.pxPointToUnitPoint({x: this.offsets.left, y: 0}).x - 1,
          to: this.pxPointToUnitPoint({x: this.totalWidth - this.offsets.right, y: 0}).x + 1
        }
      );
      switch (mode) {
        case MESH_MODES.CROSS:
          gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.cross.buffer);
          break;
        case MESH_MODES.CIRCLES:
          gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.circleBorder.buffer);
          break;
        case MESH_MODES.LINES:
        default:
          gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.line.buffer);
          break;
      }
      gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(this.defaultColor));
      this.setUnitsDrawingUniforms();
      if (mode === MESH_MODES.LINES) {
        gl.uniformMatrix4fv(
          this.defaultGlProgram.modelScale,
          false,
          mat4scale([this.width, 0, 1])
        );
        horizontalLines.forEach(y => {
          gl.uniformMatrix4fv(
            this.defaultGlProgram.modelTranslate,
            false,
            mat4translate(-this.center.x, -this.center.y + y, 0)
          );
          gl.drawArrays(gl.LINES, 0, this.buffers.line.vertexCount);
        });
        gl.uniformMatrix4fv(
          this.defaultGlProgram.modelScale,
          false,
          mat4scale([0, this.height, 1])
        );
        verticalLines.forEach(x => {
          gl.uniformMatrix4fv(
            this.defaultGlProgram.modelTranslate,
            false,
            mat4translate(-this.center.x + x, -this.center.y, 0)
          );
          gl.drawArrays(gl.LINES, 0, this.buffers.line.vertexCount);
        });
      } else if (mode === MESH_MODES.CROSS) {
        const CROSS_SIZE_PX = 3;
        gl.uniformMatrix4fv(
          this.defaultGlProgram.modelScale,
          false,
          mat4scale(CROSS_SIZE_PX / this.unitScale)
        );
        for (let xIdx = 0; xIdx < verticalLines.length; xIdx++) {
          const x = verticalLines[xIdx];
          for (let yIdx = 0; yIdx < horizontalLines.length; yIdx++) {
            const y = horizontalLines[yIdx];
            gl.uniformMatrix4fv(
              this.defaultGlProgram.modelTranslate,
              false,
              mat4translate(x - this.center.x, y - this.center.y, 0)
            );
            gl.drawArrays(gl.LINES, 0, this.buffers.cross.vertexCount);
          }
        }
      } else if (mode === MESH_MODES.CIRCLES) {
        gl.uniformMatrix4fv(
          this.defaultGlProgram.modelScale,
          false,
          mat4scale(0.5 - 1.0 / this.unitScale)
        );
        for (let xIdx = 0; xIdx < verticalLines.length; xIdx++) {
          const x = verticalLines[xIdx];
          if (x >= this.width) {
            continue;
          }
          for (let yIdx = 0; yIdx < horizontalLines.length; yIdx++) {
            const y = horizontalLines[yIdx];
            if (y >= this.height) {
              continue;
            }
            gl.uniformMatrix4fv(
              this.defaultGlProgram.modelTranslate,
              false,
              mat4translate(x + 0.5 - this.center.x, y + 0.5 - this.center.y, 0)
            );
            gl.drawArrays(gl.LINES, 0, this.buffers.circleBorder.vertexCount);
          }
        }
      }
    }
  };

  drawElements = () => {
    if (!this.ctx || !this.elements || !this.defaultGlProgram || !this.buffers) {
      return;
    }
    const {
      getColorConfigurationForCell
    } = this.props;
    const gl = this.ctx;
    const mode = this.elementsMode;
    let currentColor;
    let currentBuffer;
    const colorsEqual = (a, b) => {
      if (!a && !b) {
        return true;
      }
      if (!a || !b) {
        return false;
      }
      return a[0] === b[0] && a[1] === b[1] && a[2] === b[2] && a[3] === b[3];
    };
    const prepareBuffers = (options = {}) => {
      const {
        color,
        buffer
      } = options;
      if (color && !colorsEqual(color, currentColor)) {
        gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(color));
        currentColor = color;
      }
      if (buffer && currentBuffer !== buffer) {
        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
        gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
        gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      }
    };
    this.setUnitsDrawingUniforms();
    const scaleMatrices = new Map();
    const getScaleMatrixForElement = (element) => {
      const {
        width = 1.0,
        height = 1.0
      } = element;
      const key = `${width}|${height}`;
      if (!scaleMatrices.has(key)) {
        if (mode === MESH_MODES.CIRCLES) {
          scaleMatrices.set(key, mat4scale(
            [
              (element.width || 1) / 2.0 - 1.0 / this.unitScale,
              (element.height || 1) / 2.0 - 1.0 / this.unitScale,
              1
            ]
          ));
        } else {
          scaleMatrices.set(key, mat4scale(
            [
              (element.width || 1),
              (element.height || 1),
              1
            ]
          ));
        }
      }
      return scaleMatrices.get(key);
    };
    const renderElement = (element, options = {}) => {
      const {
        fill,
        stroke,
        thickStroke
      } = options;
      const {
        x = 0,
        y = 0,
        width = 1.0,
        height = 1.0
      } = element;
      const scaleMatrix = getScaleMatrixForElement(element);
      if (mode === MESH_MODES.CIRCLES) {
        gl.uniformMatrix4fv(this.defaultGlProgram.modelScale, false, scaleMatrix);
        gl.uniformMatrix4fv(this.defaultGlProgram.modelTranslate, false, mat4translate(
          -this.center.x + x + width / 2.0,
          -this.center.y + y + height / 2.0,
          0
        ));
      } else {
        gl.uniformMatrix4fv(this.defaultGlProgram.modelScale, false, scaleMatrix);
        gl.uniformMatrix4fv(this.defaultGlProgram.modelTranslate, false, mat4translate(
          -this.center.x + x,
          -this.center.y + y,
          0
        ));
      }
      if (fill) {
        prepareBuffers({
          color: fill,
          buffer: mode === MESH_MODES.CIRCLES
            ? this.buffers.circle.buffer
            : this.buffers.rectangle.buffer
        });
        if (mode === MESH_MODES.CIRCLES) {
          gl.drawArrays(gl.TRIANGLES, 0, this.buffers.circle.vertexCount);
        } else {
          gl.drawArrays(gl.TRIANGLES, 0, this.buffers.rectangle.vertexCount);
        }
      }
      if (stroke) {
        prepareBuffers({
          color: stroke,
          buffer: mode === MESH_MODES.CIRCLES
            ? this.buffers.circleBorder.buffer
            : this.buffers.rectangleBorder.buffer
        });
        if (mode === MESH_MODES.CIRCLES) {
          gl.drawArrays(gl.LINES, 0, this.buffers.circleBorder.vertexCount);
        } else {
          gl.drawArrays(gl.LINES, 0, this.buffers.rectangleBorder.vertexCount);
        }
      }
      if (thickStroke) {
        prepareBuffers({
          color: thickStroke,
          buffer: mode === MESH_MODES.CIRCLES
            ? this.buffers.circleThickBorder.buffer
            : this.buffers.rectangleBorder.buffer
        });
        if (mode === MESH_MODES.CIRCLES) {
          gl.drawArrays(gl.TRIANGLES, 0, this.buffers.circleThickBorder.vertexCount);
        } else {
          gl.drawArrays(gl.LINES, 0, this.buffers.rectangleBorder.vertexCount);
        }
      }
    };
    const elementIsSelected = element => {
      if (this.mouseEvent && this.mouseEvent.moved) {
        const underMouseSelection = (this.mouseEvent.elements || []).includes(element);
        if (underMouseSelection) {
          return true;
        }
        if (!this.mouseEvent.shift) {
          return false;
        }
      }
      return this.isCellSelected(element);
    };
    const filtered = this.elements.filter(this.cellIsFiltered);
    const unFiltered = this.elements.filter(aCell => !this.cellIsFiltered(aCell));
    if (getColorConfigurationForCell) {
      this.elements
        .forEach(element => {
          const hovered = element === this.hoveredElement;
          const selected = elementIsSelected(element);
          const elementIsFiltered = filtered.includes(element);
          renderElement(
            element,
            getColorConfigurationForCell(
              element,
              {
                hovered,
                selected,
                filtered: elementIsFiltered
              }
            )
          );
        });
    } else {
      unFiltered
        .filter(element => element !== this.hoveredElement && !elementIsSelected(element))
        .forEach(element => renderElement(element, {stroke: this.primaryColor}));
      unFiltered
        .filter(element => elementIsSelected(element))
        .forEach(element => renderElement(element, {stroke: this.selectedColor}));
      filtered
        .filter(element => element !== this.hoveredElement && !elementIsSelected(element))
        .forEach(element => renderElement(element, {fill: this.primaryColor}));
      filtered
        .filter(element => elementIsSelected(element))
        .forEach(element => renderElement(element, {fill: this.selectedColor}));
      filtered
        .filter(element => element !== this.hoveredElement)
        .forEach(element => renderElement(element, {stroke: this.defaultColor}));
      if (this.hoveredElement) {
        renderElement(
          this.hoveredElement,
          {
            stroke: this.defaultColor,
            fill: elementIsSelected(this.hoveredElement)
              ? this.selectedHoverColor
              : this.primaryHoverColor
          }
        );
      }
    }
    if (this.hoveredElement && elementIsSelectable(this.hoveredElement)) {
      this.canvas.style.cursor = 'pointer';
    } else {
      this.canvas.style.cursor = 'default';
    }
    scaleMatrices.clear();
  };

  renderRect = (x, y, width, height, options = {}) => {
    if (
      !this.ctx ||
      !this.defaultGlProgram ||
      !this.buffers
    ) {
      return;
    }
    if (width < 0 || height < 0) {
      return;
    }
    const {
      stroke,
      fill
    } = options;
    const gl = this.ctx;
    this.setPixelDrawingUniforms();
    this.setViewUniforms(x, y, width, -height);
    if (fill) {
      gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(fill));
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.rectangle.buffer);
      gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      gl.drawArrays(gl.TRIANGLES, 0, this.buffers.rectangle.vertexCount);
    }
    if (stroke) {
      gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(stroke));
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.rectangleBorder.buffer);
      gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      gl.drawArrays(gl.LINES, 0, this.buffers.rectangleBorder.vertexCount);
    }
  }

  renderCircle = (x, y, radius, options = {}) => {
    if (
      !this.ctx ||
      !this.defaultGlProgram ||
      !this.buffers
    ) {
      return;
    }
    if (radius < 0 || !radius) {
      return;
    }
    const {
      stroke,
      fill
    } = options;
    const gl = this.ctx;
    this.setPixelDrawingUniforms();
    this.setViewUniforms(x, y, radius, radius);
    if (fill) {
      gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(fill));
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.circle.buffer);
      gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      gl.drawArrays(gl.TRIANGLES, 0, this.buffers.circle.vertexCount);
    }
    if (stroke) {
      gl.uniform4fv(this.defaultGlProgram.color, new Float32Array(stroke));
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.circleBorder.buffer);
      gl.vertexAttribPointer(this.defaultGlProgram.aPosition, 2, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(this.defaultGlProgram.aPosition);
      gl.drawArrays(gl.LINES, 0, this.buffers.circleBorder.vertexCount);
    }
  }

  drawSelection = () => {
    if (this.mouseEvent && this.mouseEvent.startUnits && this.mouseEvent.endUnits) {
      const {
        x: x1,
        y: y1
      } = this.unitPointToPxPoint(this.mouseEvent.startUnits);
      const {
        x: x2,
        y: y2
      } = this.unitPointToPxPoint(this.mouseEvent.endUnits);
      this.renderRect(
        Math.min(x1, x2),
        Math.min(y1, y2),
        Math.abs(x2 - x1),
        Math.abs(y2 - y1),
        {
          stroke: this.primaryColor,
          fill: this.selectionColor
        }
      );
    }
  };

  drawWell = () => {
    const {radius} = this.props;
    if (!this.ctx || !radius || Number.isNaN(Number(radius))) {
      return;
    }
    const center = this.unitPointToPxPoint({x: this.width / 2.0, y: this.height / 2.0});
    this.renderCircle(
      center.x,
      center.y,
      2.0,
      {
        fill: this.defaultColor
      }
    );
    this.renderCircle(
      center.x,
      center.y,
      radius * this.unitScale,
      {
        stroke: this.defaultColor
      }
    );
  };

  drawRulers = () => {
    this.renderRect(
      0,
      0,
      this.totalWidth,
      this.offsets.top - 1,
      {fill: this.backgroundColor}
    );
    this.renderRect(
      0,
      this.totalHeight - this.offsets.bottom + 1,
      this.totalWidth,
      this.offsets.bottom - 1,
      {fill: this.backgroundColor}
    );
    this.renderRect(
      0,
      0,
      this.offsets.left - 1,
      this.totalHeight,
      {fill: this.backgroundColor}
    );
    this.renderRect(
      this.totalWidth - this.offsets.right + 1,
      0,
      this.offsets.right - 1,
      this.totalHeight,
      {fill: this.backgroundColor}
    );
    if (!this.textCtx || !this.props.showRulers) {
      return;
    }

    this.textCtx.clearRect(
      0,
      0,
      this.textCtx.canvas.width,
      this.textCtx.canvas.height
    );
    const dpr = window.devicePixelRatio;
    this.textCtx.font = `${FONT_SIZE_PX * dpr}px sans-serif`;
    this.textCtx.fillStyle = this.textColor;
    this.textCtx.textAlign = 'center';
    this.textCtx.textBaseline = 'middle';

    let previousVisiblePosition = -Infinity;
    for (let i = 0; i < this.width; i++) {
      const p = this.unitPointToPxPoint({x: i + 0.5, y: 0});
      const x = p.x - this.maxColumnLabelSize / 2.0;
      if (x > previousVisiblePosition) {
        previousVisiblePosition = p.x + this.maxColumnLabelSize / 2.0;
        if (p.x < this.offsets.left || p.x > this.totalWidth - this.offsets.right) {
          continue;
        }
        this.textCtx.fillText(
          `${i + 1}`,
          p.x * dpr,
          this.offsets.top * dpr / 2.0
        );
      }
    }
    previousVisiblePosition = -Infinity;
    this.textCtx.textAlign = 'center';
    this.textCtx.textBaseline = 'middle';
    const verticalSize = FONT_SIZE_PX * 1.5;
    for (let i = 0; i < this.height; i++) {
      const p = this.unitPointToPxPoint({x: 0, y: i + 0.5});
      const y = p.y - verticalSize / 2.0;
      if (y > previousVisiblePosition) {
        previousVisiblePosition = p.y + verticalSize / 2.0;
        if (p.y < this.offsets.top || p.y > this.totalHeight - this.offsets.bottom) {
          continue;
        }
        this.textCtx.fillText(
          getWellRowName(i),
          this.offsets.left * dpr / 2.0,
          p.y * dpr
        );
      }
    }
  };

  drawScrollbars = () => {
    if (!this.ctx || !this.buffers) {
      return;
    }
    const {
      vertical,
      horizontal
    } = this.getScrollBars();
    const scrollBarOptions = {
      stroke: [0.8, 0.8, 0.8, 1.0],
      fill: [0.9, 0.9, 0.9, 1.0]
    };
    const hoveredScrollBarOptions = {
      stroke: [0.8, 0.8, 0.8, 1.0],
      fill: [0.8, 0.8, 0.8, 1.0]
    };
    if (vertical) {
      const {
        size,
        center
      } = vertical;
      this.renderRect(
        this.totalWidth - this.offsets.right / 2.0 - SCROLL_BAR_SIZE / 2.0,
        this.offsets.top + center - size / 2.0,
        SCROLL_BAR_SIZE,
        size,
        this.scrollBarHovered.vertical ? hoveredScrollBarOptions : scrollBarOptions
      );
    }
    if (horizontal) {
      const {
        size,
        center
      } = horizontal;
      this.renderRect(
        this.offsets.left + center - size / 2.0,
        this.totalHeight - this.offsets.bottom / 2.0 - SCROLL_BAR_SIZE / 2.0,
        size,
        SCROLL_BAR_SIZE,
        this.scrollBarHovered.horizontal ? hoveredScrollBarOptions : scrollBarOptions
      );
    }
  }

  resize = () => {
    this.resizerRAF = requestAnimationFrame(this.resize);
    if (
      this.container &&
      (
        this.totalWidth !== this.container.clientWidth ||
        this.totalHeight !== this.container.clientHeight
      )
    ) {
      this.totalWidth = this.container.clientWidth;
      this.totalHeight = this.container.clientHeight;
      this.resizeCanvas();
    }
  };

  resizeCanvas = () => {
    const size = {width: this.totalWidth, height: this.totalHeight};
    resizeCanvas(this.textCanvas, size);
    if (resizeCanvas(this.canvas, size)) {
      this.buildDefaultScale();
    }
    this.setNeedRedraw();
  };

  draw = () => {
    this.renderRAF = requestAnimationFrame(this.draw);
    if (
      !this.ctx ||
      !this.needRender ||
      !this.defaultGlProgram ||
      !this.width ||
      !this.height
    ) {
      return;
    }
    this.needRender = false;
    const gl = this.ctx;
    gl.viewport(0, 0, gl.canvas.width, gl.canvas.height);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.clearColor(...this.backgroundColor);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(this.defaultGlProgram.program);
    gl.program = this.defaultGlProgram.program;
    this.drawMesh();
    this.drawElements();
    this.drawSelection();
    this.drawWell();
    this.drawRulers();
    this.drawScrollbars();
  };

  renderTagsSelector = () => {
    const {
      tags = [],
      selectedTags = []
    } = this.state;
    const {
      searchPlaceholder = 'Search'
    } = this.props;
    if (tags.length === 0) {
      return null;
    }
    const onChange = (selected) => this.setState({
      selectedTags: selected
    }, () => this.setNeedRedraw());
    return (
      <div
        className={styles.tags}
      >
        <Select
          allowClear
          mode="multiple"
          className={styles.tagsSelector}
          dropdownClassName={styles.tagDropdown}
          placeholder={searchPlaceholder}
          notFoundContent="Not found"
          value={selectedTags}
          onChange={onChange}
          filterOption={(input, option) => {
            return (option.props.tagValue || '').toLowerCase().indexOf((input || '').toLowerCase()) >= 0;
          }}
        >
          {
            tags.map((tag) => (
              <Select.OptGroup key={tag.name} label={tag.name}>
                {
                  tag.values.map((value) => (
                    <Select.Option
                      key={value}
                      value={buildTagValue(tag.name, value)}
                      tagValue={value}
                    >
                      {value}
                    </Select.Option>
                  ))
                }
              </Select.OptGroup>
            ))
          }
        </Select>
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      title,
      showElementHint
    } = this.props;
    return (
      <div
        className={
          classNames(
            className,
            styles.container
          )
        }
        style={style}
      >
        <div
          className={styles.header}
        >
          <span className={styles.title}>
            {title}
          </span>
          <div
            className={styles.zoomControls}
          >
            {
              this.fitScale &&
              this.fitCenter && (
                <Icon
                  type="shrink"
                  className={classNames(
                    'cp-hcs-zoom-button',
                    styles.zoomControlBtn
                  )}
                  onClick={this.fit}
                />
              )
            }
            <Icon
              type="minus-circle-o"
              className={classNames(
                'cp-hcs-zoom-button',
                {'cp-disabled': !this.zoomOutAvailable},
                styles.zoomControlBtn
              )}
              onClick={() => this.zoom(-1)}
            />
            <Icon
              type="plus-circle-o"
              className={classNames(
                'cp-hcs-zoom-button',
                {'cp-disabled': !this.zoomInAvailable},
                styles.zoomControlBtn
              )}
              onClick={() => this.zoom(1)}
            />
          </div>
        </div>
        {this.renderTagsSelector()}
        <div
          className={styles.canvasContainer}
          ref={this.initializeContainer}
        >
          <canvas
            ref={this.canvasInitializer}
            style={{position: 'absolute'}}
          />
          {
            this.hoveredElement && showElementHint && (
              <Tooltip
                visible
                title={(<ElementHint element={this.hoveredElement} />)}
                overlayStyle={{pointerEvents: 'none'}}
              >
                <div
                  className={styles.tooltipPoint}
                  style={this.getHoveredElementPointStyle()}
                />
              </Tooltip>
            )
          }
          <canvas
            ref={this.textCanvasInitializer}
            style={{position: 'absolute', pointerEvents: 'none'}}
          />
        </div>
      </div>
    );
  }
}

const CellPropType = PropTypes.shape({
  id: PropTypes.string,
  x: PropTypes.number,
  y: PropTypes.number,
  fieldWidth: PropTypes.number,
  fieldHeight: PropTypes.number
});

const arrayOf = of => PropTypes.oneOfType([PropTypes.object, PropTypes.arrayOf(of)]);

HcsCellSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  title: PropTypes.string,
  cells: arrayOf(CellPropType),
  selected: arrayOf(CellPropType),
  selectionEnabled: PropTypes.bool,
  onChange: PropTypes.func,
  width: PropTypes.number,
  height: PropTypes.number,
  gridMode: PropTypes.oneOf([MESH_MODES.CIRCLES, MESH_MODES.LINES, MESH_MODES.CROSS, MESH_MODES.NONE]),
  showRulers: PropTypes.bool,
  scaleToROI: PropTypes.bool,
  radius: PropTypes.number,
  searchPlaceholder: PropTypes.string,
  showElementHint: PropTypes.bool,
  getColorConfigurationForCell: PropTypes.func
};

HcsCellSelector.defaultProps = {
  gridMode: MESH_MODES.CIRCLES,
  selectionEnabled: true
};

function sizeCorrection (size, cells = [], selector = () => 0) {
  return Math.max(
    size || 0,
    ...(cells || []).map(selector).map(o => Math.ceil(o))
  );
}
HcsCellSelector.widthCorrection = (width, cells) =>
  sizeCorrection(width, cells, cell => cell.x);
HcsCellSelector.heightCorrection = (height, cells) =>
  sizeCorrection(height, cells, cell => cell.y);

export default HcsCellSelector;
