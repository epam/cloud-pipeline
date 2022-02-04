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

import { useEffect, useReducer, useRef } from 'react';
import useMetadata from '../utilities/use-metadata';
import guessRgb from '../utilities/guess-rgb';
import useLoader from '../utilities/use-loader';
import isInterleaved from '../utilities/is-interleaved';
import { buildDefaultSelection, getBoundingCube, getMultiSelectionStats } from '../utilities/tiff-pixel-source-utilities';
import { GlobalDimensionFields } from '../constants';

function mapChannel(channel, index) {
  return channel.Name || channel.name || channel.ID || `Channel ${index + 1}`;
}

const red = [255, 0, 0];
const green = [0, 255, 0];
const blue = [0, 0, 255];
const white = [255, 255, 255];
const pink = [255, 0, 255];
const violet = [154, 0, 255];
const yellow = [255, 255, 0];
const orange = [255, 60, 0];

const BYTE_RANGE = [0, 255];

const COLOR_PALETTE = [
  blue,
  green,
  pink,
  yellow,
  orange,
  violet,
  white,
  red,
];

export const ViewerStateActions = {
  setDefault: 'set-default',
  setLoading: 'set-loading',
  setError: 'set-error',
  setChannelProperties: 'set-channel-properties',
  setColorMap: 'set-color-map',
};

function init() {
  return {
    identifiers: [],
    channels: [],
    channelsVisibility: [],
    selections: [],
    globalSelection: undefined,
    colors: [],
    domains: [],
    contrastLimits: [],
    useLens: false,
    useColorMap: false,
    colorMap: '',
    use3D: false,
    pixelValues: [],
    xSlice: [0, 1],
    ySlice: [0, 1],
    zSlice: [0, 1],
    ready: false,
    isRGB: false,
    shapeIsInterleaved: false,
    pending: false,
    globalDimensions: [],
    metadata: [],
    loader: [],
    error: undefined,
  };
}

function changeChannelProperties(state, channelIndex, properties) {
  const propertiesArray = Object
    .entries(properties || {})
    .filter(([property]) => state && state[property] && Array.isArray(state[property]));
  if (propertiesArray.length > 0) {
    const newState = { ...(state || {}) };
    propertiesArray.forEach(([property, value]) => {
      const propertyValue = newState[property];
      newState[property] = [...propertyValue];
      newState[property][channelIndex] = value;
    });
    return newState;
  }
  return state;
}

function reducer(state, action) {
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
    case ViewerStateActions.setDefault: {
      const {
        identifiers = [],
        channels = [],
        selections = [],
        colors = [],
        domains = [],
        contrastLimits = [],
        useLens = false,
        useColorMap = false,
        colorMap = useColorMap ? state.colorMap : '',
        xSlice = [0, 1],
        ySlice = [0, 1],
        zSlice = [0, 1],
        use3D = false,
        ready = false,
        isRGB = false,
        shapeIsInterleaved = false,
        globalDimensions = [],
        metadata = [],
        loader = [],
      } = action;
      return {
        identifiers,
        channels,
        channelsVisibility: channels.map(() => true),
        selections,
        globalSelection: selections[0],
        pixelValues: new Array(selections.length).fill('-----'),
        colors,
        domains,
        contrastLimits,
        useLens,
        useColorMap,
        colorMap,
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

export default function useViewerState(state) {
  const [viewerState, dispatch] = useReducer(reducer, {}, init);
  const metadata = useMetadata(state);
  const loader = useLoader(state);
  const asyncIdentifier = useRef(0);
  useEffect(() => {
    async function fetchInfo() {
      try {
        const { shape, labels = [] } = loader[0] || {};
        const globalDimensions = labels
          .map((label, index) => ({ label, size: shape[index] || 0 }))
          .filter((dimension) => dimension.size > 1
            && GlobalDimensionFields.includes(dimension.label));
        const selections = buildDefaultSelection(loader[0]);
        asyncIdentifier.current += 1;
        const identifier = asyncIdentifier.current;
        const { Pixels = {} } = metadata;
        const {
          Channels = [],
        } = Pixels;
        let contrastLimits = [];
        let colors = [];
        let domains = [];
        let useLens = false;
        let useColorMap = false;
        const isRGB = guessRgb(metadata);
        const shapeIsInterleaved = isRGB && isInterleaved(shape);
        if (isRGB) {
          if (isInterleaved(shape)) {
            contrastLimits = [BYTE_RANGE.slice()];
            domains = [BYTE_RANGE.slice()];
            colors = [red];
          } else {
            contrastLimits = [
              BYTE_RANGE.slice(),
              BYTE_RANGE.slice(),
              BYTE_RANGE.slice(),
            ];
            domains = [
              BYTE_RANGE.slice(),
              BYTE_RANGE.slice(),
              BYTE_RANGE.slice(),
            ];
            colors = [red, green, blue];
          }
          useLens = false;
          useColorMap = false;
        } else {
          const stats = await getMultiSelectionStats({
            loader,
            selections,
            use3d: false,
          });
          if (identifier !== asyncIdentifier.current) {
            return;
          }
          domains = stats.domains.slice();
          contrastLimits = stats.contrastLimits.slice();
          // If there is only one channel, use white.
          colors = stats.domains.length === 1
            ? [white]
            : stats.domains.map((_, i) => COLOR_PALETTE[i]);
          useLens = Channels.length > 1;
          useColorMap = true;
        }
        const channels = Channels.map(mapChannel);
        const [xSlice, ySlice, zSlice] = getBoundingCube(loader);
        dispatch({
          type: ViewerStateActions.setDefault,
          identifiers: channels.map((name, index) => `${name || 'channel'}-${index}`),
          channels,
          selections,
          useLens,
          useColorMap,
          colors,
          domains,
          contrastLimits,
          xSlice,
          ySlice,
          zSlice,
          ready: true,
          isRGB,
          shapeIsInterleaved,
          globalDimensions,
          metadata,
          loader,
        });
      } catch (e) {
        console.warn(`HCS Image Viewer error: ${e.message}`);
        console.error(e);
        dispatch({
          type: ViewerStateActions.setError,
          error: e.message,
        });
      }
    }
    if (metadata && loader) {
      dispatch({ type: ViewerStateActions.setLoading });
      fetchInfo();
    } else {
      dispatch({ type: ViewerStateActions.setDefault });
    }
  }, [
    metadata,
    loader,
    asyncIdentifier,
    dispatch,
  ]);
  return { state: viewerState, dispatch };
}
