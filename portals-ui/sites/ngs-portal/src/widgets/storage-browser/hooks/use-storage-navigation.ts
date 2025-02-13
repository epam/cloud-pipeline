import { fetchDataStoragePage } from '@cloud-pipeline/api';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { useState, useMemo, useCallback, useEffect } from 'react';
import type { PageMarkers, StoragePaging } from '../types';
import {
  BLANK_MARKER,
  insertNextPageMarker,
  ROOT_PLACEHOLDER,
  setCurrentPage,
} from '../utils/navigation';

export function useStorageNavigation(storageId: number) {
  const [currentPath, setCurrentPath] = useState<string | undefined>(
    ROOT_PLACEHOLDER,
  );
  const [markers, setMarkers] = useState<PageMarkers>({});
  const [items, setItems] = useState<DataStorageItem[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>();

  const paging = useMemo<StoragePaging>(() => {
    const currentMarker =
      markers[currentPath ?? ROOT_PLACEHOLDER] ?? BLANK_MARKER;
    return {
      marker: currentMarker.markers[currentMarker.currentPage],
      currentPage: currentMarker.currentPage,
      canNavigateNext:
        currentMarker.currentPage + 1 < currentMarker.markers.length,
      canNavigatePrev: currentMarker.currentPage > 0,
    };
  }, [currentPath, markers]);

  const currentMarker = useMemo(() => {
    const currentMarker = markers[currentPath ?? ROOT_PLACEHOLDER];
    return currentMarker
      ? currentMarker.markers[currentMarker.currentPage]
      : undefined;
  }, [currentPath, markers]);

  const setupMarkersForPath = useCallback(
    (path: string = ROOT_PLACEHOLDER) => {
      if (!markers[path]) {
        const updatedMarkers = {
          ...markers,
          [path]: BLANK_MARKER,
        } as PageMarkers;
        setMarkers(updatedMarkers);
      }
    },
    [markers],
  );

  const fetchCurrentPage = useCallback(async () => {
    try {
      setPending(true);
      const response = await fetchDataStoragePage({
        id: storageId,
        path: currentPath === ROOT_PLACEHOLDER ? undefined : currentPath,
        marker: currentMarker,
      });
      if (response.nextPageMarker) {
        const newMarkers = insertNextPageMarker(
          currentPath,
          response.nextPageMarker,
          markers,
        );
        setMarkers(newMarkers);
      }
      setItems(response.results);
    } catch (error) {
      if (error instanceof Error) {
        setError(error.message);
      } else {
        setError('Error loading datastorage content.');
      }
    } finally {
      setPending(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMarker, currentPath, storageId]);

  const navigateNextPage = useCallback(() => {
    if (!paging.canNavigateNext) {
      return;
    }
    const newMarkers = setCurrentPage(
      currentPath ?? ROOT_PLACEHOLDER,
      (page) => page + 1,
      markers,
    ) as PageMarkers;
    if (newMarkers) {
      setMarkers(newMarkers);
    }
  }, [currentPath, markers, paging.canNavigateNext]);

  const navigatePrevPage = useCallback(() => {
    if (!paging.canNavigatePrev) {
      return;
    }
    const newMarkers = setCurrentPage(
      currentPath ?? ROOT_PLACEHOLDER,
      (page) => page - 1,
      markers,
    ) as PageMarkers;
    if (newMarkers) {
      setMarkers(newMarkers);
    }
  }, [currentPath, markers, paging.canNavigatePrev]);

  const changePath = useCallback(
    (path: string) => {
      setCurrentPath(path);
      setupMarkersForPath(path);
    },
    [setupMarkersForPath],
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

  return useMemo(
    () => ({
      changePath,
      currentPath,
      navigatePrevPage,
      navigateNextPage,
      items,
      refresh: fetchCurrentPage,
      paging,
      pending,
      error,
    }),
    [
      changePath,
      error,
      navigateNextPage,
      navigatePrevPage,
      fetchCurrentPage,
      currentPath,
      pending,
      items,
      paging,
    ],
  );
}
