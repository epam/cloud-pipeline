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

import fadeVec4Color from '../../utilities/fade-vec4-color';
import { vec4FromColor } from '../../utilities/vec4-from-color';

export default function getAnnotationColor(
  annotation,
  selectedAnnotation,
  accessor = ((o) => o.lineColor),
) {
  const faded = selectedAnnotation && selectedAnnotation !== annotation.identifier;
  return fadeVec4Color(
    vec4FromColor(accessor(annotation) || [220, 220, 220]),
    faded ? 0.25 : 1.0,
  );
}
