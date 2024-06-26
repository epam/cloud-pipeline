import {useCallback, useEffect, useState} from 'react';
import downloadDataStorageItem from '../../models/cloud-pipeline-api/data-storage-item-download';

let cache = new Map();

function wrapDownloadPromise (promise) {
  return new Promise((resolve) => {
    promise
      .then((blob) => {
        const fileReader = new FileReader();
        fileReader.onload = function () {
          resolve(this.result);
        };
        fileReader.onerror = function () {
          resolve(undefined);
        };
        fileReader.readAsDataURL(blob);
      })
      .catch(() => resolve(undefined));
  });
}

export default function useApplicationIcon (storage, path) {
  const [icon, setIcon] = useState(undefined);
  const [pending, setPending] = useState(true);
  useEffect(() => {
    if (storage && path) {
      const key = `${storage}/${path}`;
      if (!cache.has(key)) {
        cache.set(
          key,
          wrapDownloadPromise(downloadDataStorageItem(storage, path))
        );
      }
      const promise = cache.get(key);
      setPending(true);
      promise
        .then(setIcon)
        .then(() => setPending(false));
    } else {
      setPending(false);
    }
  }, [storage, path, setIcon, setPending]);
  const clearCache = useCallback(() => {
    if (storage && path) {
      const key = `${storage}/${path}`;
      if (cache.has(key)) {
        cache.delete(key);
      }
    }
  }, [storage, path]);
  return {
    pending,
    icon,
    clearCache,
  };
}
