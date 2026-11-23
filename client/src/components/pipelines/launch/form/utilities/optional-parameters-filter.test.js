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

import {fn} from '@test';
import getOptionalParametersFilterKeys, {
  createOptionalParametersFilterSettings,
  forgetOptionalParametersFilterTarget,
  getOptionalParametersFilterDefault
} from './optional-parameters-filter';

const dockerRegistries = {
  loaded: true,
  value: {
    registries: [{
      path: 'cp-docker:443',
      groups: [{
        name: 'library',
        tools: [{id: 17, image: 'library/ubuntu'}]
      }]
    }]
  }
};

describe('getOptionalParametersFilterKeys', () => {
  it('returns the pipeline id and name for a pipeline', () => {
    const pipeline = {id: 42, name: 'my-pipeline'};
    expect(getOptionalParametersFilterKeys({
      pipeline,
      dockerImage: 'cp-docker:443/library/ubuntu:latest',
      dockerRegistries
    })).toEqual([42, 'my-pipeline']);
  });

  it('returns the tool id and name, without the version, for a tool', () => {
    expect(getOptionalParametersFilterKeys({
      dockerImage: 'cp-docker:443/library/ubuntu:18.04',
      dockerRegistries
    })).toEqual([17, 'cp-docker:443/library/ubuntu']);
  });

  it('returns the tool name lower-cased', () => {
    expect(getOptionalParametersFilterKeys({
      dockerImage: 'CP-Docker:443/Library/Ubuntu:18.04',
      dockerRegistries
    })).toEqual([17, 'cp-docker:443/library/ubuntu']);
  });

  it('returns the tool name alone when the tool is not in the registries', () => {
    expect(getOptionalParametersFilterKeys({
      dockerImage: 'cp-docker:443/library/centos',
      dockerRegistries
    })).toEqual(['cp-docker:443/library/centos']);
  });

  it('returns the tool name alone while the registries are loading', () => {
    const loading = {loaded: false, fetchIfNeededOrWait: fn()};
    expect(getOptionalParametersFilterKeys({
      dockerImage: 'cp-docker:443/library/ubuntu:latest',
      dockerRegistries: loading
    })).toEqual(['cp-docker:443/library/ubuntu']);
  });

  it('leaves out a pipeline name made of digits only', () => {
    expect(getOptionalParametersFilterKeys({pipeline: {id: 77, name: '123'}})).toEqual([77]);
    expect(getOptionalParametersFilterKeys({pipeline: {id: 77, name: 'v123'}}))
      .toEqual([77, 'v123']);
  });

  it('returns the tool id and name of an image with a digest', () => {
    expect(getOptionalParametersFilterKeys({
      dockerImage: 'cp-docker:443/library/ubuntu@sha256:abc',
      dockerRegistries
    })).toEqual([17, 'cp-docker:443/library/ubuntu']);
  });

  it('returns the image name of an image without a registry', () => {
    expect(getOptionalParametersFilterKeys({dockerImage: 'Library/Ubuntu:18.04'}))
      .toEqual(['library/ubuntu']);
  });

  it('returns no keys without a pipeline or a docker image', () => {
    expect(getOptionalParametersFilterKeys()).toEqual([]);
    expect(getOptionalParametersFilterKeys({dockerImage: ' '})).toEqual([]);
  });
});

describe('getOptionalParametersFilterDefault', () => {
  const tool = 'cp-docker:443/library/ubuntu:latest';
  const apply = (options, previous) => getOptionalParametersFilterDefault({
    checked: previous ? previous.target.checked : false,
    target: previous ? previous.target : undefined,
    ...options
  });

  it('applies the default value the first time', () => {
    expect(apply({dockerImage: tool, defaultValue: true})).toEqual({
      target: {
        launched: 'tool:cp-docker:443/library/ubuntu',
        defaultValue: true,
        checked: true,
        touched: false
      },
      value: true
    });
  });

  it('leaves the checkbox alone while the tool and its default stay the same', () => {
    const first = apply({dockerImage: tool, defaultValue: true});
    expect(apply({dockerImage: 'CP-DOCKER:443/library/ubuntu:18.04', defaultValue: true}, first))
      .toEqual({target: first.target, value: undefined});
  });

  it('applies the default value again for another tool or a changed default', () => {
    const first = apply({dockerImage: tool, defaultValue: true});
    expect(apply({dockerImage: 'cp-docker:443/library/centos', defaultValue: true}, first).value)
      .toBe(true);
    expect(apply({dockerImage: tool, defaultValue: false}, first).value).toBe(false);
  });

  it('keeps the user\'s choice when the default value changes later', () => {
    const first = apply({dockerImage: tool, defaultValue: false});
    const clicked = apply({dockerImage: tool, defaultValue: false, checked: true}, first);
    expect(clicked.value).toBeUndefined();
    expect(clicked.target.touched).toBe(true);
    const loaded = apply({dockerImage: tool, defaultValue: true, checked: true}, clicked);
    expect(loaded.value).toBeUndefined();
    expect(apply({dockerImage: tool, defaultValue: false, checked: true}, loaded).value)
      .toBeUndefined();
  });

  it('applies the default value again for another tool after the user\'s choice', () => {
    const first = apply({dockerImage: tool, defaultValue: false});
    const clicked = apply({dockerImage: tool, defaultValue: false, checked: true}, first);
    expect(apply({
      dockerImage: 'cp-docker:443/library/centos',
      defaultValue: false,
      checked: true
    }, clicked)).toEqual({
      target: {
        launched: 'tool:cp-docker:443/library/centos',
        defaultValue: false,
        checked: false,
        touched: false
      },
      value: false
    });
  });

  it('tells apart the tools that are not in the registry/group/tool format', () => {
    const first = apply({dockerImage: 'library/ubuntu', defaultValue: true});
    const clicked = apply({dockerImage: 'library/ubuntu', defaultValue: true, checked: false},
      first);
    expect(apply({dockerImage: 'library/centos', defaultValue: true, checked: false}, clicked)
      .value).toBe(true);
    expect(apply({dockerImage: 'reg/group/tool@sha256:abc'}).target.launched)
      .toBe('tool:reg/group/tool');
  });

  it('keys a pipeline by its id, whatever the docker image', () => {
    const pipeline = {id: 42, name: 'my-pipeline'};
    const first = apply({pipeline, dockerImage: tool, defaultValue: false});
    expect(first).toEqual({
      target: {launched: 'pipeline:42', defaultValue: false, checked: false, touched: false},
      value: false
    });
    expect(apply({pipeline, dockerImage: 'cp-docker:443/library/centos', defaultValue: false},
      first).value).toBeUndefined();
  });

  it('never applies a default when neither target sets one', () => {
    const first = apply({dockerImage: tool});
    expect(first.value).toBeUndefined();
    expect(apply({dockerImage: 'cp-docker:443/library/centos'}, first).value).toBeUndefined();
  });

  it('unchecks the checkbox when only the previous target set a default', () => {
    const first = apply({dockerImage: tool, defaultValue: true});
    expect(apply({pipeline: {id: 42}}, first)).toEqual({
      target: {launched: 'pipeline:42', defaultValue: undefined, checked: false, touched: false},
      value: false
    });
  });
});

describe('forgetOptionalParametersFilterTarget', () => {
  it('makes the default value apply again to the same tool', () => {
    const tool = 'cp-docker:443/library/ubuntu';
    const first = getOptionalParametersFilterDefault({dockerImage: tool, defaultValue: true});
    const clicked = getOptionalParametersFilterDefault({
      dockerImage: tool,
      defaultValue: true,
      checked: false,
      target: first.target
    });
    expect(getOptionalParametersFilterDefault({
      dockerImage: tool,
      defaultValue: true,
      checked: false,
      target: forgetOptionalParametersFilterTarget(clicked.target)
    }).value).toBe(true);
  });

  it('keeps an empty target empty', () => {
    expect(forgetOptionalParametersFilterTarget(undefined)).toBeUndefined();
  });
});

describe('createOptionalParametersFilterSettings', () => {
  const settings = {
    pipelines: {enabled: true, defaultValue: true},
    tools: {enabled: false, defaultValue: undefined}
  };

  it('resolves the pipeline settings by the pipeline keys', () => {
    const resolve = fn(() => settings);
    const get = createOptionalParametersFilterSettings();
    expect(get({pipeline: {id: 42, name: 'p'}, resolve})).toBe(settings.pipelines);
    expect(resolve).toHaveBeenCalledWith([42, 'p']);
  });

  it('resolves the tool settings by the tool keys', () => {
    const resolve = fn(() => settings);
    const get = createOptionalParametersFilterSettings();
    expect(get({dockerImage: 'cp-docker:443/library/ubuntu', dockerRegistries, resolve}))
      .toBe(settings.tools);
    expect(resolve).toHaveBeenCalledWith([17, 'cp-docker:443/library/ubuntu']);
  });

  it('resolves again only when an input changes', () => {
    const resolve = fn(() => settings);
    const get = createOptionalParametersFilterSettings();
    const pipeline = {id: 42};
    const launchForm = {};
    get({pipeline, launchForm, dockerRegistries, resolve});
    get({pipeline, launchForm, dockerRegistries, resolve});
    expect(resolve).toHaveBeenCalledTimes(1);
    get({pipeline, launchForm: {}, dockerRegistries, resolve});
    get({pipeline: {id: 43}, launchForm: {}, dockerRegistries, resolve});
    get({pipeline: {id: 43}, dockerImage: 'reg/group/tool', launchForm: {}, resolve});
    expect(resolve).toHaveBeenCalledTimes(4);
  });

  it('resolves again once the registries load', () => {
    const resolve = fn(() => settings);
    const get = createOptionalParametersFilterSettings();
    const dockerImage = 'cp-docker:443/library/ubuntu';
    const loading = {loaded: false, fetchIfNeededOrWait: fn()};
    get({dockerImage, dockerRegistries: loading, resolve});
    get({dockerImage, dockerRegistries: loading, resolve});
    expect(resolve).toHaveBeenCalledTimes(1);
    get({dockerImage, dockerRegistries, resolve});
    expect(resolve).toHaveBeenLastCalledWith([17, dockerImage]);
  });
});
