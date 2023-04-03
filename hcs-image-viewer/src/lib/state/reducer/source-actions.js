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

export function setSourceInitializing(state) {
  return {
    ...state,
    loader: undefined,
    metadata: undefined,
    imageIndex: 0,
    source: undefined,
    sourceError: undefined,
    sourcePending: true,
    imagePending: false,
    overlayImages: [],
  };
}

export function setSourceError(state, action) {
  const { error } = action;
  const { sourceCallback } = state;
  if (typeof sourceCallback === 'function') {
    setTimeout(() => {
      sourceCallback({ error });
    }, 0);
  }
  return {
    ...state,
    loader: undefined,
    metadata: undefined,
    imageIndex: 0,
    source: undefined,
    sourceError: error,
    sourcePending: false,
    sourceCallback: undefined,
    overlayImages: [],
  };
}

export function setSource(state, action) {
  const { source } = action;
  const { sourceCallback } = state;
  if (typeof sourceCallback === 'function') {
    setTimeout(() => {
      sourceCallback();
    }, 0);
  }
  const array = Array.isArray(source) ? source : [source];
  const loader = array.map((o) => o.data);
  const metadata = array.map((o) => o.metadata);
  return {
    ...state,
    loader,
    metadata,
    imageIndex: 0,
    source,
    sourceError: undefined,
    sourcePending: false,
    sourceCallback: undefined,
    overlayImages: [],
  };
}

export function setImage(state, action) {
  const {
    index,
    ID,
    Name,
    search,
    imageTimePosition = 0,
    imageZPosition = 0,
    mesh,
    overlayImages = [],
  } = action;
  const { metadata = [], imageIndex: currentImageIndex } = state;
  let metadataItem;
  if (index !== undefined && index !== null) {
    metadataItem = metadata[index];
  } else if (ID !== undefined) {
    metadataItem = metadata.find((o) => o.ID === ID);
  } else if (Name !== undefined) {
    metadataItem = metadata.find((o) => (o.Name || o.name || '').toLowerCase() === Name.toLowerCase());
  } else if (search && /^[\d]+_[\d]+$/.test(search)) {
    const [, well, field] = /^([\d]+)_([\d])+$/.exec(search);
    const regExp = new RegExp(`^\\s*well\\s+${well}\\s*,\\s*field\\s+${field}\\s*$`, 'i');
    metadataItem = metadata.find((o) => regExp.test(o.Name || o.name));
  }
  if (metadataItem) {
    const imageIndex = metadata.indexOf(metadataItem);
    return {
      ...state,
      imageIndex,
      imagePending: currentImageIndex !== imageIndex,
      imageTimePosition,
      imageZPosition,
      mesh,
      overlayImages,
    };
  }
  return state;
}

export function setMesh(state, action) {
  const { mesh } = action;
  return { ...state, mesh };
}

export function setOverlayImages(state, action) {
  const { overlayImages = [] } = action;
  return { ...state, overlayImages };
}

export function setImageViewportLoaded(state) {
  if (state.imagePending) {
    return {
      ...state,
      imagePending: false,
    };
  }
  return state;
}
