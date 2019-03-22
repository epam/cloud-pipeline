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

export default function highlightText (text, searchString, highlightStyle = {backgroundColor: 'yellow', margin: 0}) {
  text = text || '';
  searchString = searchString || '';
  if (!searchString.length) {
    return text;
  }
  const start = text.toLowerCase().indexOf(searchString.toLowerCase());
  if (start >= 0) {
    return [
      <span key="before" style={{margin: 0}}>{text.substring(0, start)}</span>,
      <span key="found" style={highlightStyle}>{text.substring(start, start + searchString.length)}</span>,
      <span key="after" style={{margin: 0}}>{text.substring(start + searchString.length)}</span>
    ];
  } else {
    return text;
  }
}
