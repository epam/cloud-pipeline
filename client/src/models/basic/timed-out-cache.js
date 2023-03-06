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

const second = 1000;

class TimedOutCache extends Map {
  constructor (timeoutSeconds = 5 * 60) {
    super();
    this.timeout = timeoutSeconds;
    this.cache = new Map();
    this.timeouts = new Map();
  }

  clear () {
    for (const handle of this.timeouts.values()) {
      clearTimeout(handle);
    }
    this.timeouts.clear();
    return super.clear();
  }

  delete (key) {
    const invalidate = this.timeouts.get(key);
    clearTimeout(invalidate);
    return super.delete(key);
  }

  set (key, value) {
    this.delete(key);
    const clear = () => this.delete(key);
    this.timeouts.set(key, setTimeout(clear, this.timeout * second));
    return super.set(key, value);
  }
}

export default TimedOutCache;
