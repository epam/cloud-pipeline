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

/**
 * Returns the version of a `registry/group/image[:version]` docker image.
 * The case is kept as is: docker tags are case-sensitive.
 * @param {string} dockerImage
 * @returns {string|undefined}
 */
export default function getDockerImageVersion (dockerImage) {
  const [, , imageAndVersion = ''] = (dockerImage || '').split('/');
  const [, version] = imageAndVersion.split(':');
  return version;
}
