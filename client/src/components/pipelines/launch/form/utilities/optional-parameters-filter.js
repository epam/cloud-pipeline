/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {getDockerImage} from '../../../../../utils/get-docker-image';

function getToolName (dockerImage) {
  const parts = (dockerImage || '').trim().split('/');
  if (parts.length !== 3) {
    return undefined;
  }
  const [registry, group, toolAndVersion] = parts;
  const [tool] = toolAndVersion.split(':');
  return [registry, group, tool].join('/');
}

/**
 * Returns the identifiers an `optional-parameters-filter` override can name the launched
 * pipeline or tool by: pipeline id and name, or tool id and `registry/group/tool`.
 * @param {{pipeline?: {id, name}, dockerImage?: string, dockerRegistries?: *}} options
 * @returns {Array<string|number>}
 */
export default function getOptionalParametersFilterKeys (options = {}) {
  const {
    pipeline,
    dockerImage,
    dockerRegistries
  } = options;
  if (pipeline) {
    return [pipeline.id, pipeline.name]
      .filter(key => key !== undefined && key !== null);
  }
  const {tool} = getDockerImage(dockerImage, dockerRegistries) || {};
  return [tool ? tool.id : undefined, getToolName(dockerImage)]
    .filter(key => key !== undefined && key !== null);
}

/**
 * Decides whether the "Show optional parameters" checkbox is reset to its default value.
 * It is reset once per launched pipeline or tool, and again when its default value changes.
 * Where the new target sets no default but the previous one did, it is reset to unchecked.
 * @param {{pipeline?: {id}, dockerImage?: string, defaultValue?: boolean, target?: *}}
 * options - `target` is the one this function returned last time
 * @returns {{target: {launched: string, defaultValue: boolean|undefined},
 * value: boolean|undefined}} - `value` is undefined when the checkbox is left as it is
 */
export function getOptionalParametersFilterDefault (options = {}) {
  const {
    pipeline,
    dockerImage,
    defaultValue,
    target: previous = {}
  } = options;
  const launched = pipeline
    ? `pipeline:${pipeline.id}`
    : `tool:${(getToolName(dockerImage) || '').toLowerCase()}`;
  const target = {launched, defaultValue};
  if (previous.launched === launched && previous.defaultValue === defaultValue) {
    return {target, value: undefined};
  }
  if (defaultValue !== undefined) {
    return {target, value: defaultValue};
  }
  if (previous.defaultValue !== undefined) {
    return {target, value: false};
  }
  return {target, value: undefined};
}
