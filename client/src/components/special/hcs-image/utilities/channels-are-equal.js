/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

export default function channelsAreEqual (channelsSet1, channelsSet2) {
  const channels1 = Object.keys(channelsSet1 || {}).sort();
  const channels2 = Object.keys(channelsSet2 || {}).sort();
  if (channels1.length !== channels2.length) {
    return false;
  }
  for (let c = 0; c < channels1.length; c++) {
    const channelName1 = channels1[c];
    const channelName2 = channels2[c];
    const channel1 = channelsSet1[channelName1];
    const channel2 = channelsSet2[channelName2];
    if (
      channelName1 !== channelName2 ||
      channel1.length !== channel2.length
    ) {
      return false;
    }
    for (let i = 0; i < channel1.length; i++) {
      if (channel1[i] !== channel2[i]) {
        return false;
      }
    }
  }
  return true;
}
