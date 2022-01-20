/*
 * Copyright 2022-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

const INTERSECTION_DISTANCE = 12;

const getElementsAtXQuadrant = (chart, event) => {
  const elementsAtX = chart.getElementsAtXAxis(event);
  const isLeftHalfClick = elementsAtX[0]._view.x - event.x < 0;
  const datasetsInfo = elementsAtX.map(dataset => ({
    nextIndex: isLeftHalfClick ? dataset._index + 1 : dataset._index,
    prevIndex: isLeftHalfClick ? dataset._index : dataset._index - 1,
    datasetIndex: dataset._datasetIndex
  }));
  const elementsAtXQuadrant = datasetsInfo
    .reduce((acc, info) => {
      const datasetElements = chart.getDatasetMeta(info.datasetIndex).data;
      const prevElements = datasetElements
        .filter(meta => meta._index === info.prevIndex);
      const nextElements = datasetElements
        .filter(meta => meta._index === info.nextIndex);
      acc.prev = [...acc.prev, ...prevElements];
      acc.next = [...acc.next, ...nextElements];
      return acc;
    }, {
      prev: [],
      next: []
    });
  return elementsAtXQuadrant;
};

const getInterpolatedCoordsAtX = (elements, event) => {
  const ids = [...new Set([...elements.prev, ...elements.next]
    .map(el => el._datasetIndex)
  )];
  return ids.map(id => {
    const prevPoint = elements.prev
      .find(element => element._datasetIndex === id);
    const nextPoint = elements.next
      .find(element => element._datasetIndex === id);
    if (!prevPoint || !nextPoint) {
      return null;
    }
    const prevX = prevPoint._model.x;
    const prevY = prevPoint._model.y;
    const nextX = nextPoint._model.x;
    const nextY = nextPoint._model.y;
    const interpolatedY = prevY === nextY
      ? nextY
      : ((event.x - prevX) / (nextX - prevX) + (prevY / (nextY - prevY))) * (nextY - prevY);
    return {
      dataIndex: id,
      prev: prevPoint,
      next: nextPoint,
      lineY: interpolatedY,
      distanceY: Math.abs(event.y - interpolatedY)
    };
  }).filter(Boolean);
};

const chartEventIntersections = (chart, event) => {
  if (!chart || !event) {
    return undefined;
  }
  const XQuadrantElements = getElementsAtXQuadrant(chart, event);
  const coords = getInterpolatedCoordsAtX(XQuadrantElements, event);
  if (coords && coords.length) {
    return (getInterpolatedCoordsAtX(XQuadrantElements, event) || [])
      .filter(coords => coords.distanceY < INTERSECTION_DISTANCE)
      .sort((coordA, coordB) => coordA.distanceY - coordB.distanceY);
  }
  return [];
};

export default chartEventIntersections;
