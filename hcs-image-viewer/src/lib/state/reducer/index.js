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

import actions from '../actions';
import setDataAction from './set-data';
import setErrorAction from './set-error';
import * as sourceActions from './source-actions';

/**
 * @typedef {Object} HCSImageStateAction
 * @property {String} type
 */

/**
 *
 * @param state
 * @param {HCSImageStateAction} action
 */
function reducer(state, action) {
  switch (action.type) {
    case actions.setData:
      return setDataAction(state, action);
    case actions.setError:
      return setErrorAction(state, action);
    case actions.setSourceInitializing:
      return sourceActions.setSourceInitializing(state);
    case actions.setSourceError:
      return sourceActions.setSourceError(state, action);
    case actions.setSource:
      return sourceActions.setSource(state, action);
    case actions.setImage:
      return sourceActions.setImage(state, action);
    case actions.setImageViewportLoaded:
      return sourceActions.setImageViewportLoaded(state);
    case actions.setMesh:
      return sourceActions.setMesh(state, action);
    case actions.setOverlayImages:
      return sourceActions.setOverlayImages(state, action);
    case actions.setAnnotations:
      return sourceActions.setAnnotations(state, action);
    case actions.setSelectedAnnotation:
      return sourceActions.setSelectedAnnotation(state, action);
    default:
      return state;
  }
}
export default reducer;
