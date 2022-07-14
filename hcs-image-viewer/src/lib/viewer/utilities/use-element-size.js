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

import { useEffect, useState } from 'react';

export default function useElementSize(elementRef) {
  const [size, setSize] = useState({ width: undefined, height: undefined });
  useEffect(() => {
    let width;
    let height;
    let handle;
    const callback = () => {
      const currentWidth = elementRef?.current?.clientWidth;
      const currentHeight = elementRef?.current?.clientHeight;
      if (
        elementRef
          && elementRef.current
          && (currentWidth !== width || currentHeight !== height)
      ) {
        width = currentWidth;
        height = currentHeight;
        setSize({ width, height });
      }
      handle = requestAnimationFrame(callback);
    };
    callback();
    return () => cancelAnimationFrame(handle);
  }, [elementRef, setSize]);
  return size;
}
