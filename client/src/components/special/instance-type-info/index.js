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
import React from 'react';
import {Select} from 'antd';
import classNames from 'classnames';
import styles from './instance-type-info.css';

const cpuMapper = (cpu, hyperThreadingDisabled = false) => {
  return hyperThreadingDisabled && !Number.isNaN(Number(cpu))
    ? (cpu / 2.0)
    : cpu;
};

/**
 * @typedef {Object} GPUDeviceInfo
 * @property {string} [manufacturer]
 * @property {string} [name]
 * @property {string|number} [cores]
 */

/**
 * @typedef {Object} InstanceInfo
 * @property {string} [sku]
 * @property {string} [instanceFamily]
 * @property {string|number} [vcpu]
 * @property {string|number} [memory]
 * @property {string|number} [gpu]
 * @property {GPUDeviceInfo} [gpuDevice]
 */

/**
 * @typedef {Object} InstanceInfoOptions
 * @property {boolean} [hyperThreadingDisabled=false]
 * @property {string} [className]
 * @property {Object} [style]
 * @property {boolean} [plainText=true]
 */

/**
 * @param {InstanceInfo} instance
 * @returns {undefined|string}
 */
function getGPUPresentation (instance) {
  const {
    gpu,
    gpuDevice
  } = instance;
  const parts = [];
  if (gpu) {
    parts.push(gpu);
  }
  if (gpuDevice) {
    const {
      manufacturer,
      name: gpuDeviceName,
      cores
    } = gpuDevice;
    if (manufacturer || name) {
      parts.push([manufacturer, gpuDeviceName].filter(Boolean).join(' '));
      if (cores) {
        parts.push(`${cores} cores`);
      }
    }
  }
  if (parts.length > 0) {
    return parts.join(' x ');
  }
  return undefined;
}

/**
 * @param {InstanceInfo} instance
 * @param {InstanceInfoOptions} [options]
 * @returns {string}
 */
export function instanceInfoString (instance, options = {}) {
  if (!instance) {
    return '';
  }
  const {
    hyperThreadingDisabled = false,
    plainText = true,
    className,
    style
  } = options;
  const infoParts = [];
  const {
    sku,
    memory,
    vcpu,
    name
  } = instance;
  if (vcpu) {
    infoParts.push(plainText
      ? `CPU: ${cpuMapper(vcpu, hyperThreadingDisabled)}`
      : (
        <span key="cpu" className={styles.instanceTypeInfoPart}>
          CPU: <span>{cpuMapper(vcpu, hyperThreadingDisabled)}</span>
        </span>
      )
    );
  }
  if (memory) {
    infoParts.push(plainText
      ? `RAM: ${memory}`
      : (
        <span key="ram" className={styles.instanceTypeInfoPart}>
          RAM: <span>{memory}</span>
        </span>
      )
    );
  }
  const gpu = getGPUPresentation(instance);
  if (gpu) {
    infoParts.push(plainText
      ? `GPU: ${gpu}`
      : (
        <span key="gpu" className={styles.instanceTypeInfoPart}>
          GPU: <span>{gpu}</span>
        </span>
      )
    );
  }
  const info = infoParts.length > 0
    ? (plainText ? `${infoParts.join(', ')}` : infoParts)
    : undefined;
  if (info) {
    return plainText
      ? `${name} (${info})`
      : (
        <span
          key={sku || name}
          className={classNames(className, styles.instanceTypeInfo)}
          style={style}
        >
          <span>{name}</span>
          <span style={{whiteSpace: 'pre'}}> </span>
          <span
            className={
              classNames(
                styles.instanceTypeInfoParts,
                'cp-text-not-important'
              )
            }
          >
            {infoParts}
          </span>
        </span>
      );
  }
  return plainText
    ? name
    : (
      <span
        className={classNames(className, styles.instanceTypeInfo)}
        style={style}
      >
        {name}
      </span>
    );
}

/**
 * @param {InstanceInfo} instance
 * @param {InstanceInfoOptions} [options]
 * @returns {JSX.Element|null}
 */
export function getSelectOptionForInstance (instance, options = {}) {
  if (!instance) {
    return null;
  }
  const {
    sku,
    name
  } = instance;
  return (
    <Select.Option
      key={sku || name}
      value={name}
      title={
        instanceInfoString(
          instance,
          {
            ...(options || {}),
            plaintText: true
          }
        )
      }
    >
      {
        instanceInfoString(
          instance,
          {
            ...(options || {}),
            plainText: false
          }
        )
      }
    </Select.Option>
  );
}

/**
 * @param {InstanceInfo[]} instanceTypes
 * @param {InstanceInfoOptions} options
 * @returns {JSX.Element}
 */
export function getSelectOptions (instanceTypes = [], options = {}) {
  const instanceFamilies = [...new Set(instanceTypes.map((i) => i.instanceFamily))];
  return instanceFamilies
    .map((instanceFamily) => (
      <Select.OptGroup
        key={instanceFamily || 'Other'}
        label={instanceFamily || 'Other'} >
        {
          instanceTypes
            .filter(t => t.instanceFamily === instanceFamily)
            .map(t => getSelectOptionForInstance(t, options))
        }
      </Select.OptGroup>
    ));
}
