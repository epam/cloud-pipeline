import {useCallback, useEffect, useState} from 'react';

const FOLDER_APPLICATIONS = 'FOLDER_APPLICATIONS_FAVOURITES';
const DOCKER_APPLICATIONS = 'DOCKER_APPLICATIONS_FAVOURITES';

export {
  FOLDER_APPLICATIONS,
  DOCKER_APPLICATIONS
};

export default function useFavouriteApplications (key, property = 'id') {
  const [favourites, setFavourites] = useState([]);
  useEffect(() => {
    try {
      const storageValue = JSON.parse(localStorage.getItem(key))
      if (Array.isArray(storageValue)) {
        setFavourites(storageValue);
      }
    } catch (_) {}
  }, [setFavourites]);
  const updateFavourites = useCallback((o) => {
    localStorage.setItem(key, JSON.stringify(o || []));
    setFavourites(o || []);
  }, [setFavourites]);
  const isFavourite = useCallback((application) => {
    return !!application && !!favourites.find(p => `${application[property]}` === `${p}`);
  }, [favourites, property]);
  const onFavouriteChange = useCallback((application, favourite) => {
    if (application) {
      updateFavourites([
        ...favourites.filter(o => `${o}` !== `${application[property]}`),
        ...(favourite ? [application[property]] : [])
      ]);
    }
  }, [favourites, updateFavourites, property]);
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
