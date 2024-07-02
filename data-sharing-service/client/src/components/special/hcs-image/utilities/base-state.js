/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

class HCSBaseState {
  /**
   * @type {Object}
   */
  viewer;
  /**
   * @param {Object} viewer
   * @param {string} stateChangeEvent
   */
  constructor (viewer, stateChangeEvent) {
    this.stateChangeEvent = stateChangeEvent;
    this.attachToViewer(viewer);
  }

  attachToViewer (viewer) {
    this.detachFromViewer();
    if (viewer) {
      this.viewer = viewer;
      this.callback = this.onStateChanged.bind(this);
      if (this.stateChangeEvent && this.viewer.Events[this.stateChangeEvent]) {
        this.viewer.addEventListener(
          this.viewer.Events[this.stateChangeEvent],
          this.callback
        );
      }
    }
  }

  detachFromViewer () {
    if (this.viewer) {
      if (this.stateChangeEvent && this.viewer.Events[this.stateChangeEvent]) {
        this.viewer.removeEventListener(
          this.viewer.Events[this.stateChangeEvent],
          this.callback
        );
      }
      this.viewer = undefined;
    }
  }

  onStateChanged (viewer, newState) {
    // empty
  }
}

export default HCSBaseState;
