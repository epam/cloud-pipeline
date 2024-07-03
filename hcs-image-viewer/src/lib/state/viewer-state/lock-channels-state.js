/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

const EMPTY_ARRAY = [];

export default function lockChannelsState(state, channelsInfo) {
  const {
    lockChannels = false,
    channels: currentChannels = EMPTY_ARRAY,
    colors: currentColors = EMPTY_ARRAY,
    domains: currentDomains = EMPTY_ARRAY,
    contrastLimits: currentContrastLimits = EMPTY_ARRAY,
    channelsVisibility: currentChannelsVisibility = EMPTY_ARRAY,
  } = state || {};
  const {
    channels = EMPTY_ARRAY,
    colors = EMPTY_ARRAY,
    domains = EMPTY_ARRAY,
    contrastLimits = EMPTY_ARRAY,
    ...rest
  } = channelsInfo || {};
  if (!lockChannels || (Array.isArray(lockChannels) && lockChannels.length === 0)) {
    return {
      ...rest,
      channels,
      colors,
      domains,
      contrastLimits,
      realDomains: domains.slice(),
      channelsVisibility: channels.map(() => true),
    };
  }
  if (channels.length === 0) {
    return state;
  }
  const channelsToLock = Array.isArray(lockChannels)
    ? lockChannels
    : channels.slice();
  const currentChannelNames = currentChannels.slice();
  const lockedChannels = [];
  for (let c = 0; c < channels.length; c += 1) {
    const currentChannelIndex = currentChannelNames.indexOf(channels[c]);
    const skip = !channelsToLock.includes(channels[c]);
    if (skip || currentChannelIndex === -1) {
      lockedChannels.push({
        channel: channels[c],
        color: colors[c],
        domain: domains[c],
        realDomain: domains[c].slice(),
        contrastLimits: contrastLimits[c],
        visibility: true,
      });
    } else {
      currentChannelNames.splice(currentChannelIndex, 1, undefined);
      lockedChannels.push({
        channel: channels[c],
        color: currentColors[currentChannelIndex] || colors[c],
        domain: currentDomains[currentChannelIndex] || domains[c],
        realDomain: domains[c].slice(),
        contrastLimits: currentContrastLimits[currentChannelIndex] || contrastLimits[c],
        visibility: currentChannelsVisibility[currentChannelIndex] === undefined
          ? true
          : currentChannelsVisibility[currentChannelIndex],
      });
    }
  }
  return {
    ...rest,
    channels: lockedChannels.map((o) => o.channel),
    colors: lockedChannels.map((o) => o.color),
    domains: lockedChannels.map((o) => o.domain),
    realDomains: lockedChannels.map((o) => o.realDomain),
    contrastLimits: lockedChannels.map((o) => o.contrastLimits),
    channelsVisibility: lockedChannels.map((o) => o.visibility),
  };
}
