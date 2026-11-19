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

/* eslint-env jest */

import {excludeNoMachineWithDcv, keepOneDesktopParameter} from './exclusive-desktops';
import {
  CP_CAP_DESKTOP_NM,
  CP_CAP_SYSTEMD_CONTAINER,
  RUN_CAPABILITIES,
  RUN_CAPABILITIES_PARAMETERS
} from '../parameters';

const DCV = RUN_CAPABILITIES.dcv;
const NO_MACHINE = RUN_CAPABILITIES.noMachine;
const SYSTEMD = RUN_CAPABILITIES.systemD;
const DCV_PARAMETER = RUN_CAPABILITIES_PARAMETERS[DCV];

const enabled = (value = true) => ({type: 'boolean', value});

let consoleWarn;

beforeEach(() => {
  consoleWarn = console.warn;
  console.warn = jest.fn();
});

afterEach(() => {
  console.warn = consoleWarn;
});

describe('excludeNoMachineWithDcv', () => {
  it('keeps NICE DCV and switches NoMachine off, if both are selected', () => {
    expect(excludeNoMachineWithDcv([NO_MACHINE, SYSTEMD, DCV])).toEqual([SYSTEMD, DCV]);
    expect(console.warn).toHaveBeenCalledTimes(1);
  });

  it('keeps either of them, if it is the only one', () => {
    expect(excludeNoMachineWithDcv([NO_MACHINE, SYSTEMD])).toEqual([NO_MACHINE, SYSTEMD]);
    expect(excludeNoMachineWithDcv([DCV])).toEqual([DCV]);
    expect(excludeNoMachineWithDcv([])).toEqual([]);
    expect(excludeNoMachineWithDcv(undefined)).toEqual([]);
    expect(console.warn).not.toHaveBeenCalled();
  });
});

describe('keepOneDesktopParameter', () => {
  it('switches the NoMachine parameter off, if NICE DCV is on as well', () => {
    const parameters = {
      [DCV_PARAMETER]: enabled(),
      [CP_CAP_DESKTOP_NM]: enabled(),
      [CP_CAP_SYSTEMD_CONTAINER]: enabled()
    };
    expect(keepOneDesktopParameter(parameters)).toBe(parameters);
    expect(parameters).toEqual({
      [DCV_PARAMETER]: enabled(),
      [CP_CAP_DESKTOP_NM]: enabled(false),
      [CP_CAP_SYSTEMD_CONTAINER]: enabled()
    });
    expect(console.warn).toHaveBeenCalledTimes(1);
  });

  it('switches NICE DCV off instead, if it is not available for the OS or the cloud', () => {
    const parameters = {[DCV_PARAMETER]: enabled(), [CP_CAP_DESKTOP_NM]: enabled()};
    keepOneDesktopParameter(parameters, false);
    expect(parameters).toEqual({[DCV_PARAMETER]: enabled(false), [CP_CAP_DESKTOP_NM]: enabled()});
    expect(console.warn).toHaveBeenCalledTimes(1);
  });

  it('takes the values in any case, as text as well', () => {
    const parameters = {
      [DCV_PARAMETER]: {type: 'string', value: 'TRUE'},
      [CP_CAP_DESKTOP_NM]: {type: 'string', value: 'true'}
    };
    keepOneDesktopParameter(parameters);
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
    expect(parameters[DCV_PARAMETER]).toEqual({type: 'string', value: 'TRUE'});
  });

  it('keeps the parameters, if only one of them is on', () => {
    const noMachineOnly = {[DCV_PARAMETER]: enabled(false), [CP_CAP_DESKTOP_NM]: enabled()};
    const dcvOnly = {[DCV_PARAMETER]: enabled()};
    keepOneDesktopParameter(noMachineOnly);
    keepOneDesktopParameter(dcvOnly);
    expect(noMachineOnly).toEqual({
      [DCV_PARAMETER]: enabled(false),
      [CP_CAP_DESKTOP_NM]: enabled()
    });
    expect(dcvOnly).toEqual({[DCV_PARAMETER]: enabled()});
    expect(keepOneDesktopParameter(undefined)).toEqual({});
    expect(console.warn).not.toHaveBeenCalled();
  });
});
