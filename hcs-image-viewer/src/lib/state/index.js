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

import { useCallback, useMemo, useReducer } from 'react';
import reducer from './reducer';
import actions from './actions';
import useSource from './utilities/use-source';
import useViewerState from './viewer-state';
import viewerActions from './viewer-state/actions';

function initReducerState() {
  return {};
}

function useHCSImageState() {
  const [state, dispatch] = useReducer(reducer, {}, initReducerState);
  useSource(state, dispatch);
  const {
    state: viewerState,
    dispatch: viewerDispatch,
  } = useViewerState(state);
  const setData = useCallback((url, offsetsUrl, callback) => {
    dispatch({
      url,
      offsetsUrl,
      callback,
      type: actions.setData,
    });
  }, [dispatch]);
  const setImage = useCallback((options) => {
    dispatch({ type: actions.setImage, ...options });
  }, [dispatch]);
  const setMesh = useCallback((mesh) => {
    dispatch({ type: actions.setMesh, mesh });
  }, [dispatch]);
  const setOverlayImages = useCallback((overlayImages = []) => {
    dispatch({ type: actions.setOverlayImages, overlayImages });
  }, [dispatch]);
  const setAnnotations = useCallback((annotations = []) => {
    dispatch({ type: actions.setAnnotations, annotations });
  }, [dispatch]);
  const setSelectedAnnotation = useCallback((annotation) => {
    dispatch({ type: actions.setSelectedAnnotation, selectedAnnotation: annotation });
  }, [dispatch]);
  const setImageViewportLoaded = useCallback((options) => {
    dispatch({ type: actions.setImageViewportLoaded, ...options });
    viewerDispatch({ type: viewerActions.setLoaded });
  }, [dispatch, viewerDispatch]);
  const setImageViewportLoading = useCallback(() => {
    viewerDispatch({ type: viewerActions.setLoading });
  }, [viewerDispatch]);
  const setChannelProperties = useCallback((channel, properties) => {
    viewerDispatch({ type: viewerActions.setChannelProperties, channel, properties });
  }, [viewerDispatch]);
  const setDefaultChannelsColors = useCallback((defaultColors = {}) => {
    viewerDispatch({ type: viewerActions.setDefaultChannelsColors, defaultColors });
  }, [viewerDispatch]);
  const setColorMap = useCallback((colorMap) => {
    viewerDispatch({ type: viewerActions.setColorMap, colorMap });
  }, [viewerDispatch]);
  const setLensEnabled = useCallback((enabled) => {
    viewerDispatch({ type: viewerActions.setLensEnabled, lensEnabled: enabled });
  }, [viewerDispatch]);
  const setLensChannel = useCallback((channel) => {
    viewerDispatch({ type: viewerActions.setLensChannel, lensChannel: channel });
  }, [viewerDispatch]);
  const setGlobalPosition = useCallback((position) => {
    viewerDispatch({ type: viewerActions.setGlobalPosition, position });
  }, [viewerDispatch]);
  const setLockChannels = useCallback((lock) => {
    viewerDispatch({ type: viewerActions.setLockChannels, lock });
  }, [viewerDispatch]);
  const callbacks = useMemo(() => ({
    setData,
    setImage,
    setImageViewportLoading,
    setImageViewportLoaded,
    setChannelProperties,
    setDefaultChannelsColors,
    setColorMap,
    setLensEnabled,
    setLensChannel,
    setGlobalPosition,
    setLockChannels,
    setMesh,
    setOverlayImages,
    setAnnotations,
    setSelectedAnnotation,
  }), [
    setData,
    setImage,
    setImageViewportLoading,
    setImageViewportLoaded,
    setChannelProperties,
    setDefaultChannelsColors,
    setColorMap,
    setLensEnabled,
    setLensChannel,
    setGlobalPosition,
    setLockChannels,
    setMesh,
    setOverlayImages,
    setAnnotations,
    setSelectedAnnotation,
  ]);
  return {
    callbacks,
    dispatch,
    state,
    viewerState,
    viewerDispatch,
  };
}

export default useHCSImageState;
