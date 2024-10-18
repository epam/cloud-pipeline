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

import styles from './StatusIcon.css';
import Statuses from './run-statuses';

export default {
  [Statuses.failure]: 'cp-runs-table-icon-red',
  [Statuses.paused]: 'cp-runs-table-icon-blue',
  [Statuses.pausing]: `cp-runs-table-icon-blue ${styles.blink}`,
  [Statuses.pulling]: 'cp-runs-table-icon-blue',
  [Statuses.queued]: 'cp-runs-table-icon-blue',
  [Statuses.nodePending]: 'cp-runs-table-icon-yellow',
  [Statuses.resuming]: `cp-runs-table-icon-blue ${styles.blink}`,
  [Statuses.running]: 'cp-runs-table-icon-blue',
  [Statuses.scheduled]: 'cp-runs-table-icon-blue',
  [Statuses.stopped]: 'cp-runs-table-icon-yellow',
  [Statuses.success]: 'cp-runs-table-icon-green',

  [Statuses.unknown]: 'cp-runs-table-icon-yellow'
};
