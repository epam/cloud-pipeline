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
import styles from './controls.css';

export default function SearchHint () {
  return (
    <div className={styles.hint}>
      <div>
        <span style={{fontSize: 'large'}}>
          The query string supports the following special characters:
        </span>
      </div>
      <div>
        <code>+</code> signifies AND operation
      </div>
      <div>
        <code>|</code> signifies OR operation
      </div>
      <div>
        <code>-</code> negates a single token
      </div>
      <div>
        <code>"</code> wraps a number of tokens to signify a phrase for searching
      </div>
      <div>
        <code>*</code> at the end of a term signifies a prefix query
      </div>
      <div>
        <code>(</code> and <code>)</code> signify precedence
      </div>
      <div>
        <code>~N</code> after a word signifies edit distance (fuzziness)
      </div>
      <div>
        <code>~N</code> after a phrase signifies slop amount
      </div>
      <div>
        <span style={{fontSize: 'larger'}}>
          In order to search for any of these special characters,
          they will need to be escaped with <code>\</code>
        </span>
      </div>
    </div>
  );
}
