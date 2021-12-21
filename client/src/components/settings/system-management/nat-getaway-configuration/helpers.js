/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

const validationConfig = {
  port: {
    min: 1,
    message: rangeMessage
  },
  ip: new RegExp(/^(?!0)(?!.*\.$)((1?\d?\d|25[0-5]|2[0-4]\d)(\.|$)){4}$/),
  messages: {
    required: 'Field is required',
    invalid: 'Invalid format',
    duplicate: 'Value should be unique'
  }
};

function rangeMessage (min, max) {
  if (min && !max) {
    return `The value should be at least ${min}`;
  }
  if (max && !min) {
    return `The value should be less than ${max}`;
  }
  if (max && min) {
    return `The value should be between ${min} and ${max}`;
  }
}

export function validatePort (value, portsDuplicates) {
  const {port, messages} = validationConfig;

  const portIsValid = value && Number(value) > 0 &&
  (port.min ? Number(value) >= port.min : true) &&
  (port.max ? Number(value) <= port.max : true);

  if (!value) {
    return {error: true, message: messages.required};
  }
  if (!portIsValid) {
    return {
      error: true,
      message: (port.min || port.max)
        ? port.message(port.min, port.max)
        : messages.invalid
    };
  }
  if (portsDuplicates && portsDuplicates.filter(o => Number(o) === Number(value)).length > 1) {
    return {
      error: true,
      message: messages.duplicate
    };
  }
  return {error: false};
}

export function validateServerName (value) {
  const {messages} = validationConfig;
  if (!value || !value.trim()) {
    return {error: true, message: messages.required};
  } else {
    return {error: false};
  }
}

export function validateIP (value, skip = false) {
  const {ip, messages} = validationConfig;
  if (skip) {
    return {error: false};
  }
  if (!value) {
    return {error: true, message: messages.required};
  }
  if (!ip.test(value)) {
    return {error: true, message: messages.invalid};
  }
  return {error: false};
}

export const columns = {
  external: [
    {name: 'externalName', prettyName: 'name'},
    {name: 'externalIp', prettyName: 'ip'},
    {name: 'externalPort', prettyName: 'port'}],
  internal: [
    {name: 'internalName', prettyName: 'service name'},
    {name: 'internalIp', prettyName: 'ip'},
    {name: 'internalPort', prettyName: 'port'}
  ]
};
