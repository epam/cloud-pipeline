/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import UpdatePathPermissions from './update-path-permissions';

/*
 This is the same method as UpdatePathPermissions; to remove permissions, we shall NOT include
 `permissions` array as a payload.
 If permissions field was not provided all granted permissions for specified path shall be removed
 */

export default class RemovePathPermissions extends UpdatePathPermissions {
  async send (body, abortSignal) {
    const modifiedBody = body.map((o) => {
      const {path, type} = o;
      return {path, type};
    });
    return super.send(modifiedBody, abortSignal);
  }
}
