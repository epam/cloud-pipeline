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

  it('returns no keys without a pipeline or a valid docker image', () => {
    expect(getOptionalParametersFilterKeys()).toEqual([]);
    expect(getOptionalParametersFilterKeys({dockerImage: 'ubuntu:latest'})).toEqual([]);
  });
});

describe('getOptionalParametersFilterDefault', () => {
  const tool = 'cp-docker:443/library/ubuntu:latest';

  it('applies the default value the first time', () => {
    expect(getOptionalParametersFilterDefault({dockerImage: tool, defaultValue: true})).toEqual({
      target: {launched: 'tool:cp-docker:443/library/ubuntu', defaultValue: true},
      value: true
    });
  });

  it('leaves the checkbox alone while the tool and its default stay the same', () => {
    const first = getOptionalParametersFilterDefault({dockerImage: tool, defaultValue: true});
    expect(getOptionalParametersFilterDefault({
      dockerImage: 'CP-DOCKER:443/library/ubuntu:18.04',
      defaultValue: true,
      target: first.target
    })).toEqual({target: first.target, value: undefined});
  });

  it('applies the default value again for another tool or a changed default', () => {
    const {target} = getOptionalParametersFilterDefault({dockerImage: tool, defaultValue: true});
    expect(getOptionalParametersFilterDefault({
      dockerImage: 'cp-docker:443/library/centos',
      defaultValue: true,
      target
    }).value).toBe(true);
    expect(getOptionalParametersFilterDefault({
      dockerImage: tool,
      defaultValue: false,
      target
    }).value).toBe(false);
  });

  it('keys a pipeline by its id, whatever the docker image', () => {
    const pipeline = {id: 42, name: 'my-pipeline'};
    const first = getOptionalParametersFilterDefault({
      pipeline,
      dockerImage: tool,
      defaultValue: false
    });
    expect(first).toEqual({target: {launched: 'pipeline:42', defaultValue: false}, value: false});
    expect(getOptionalParametersFilterDefault({
      pipeline,
      dockerImage: 'cp-docker:443/library/centos',
      defaultValue: false,
      target: first.target
    }).value).toBeUndefined();
  });

  it('never applies a default when neither target sets one', () => {
    const first = getOptionalParametersFilterDefault({dockerImage: tool});
    expect(first.value).toBeUndefined();
    expect(getOptionalParametersFilterDefault({
      dockerImage: 'cp-docker:443/library/centos',
      target: first.target
    }).value).toBeUndefined();
  });

  it('unchecks the checkbox when only the previous target set a default', () => {
    const {target} = getOptionalParametersFilterDefault({dockerImage: tool, defaultValue: true});
    expect(getOptionalParametersFilterDefault({pipeline: {id: 42}, target}))
      .toEqual({target: {launched: 'pipeline:42', defaultValue: undefined}, value: false});
  });
});
