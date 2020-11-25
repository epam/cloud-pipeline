/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import AWSRegionTag from '../../special/AWSRegionTag';

function InstanceProperties ({instance}) {
  const parts = [
    {
      key: 'CPU',
      value: instance.vcpu
    },
    {
      key: 'RAM',
      value: instance.memory
    },
    {
      key: 'GPU',
      value: instance.gpu
    }
  ].filter(p => p.value);
  if (parts.length === 0) {
    return null;
  }
  return (
    <span style={{marginLeft: 5, fontSize: 'smaller'}}>
      ({parts.map(p => `${p.key}: ${p.value}`).join(', ')})
    </span>
  );
}

function InstanceDetails ({allInstanceTypes, instance: name, instances = []}) {
  if (!name) {
    return null;
  }
  const array = instances.length > 0
    ? instances
    : (allInstanceTypes.loaded ? (allInstanceTypes.value || []) : []);
  if (array.length === 0) {
    return (<span>{name}</span>);
  }
  const instance = array
    .find(i => i.name === name);
  if (!instance) {
    return (<span>{name}</span>);
  }
  return (
    <span>
      <AWSRegionTag regionId={instance.regionId} />
      <span>{instance.name}</span>
      <InstanceProperties instance={instance} />
    </span>
  );
}

export default inject('allInstanceTypes')(observer(InstanceDetails));
