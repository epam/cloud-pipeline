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

import React from 'react';
import {Popover} from 'antd';

export default function ({className, value}) {
  const Renderer = getRenderer(value);
  return (
    <Renderer
      className={className}
      value={value}
    />
  );
}

function getRenderer (value) {
  if (value && Array.isArray(value)) {
    return ArrayRenderer;
  }
  if (value && typeof value === 'object') {
    return ObjectRenderer;
  }
  return DefaultRenderer;
}

function DefaultRenderer ({className, value, style}) {
  return (
    <span
      className={className}
      style={style}
    >
      {value}
    </span>
  );
}

export function ObjectRenderer ({className, value}) {
  const keys = Object.keys(value);
  const details = [];
  let identifier, name;
  for (let i = 0; i < keys.length; i++) {
    if (value.hasOwnProperty(keys[i])) {
      details.push({
        key: keys[i],
        value: value[keys[i]],
        valueRenderer: getRenderer(value[keys[i]])
      });
      if (/^id$/i.test(keys[i])) {
        identifier = `#${value[keys[i]]}`;
      }
      if (/^name$/i.test(keys[i])) {
        name = value[keys[i]];
      }
    }
  }
  return (
    <Popover
      title={undefined}
      content={
        details.length === 0
          ? 'Empty object'
          : (
            <table>
              <tbody>
                {
                  details.map(detail => {
                    const Renderer = detail.valueRenderer;
                    return (
                      <tr key={detail.key}>
                        <th>{detail.key}:</th>
                        <td><Renderer value={detail.value} /></td>
                      </tr>
                    );
                  })
                }
              </tbody>
            </table>
          )
      }
    >
      <a
        className={className}
        style={{
          fontStyle: 'italic',
          color: '#666',
          cursor: 'pointer',
          textDecoration: 'underline'
        }}
      >
        <DefaultRenderer
          value={name || identifier || 'Object'}
        />
      </a>
    </Popover>
  );
}

export function ArrayRenderer ({className, value}) {
  const results = [];
  for (let i = 0; i < value.length; i++) {
    const item = value[i];
    const Renderer = getRenderer(item);
    results.push(<Renderer key={i} value={item} />);
  }
  return (
    <div
      className={className}
    >
      {results}
    </div>
  );
}
