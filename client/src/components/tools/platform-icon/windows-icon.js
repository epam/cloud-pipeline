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
import styles from './tool-icons.css';

export default function WindowsIcon ({className, style}) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className={
        classNames(
          styles.toolIcon,
          styles.windows,
          className
        )
      }
      style={style}
      viewBox="0 0 88 88"
    >
      <path
        xmlns="http://www.w3.org/2000/svg"
        /* eslint-disable-next-line */
        d="m0,12.402,35.687-4.8602,0.0156,34.423-35.67,0.20313zm35.67,33.529,0.0277,34.453-35.67-4.9041-0.002-29.78zm4.3261-39.025,47.318-6.906,0,41.527-47.318,0.37565zm47.329,39.349-0.0111,41.34-47.318-6.6784-0.0663-34.739z"
      />
    </svg>
  );
}
