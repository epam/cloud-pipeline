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

const autoScaledHint = (
  <div>
    If enabled, the Pool's capacity will be automatically managed, based on a current usage.<br />
    A number of thresholds can be configured to increase to reduce a number of spare nodes<br />
    available for the jobs execution. This allows to keep a certain amount of the hot nodes free<br />
    and guarantee fast initialization times for the runs' instances.
  </div>
);

const minSizeHint = (
  <div>
    Defines the minimal size of the pool. Pool capacity will not decrease lower than this value.<br />
    This parameter defines the initial pool size as well.
  </div>
);

const maxSizeHint = (
  <div>
    Defines the maximum size of the pool during the scale-up process.<br />
    Capacity of the pool will not go above this value.
  </div>
);

const scaleDownThresholdHint = (
  <div>
    If a percent of occupied instances of the pool is lower than this value,<br />
    pool capacity will be decreased
  </div>
);

const scaleUpThresholdHint = (
  <div>
    If a percent of occupied instances of the pool is higher than this value,<br />
    pool capacity will be increased
  </div>
);

const scaleStepHint = (
  <div>
    Defines a number of the nodes, that will be added or removed to/from the pool<br />
    during the scaling process.
  </div>
);

export {
  autoScaledHint,
  minSizeHint,
  maxSizeHint,
  scaleDownThresholdHint,
  scaleUpThresholdHint,
  scaleStepHint
};
