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

/*
 MIT License

 Copyright (c) 2020 viv

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

import React, {
  useEffect,
  useRef,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import {
  AdditiveColormapExtension,
  LensExtension,
  getDefaultInitialViewState,
  DETAIL_VIEW_ID,
} from '@hms-dbmi/viv';
import VivViewer from './components/viv-viewer';
import useHCSImageState from '../state';
import useElementSize from './utilities/use-element-size';
import getZoomLevel from '../state/utilities/get-zoom-level';

const additiveColorMapExtension = new AdditiveColormapExtension();
const lensExtension = new LensExtension();

const deckProps = {
  glOptions: {
    preserveDrawingBuffer: true,
  },
};

const colorMapExtensions = [additiveColorMapExtension];
const defaultExtensions = [lensExtension];

function HCSImageViewer(
  {
    className,
    onStateChange,
    onRegisterStateActions,
    onViewerStateChanged,
    style,
    minZoomBackOff = 0,
    maxZoomBackOff = undefined,
    defaultZoomBackOff = 0,
    overview,
    onCellClick,
  },
) {
  const {
    state,
    viewerState,
    callbacks,
  } = useHCSImageState();
  const {
    mesh,
    overlayImages,
    pending
  } = state;
  const containerRef = useRef();
  const size = useElementSize(containerRef);
  useEffect(() => {
    if (onStateChange) {
      onStateChange(state);
    }
  }, [state, onStateChange]);
  useEffect(() => {
    if (onViewerStateChanged) {
      onViewerStateChanged(viewerState);
    }
  }, [viewerState, onViewerStateChanged]);
  useEffect(() => {
    if (onRegisterStateActions) {
      onRegisterStateActions(callbacks);
    }
  }, [callbacks, onRegisterStateActions]);
  const {
    setImageViewportLoading,
    setImageViewportLoaded,
  } = callbacks || {};
  const {
    channelsVisibility = [],
    contrastLimits = [],
    colors = [],
    selections = [],
    ready = false,
    colorMap,
    loader,
    useLens,
    lensEnabled,
    lensChannel,
    pending: viewerStatePending
  } = viewerState;
  useEffect(() => {
    if (typeof setImageViewportLoading === 'function') {
      setImageViewportLoading();
    }
  }, [selections, loader, setImageViewportLoading]);
  const [viewState, setViewState] = useState(undefined);
  useEffect(() => {
    if (
      loader
      && loader.length
      && size.width
      && size.height
    ) {
      const [first] = Array.isArray(loader) ? loader : [loader];
      const last = Array.isArray(loader) ? loader[loader.length - 1] : loader;
      const defaultViewState = [{
        ...getDefaultInitialViewState(loader, size, defaultZoomBackOff),
        id: DETAIL_VIEW_ID,
        minZoom: minZoomBackOff !== undefined
          ? getZoomLevel(first, size, minZoomBackOff)
          : -Infinity,
        maxZoom: maxZoomBackOff !== undefined
          ? getZoomLevel(last, size, maxZoomBackOff)
          : Infinity,
      }];
      setViewState(defaultViewState);
    } else {
      setViewState(undefined);
    }
  }, [
    loader,
    size,
    setViewState,
    minZoomBackOff,
    maxZoomBackOff,
    defaultZoomBackOff,
  ]);
  const readyForRendering = loader
    && ready
    && size.width
    && size.height
    && viewState;
  return (
    <div
      className={className}
      style={({ position: 'relative', ...style || {} })}
      ref={containerRef}
    >
      {
        readyForRendering && (
          <VivViewer
            mesh={pending || viewerStatePending ? undefined : mesh}
            overlayImages={overlayImages}
            contrastLimits={contrastLimits}
            colors={colors}
            channelsVisible={channelsVisibility}
            loader={loader}
            selections={selections}
            height={size.height}
            width={size.width}
            extensions={colorMap ? colorMapExtensions : defaultExtensions}
            colormap={colorMap || 'viridis'}
            onViewportLoad={setImageViewportLoaded}
            viewStates={viewState}
            overviewOn={!!overview}
            overview={overview}
            lensSelection={useLens && lensEnabled ? lensChannel : undefined}
            lensEnabled={useLens && lensEnabled}
            onCellClick={onCellClick}
            deckProps={deckProps}
          />
        )
      }
    </div>
  );
}

HCSImageViewer.propTypes = {
  className: PropTypes.string,
  onStateChange: PropTypes.func,
  onRegisterStateActions: PropTypes.func,
  onViewerStateChanged: PropTypes.func,
  // eslint-disable-next-line react/forbid-prop-types
  style: PropTypes.object,
  minZoomBackOff: PropTypes.number,
  maxZoomBackOff: PropTypes.number,
  defaultZoomBackOff: PropTypes.number,
  // eslint-disable-next-line react/forbid-prop-types
  overview: PropTypes.object,
  onCellClick: PropTypes.func,
};

HCSImageViewer.defaultProps = {
  className: undefined,
  onStateChange: undefined,
  onRegisterStateActions: undefined,
  onViewerStateChanged: undefined,
  style: undefined,
  minZoomBackOff: 0,
  maxZoomBackOff: undefined,
  defaultZoomBackOff: 0,
  overview: undefined,
  onCellClick: undefined,
};

export default HCSImageViewer;
