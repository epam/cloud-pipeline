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
import {
  CheckCircleOutlined,
  ClockCircleFilled,
  DownloadOutlined,
  ExclamationCircleFilled,
  HourglassOutlined,
  LoadingOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined
} from '@ant-design/icons';

export const DefaultIconSet = {
  [Statuses.failure]: ExclamationCircleFilled,
  [Statuses.paused]: PauseCircleOutlined,
  [Statuses.pausing]: PauseCircleOutlined,
  [Statuses.pulling]: DownloadOutlined,
  [Statuses.queued]: HourglassOutlined,
  [Statuses.resuming]: PlayCircleOutlined,
  [Statuses.running]: PlayCircleOutlined,
  [Statuses.scheduled]: LoadingOutlined,
  [Statuses.stopped]: ClockCircleFilled,
  [Statuses.success]: CheckCircleOutlined,

  [Statuses.unknown]: PlayCircleOutlined
};

export function getRunStatusIcon (status, iconSet) {
  if (iconSet && iconSet.hasOwnProperty(status) && !!iconSet[status]) {
    return iconSet[status];
  } else {
    return DefaultIconSet[status];
  }
}
