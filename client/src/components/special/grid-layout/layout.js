/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function getTotalDimensions (layout) {
  let maxWidth = 0;
  let maxHeight = 0;
  let minX = Infinity;
  let minY = Infinity;
  for (let i = 0; i < layout.length; i++) {
    const layoutItem = layout[i];
    if (minX > layoutItem.x) {
      minX = layoutItem.x;
    }
    if (minY > layoutItem.y) {
      minY = layoutItem.y;
    }
    if (maxWidth < layoutItem.x + layoutItem.w) {
      maxWidth = layoutItem.x + layoutItem.w;
    }
    if (maxHeight < layoutItem.y + layoutItem.h) {
      maxHeight = layoutItem.y + layoutItem.h;
    }
  }
  // two-columns presentation is better than one-column presentation
  return {
    x: minX,
    y: minY,
    width: Math.max(2, maxWidth - minX),
    height: Math.max(2, maxHeight - minY)
  };
}

function rebuildLayout (layout, gridStyles, rebuildHeights = true, composing = false, move = true) {
  const moveItem = (item, index, array) => {
    const buildBreakPoints = () => {
      const breakPointsX = [];
      const breakPointsY = [];
      for (let i = 0; i < array.length; i++) {
        const layoutItem = array[i];
        if (breakPointsX.indexOf(layoutItem.x) === -1) {
          breakPointsX.push(layoutItem.x);
        }
        if (breakPointsX.indexOf(layoutItem.x + layoutItem.w) === -1) {
          breakPointsX.push(layoutItem.x + layoutItem.w);
        }
        if (breakPointsY.indexOf(layoutItem.y) === -1) {
          breakPointsY.push(layoutItem.y);
        }
        if (breakPointsY.indexOf(layoutItem.y + layoutItem.h) === -1) {
          breakPointsY.push(layoutItem.y + layoutItem.h);
        }
      }
      breakPointsX.sort((a, b) => a - b);
      breakPointsY.sort((a, b) => a - b);
      return {
        breakPointsX,
        breakPointsY
      };
    };
    const zoneIsEmpty = (x1, y1, x2, y2) => {
      for (let i = 0; i < array.length; i++) {
        const layoutItem = array[i];
        if (layoutItem.x <= x1 && layoutItem.x + layoutItem.w >= x1 &&
          layoutItem.x <= x2 && layoutItem.x + layoutItem.w >= x2 &&
          layoutItem.y <= y1 && layoutItem.y + layoutItem.h >= y1 &&
          layoutItem.y <= y2 && layoutItem.y + layoutItem.h >= y2) {
          return false;
        }
      }
      return true;
    };
    const moveY = () => {
      const {breakPointsX, breakPointsY} = buildBreakPoints();
      const yIndex = breakPointsY.indexOf(item.y);
      const x1Index = breakPointsX.indexOf(item.x);
      const x2Index = breakPointsX.indexOf(item.x + item.w);
      let moveTo = item.y;
      for (let i = yIndex - 1; i >= 0; i--) {
        const y1 = breakPointsY[i];
        const y2 = breakPointsY[i + 1];
        let empty = true;
        for (let j = x1Index; j <= x2Index - 1; j++) {
          if (!zoneIsEmpty(breakPointsX[j], y1, breakPointsX[j + 1], y2)) {
            empty = false;
            break;
          }
        }
        if (empty) {
          moveTo = y1;
        } else {
          break;
        }
      }
      if (moveTo !== item.y) {
        item.y = moveTo;
        return true;
      }
      return false;
    };
    const moveX = () => {
      const {breakPointsX, breakPointsY} = buildBreakPoints();
      const xIndex = breakPointsX.indexOf(item.x);
      const y1Index = breakPointsY.indexOf(item.y);
      const y2Index = breakPointsY.indexOf(item.y + item.h);
      let moveTo = item.x;
      for (let i = xIndex - 1; i >= 0; i--) {
        const x1 = breakPointsX[i];
        const x2 = breakPointsX[i + 1];
        let empty = true;
        for (let j = y1Index; j <= y2Index - 1; j++) {
          if (!zoneIsEmpty(x1, breakPointsY[j], x2, breakPointsY[j + 1])) {
            empty = false;
            break;
          }
        }
        if (empty) {
          moveTo = x1;
        } else {
          break;
        }
      }
      if (moveTo !== item.x) {
        item.x = moveTo;
        return true;
      }
      return false;
    };
    const movedByX = moveX();
    const movedByY = moveY();
    return movedByX || movedByY;
  };
  const moveAll = () => {
    let moved = false;
    for (let i = 0; i < layout.length; i++) {
      moved = moved || moveItem(layout[i], i, layout);
    }
    return moved;
  };
  if (move) {
    while (moveAll()) {
    }
  }
  if (rebuildHeights) {
    const dimensions = getTotalDimensions(layout);
    if (composing) {
      dimensions.height = Math.min(3, dimensions.height);
    }
    const widthItem = gridStyles.gridCols / dimensions.width;
    const heightItem = gridStyles.gridRows / dimensions.height;
    return layout.map(item => ({
      i: item.i,
      x: Math.floor((item.x - dimensions.x) * widthItem),
      y: Math.floor((item.y - dimensions.y) * heightItem),
      w: Math.floor(item.w * widthItem),
      h: Math.floor(item.h * heightItem)
    }));
  } else {
    return layout;
  }
}

function attachZone (oldLayout, key, zone, size) {
  if (oldLayout.filter(item => item.i === key).length > 0) {
    return;
  }
  oldLayout.push({
    i: key,
    x: zone.x,
    y: zone.y,
    w: size.w,
    h: size.h
  });
}

function detachZone (oldLayout, key) {
  const [item] = oldLayout.filter(item => item.i === key);
  if (item) {
    oldLayout.splice(oldLayout.indexOf(item), 1);
  }
  return oldLayout;
}

export default function buildLayout (
  {
    defaultState = [],
    storage = 'grid-layout',
    defaultSizes = {},
    panelNeighbors = [],
    gridStyle
  }
) {
  function findAvailablePlaces (oldLayout, key, box) {
    if (oldLayout.filter(item => item.i === key).length > 0) {
      return;
    }
    const createZone = ({x, y, w, h}) => {
      return {
        x,
        y,
        x2: x + w,
        y2: y + h,
        w,
        h,
        size: w * h
      };
    };
    const xSorter = (zoneA, zoneB) => {
      if (zoneA.x < zoneB.x) {
        return -1;
      } else if (zoneA.x > zoneB.x) {
        return 1;
      }
      return 0;
    };
    const ySorter = (zoneA, zoneB) => {
      if (zoneA.y < zoneB.y) {
        return -1;
      } else if (zoneA.y > zoneB.y) {
        return 1;
      }
      return 0;
    };
    const sizeSorter = (zoneA, zoneB) => {
      if (zoneA.size < zoneB.size) {
        return -1;
      } else if (zoneA.size > zoneB.size) {
        return 1;
      }
      return 0;
    };
    const breakpointsX = [];
    if (box.w < Infinity) {
      for (let i = 0; i <= box.w; i++) {
        breakpointsX.push(i);
      }
    } else {
      breakpointsX.push(0);
      breakpointsX.push(box.w);
    }
    const breakpointsY = [0, box.h];
    for (let i = 0; i < oldLayout.length; i++) {
      const layoutItem = oldLayout[i];
      if (breakpointsX.indexOf(layoutItem.x) === -1) {
        breakpointsX.push(layoutItem.x);
      }
      if (breakpointsX.indexOf(layoutItem.x + layoutItem.w) === -1) {
        breakpointsX.push(layoutItem.x + layoutItem.w);
      }
      if (breakpointsY.indexOf(layoutItem.y) === -1) {
        breakpointsY.push(layoutItem.y);
      }
      if (breakpointsY.indexOf(layoutItem.y + layoutItem.h) === -1) {
        breakpointsY.push(layoutItem.y + layoutItem.h);
      }
    }
    breakpointsX.sort((a, b) => a - b);
    breakpointsY.sort((a, b) => a - b);
    let zones = [];
    const layoutItemOverZone = (layoutItem, zone) => {
      return layoutItem.x <= zone.x && layoutItem.x + layoutItem.w >= zone.x &&
        layoutItem.x <= zone.x + zone.w && layoutItem.x + layoutItem.w >= zone.x + zone.w &&
        layoutItem.y <= zone.y && layoutItem.y + layoutItem.h >= zone.y &&
        layoutItem.y <= zone.y + zone.h && layoutItem.y + layoutItem.h >= zone.y + zone.h;
    };
    for (let i = 0; i < breakpointsX.length - 1; i++) {
      for (let j = 0; j < breakpointsY.length - 1; j++) {
        const zone = createZone({
          x: breakpointsX[i],
          y: breakpointsY[j],
          w: breakpointsX[i + 1] - breakpointsX[i],
          h: breakpointsY[j + 1] - breakpointsY[j]
        });
        zone.empty = true;
        for (let z = 0; z < oldLayout.length; z++) {
          if (layoutItemOverZone(oldLayout[z], zone)) {
            zone.empty = false;
            break;
          }
        }
        if (zone.empty) {
          zones.push(zone);
        }
      }
    }
    const buildEmptyZonesOfSize = (size, originalZones) => {
      const result = [];
      const findZoneWithWidthAt = (x, y, w) => {
        for (let j = 0; j < originalZones.length; j++) {
          if (originalZones[j].x === x && originalZones[j].y === y && originalZones[j].w === w) {
            return originalZones[j];
          }
        }
        return null;
      };
      const firstStepZones = [];
      for (let i = 0; i < originalZones.length; i++) {
        const startZone = originalZones[i];
        let nextZone = startZone;
        while (nextZone.y2 - startZone.y < size.h) {
          let next = findZoneWithWidthAt(nextZone.x, nextZone.y2, nextZone.w);
          if (next) {
            nextZone = next;
          } else {
            break;
          }
        }
        if (nextZone.y2 - startZone.y >= size.h) {
          firstStepZones.push(createZone({
            x: startZone.x,
            y: startZone.y,
            w: startZone.w,
            h: nextZone.y2 - startZone.y
          }));
        }
      }
      const findZoneWithHeightAt = (x, y, h) => {
        for (let j = 0; j < firstStepZones.length; j++) {
          if (firstStepZones[j].x === x && firstStepZones[j].y === y && firstStepZones[j].h === h) {
            return firstStepZones[j];
          }
        }
        return null;
      };
      for (let i = 0; i < firstStepZones.length; i++) {
        const startZone = firstStepZones[i];
        let nextZone = startZone;
        while (nextZone.x2 - startZone.x < size.w) {
          let next = findZoneWithHeightAt(nextZone.x2, nextZone.y, nextZone.h);
          if (next) {
            nextZone = next;
          } else {
            break;
          }
        }
        if (nextZone.x2 - startZone.x >= size.w) {
          result.push(createZone({
            x: startZone.x,
            y: startZone.y,
            w: nextZone.x2 - startZone.x,
            h: startZone.h
          }));
        }
      }
      return result;
    };
    const size = defaultSizes[key] || {w: 1, h: 1};
    const availableZones = buildEmptyZonesOfSize(size, zones);
    availableZones.sort((zoneA, zoneB) => {
      return ySorter(zoneA, zoneB) || xSorter(zoneA, zoneB) || sizeSorter(zoneA, zoneB);
    });
    return availableZones.filter((zone, index, array) => {
      const [originalZone] = array.filter(z => z.x === zone.x && z.y === zone.y);
      return array.indexOf(originalZone) === index;
    });
  }
  function getLayoutWeight (layout) {
    let {height} = getTotalDimensions(layout);
    const weight = (items) => {
      const xx = [];
      const yy = [];
      for (let i = 0; i < items.length; i++) {
        xx.push(items[i].x);
        xx.push(items[i].x + items[i].w);
        yy.push(items[i].y);
        yy.push(items[i].y + items[i].h);
      }
      const x1 = Math.min(...xx);
      const x2 = Math.max(...xx);
      const y1 = Math.min(...yy);
      const y2 = Math.max(...yy);
      let square = (x2 - x1) * (y2 - y1);
      for (let i = 0; i < items.length; i++) {
        square -= (items[i].w * items[i].h);
      }
      return square;
    };
    let neighborWeight = 0.0;
    for (let i = 0; i < panelNeighbors.length; i++) {
      const neighbors = panelNeighbors[i];
      const items = layout.filter(item => neighbors.indexOf(item.i) >= 0);
      if (items.length < neighbors.length) {
        continue;
      }
      neighborWeight += weight(items) / 5;
    }
    return height + neighborWeight;
  }
  function extendLayout (currentLayout, keys) {
    keys.sort((keyA, keyB) => {
      const sizeA = defaultSizes[keyA] || {w: 1, h: 1};
      const sizeB = defaultSizes[keyB] || {w: 1, h: 1};
      const squareA = sizeA.w * sizeA.h;
      const squareB = sizeB.w * sizeB.h;
      return squareB - squareA;
    });
    let layout = [...currentLayout];
    let minLayout;
    let minLayoutWeight = Infinity;
    let maxWidth = gridStyle.maxLayoutColumns;
    let maxHeight = Infinity;
    for (let key in defaultSizes) {
      if (defaultSizes.hasOwnProperty(key)) {
        const size = defaultSizes[key];
        if (size.w > maxWidth) {
          maxWidth = size.w;
        }
        if (size.h > maxHeight) {
          maxHeight = size.h;
        }
      }
    }
    const recursiveComposer = () => {
      const layoutWeight = getLayoutWeight(layout);
      if (keys.filter(key => layout.filter(i => i.i === key).length === 0).length === 0) {
        if (!minLayout || minLayoutWeight > layoutWeight) {
          minLayout = [];
          minLayoutWeight = layoutWeight;
          for (let i = 0; i < layout.length; i++) {
            minLayout.push({
              i: layout[i].i,
              x: layout[i].x,
              y: layout[i].y,
              w: layout[i].w,
              h: layout[i].h
            });
          }
        }
        return;
      } else if (minLayoutWeight < layoutWeight) {
        return;
      }
      const composeForKey = (key) => {
        const size = defaultSizes[key] || {w: 1, h: 1};
        const availableZones = findAvailablePlaces(layout, key, {w: maxWidth, h: maxHeight}) ||
          [{
            x: 0,
            y: 0,
            ...size
          }];
        for (let j = 0; j < availableZones.length; j++) {
          attachZone(layout, key, availableZones[j], size);
          recursiveComposer();
          detachZone(layout, key);
        }
      };
      const key = keys.filter(key => layout.filter(item => item.i === key).length === 0)[0];
      composeForKey(key);
    };
    recursiveComposer();
    if (!minLayout) {
      return [];
    } else {
      return rebuildLayout(minLayout || [], gridStyle, true, true);
    }
  }
  return {
    restoreDefaultLayout () {
      this.setPanelsLayout(defaultState, false, false);
    },
    setPanelsLayout (layout, rebuildHeights = true) {
      if (layout) {
        try {
          localStorage.setItem(
            storage,
            JSON.stringify(rebuildLayout(layout, gridStyle, rebuildHeights))
          );
        } catch (___) {}
      }
      return layout;
    },
    getPanelsLayout (rebuildIfNull = true, staticPanels = []) {
      const info = localStorage.getItem(storage);
      let infoCount = 0;
      if (info) {
        try {
          infoCount = JSON.parse(info).length;
        } catch (___) {}
      }
      if (rebuildIfNull && (!info || infoCount === 0)) {
        return this.setPanelsLayout(defaultState, false, false);
      }
      if (info) {
        try {
          const layout = JSON.parse(info);
          if (Array.isArray(layout)) {
            return layout.map(layoutItem => {
              const isStaticPanel = staticPanels.indexOf(layoutItem.i) >= 0;
              return {...layoutItem, static: isStaticPanel};
            });
          }
        } catch (___) {}
      }
      return [];
    },
    addPanels (panels) {
      let currentLayout = this.getPanelsLayout(false);
      if (currentLayout.length > 1) {
        let minWidthDimension = Infinity;
        let minHeightDimension = Infinity;
        for (let i = 0; i < currentLayout.length; i++) {
          const layoutItem = currentLayout[i];
          if (minWidthDimension > layoutItem.w) {
            minWidthDimension = layoutItem.w;
          }
          if (minHeightDimension > layoutItem.h) {
            minHeightDimension = layoutItem.h;
          }
        }
        currentLayout = currentLayout.map(item => ({
          i: item.i,
          x: item.x / minWidthDimension,
          y: item.y / minHeightDimension,
          w: item.w / minWidthDimension,
          h: item.h / minHeightDimension
        }));
      } else if (currentLayout.length === 1) {
        const size = defaultSizes[currentLayout[0].i] || {w: 1, h: 1};
        currentLayout[0].x = 0;
        currentLayout[0].y = 0;
        currentLayout[0].w = size.w;
        currentLayout[0].h = size.h;
      }
      this.setPanelsLayout(extendLayout(currentLayout, panels), false, false);
    },
    removePanel (panel) {
      const panels = this.getPanelsLayout();
      const [item] = panels.filter(p => p.i === panel);
      if (item) {
        panels.splice(panels.indexOf(item), 1);
        this.setPanelsLayout(panels, false);
      }
    }
  };
}
