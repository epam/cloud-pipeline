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
import {vec4FromColorNormalized} from '../../utilities/vec4-from-color';

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
      model.setUniforms({ accuracy, tint: vec4FromColorNormalized(color) });
    }
  }
}

export { IgnoreColorExtension, IgnoreColorAndTintExtension };
