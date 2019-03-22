/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {inject} from 'mobx-react';
const FAVOURITES_KEY = 'favourites';
const DISPLAY_ONLY_FAVOURITES_KEY = 'display_only_favourites';

export function favouriteStorage (WrappedComponent) {
  return inject((stores, parameters) => {
    if (parameters.panelKey) {
      return {
        getFavourites: parameters.getFavourites ||
        (() => getFavouritesForPanel(parameters.panelKey)),
        setFavourites: parameters.setFavourites ||
        ((itemId, isFavourite) => setFavouritesForPanel(parameters.panelKey, itemId, isFavourite))
      };
    }
  })(WrappedComponent);
}

export function getDisplayOnlyFavourites () {
  const value = localStorage.getItem(DISPLAY_ONLY_FAVOURITES_KEY);
  if (value) {
    return JSON.parse(value);
  }
  return false;
}

export function setDisplayOnlyFavourites (value) {
  try {
    localStorage.setItem(DISPLAY_ONLY_FAVOURITES_KEY, JSON.stringify(value));
  } catch (___) {}
}

function getFavourites () {
  let favouritesJson = localStorage.getItem(FAVOURITES_KEY);
  if (favouritesJson) {
    try {
      return JSON.parse(favouritesJson);
    } catch (__) {}
  }
  return {};
}

function setFavourites (favourites) {
  try {
    localStorage.setItem(FAVOURITES_KEY, JSON.stringify(favourites || {}));
  } catch (___) {}
}

export function setFavouritesForPanel (panel, itemId, isFavourite) {
  const favourites = getFavourites();
  if (!favourites[panel]) {
    favourites[panel] = [];
  }
  if (isFavourite && favourites[panel].indexOf(itemId) === -1) {
    favourites[panel].push(itemId);
    setFavourites(favourites);
  } else if (!isFavourite) {
    const index = favourites[panel].indexOf(itemId);
    if (index >= 0) {
      favourites[panel].splice(index, 1);
      setFavourites(favourites);
    }
  }
}

export function getFavouritesForPanel (panel) {
  const favourites = getFavourites();
  if (!favourites[panel]) {
    favourites[panel] = [];
  }
  return favourites[panel];
}
