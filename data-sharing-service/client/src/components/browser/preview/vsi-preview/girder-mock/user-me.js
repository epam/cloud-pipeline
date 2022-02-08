/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import whoAmI from '../../../../../models/user/WhoAmI';

function userMe (match, resolve) {
  whoAmI
    .fetchIfNeededOrWait()
    .then(() => {
      if (whoAmI.loaded) {
        return Promise.resolve({
          _id: whoAmI.value.id,
          login: whoAmI.value.userName
        });
      } else {
        throw new Error(whoAmI.error);
      }
    })
    .catch(() => Promise.resolve())
    .then(resolve);
}

userMe.test = url => /^user\/me$/i.test(url);

export default userMe;
