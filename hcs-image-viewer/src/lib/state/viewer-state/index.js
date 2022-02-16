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

import {
  useEffect, useMemo, useReducer, useRef,
} from 'react';
import useMetadata from '../utilities/use-metadata';
import useLoader from '../utilities/use-loader';
import ViewerStateActions from './actions';
import init from './init';
import reducer from './reducer';
import fetchInfo from './fetch-info';

function useImage(metadata, loader, dispatch, options = {}) {
  const {
    selections,
    cache,
    imageTimePosition = 0,
  } = options;
  const asyncIdentifier = useRef(0);
  useEffect(() => {
    const metadataLoaded = metadata
      && loader
      && metadata.Pixels
      && loader.length;
    if (
      metadataLoaded
      && (
        (selections && selections !== cache)
        || !selections
      )
    ) {
      dispatch({ type: ViewerStateActions.setLoading });
      asyncIdentifier.current += 1;
      const identifier = asyncIdentifier.current;
      fetchInfo(
        loader,
        metadata,
        selections,
        imageTimePosition ? { t: imageTimePosition } : undefined,
      )
        .then((data) => {
          if (identifier === asyncIdentifier.current) {
            dispatch({
              type: ViewerStateActions.setDefault,
              ...data,
            });
          }
        })
        .catch((e) => {
          console.warn(`HCS Image Viewer error: ${e.message}`);
          console.error(e);
          dispatch({
            type: ViewerStateActions.setError,
            error: e.message,
          });
        });
    } else if (!metadataLoaded) {
      dispatch({ type: ViewerStateActions.setDefault });
    }
  }, [
    metadata,
    loader,
    imageTimePosition,
    selections,
    cache,
    asyncIdentifier,
    dispatch,
  ]);
}

export default function useViewerState(state) {
  const [viewerState, dispatch] = useReducer(reducer, {}, init);
  const metadata = useMetadata(state);
  const loader = useLoader(state);
  const {
    imageTimePosition = 0,
  } = state;
  const {
    metadata: currentMetadata,
    loader: currentLoader,
    selections: currentSelections,
    builtForSelections: cache,
  } = viewerState;
  const initialOptions = useMemo(() => ({
    imageTimePosition,
  }), [imageTimePosition]);
  // fetching initial data
  useImage(metadata, loader, dispatch, initialOptions);
  const options = useMemo(() => ({
    selections: currentSelections,
    cache,
  }), [currentSelections, cache]);
  // subsequent fetch for another selections
  useImage(currentMetadata, currentLoader, dispatch, options);
  return { state: viewerState, dispatch };
}
