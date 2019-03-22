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

import styles from '../HomePage.css';

export const GridStyles = {
  draggableHandle: styles.panelHeader,
  gridCols: 24,
  gridRows: 24,
  padding: 0,
  panelMargin: 5,
  top: 40,
  rowHeight: function (containerHeight) {
    return (containerHeight - 2 * this.padding - this.gridRows * this.panelMargin - this.top) /
      this.gridRows;
  },
  scrollBarSize: 15
};
