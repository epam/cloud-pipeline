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

import React from 'react';

export default function LabelsRenderer ({className, style, labelClassName, labels}) {
  const labelsList = [];
  for (let key in labels) {
    if (labels.hasOwnProperty(key)) {
      labelsList.push({
        key: key,
        value: labels[key]
      });
    }
  }
  if (labelsList.length === 0) {
    return null;
  }
  return (
    <div
      className={className}
      style={style}
    >
      {
        labelsList.map(l => (
          <span
            className={labelClassName}
            key={l.key}
          >
            {l.value}
          </span>
        ))
      }
    </div>
  );
};
