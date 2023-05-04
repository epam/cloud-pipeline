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

/* eslint-disable react/prop-types */

import React, { useCallback, useMemo, useState } from 'react';
import {
  VivViewer as HmsDbmiVivViewer,
  OverviewView,
  OVERVIEW_VIEW_ID,
  DETAIL_VIEW_ID,
  getDefaultInitialViewState,
} from '@hms-dbmi/viv';
import DetailViewWithMesh from './view';

const defaultHooks = { handleValue: () => {}, handleCoordinate: () => {} };
const defaultViewStates = [];
const defaultLensBorderColor = [255, 255, 255];
const defaultExtensions = [];

function VivViewer(props) {
  const {
    loader,
    contrastLimits,
    colors,
    channelsVisible,
    viewStates: viewStatesProp = defaultViewStates,
    colormap,
    overview,
    overviewOn,
    selections,
    hoverHooks = defaultHooks,
    height,
    width,
    lensEnabled = false,
    lensSelection = 0,
    lensRadius = 100,
    lensBorderColor = defaultLensBorderColor,
    lensBorderRadius = 0.02,
    clickCenter = true,
    transparentColor,
    onViewStateChange,
    onHover,
    onViewportLoad,
    extensions = defaultExtensions,
    deckProps,
    mesh,
    onCellClick,
    overlayImages,
    annotations,
    selectedAnnotation,
    onSelectAnnotation,
  } = props;
  const detailViewState = viewStatesProp?.find((v) => v.id === DETAIL_VIEW_ID);
  const baseViewState = useMemo(
    () => detailViewState
      || getDefaultInitialViewState(loader, { height, width }, 0.5),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [loader, detailViewState],
  );
  const [hoveredCell, setHoveredCell] = useState(undefined);
  const onCellHovered = useCallback((cell) => {
    if (cell && !hoveredCell) {
      setHoveredCell(cell);
    } else if (!cell) {
      setHoveredCell(cell);
    } else if (
      cell
      && hoveredCell
      && cell.column !== hoveredCell.column
      && cell.row !== hoveredCell.row
    ) {
      setHoveredCell(cell);
    }
  }, [hoveredCell, setHoveredCell]);
  const detailView = new DetailViewWithMesh({
    id: DETAIL_VIEW_ID,
    height,
    width,
    mesh,
    onCellHover: onCellHovered,
    hoveredCell,
    onCellClick,
    overlayImages,
    annotations,
    selectedAnnotation,
    onSelectAnnotation,
  });
  const layerConfig = {
    loader,
    contrastLimits,
    colors,
    channelsVisible,
    selections,
    onViewportLoad,
    colormap,
    lensEnabled,
    lensSelection,
    lensRadius,
    lensBorderColor,
    lensBorderRadius,
    extensions,
    transparentColor,
  };
  const views = [];
  const layerProps = [];
  const viewStates = [];
  views.push(detailView);
  layerProps.push(layerConfig);
  viewStates.push({ ...baseViewState, id: DETAIL_VIEW_ID });
  if (overviewOn && loader) {
    const overviewViewState = viewStatesProp?.find(
      (v) => v.id === OVERVIEW_VIEW_ID,
    ) || { ...baseViewState, id: OVERVIEW_VIEW_ID };
    const overviewView = new OverviewView({
      id: OVERVIEW_VIEW_ID,
      loader,
      detailHeight: height,
      detailWidth: width,
      clickCenter,
      ...overview,
    });
    views.push(overviewView);
    layerProps.push({ ...layerConfig, lensEnabled: false });
    viewStates.push(overviewViewState);
  }
  if (!loader) return null;
  return (
    <HmsDbmiVivViewer
      layerProps={layerProps}
      views={views}
      viewStates={viewStates}
      hoverHooks={hoverHooks}
      onViewStateChange={onViewStateChange}
      onHover={onHover}
      deckProps={deckProps}
    />
  );
}

export default VivViewer;
