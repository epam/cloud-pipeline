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

import Statuses from './run-statuses';

// ant.design icon set
// https://2x.ant.design/components/icon/

export const DefaultIconSet = {
  [Statuses.failure]: 'exclamation-circle',
  [Statuses.paused]: 'pause-circle-o',
  [Statuses.pausing]: 'pause-circle-o',
  [Statuses.pulling]: 'download',
  [Statuses.queued]: 'hourglass',
  [Statuses.resuming]: 'play-circle-o',
  [Statuses.running]: 'play-circle-o',
  [Statuses.scheduled]: 'loading',
  [Statuses.stopped]: 'clock-circle',
  [Statuses.success]: 'check-circle-o',

  [Statuses.unknown]: 'play-circle-o'
};

export function getRunStatusIcon (status, iconSet) {
  if (iconSet && iconSet.hasOwnProperty(status) && !!iconSet[status]) {
    return iconSet[status];
  } else {
    return DefaultIconSet[status];
  }
}
