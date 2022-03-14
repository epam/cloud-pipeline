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

import {action, observable} from 'mobx';

class SourceState {
  @observable pending = false;
  constructor (viewer) {
    this.attachToViewer(viewer);
  }

  attachToViewer (viewer) {
    this.detachFromViewer();
    if (viewer) {
      this.viewer = viewer;
      this.viewer.addEventListener(
        this.viewer.Events.stateChanged,
        this.onStateChanged
      );
    }
  }

  detachFromViewer () {
    if (this.viewer) {
      this.viewer.removeEventListener(
        this.viewer.Events.stateChanged,
        this.onStateChanged
      );
    }
  }

  @action
  onStateChanged = (viewer, newState) => {
    const {
      imagePending = false,
      sourcePending: pending = false
    } = newState || {};
    this.pending = pending || imagePending;
  };
}

export default SourceState;
