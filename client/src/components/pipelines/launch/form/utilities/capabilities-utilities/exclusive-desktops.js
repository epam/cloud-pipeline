/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {RUN_CAPABILITIES, RUN_CAPABILITIES_PARAMETERS} from '../parameters';

// A run cannot have both remote desktops, NICE DCV and NoMachine: NICE DCV is kept, as the run
// scripts do, unless NICE DCV is not offered for the OS of the tool or the cloud of the run
const NO_MACHINE_OFF_WARNING =
  'cannot enable both NICE DCV and NoMachine - switching off NoMachine capability';
const DCV_OFF_WARNING =
  'cannot enable both NICE DCV and NoMachine, and NICE DCV is not available - ' +
  'switching off NICE DCV capability';

/**
 * Removes NoMachine from the capabilities, if they have NICE DCV as well. The capabilities shall be
 * the ones offered for the OS of the tool and the cloud of the run already
 * @param {string[]} capabilities
 * @returns {string[]}
 */
export function excludeNoMachineWithDcv (capabilities = []) {
  if (
    capabilities.includes(RUN_CAPABILITIES.dcv) &&
    capabilities.includes(RUN_CAPABILITIES.noMachine)
  ) {
    console.warn(NO_MACHINE_OFF_WARNING);
    return capabilities.filter((capability) => capability !== RUN_CAPABILITIES.noMachine);
  }
  return capabilities;
}

function parameterIsEnabled (parameters, parameter) {
  return !!parameters[parameter] &&
    `${parameters[parameter].value}`.toLowerCase() === 'true';
}

/**
 * Keeps one of the desktop parameters of the run, if both NICE DCV and NoMachine are switched on:
 * NICE DCV, or NoMachine, if NICE DCV is not available for the OS of the tool or the cloud of
 * the run
 * @param {Object} parameters - the run parameters, changed in place
 * @param {boolean} [dcvAvailable=true] - NICE DCV is offered for the OS and the cloud (or they are
 * not known)
 * @returns {Object} the parameters
 */
export function keepOneDesktopParameter (parameters = {}, dcvAvailable = true) {
  const dcv = RUN_CAPABILITIES_PARAMETERS[RUN_CAPABILITIES.dcv];
  const noMachine = RUN_CAPABILITIES_PARAMETERS[RUN_CAPABILITIES.noMachine];
  if (parameterIsEnabled(parameters, dcv) && parameterIsEnabled(parameters, noMachine)) {
    console.warn(dcvAvailable ? NO_MACHINE_OFF_WARNING : DCV_OFF_WARNING);
    parameters[dcvAvailable ? noMachine : dcv] = {
      type: 'boolean',
      value: false
    };
  }
  return parameters;
}
