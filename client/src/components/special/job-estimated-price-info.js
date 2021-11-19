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

import React from 'react';
import {Tooltip} from 'antd';
import styles from './job-estimated-price-info.css';
import '../../staticStyles/tooltip-nowrap.css';

export default function JobEstimatedPriceInfo ({children}) {
  return (
    <Tooltip
      overlay={(
        <div>
          <div>The estimated price is calculated using a machine type only.</div>
          <div>During the job lifetime, it's configuration can be changed (e.g. disks added).</div>
          <div>This additional information will be reflected in the "Billing Reports".</div>
        </div>
      )}
      overlayClassName="job-estimated-price-info"
      mouseEnterDelay={1}
    >
      <span className={styles.info}>
        {children}
      </span>
    </Tooltip>
  );
}
