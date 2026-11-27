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

const modeHint = (
  <div>
    <b>Default</b> pools keep a number of nodes up on a weekly recurring schedule.<br />
    <b>Future dated</b> pools reserve capacity for a single one-time window and cannot
    be autoscaled.<br />
    <b>ML Block</b> pools purchase a concrete AWS Capacity Block offering for a single
    one-time window and cannot be autoscaled.
  </div>
);

const poolTypeHint = (
  <div>
    <b>Standard</b> - a pool node runs one job at a time, as every pool does today.<br />
    <b>Shareable node</b> - several jobs may share one pool node, which the platform<br />
    divides between them.<br />
    The pool type is chosen when the pool is created and cannot be changed afterwards.
  </div>
);

const searchFromHint = (
  <div>
    The earliest day the reservation may begin - the request is sent as that day's own<br />
    start, 00:00. Together with <b>Search until</b> it defines a search window: the cloud<br />
    provider (AWS) reserves the requested <b>Duration</b> somewhere inside that window, as<br />
    soon as matching capacity is available. The granted dates are shown on the pool once<br />
    the reservation is made.<br />
    Must be between 6 and 121 days from today.
  </div>
);

const searchUntilHint = (
  <div>
    The end of the search window - the reservation must finish by then.<br />
    The window has to be at least as long as <b>Duration</b>; the provider picks the<br />
    actual start inside it, so these two dates are not the reservation's own bounds.
  </div>
);

const commitmentDurationHint = (
  <div>
    How long the capacity is committed for, in whole days, minimum 14.<br />
    <b>Search until</b> is sent as that day's own end, 23:59:59.999, so the window must<br />
    span more than this many days - an N-day duration needs N+1 calendar days between<br />
    <b>Search from</b> and <b>Search until</b>.
  </div>
);

export {
  autoScaledHint,
  minSizeHint,
  maxSizeHint,
  scaleDownThresholdHint,
  scaleUpThresholdHint,
  scaleStepHint,
  modeHint,
  poolTypeHint,
  searchFromHint,
  searchUntilHint,
  commitmentDurationHint
};
