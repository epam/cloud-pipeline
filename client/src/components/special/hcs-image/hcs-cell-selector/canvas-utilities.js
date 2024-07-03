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
/* eslint-disable max-len */
const VERTEX = `attribute vec2 position;
    uniform vec4 color;
    uniform mat4 viewScale;
    uniform mat4 viewTranslate;
    uniform mat4 modelScale;
    uniform mat4 modelTranslate;
    uniform mat4 projection;
    varying vec4 vColor;
    void main() {
        vColor = color;
        gl_Position = projection * viewTranslate * viewScale * modelTranslate * modelScale * vec4(position, 0, 1);
    }`;
const FRAGMENT = `precision mediump float;
    varying vec4 vColor;
    void main() {
        gl_FragColor = vColor;
    }`;

function makeShader (gl, src, type) {
  if (!gl) {
    return;
  }
  const shader = gl.createShader(type);
  gl.shaderSource(shader, src);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.warn(gl.getShaderInfoLog(shader));
    return;
  }
  return shader;
}

function initShaders (gl) {
  const vertexShader = makeShader(gl, VERTEX, gl.VERTEX_SHADER);
  const fragmentShader = makeShader(gl, FRAGMENT, gl.FRAGMENT_SHADER);
  const glProgram = gl.createProgram();
  gl.attachShader(glProgram, vertexShader);
  gl.attachShader(glProgram, fragmentShader);
  gl.linkProgram(glProgram);
  if (!gl.getProgramParameter(glProgram, gl.LINK_STATUS)) {
    console.warn('Error initializing gl program');
    return false;
  }
  gl.useProgram(glProgram);
  gl.program = glProgram;
  return glProgram;
}

export function resizeCanvas (canvas, size) {
  if (!canvas || !size || !size.width || !size.height) {
    return false;
  }
  const {
    width: canvasWidth,
    height: canvasHeight
  } = size;
  if (
    canvas.width !== canvasWidth * window.devicePixelRatio ||
    canvas.height !== canvasHeight * window.devicePixelRatio
  ) {
    canvas.width = canvasWidth * window.devicePixelRatio;
    canvas.height = canvasHeight * window.devicePixelRatio;
    canvas.style.width = `${canvasWidth}px`;
    canvas.style.height = `${canvasHeight}px`;
    return true;
  }
  return false;
}

export function createGLProgram (gl) {
  if (!gl) {
    return;
  }
  const glProgram = initShaders(gl);
  if (glProgram) {
    gl.useProgram(glProgram);
    return {
      program: glProgram,
      color: gl.getUniformLocation(glProgram, 'color'),
      aPosition: gl.getAttribLocation(glProgram, 'position'),
      modelScale: gl.getUniformLocation(glProgram, 'modelScale'),
      modelTranslate: gl.getUniformLocation(glProgram, 'modelTranslate'),
      viewScale: gl.getUniformLocation(glProgram, 'viewScale'),
      viewTranslate: gl.getUniformLocation(glProgram, 'viewTranslate'),
      projection: gl.getUniformLocation(glProgram, 'projection')
    };
  }
  return undefined;
}

/**
 * @param {number} size
 * @param {{borders: boolean, threshold: number, from: number?, to: number?, unitScale: number}} options
 * @returns {number[]}
 */
export function getLinesToDraw (size, options = {}) {
  const {
    borders = true,
    threshold = 10,
    from = 0,
    to = size,
    unitScale = 1
  } = options;
  const aStep = Math.floor(2 ** Math.max(0, Math.log2(threshold / unitScale)));
  const fromCorrected = Math.min(size, Math.max(0, Math.ceil(from)));
  const toCorrected = Math.min(size, Math.max(0, Math.ceil(to)));
  const result = new Set(borders
    ? [fromCorrected, toCorrected]
    : [fromCorrected]
  );
  for (let i = fromCorrected + aStep; i <= toCorrected; i += aStep) {
    result.add(i);
  }
  return [...result];
}
