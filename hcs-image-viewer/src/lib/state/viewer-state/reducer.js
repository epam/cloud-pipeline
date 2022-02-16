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

import ViewerStateActions from './actions';
import changeChannelProperties from './change-channel-properties';
import { GlobalDimensionFields } from '../constants';

const EMPTY_ARRAY = [];
const DEFAULT_SLICE = [0, 1];

export default function reducer(state, action) {
  switch (action.type) {
    case ViewerStateActions.setLoading: {
      return { ...state, pending: true, error: undefined };
    }
    case ViewerStateActions.setChannelProperties: {
      const { channel, properties } = action;
      return changeChannelProperties(state, channel, properties);
    }
    case ViewerStateActions.setColorMap: {
      const { colorMap = '' } = action;
      const { useColorMap } = state;
      if (useColorMap) {
        return { ...state, colorMap };
      }
      return state;
    }
    case ViewerStateActions.setLensChannel: {
      const { lensChannel = 0 } = action;
      const { lensEnabled, useLens } = state;
      if (lensEnabled && useLens) {
        return { ...state, lensChannel };
      }
      return state;
    }
    case ViewerStateActions.setLensEnabled: {
      const { lensEnabled = false } = action;
      const {
        lensEnabled: currentLensEnabled,
        useLens,
      } = state;
      if (useLens && !currentLensEnabled && lensEnabled) {
        return { ...state, lensEnabled, lensChannel: 0 };
      }
      return { ...state, lensEnabled: false };
    }
    case ViewerStateActions.setGlobalPosition: {
      const { position = {} } = action;
      const {
        globalDimensions = [],
        selections = [],
        globalSelection = {},
      } = state;
      const correctDimensionPosition = (dimension, x) => {
        const shape = globalDimensions.find((o) => o.label === dimension);
        const { size = 0 } = shape || {};
        return Math.max(0, Math.min(size, Math.round(x)));
      };
      const filtered = Object
        .entries(position)
        .filter(([dimension]) => GlobalDimensionFields.includes(dimension)
          && globalDimensions.find((o) => o.label === dimension))
        .map(([dimension, dPosition]) => ({
          dimension,
          position: correctDimensionPosition(dimension, dPosition),
        }));
      if (filtered.length > 0) {
        const newSelectionsPart = filtered
          .map(({ dimension, position: x }) => ({ [dimension]: x }))
          .reduce((r, c) => ({ ...r, ...c }), {});
        const newGlobalSelection = {
          ...globalSelection,
          ...newSelectionsPart,
        };
        const newSelections = selections.map((channelSelections) => ({
          ...channelSelections,
          ...newSelectionsPart,
        }));
        return {
          ...state,
          selections: newSelections,
          globalSelection: newGlobalSelection,
        };
      }
      return state;
    }
    case ViewerStateActions.setDefault: {
      const {
        identifiers = EMPTY_ARRAY,
        channels = EMPTY_ARRAY,
        selections = EMPTY_ARRAY,
        colors = EMPTY_ARRAY,
        domains = EMPTY_ARRAY,
        contrastLimits = EMPTY_ARRAY,
        useLens = false,
        useColorMap = false,
        colorMap = useColorMap ? state.colorMap : '',
        lensEnabled = false,
        lensChannel = 0,
        xSlice = DEFAULT_SLICE,
        ySlice = DEFAULT_SLICE,
        zSlice = DEFAULT_SLICE,
        use3D = false,
        ready = false,
        isRGB = false,
        shapeIsInterleaved = false,
        globalDimensions = EMPTY_ARRAY,
        metadata,
        loader,
      } = action;
      return {
        identifiers,
        channels,
        channelsVisibility: channels.map(() => true),
        selections,
        builtForSelections: selections,
        globalSelection: (selections || [])[0],
        pixelValues: new Array((selections || []).length).fill('-----'),
        colors,
        domains,
        contrastLimits,
        useLens,
        useColorMap,
        colorMap,
        lensEnabled,
        lensChannel,
        xSlice,
        ySlice,
        zSlice,
        use3D,
        ready,
        isRGB,
        shapeIsInterleaved,
        globalDimensions,
        pending: false,
        error: undefined,
        metadata,
        loader,
      };
    }
    case ViewerStateActions.setError: {
      const { error } = action;
      return {
        ...state,
        error,
        pending: false,
      };
    }
    default:
      return state;
  }
}
