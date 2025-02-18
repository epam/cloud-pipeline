import { fetchDataStoragePage } from '@cloud-pipeline/api';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { correctPath } from '@cloud-pipeline/core';
import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { PageMarkers, StoragePaging } from '../types';
import {
  BLANK_MARKER,
  insertNextPageMarker,
  resetMarkersForPath,
  ROOT_PLACEHOLDER,
  setCurrentPage,
} from '../utils/navigation';

export function useStorageNavigation(storageId: number | undefined) {
  const [currentPath, setCurrentPath] = useState<string | undefined>(ROOT_PLACEHOLDER);
  const prevCurrentPath = useRef<string | undefined>(undefined);
  const [markers, setMarkers] = useState<PageMarkers>({});
  const [items, setItems] = useState<DataStorageItem[]>([]);
  const [refreshToken, setRefreshtoken] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>();

  const paging = useMemo<StoragePaging>(() => {
    const currentMarker = markers[currentPath ?? ROOT_PLACEHOLDER] ?? BLANK_MARKER;
    return {
      marker: currentMarker.markers[currentMarker.currentPage],
      currentPage: currentMarker.currentPage,
      canNavigateNext: currentMarker.currentPage + 1 < currentMarker.markers.length,
      canNavigatePrev: currentMarker.currentPage > 0,
    };
  }, [currentPath, markers]);

  const currentMarker = useMemo(() => {
    const currentMarker = markers[currentPath ?? ROOT_PLACEHOLDER];
    return currentMarker ? currentMarker.markers[currentMarker.currentPage] : undefined;
  }, [currentPath, markers]);

  const fetchCurrentPage = useCallback(async () => {
    if (storageId) {
      let pagePath = currentPath === ROOT_PLACEHOLDER ? undefined : currentPath;
      const pathChanged = prevCurrentPath.current !== currentPath;
      if (pagePath) {
        pagePath = correctPath(pagePath, { removeTrailingSlash: true, removeLeadingSlash: true });
      }
      try {
        setPending(true);
        const response = await fetchDataStoragePage({
          id: storageId,
          path: pagePath,
          marker: currentMarker,
        });
        if (response.nextPageMarker) {
          const newMarkers = insertNextPageMarker(pagePath, response.nextPageMarker, markers);
          setMarkers(newMarkers);
        }
        if (response.results) {
          setItems(pathChanged ? response.results : [...items, ...response.results]);
        }
        if (error) {
          setError(undefined);
        }
      } catch (error) {
        if (error instanceof Error) {
          setError(error.message);
        } else {
          setError('Error loading datastorage content.');
        }
      } finally {
        setPending(false);
        prevCurrentPath.current = currentPath;
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMarker, currentPath, storageId, refreshToken]);

  const navigateNextPage = useCallback(() => {
    if (!paging.canNavigateNext) {
      return;
    }
    const newMarkers = setCurrentPage(currentPath ?? ROOT_PLACEHOLDER, (page) => page + 1, markers) as PageMarkers;
    if (newMarkers) {
      setMarkers(newMarkers);
    }
  }, [currentPath, markers, paging.canNavigateNext]);

  const navigatePrevPage = useCallback(() => {
    if (!paging.canNavigatePrev) {
      return;
    }
    const newMarkers = setCurrentPage(currentPath ?? ROOT_PLACEHOLDER, (page) => page - 1, markers) as PageMarkers;
    if (newMarkers) {
      setMarkers(newMarkers);
    }
  }, [currentPath, markers, paging.canNavigatePrev]);

  const resetPageForPath = useCallback(
    (path: string) => {
      if (!path) {
        return;
      }
      const newMarkers = setCurrentPage(path, () => 0, markers) as PageMarkers;
      if (newMarkers) {
        setMarkers(resetMarkersForPath(currentPath, newMarkers));
      }
    },
    [currentPath, markers],
  );

  const changePath = useCallback(
    (path: string) => {
      setCurrentPath(path);
      // setItems([]);
      resetPageForPath(path ?? ROOT_PLACEHOLDER);
    },
    [resetPageForPath],
  );

  const resetNavigation = useCallback(() => {
    setCurrentPath(ROOT_PLACEHOLDER);
    setMarkers({});
    setError(undefined);
  }, []);

  useEffect(() => {
    resetNavigation();
  }, [resetNavigation, storageId]);

  useEffect(() => {
    void fetchCurrentPage();
  }, [fetchCurrentPage]);

  const refreshCurrentPath = useCallback(() => {
    resetPageForPath(currentPath ?? ROOT_PLACEHOLDER);
    setRefreshtoken(refreshToken + 1);
  }, [currentPath, refreshToken, resetPageForPath]);

  return useMemo(
    () => ({
      changePath,
      currentPath,
      prevCurrentPath: prevCurrentPath.current,
      navigatePrevPage,
      navigateNextPage,
      items,
      refreshCurrentPath,
      paging,
      pending,
      error,
    }),
    [changePath, currentPath, navigatePrevPage, navigateNextPage, items, refreshCurrentPath, paging, pending, error],
  );
}
