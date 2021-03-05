/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import classNames from 'classnames';
import MENU_VIEW from '../enums/menu-view';
import styles from './controls.css';

function FilterControl ({menuView, onExpand, onCollapse, visible}) {
  const handleExpand = (event) => {
    event && event.stopPropagation();
    onExpand && onExpand(event);
  };
  const handleCollapse = (event) => {
    event && event.stopPropagation();
    onExpand && onCollapse(event);
  };
  if (menuView === MENU_VIEW.entirelyCollapsed) {
    return null;
  }
  if (menuView === MENU_VIEW.expanded) {
    return (
      <div
        onClick={handleCollapse}
        className={
          classNames(styles.expandBtn,
            {[styles.expanded]: menuView === MENU_VIEW.expanded})
        }
      >
        collapse...
      </div>);
  }
  if (menuView === MENU_VIEW.collapsed) {
    return (
      <div
        onClick={handleExpand}
        className={styles.expandBtn}
      >
        expand all...
      </div>);
  }
  return null;
};

export default FilterControl;
