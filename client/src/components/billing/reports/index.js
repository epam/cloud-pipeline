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
import Filters from './filters';
import Discounts from './discounts';
import {Container, RestoreLayoutProvider} from './layout';
import ThemedReport from './themed-report';
import styles from './reports.css';

function Reports ({children, location, router}) {
  return (
    <ThemedReport>
      <Discounts>
        <RestoreLayoutProvider>
          <Filters location={location} router={router}>
            <Container className={styles.chartsLayout}>
              {children}
            </Container>
          </Filters>
        </RestoreLayoutProvider>
      </Discounts>
    </ThemedReport>
  );
}

export default Reports;
export {default as InstanceReport} from './instance-report';
export {default as StorageReport} from './storage-report';
export {default as GeneralReport} from './general-report';
