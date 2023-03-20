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

const LINE = {
  data: new Float32Array([0, 0, 1, 1]),
  vertexCount: 2
};

const CROSS = {
  data: new Float32Array([-1, 0, 1, 0, 0, -1, 0, 1]),
  vertexCount: 4
};

const RECTANGLE = {
  data: new Float32Array([
    0, 0, 1, 0, 1, 1,
    1, 1, 0, 1, 0, 0
  ]),
  vertexCount: 6
};

const RECTANGLE_BORDER = {
  data: new Float32Array([
    0, 0, 1, 0,
    1, 0, 1, 1,
    1, 1, 0, 1,
    0, 1, 0, 0
  ]),
  vertexCount: 8
};

const circleSectors = 180;
const circleBorderPoints = [];
const circleThickBorderPoints = [];
const circlePoints = [];
for (let i = 1; i <= circleSectors; i++) {
  const prev = (i - 1) * 2.0 * Math.PI / circleSectors;
  const curr = i * 2.0 * Math.PI / circleSectors;
  circleBorderPoints.push(
    Math.cos(prev), Math.sin(prev),
    Math.cos(curr), Math.sin(curr)
  );
  circlePoints.push(
    0, 0,
    Math.cos(prev), Math.sin(prev),
    Math.cos(curr), Math.sin(curr)
  );
  circleThickBorderPoints.push(
    Math.cos(prev) * 0.8, Math.sin(prev) * 0.8,
    Math.cos(prev), Math.sin(prev),
    Math.cos(curr), Math.sin(curr),
    Math.cos(curr), Math.sin(curr),
    Math.cos(curr) * 0.8, Math.sin(curr) * 0.8,
    Math.cos(prev) * 0.8, Math.sin(prev) * 0.8
  );
}

const CIRCLE = {
  data: new Float32Array(circlePoints),
  vertexCount: circlePoints.length / 2
};

const CIRCLE_BORDER = {
  data: new Float32Array(circleBorderPoints),
  vertexCount: circleBorderPoints.length / 2
};

const CIRCLE_THICK_BORDER = {
  data: new Float32Array(circleThickBorderPoints),
  vertexCount: circleThickBorderPoints.length / 2
};

/**
 * @typedef {Object} BufferWrapper
 * @property {WebGLBuffer} buffer
 * @property {number} vertexCount
 */

/**
 * @param gl
 * @param {{data: Float32Array, vertexCount: number}} buffer
 * @returns {BufferWrapper}
 */
function createBuffer (gl, buffer) {
  if (gl) {
    const id = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, id);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(buffer.data), gl.STATIC_DRAW);
    return {
      buffer: id,
      vertexCount: buffer.vertexCount
    };
  }
  return undefined;
}

/**
 * @param gl
 * @returns {{line: BufferWrapper, cross: BufferWrapper, rectangle: BufferWrapper, circle: BufferWrapper, circleBorder: BufferWrapper, circleThickBorder: BufferWrapper, rectangleBorder: BufferWrapper}}
 */
export default function createBuffers (gl) {
  if (!gl) {
    return undefined;
  }
  return {
    line: createBuffer(gl, LINE),
    cross: createBuffer(gl, CROSS),
    rectangle: createBuffer(gl, RECTANGLE),
    rectangleBorder: createBuffer(gl, RECTANGLE_BORDER),
    circle: createBuffer(gl, CIRCLE),
    circleBorder: createBuffer(gl, CIRCLE_BORDER),
    circleThickBorder: createBuffer(gl, CIRCLE_THICK_BORDER)
  };
}
