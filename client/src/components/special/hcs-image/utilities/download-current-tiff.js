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

export function downloadAvailable (viewer) {
  if (!viewer) {
    return false;
  }
  const {
    viewerState
  } = viewer;
  const {
    loader,
    pending
  } = viewerState || {};
  return !pending &&
    loader &&
    loader.length > 0;
}

/**
 * @param {object} viewer
 * @param {{wellView: boolean?, wellId: string?}} options
 */
export function downloadCurrentTiff (viewer, options = {}) {
  if (downloadAvailable(viewer)) {
    const {
      wellId,
      wellView
    } = options;
    let fileName;
    if (wellId && wellView) {
      fileName = wellId;
    } else if (wellId) {
      const {
        viewerState
      } = viewer;
      const {
        metadata
      } = viewerState || {};
      const {Name = wellId} = metadata;
      const parts = Name.split(',').map(o => o.trim());
      fileName = parts.map(o => /(\s|^)well(\s|$)/i.test(o) ? wellId : o).join(', ');
    }
    viewer.makeSnapshot(fileName);
  }
}
