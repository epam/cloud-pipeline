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

import {observable} from 'mobx';

export default class PhysicalSize {
  @observable unitsInPixel = 1;
  @observable unit = 'px';

  constructor (unitsInPixel, unit) {
    this.update(unitsInPixel, unit);
  }

  update (unitsInPixel, unit) {
    if (unitsInPixel && unit) {
      this.unitsInPixel = unitsInPixel;
      this.unit = unit;
    }
  }

  getPixels (physicalSize) {
    if (physicalSize === undefined || Number.isNaN(Number(physicalSize))) {
      return physicalSize;
    }
    const p = Number(physicalSize);
    return p / this.unitsInPixel;
  }

  getSquarePixels (physicalSize) {
    if (physicalSize === undefined || Number.isNaN(Number(physicalSize))) {
      return physicalSize;
    }
    const p = Number(physicalSize);
    return p / Math.pow(this.unitsInPixel, 2);
  }

  getPhysicalSize (pixels) {
    if (pixels === undefined || Number.isNaN(Number(pixels))) {
      return pixels;
    }
    const p = Number(pixels);
    return p * this.unitsInPixel;
  }

  getSquarePhysicalSize (pixels) {
    if (pixels === undefined || Number.isNaN(Number(pixels))) {
      return pixels;
    }
    const p = Number(pixels);
    return p * Math.pow(this.unitsInPixel, 2);
  }
}
