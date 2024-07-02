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

import React from 'react';
import moment from 'moment-timezone';

export default function getHoveredElementsInfo (hoveredItems = [], styles = {}) {
  const dates = [...new Set(hoveredItems.map((item) => item.item.date))];
  const renderInfoForDate = (date) => {
    const elements = hoveredItems.filter((item) => item.item.date === date);
    const result = [];
    for (let e = 0; e < elements.length; e += 1) {
      const {
        dataset,
        item,
        color
      } = elements[e];
      const {
        value
      } = item;
      const {
        name
      } = dataset;
      result.push((
        <div
          className={styles.row}
          key={`element-${e}`}
        >
          {
            color && (
              <div
                className={styles.color}
                style={{background: color}}
              />
            )
          }
          {
            name && (
              <span className={styles.dataset}>
                {name}:
              </span>
            )
          }
          <span className={styles.value}>{value}</span>
        </div>
      ));
    }
    return result;
  };
  return (
    <div>
      {
        dates.map((date) => (
          <div key={`${date}`}>
            <div>
              <b>{moment.unix(date).format('D MMMM YYYY, HH:mm:ss')}</b>
            </div>
            <div>
              {renderInfoForDate(date)}
            </div>
          </div>
        ))
      }
    </div>
  );
}
