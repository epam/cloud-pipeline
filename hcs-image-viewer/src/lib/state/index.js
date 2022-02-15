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
import useViewerState, { ViewerStateActions } from './viewer-state';

export { default as HCSImageContext } from './context';

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
  const setImageViewportLoaded = useCallback((options) => {
    dispatch({ type: actions.setImageViewportLoaded, ...options });
  }, [dispatch]);
  const setChannelProperties = useCallback((channel, properties) => {
    viewerDispatch({ type: ViewerStateActions.setChannelProperties, channel, properties });
  }, [viewerDispatch]);
  const setColorMap = useCallback((colorMap) => {
    viewerDispatch({ type: ViewerStateActions.setColorMap, colorMap });
  }, [viewerDispatch]);
  const setLensEnabled = useCallback((enabled) => {
    viewerDispatch({ type: ViewerStateActions.setLensEnabled, lensEnabled: enabled });
  }, [viewerDispatch]);
  const setLensChannel = useCallback((channel) => {
    viewerDispatch({ type: ViewerStateActions.setLensChannel, lensChannel: channel });
  }, [viewerDispatch]);
  const callbacks = useMemo(() => ({
    setData,
    setImage,
    setImageViewportLoaded,
    setChannelProperties,
    setColorMap,
    setLensEnabled,
    setLensChannel,
  }), [
    setData,
    setImage,
    setImageViewportLoaded,
    setChannelProperties,
    setColorMap,
    setLensEnabled,
    setLensChannel,
  ]);
  return {
    callbacks,
    dispatch,
    state,
    viewerState,
    viewerDispatch,
  };
}

export { useHCSImageState };
