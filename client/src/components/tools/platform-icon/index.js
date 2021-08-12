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

import React from 'react';
import PropTypes from 'prop-types';
import WindowsIcon from './windows-icon';
import LinuxIcon from './linux-icon';
import CentosIcon from './centos-icon';
import UbuntuIcon from './ubuntu-icon';

function PlatformIcon ({className, platform, style}) {
  if (/^windows$/i.test(platform)) {
    return (<WindowsIcon className={className} style={style} />);
  }
  if (/^linux/i.test(platform)) {
    return (<LinuxIcon className={className} style={style} />);
  }
  if (/^centos/i.test(platform)) {
    return (<CentosIcon className={className} style={style} />);
  }
  if (/^ubuntu/i.test(platform)) {
    return (<UbuntuIcon className={className} style={style} />);
  }
  return null;
}

PlatformIcon.propTypes = {
  className: PropTypes.string,
  platform: PropTypes.string,
  style: PropTypes.object
};

export default PlatformIcon;
