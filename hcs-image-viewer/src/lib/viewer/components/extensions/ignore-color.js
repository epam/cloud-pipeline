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

/* eslint-disable max-classes-per-file */
import { LayerExtension } from '@deck.gl/core';

const DEFAULT_ACCURACY = 0.1;

class IgnoreColorExtension extends LayerExtension {
  // eslint-disable-next-line class-methods-use-this
  getShaders() {
    return {
      inject: {
        // Declare custom uniform
        'fs:#decl': 'uniform float accuracy;',
        // Standard injection hook - see "Writing Shaders"
        'fs:DECKGL_FILTER_COLOR': `
          if (color.r < accuracy && color.g < accuracy && color.b < accuracy) {
            // is transparent
            color = vec4(0.0, 0.0, 0.0, 0.0);
          }
        `,
      },
    };
  }

  updateState(params) {
    const { ignoreColorAccuracy: accuracy = DEFAULT_ACCURACY } = params.props;
    const models = this.getModels();
    for (let m = 0; m < models.length; m += 1) {
      const model = models[m];
      model.setUniforms({ accuracy });
    }
  }
}

function vec4FromColor(color) {
  if (!color) {
    return [1.0, 1.0, 1.0, 1.0];
  }
  if (typeof color === 'object' && Array.isArray(color)) {
    const [r = 1.0, g = 1.0, b = 1.0, a = 1.0] = color;
    return [r, g, b, a];
  }
  if (typeof color === 'string' && /^#/i.test(color)) {
    const parseAndNormalize = (o) => parseInt(o || 'FF', 16) / 255.0;
    const r = parseAndNormalize(color.slice(1, 3));
    const g = parseAndNormalize(color.slice(3, 5));
    const b = parseAndNormalize(color.slice(5, 7));
    const a = parseAndNormalize(color.slice(7, 9));
    return [
      r,
      g,
      b,
      a,
    ];
  }
  if (typeof color === 'number') {
    const normalize = (o) => Math.max(0, Math.min(1, o));
    return [
      normalize(color),
      normalize(color),
      normalize(color),
      1.0,
    ];
  }
  return [1.0, 1.0, 1.0, 1.0];
}

class IgnoreColorAndTintExtension extends LayerExtension {
  // eslint-disable-next-line class-methods-use-this
  getShaders() {
    return {
      inject: {
        // Declare custom uniform
        'fs:#decl': 'uniform float accuracy; uniform vec4 tint;',
        // Standard injection hook - see "Writing Shaders"
        'fs:DECKGL_FILTER_COLOR': `
          if (color.r < accuracy && color.g < accuracy && color.b < accuracy) {
            // is transparent
            color = vec4(0.0, 0.0, 0.0, 0.0);
          } else {
            color = tint;
          }
        `,
      },
    };
  }

  updateState(params) {
    const {
      ignoreColorAccuracy: accuracy = DEFAULT_ACCURACY,
      color = [1.0, 1.0, 1.0, 1.0],
    } = params.props;
    const models = this.getModels();
    for (let m = 0; m < models.length; m += 1) {
      const model = models[m];
      model.setUniforms({ accuracy, tint: vec4FromColor(color) });
    }
  }
}

export { IgnoreColorExtension, IgnoreColorAndTintExtension };
