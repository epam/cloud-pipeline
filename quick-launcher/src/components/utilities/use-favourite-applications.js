import {useCallback, useEffect, useState} from 'react';

const KEY = 'FOLDER_APPLICATIONS_FAVOURITES';

export default function useFavouriteApplications () {
  const [favourites, setFavourites] = useState([]);
  useEffect(() => {
    try {
      const storageValue = JSON.parse(localStorage.getItem(KEY))
      if (Array.isArray(storageValue)) {
        setFavourites(storageValue);
      }
    } catch (_) {}
  }, [setFavourites]);
  const updateFavourites = useCallback((o) => {
    localStorage.setItem(KEY, JSON.stringify(o || []));
    setFavourites(o || []);
  }, [setFavourites]);
  const isFavourite = useCallback((application) => {
    return !!application && !!favourites.find(p => application.path === p);
  }, [favourites]);
  const onFavouriteChange = useCallback((application, favourite) => {
    if (application) {
      updateFavourites([
        ...favourites.filter(o => o !== application.path),
        ...(favourite ? [application.path] : [])
      ]);
    }
  }, [favourites, updateFavourites]);
  const toggleFavourite = useCallback((application) => {
    onFavouriteChange(application, !isFavourite(application));
  }, [onFavouriteChange, isFavourite]);
  const sorter = useCallback((a, b) => {
    const aFavourite = isFavourite(a);
    const bFavourite = isFavourite(b);
    if (aFavourite === bFavourite) {
      return 0;
    }
    if (aFavourite) {
      return -1;
    }
    return 1;
  }, [isFavourite]);
  return {
    isFavourite,
    toggleFavourite,
    onFavouriteChange,
    sorter
  };
}
