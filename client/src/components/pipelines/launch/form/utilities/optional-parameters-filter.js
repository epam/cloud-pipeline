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

import {getDockerImage, parseDockerImage} from '../../../../../utils/get-docker-image';

/**
 * Returns the docker image without its version or digest, lower-cased:
 * `registry/group/tool` for a full image name
 * @param {string} dockerImage
 * @returns {string|undefined}
 */
function getImageName (dockerImage) {
  const [image] = (dockerImage || '').trim().toLowerCase().split('@');
  const parsed = parseDockerImage(image);
  if (parsed) {
    return [parsed.registry, parsed.group, parsed.tool].join('/');
  }
  const parts = image.split('/');
  const [tool] = parts.pop().split(':');
  if (!tool) {
    return undefined;
  }
  return parts.concat(tool).join('/');
}

function isPipelineName (name) {
  return name !== undefined && name !== null && !/^\d+$/.test(`${name}`);
}

/**
 * Returns the identifiers an `optional-parameters-filter` override can name the launched
 * pipeline or tool by: pipeline id and name, or tool id and `registry/group/tool`.
 * A pipeline name made of digits only is left out, as such a key names an id.
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
    return [pipeline.id]
      .concat(isPipelineName(pipeline.name) ? [pipeline.name] : [])
      .filter(key => key !== undefined && key !== null);
  }
  const imageName = getImageName(dockerImage);
  const {tool} = getDockerImage(imageName, dockerRegistries) || {};
  return [tool ? tool.id : undefined, imageName]
    .filter(key => key !== undefined && key !== null);
}

/**
 * Decides whether the "Show optional parameters" checkbox is reset to its default value.
 * It is reset once per launched pipeline or tool. When the default value changes later
 * (the settings or the registries load), it is reset again, unless the checkbox
 * was changed since. Where the new value sets no default but the previous one did,
 * it is reset to unchecked.
 * @param {{pipeline?: {id}, dockerImage?: string, defaultValue?: boolean,
 * checked?: boolean, target?: *}} options - `checked` is the current checkbox state,
 * `target` is the one this function returned last time
 * @returns {{target: {launched: string, defaultValue: boolean|undefined, checked: boolean,
 * touched: boolean}, value: boolean|undefined}} - `value` is undefined when the checkbox
 * is left as it is
 */
export function getOptionalParametersFilterDefault (options = {}) {
  const {
    pipeline,
    dockerImage,
    defaultValue,
    checked,
    target: previous
  } = options;
  const launched = pipeline
    ? `pipeline:${pipeline.id}`
    : `tool:${getImageName(dockerImage) || ''}`;
  const same = !!previous && previous.launched === launched;
  const touched = same && (previous.touched || previous.checked !== checked);
  let value;
  if (!touched && (!same || previous.defaultValue !== defaultValue)) {
    if (defaultValue !== undefined) {
      value = defaultValue;
    } else if (previous && previous.defaultValue !== undefined) {
      value = false;
    }
  }
  return {
    target: {
      launched,
      defaultValue,
      checked: value === undefined ? checked : value,
      touched
    },
    value
  };
}

/**
 * Makes the next `getOptionalParametersFilterDefault` call apply the default value again,
 * as for a newly launched pipeline or tool.
 * @param {*} target - the one `getOptionalParametersFilterDefault` returned last time
 * @returns {*}
 */
export function forgetOptionalParametersFilterTarget (target) {
  if (!target) {
    return target;
  }
  return {...target, launched: undefined};
}

/**
 * Creates a function returning the "Show optional parameters" checkbox settings
 * of the launched pipeline or tool. They are resolved again only when the pipeline,
 * the docker image, the `ui-launch-form` settings or the loaded registries change.
 * @returns {function({pipeline?: *, dockerImage?: string, dockerRegistries?: *,
 * launchForm?: *, resolve: function(Array): {pipelines: *, tools: *}}):
 * {enabled: boolean, defaultValue: boolean|undefined}}
 */
export function createOptionalParametersFilterSettings () {
  let cache;
  return (options = {}) => {
    const {
      pipeline,
      dockerImage,
      dockerRegistries,
      launchForm,
      resolve
    } = options;
    const registries = dockerRegistries && dockerRegistries.loaded
      ? dockerRegistries.value
      : undefined;
    if (
      cache &&
      cache.pipeline === pipeline &&
      cache.dockerImage === dockerImage &&
      cache.launchForm === launchForm &&
      cache.registries === registries
    ) {
      return cache.settings;
    }
    const keys = getOptionalParametersFilterKeys({pipeline, dockerImage, dockerRegistries});
    const {pipelines, tools} = resolve(keys);
    const settings = pipeline ? pipelines : tools;
    cache = {pipeline, dockerImage, launchForm, registries, settings};
    return settings;
  };
}
