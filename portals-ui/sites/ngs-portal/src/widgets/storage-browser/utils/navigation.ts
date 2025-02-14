import { correctPath } from '@cloud-pipeline/core';
import type { PageMarkers } from '../types';

export const ROOT_PLACEHOLDER = '/';

export const BLANK_MARKER = {
  currentPage: 0,
  markers: [undefined],
};

export function ensureMarkersForPath(path = '', markers: PageMarkers = {}): PageMarkers {
  const correctedPath = correctPath(path) || ROOT_PLACEHOLDER;
  if (markers[correctedPath]) {
    return markers;
  }
  return {
    ...markers,
    [correctedPath]: {
      currentPage: 0,
      markers: [undefined],
    },
  } as PageMarkers;
}

export function insertNextPageMarker(path: string | undefined, marker: string, markers: PageMarkers = {}): PageMarkers {
  const correctedPath = correctPath(path) || ROOT_PLACEHOLDER;
  const newMarkers = ensureMarkersForPath(correctedPath, markers);
  const { currentPage, markers: pathMarkers = [undefined] } = newMarkers[correctedPath];
  if (currentPage + 1 <= pathMarkers.length && marker) {
    const newPathMarkers = pathMarkers.slice(0, currentPage + 1).concat([marker]);
    return {
      ...markers,
      [correctedPath]: {
        currentPage,
        markers: newPathMarkers,
      },
    } as PageMarkers;
  }
  return markers;
}

export function setCurrentPage(path: string, pageFn: (page: number) => number, markers: PageMarkers = {}) {
  if (typeof pageFn !== 'function') {
    return undefined;
  }
  const correctedPath = correctPath(path) || ROOT_PLACEHOLDER;
  const newMarkers = ensureMarkersForPath(correctedPath, markers);
  const { currentPage = 0, markers: pathMarkers = [undefined] } = newMarkers[correctedPath];
  const page = pageFn(currentPage);
  if (page >= 0 && page < pathMarkers.length && page !== currentPage) {
    return {
      ...markers,
      [correctedPath]: {
        currentPage: page,
        markers: pathMarkers.slice(0, page + 1),
      },
    };
  }
  return undefined;
}

export function resetMarkersForPath(path = '/', markers: PageMarkers = {}) {
  const correctedPath = correctPath(path);
  const result = {} as PageMarkers;
  Object.entries(markers).forEach(([pathKey, markers]) => {
    if (pathKey !== correctedPath && !pathKey.startsWith(correctedPath)) {
      result[pathKey] = markers;
    }
  });
  return result;
}
