import useLocalStorageApplicationsList from './use-local-storage-applications-list';
import { useCallback } from "react";

const FOLDER_APPLICATIONS_FAVOURITES = 'FOLDER_APPLICATIONS_FAVOURITES';
const DOCKER_APPLICATIONS_FAVOURITES = 'DOCKER_APPLICATIONS_FAVOURITES';

const FOLDER_APPLICATIONS_HIDDEN = 'FOLDER_APPLICATIONS_HIDDEN';
const DOCKER_APPLICATIONS_HIDDEN = 'DOCKER_APPLICATIONS_HIDDEN';

const Modes = {
  folder: 'folder',
  docker: 'docker'
}

export {
  Modes
};

export default function useFavouriteApplications (mode, property = 'id') {
  const {
    isInList: isFavourite,
    onChange: onFavouriteChange,
    sorter: favouriteSorter
  } = useLocalStorageApplicationsList(
    mode === Modes.docker ? DOCKER_APPLICATIONS_FAVOURITES : FOLDER_APPLICATIONS_FAVOURITES,
    property
  );
  const {
    isInList: isHidden,
    onChange: onHiddenChange,
    sorter: hiddenSorter
  } = useLocalStorageApplicationsList(
    mode === Modes.docker ? DOCKER_APPLICATIONS_HIDDEN : FOLDER_APPLICATIONS_HIDDEN,
    property
  );
  const sorter = useCallback(
    (a, b) => -hiddenSorter(a, b) || favouriteSorter(a, b),
    [hiddenSorter, favouriteSorter]
  );
  const changeFavourite = useCallback((application, favourite) => {
    onFavouriteChange(application, favourite);
    if (favourite) {
      onHiddenChange(application, false);
    }
  }, [onFavouriteChange, onHiddenChange]);
  const changeHidden = useCallback((application, hidden) => {
    onHiddenChange(application, hidden);
    if (hidden) {
      onFavouriteChange(application, false);
    }
  }, [onFavouriteChange, onHiddenChange]);
  const toggleFavourite = useCallback((app) => {
    const appIsFavourite = isFavourite(app);
    changeFavourite(app, !appIsFavourite);
  }, [changeFavourite, isFavourite]);
  const toggleHidden = useCallback((app) => {
    const appIsHidden = isHidden(app);
    changeHidden(app, !appIsHidden);
  }, [changeHidden, isHidden]);
  return {
    isFavourite,
    isHidden,
    toggleFavourite,
    toggleHidden,
    onFavouriteChange: changeFavourite,
    onHiddenChange: changeHidden,
    sorter
  };
}
