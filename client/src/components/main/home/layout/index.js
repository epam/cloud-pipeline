/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {AsyncLayout} from '../../../special/grid-layout';
import gridStyle from './grid-style';
import defaultState from './default-panels-state';
import defaultSizes from './default-panels-sizes';
import neighbors from './panel-neighbors';
import Panels, {mapPanelIdentifier} from './panels';
import PanelIcons from './panel-icons';
import PanelTitles from './panel-titles';
import PanelInfos from './panel-informations';

const PANELS_LAYOUT_STORAGE_KEY = 'panelsLayout';

try {
  const layout = JSON.parse(localStorage.getItem(PANELS_LAYOUT_STORAGE_KEY));
  let modified = false;
  layout.forEach(panel => {
    const identifier = mapPanelIdentifier(panel.i);
    modified = modified || identifier !== panel.i;
    panel.i = identifier;
  });
  if (modified) {
    localStorage.setItem(PANELS_LAYOUT_STORAGE_KEY, JSON.stringify(layout));
  }
} catch (_) {}

const userLayout = (...opts) => inject('uiNavigation')(
  AsyncLayout.inject(
    ({uiNavigation}) => new Promise((resolve) => {
      uiNavigation
        .fetchDashboard()
        .then(dashboard => {
          resolve({
            defaultState: dashboard || defaultState,
            storage: PANELS_LAYOUT_STORAGE_KEY,
            defaultSizes,
            panelNeighbors: neighbors,
            gridStyle
          });
        });
    })
  )(...opts)
);

export {
  AsyncLayout,
  userLayout,
  gridStyle as GridStyles,
  Panels,
  PanelIcons,
  PanelInfos,
  PanelTitles
};
