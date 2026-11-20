/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

'use strict';

// Pins the timezone the suites format dates in, so a date assertion means the
// same thing on every machine.
//
// Assigning `process.env.TZ` here does not work: Node 14 reads the zone once at
// startup, and a setup file runs long after that — measured, the offset stays
// the machine's. Setting it on the npm scripts instead would not work on
// Windows without another dependency. So the pin goes where the code that
// formats dates actually reads it: src/utils/displayDate.js formats through
// moment-timezone in *local* time, and `moment.tz.setDefault` is what local
// means to it.
//
// The limit of this, which the test rules spell out: raw `Date` local-time
// methods (`getHours`, `toString`, ...) still follow the machine. Assert
// through the app's own date helpers, or in UTC.

const moment = require('moment-timezone');
const {testTimezone} = require('../config');

moment.tz.setDefault(testTimezone);

module.exports = testTimezone;
