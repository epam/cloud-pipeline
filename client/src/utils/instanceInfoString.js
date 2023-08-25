/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

const cpuMapper = (cpu, hyperThreadingDisabled = false) => {
  return hyperThreadingDisabled && !Number.isNaN(Number(cpu))
    ? (cpu / 2.0)
    : cpu;
};

function instanceInfoString (instance, hyperThreadingDisabled = false) {
  if (!instance) {
    return '';
  }
  const {gpu, gpuDevice} = instance;
  const cpuString = cpuMapper(instance.vcpu, hyperThreadingDisabled);
  let gpuString = '';
  if (gpu) {
    gpuString = `, GPU: ${instance.gpu}`;
  }
  if (gpu && gpuDevice) {
    const manufacturer = gpuDevice.manufacturer || gpuDevice.name
      ? ` x ${gpuDevice.manufacturer || ''}${gpuDevice.name ? ` ${gpuDevice.name}` : ''}`
      : '';
    const cores = gpuDevice.cores ? ` x ${gpuDevice.cores} cores` : '';
    gpuString = `${gpuString}${manufacturer}${cores}`;
  }
  return ([
    `${instance.name} (CPU: ${cpuString}, `,
    `RAM: ${instance.memory}`,
    `${gpuString})`
  ]).join('');
}

export default instanceInfoString;
