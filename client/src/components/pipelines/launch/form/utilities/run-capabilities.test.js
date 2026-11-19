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

// The two remote desktops of a run, NICE DCV and NoMachine, at a launch outside the launch form
// (e.g. "Run with default settings"): the capabilities of the user and the tool defaults

import fetchToolOS from './fetch-tool-os';
import {
  applyCapabilities,
  applyCustomCapabilitiesParameters,
  getCapabilitiesPlatformInfo,
  normalizeCapabilities,
  updateCapabilities
} from './run-capabilities';
import {
  CP_CAP_DCV,
  CP_CAP_DESKTOP_NM,
  CP_CAP_SYSTEMD_CONTAINER,
  RUN_CAPABILITIES
} from './parameters';

jest.mock('./fetch-tool-os', () => jest.fn());

const preferences = {launchCapabilities: []};
const enabled = (value = true) => ({type: 'boolean', value});
const ROCKY_ON_AWS = {platform: 'linux', os: 'rocky 10.2', provider: 'AWS'};
const DEBIAN_ON_AWS = {platform: 'linux', os: 'debian 10', provider: 'AWS'};
const ROCKY_ON_GCP = {platform: 'linux', os: 'rocky 10.2', provider: 'GCP'};

let consoleWarn;

beforeEach(() => {
  consoleWarn = console.warn;
  console.warn = jest.fn();
  fetchToolOS.mockReset();
});

afterEach(() => {
  console.warn = consoleWarn;
});

describe('applyCapabilities', () => {
  it('keeps NICE DCV of the user and switches NoMachine of the tool off, where DCV runs', () => {
    const parameters = applyCapabilities(
      {[CP_CAP_DESKTOP_NM]: enabled()},
      [RUN_CAPABILITIES.dcv],
      preferences,
      ROCKY_ON_AWS
    );
    expect(parameters[CP_CAP_DCV]).toEqual(enabled());
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
  });

  it('keeps NoMachine of the tool, where NICE DCV of the user is not offered', () => {
    [DEBIAN_ON_AWS, ROCKY_ON_GCP].forEach((platformInfo) => {
      const parameters = applyCapabilities(
        {[CP_CAP_DESKTOP_NM]: enabled()},
        [RUN_CAPABILITIES.dcv],
        preferences,
        platformInfo
      );
      expect(parameters[CP_CAP_DCV]).toEqual(enabled(false));
      expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled());
    });
  });

  it('keeps NoMachine, if the tool defaults have both where NICE DCV is not offered', () => {
    const parameters = applyCapabilities(
      {[CP_CAP_DCV]: enabled(), [CP_CAP_DESKTOP_NM]: enabled()},
      [],
      preferences,
      DEBIAN_ON_AWS
    );
    expect(parameters[CP_CAP_DCV]).toEqual(enabled(false));
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled());
  });

  it('keeps NICE DCV, if the OS and the cloud are not known', () => {
    ['linux', undefined].forEach((platform) => {
      const parameters = applyCapabilities(
        {[CP_CAP_DESKTOP_NM]: enabled()},
        [RUN_CAPABILITIES.dcv],
        preferences,
        platform
      );
      expect(parameters[CP_CAP_DCV]).toEqual(enabled());
      expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
    });
  });
});

describe('applyCapabilities with the OS and the cloud', () => {
  it('takes the other capabilities for the platform only, as before', () => {
    // SystemD is offered for CentOS and Rocky Linux only: a rerun of an Ubuntu run keeps it
    const parameters = updateCapabilities(
      {},
      [RUN_CAPABILITIES.systemD],
      preferences,
      {platform: 'linux', os: 'ubuntu 24.04', provider: 'AWS'}
    );
    expect(parameters[CP_CAP_SYSTEMD_CONTAINER]).toEqual(enabled());
  });
});

describe('updateCapabilities', () => {
  it('keeps NICE DCV of both, where DCV runs', () => {
    const parameters = updateCapabilities(
      {},
      [RUN_CAPABILITIES.noMachine, RUN_CAPABILITIES.dcv],
      preferences,
      ROCKY_ON_AWS
    );
    expect(parameters[CP_CAP_DCV]).toEqual(enabled());
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
  });
});

describe('normalizeCapabilities', () => {
  it('keeps NICE DCV of the parameters with both', () => {
    const parameters = normalizeCapabilities({
      [CP_CAP_DCV]: enabled(),
      [CP_CAP_DESKTOP_NM]: enabled()
    });
    expect(parameters[CP_CAP_DCV]).toEqual(enabled());
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
  });
});

describe('applyCustomCapabilitiesParameters', () => {
  // A rerun of a run, launched with both through the API: the parameters of the run, before the
  // launch dialog
  const both = () => ({[CP_CAP_DCV]: enabled(), [CP_CAP_DESKTOP_NM]: enabled()});

  it('keeps NoMachine, where NICE DCV is not offered', () => {
    const parameters = applyCustomCapabilitiesParameters(both(), preferences, DEBIAN_ON_AWS);
    expect(parameters[CP_CAP_DCV]).toEqual(enabled(false));
    expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled());
  });

  it('keeps NICE DCV, where it runs, or if the OS is not known', () => {
    [ROCKY_ON_AWS, undefined].forEach((platformInfo) => {
      const parameters = applyCustomCapabilitiesParameters(both(), preferences, platformInfo);
      expect(parameters[CP_CAP_DCV]).toEqual(enabled());
      expect(parameters[CP_CAP_DESKTOP_NM]).toEqual(enabled(false));
    });
  });
});

describe('getCapabilitiesPlatformInfo', () => {
  const cloudRegions = {
    fetchIfNeededOrWait: () => Promise.resolve(),
    value: [{id: 1, provider: 'GCP'}, {id: 2, provider: 'AWS', default: true}]
  };

  it('takes the OS of the docker image and the provider of the region of the run', async () => {
    fetchToolOS.mockReturnValue(Promise.resolve('rocky 10.2'));
    const info = await getCapabilitiesPlatformInfo(
      {dockerImage: 'registry/library/rocky:10.2', cloudRegionId: '1'},
      'linux',
      cloudRegions
    );
    expect(info).toEqual({platform: 'linux', os: 'rocky 10.2', provider: 'GCP'});
    expect(fetchToolOS).toHaveBeenCalledWith('registry/library/rocky:10.2');
  });

  it('takes the default region, if the run has none', async () => {
    fetchToolOS.mockReturnValue(Promise.resolve('rocky 10.2'));
    const info = await getCapabilitiesPlatformInfo({dockerImage: 'image'}, 'linux', cloudRegions);
    expect(info.provider).toBe('AWS');
  });

  it('leaves out what cannot be determined', async () => {
    fetchToolOS.mockReturnValue(Promise.reject(new Error('no registry')));
    const info = await getCapabilitiesPlatformInfo({dockerImage: 'image'}, undefined, undefined);
    expect(info).toEqual({platform: undefined, os: undefined, provider: undefined});
  });
});
