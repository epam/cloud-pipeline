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
import styles from './preview.css';

export default function renderHighlights (item) {
  const highlights = (item.highlights || []).map(h => h);
  if (highlights.length) {
    const processHighlight = (text) => {
      let position = 0;
      let opened = false;
      const expr = /(<highlight>|<\/highlight>)/gm;
      const parts = [];
      let m = expr.exec(text);
      while (m) {
        if (m) {
          const part = text.substring(position, m.index);
          parts.push({
            text: part,
            highlight: opened
          });
          position = m.index + m[1].length;
          opened = (m[1].toLowerCase() === '<highlight>');
        }
        m = expr.exec(text);
      }
      const part = text.substring(position);
      parts.push({
        text: part,
        highlight: opened
      });
      return parts;
    };
    return (
      <div className={styles.highlights}>
        <table>
          <tbody>
          {
            highlights.map((h, index) => {
              return (
                <tr key={index}>
                  <td style={{verticalAlign: 'top', paddingRight: 5, paddingTop: 2, whiteSpace: 'nowrap'}}>
                    Found in {h.fieldName}:
                  </td>
                  <td>
                    {
                      (h.matches || []).map((hh, hIndex) => {
                        const parts = processHighlight(`...${hh}...`);
                        return (
                          <div key={`highlight-${index}-${hIndex}`} style={{margin: 2}}>
                                <span className={styles.highlight}>
                                  {
                                    parts.map((p, pIndex) => {
                                      return (
                                        <span
                                          key={`highlight-${index}-${hIndex}-${pIndex}`}
                                          style={p.highlight ? {
                                            backgroundColor: 'rgb(249, 255, 0)',
                                            color: 'black',
                                            padding: '0px 2px'
                                          } : {}}>
                                          {p.text}
                                        </span>
                                      );
                                    })
                                  }
                                </span>
                          </div>
                        );
                      })
                    }
                  </td>
                </tr>
              );
            })
          }
          </tbody>
        </table>
      </div>
    );
  }
  return null;
};

