import {parseColor} from '../../../../themes/utilities/color-utilities';

const pathProgram = {
  vertex: `#version 300 es

in vec2 a_position;
in vec2 a_vector;

uniform mat4 projection;
uniform mat4 viewModel;
uniform float width;
uniform vec2 pixelResolution;
uniform vec4 color;

out vec4 vColor;

void main() {
  vec4 a = projection * viewModel * vec4(a_position, 0.0, 1.0);
  vec4 b = projection * viewModel * vec4(a_position + a_vector, 0.0, 1.0);
  vec4 v = b - a;
  vec2 v_px = vec2(v.xy / pixelResolution);
  vec2 n_px = normalize(vec2(-v_px.y, v_px.x)) * width / 2.0;
  gl_Position = a + vec4(n_px * pixelResolution, 0.0, 0.0);
  vColor = color;
}
`,
  fragment: `#version 300 es
precision highp float;
in vec4 vColor;
out vec4 fragmentColor;
void main() {
  fragmentColor = vColor;
}
`,
  attributes: [
    'a_position',
    'a_vector'
  ],
  uniforms: [
    'projection',
    'viewModel',
    'color',
    'width',
    'pixelResolution'
  ]
};

const defaultProgram = {
  vertex: `#version 300 es
precision highp float;

in vec2 a_position;

uniform mat4 projection;
uniform mat4 viewModel;
uniform vec4 color;

out vec4 vColor;

void main() {
  gl_Position = projection * viewModel * vec4(a_position, 0.0, 1.0);
  vColor = color;
}
`,
  fragment: `#version 300 es
precision highp float;

in vec4 vColor;
out vec4 fragmentColor;
void main() {
  fragmentColor = vColor;
}
`,
  attributes: [
    'a_position'
  ],
  uniforms: [
    'projection',
    'viewModel',
    'color'
  ]
};

const circleProgram = {
  vertex: `#version 300 es

in vec3 af;

uniform mat4 projection;
uniform float radius;
uniform float border;
uniform vec2 pixelResolution;
uniform vec4 color;
uniform vec2 center;

out vec4 vColor;

void main() {
  vec4 a = projection * vec4(center, 0.0, 1.0);
  vec2 v_px = vec2(cos(af.x), sin(af.x)) * af.y * (radius + af.z * border / 2.0);
  gl_Position = a + vec4(v_px * pixelResolution, 0.0, 0.0);
  vColor = color;
}
`,
  fragment: `#version 300 es
precision highp float;
in vec4 vColor;
out vec4 fragmentColor;
void main() {
  fragmentColor = vColor;
}
`,
  attributes: [
    'af'
  ],
  uniforms: [
    'projection',
    'center',
    'color',
    'radius',
    'pixelResolution',
    'border'
  ]
};

function buildOrthoMatrix (
  {
    left,
    right,
    top,
    bottom
  }
) {
  const result = buildIdentityMatrix();
  const near = -1.0;
  const far = 1.0;
  result[0] = -2 / (left - right);
  result[1] = 0;
  result[2] = 0;
  result[3] = 0;
  result[4] = 0;
  result[5] = -2 / (bottom - top);
  result[6] = 0;
  result[7] = 0;
  result[8] = 0;
  result[9] = 0;
  result[10] = 2 / (near - far);
  result[11] = 0;
  result[12] = (left + right) / (left - right);
  result[13] = (top + bottom) / (bottom - top);
  result[14] = (far + near) / (near - far);
  result[15] = 1;
  return result;
}

function buildIdentityMatrix () {
  return [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1
  ];
}

function multiplyMatrices (...matrix) {
  if (matrix.length === 0) {
    return buildIdentityMatrix();
  }
  if (matrix.length === 1) {
    return matrix[0].slice();
  }
  const [matrix1, matrix2, ...rest] = matrix;
  const result = [];
  for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 4; j++) {
      let sum = 0;
      for (let k = 0; k < 4; k++) {
        sum += matrix1[k * 4 + j] * matrix2[i * 4 + k];
      }
      result.push(sum);
    }
  }
  return multiplyMatrices(result, ...rest);
}

function translationMatrix (translation) {
  const matrix = buildIdentityMatrix();
  matrix[12] = translation.x;
  matrix[13] = translation.y;
  return matrix;
}

function scaleMatrix (scale) {
  const matrix = buildIdentityMatrix();
  matrix[0] = scale.x;
  matrix[5] = scale.y;
  return matrix;
}

function rotationMatrix (angle) {
  const matrix = buildIdentityMatrix();
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  matrix[0] = cos;
  matrix[1] = sin;
  matrix[4] = -sin;
  matrix[5] = cos;
  return matrix;
}

function buildTranslationScaleRotateMatrix (translation, scale, angle) {
  return multiplyMatrices(
    translationMatrix(translation),
    rotationMatrix(angle),
    scaleMatrix(scale)
  );
}

function buildPathVAO (pathBlock, program) {
  const positions = [];
  const vectors = [];
  const indices = [];
  for (let i = 0; i < pathBlock.length; i += 1) {
    const current = pathBlock[i];
    const next = i < pathBlock.length - 1 ? pathBlock[i + 1] : pathBlock[i];
    const {
      vector
    } = current;
    positions.push(...[
      current.x, current.y,
      current.x, current.y,
      next.x, next.y,
      next.x, next.y
    ]);
    vectors.push(...[
      vector.x, vector.y,
      -vector.x, -vector.y,
      vector.x, vector.y,
      -vector.x, -vector.y
    ]);
    const start = i * 4;
    indices.push(...[
      start, start + 1, start + 2,
      start + 1, start + 2, start + 3
    ]);
    if (i < pathBlock.length - 1) {
      indices.push(...[
        start + 2, start + 4, start + 3,
        start + 2, start + 3, start + 5
      ]);
    }
  }
  const {
    context: gl
  } = program;
  const vao = gl.createVertexArray();
  const positionsBuffer = gl.createBuffer();
  const vectorsBuffer = gl.createBuffer();
  const positionsArray = new Float32Array(positions);
  const vectorsArray = new Float32Array(vectors);
  const indicesArray = new Uint16Array(indices);
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(program.attributes['a_position']);
  gl.bindBuffer(gl.ARRAY_BUFFER, positionsBuffer);
  gl.vertexAttribPointer(
    program.attributes['a_position'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, positionsArray, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(program.attributes['a_vector']);
  gl.bindBuffer(gl.ARRAY_BUFFER, vectorsBuffer);
  gl.vertexAttribPointer(
    program.attributes['a_vector'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, vectorsArray, gl.STATIC_DRAW);
  const indexBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indicesArray, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  return {
    vao,
    count: indices.length,
    size: vectorsArray.byteLength + positionsArray.byteLength + indicesArray.byteLength,
    items: pathBlock
  };
}

function buildRectangleVAO (program) {
  const positions = [
    0, 0,
    1, 0,
    1, 1,
    0, 1
  ];
  const indices = [
    0, 1, 2,
    2, 3, 0
  ];
  const {
    context: gl
  } = program;
  const vao = gl.createVertexArray();
  const positionsBuffer = gl.createBuffer();
  const positionsArray = new Float32Array(positions);
  const indicesArray = new Uint16Array(indices);
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(program.attributes['a_position']);
  gl.bindBuffer(gl.ARRAY_BUFFER, positionsBuffer);
  gl.vertexAttribPointer(
    program.attributes['a_position'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, positionsArray, gl.STATIC_DRAW);
  const indexBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indicesArray, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  return {
    vao,
    count: indices.length,
    size: positionsArray.byteLength + indicesArray.byteLength
  };
}

function buildCircleVAO (program, sectors) {
  const {
    context: gl
  } = program;
  const af = [0, 0];
  const indices = [];
  for (let i = 0; i <= sectors; i += 1) {
    af.push(
      i * 2.0 * Math.PI / sectors,
      1
    );
    indices.push(0, (i + 1), i === sectors ? 1 : (i + 2));
  }
  const vao = gl.createVertexArray();
  const anglesBuffer = gl.createBuffer();
  const afArray = new Float32Array(af);
  const indicesArray = new Uint16Array(indices);
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(program.attributes['af']);
  gl.bindBuffer(gl.ARRAY_BUFFER, anglesBuffer);
  gl.vertexAttribPointer(
    program.attributes['af'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, afArray, gl.STATIC_DRAW);
  const indexBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indicesArray, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  return {
    vao,
    count: indices.length,
    size: afArray.byteLength + indicesArray.byteLength
  };
}

function buildStrokeCircleVAO (program, sectors) {
  const {
    context: gl
  } = program;
  const af = [];
  const indices = [];
  for (let i = 0; i <= sectors; i += 1) {
    af.push(
      i * 2.0 * Math.PI / sectors,
      1,
      -1,
      i * 2.0 * Math.PI / sectors,
      1,
      1
    );
    const start = i * 2;
    indices.push(
      start, (start + 1), i === sectors ? 0 : (start + 2),
      start + 1, i === sectors ? 0 : (start + 2), i === sectors ? 1 : (start + 3)
    );
  }
  const vao = gl.createVertexArray();
  const anglesBuffer = gl.createBuffer();
  const afArray = new Float32Array(af);
  const indicesArray = new Uint16Array(indices);
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(program.attributes['af']);
  gl.bindBuffer(gl.ARRAY_BUFFER, anglesBuffer);
  gl.vertexAttribPointer(
    program.attributes['af'],
    3,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, afArray, gl.STATIC_DRAW);
  const indexBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indicesArray, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  return {
    vao,
    count: indices.length,
    size: afArray.byteLength + indicesArray.byteLength
  };
}

function buildLineVAO (program) {
  const positions = [
    0, 0,
    0, 0,
    1, 0,
    1, 0
  ];
  const vectors = [
    1, 0,
    -1, 0,
    1, 0,
    -1, 0
  ];
  const indices = [
    0, 1, 2,
    1, 2, 3
  ];
  const {
    context: gl
  } = program;
  const vao = gl.createVertexArray();
  const positionsBuffer = gl.createBuffer();
  const vectorsBuffer = gl.createBuffer();
  const positionsArray = new Float32Array(positions);
  const vectorsArray = new Float32Array(vectors);
  const indicesArray = new Uint16Array(indices);
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(program.attributes['a_position']);
  gl.bindBuffer(gl.ARRAY_BUFFER, positionsBuffer);
  gl.vertexAttribPointer(
    program.attributes['a_position'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, positionsArray, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(program.attributes['a_vector']);
  gl.bindBuffer(gl.ARRAY_BUFFER, vectorsBuffer);
  gl.vertexAttribPointer(
    program.attributes['a_vector'],
    2,
    gl.FLOAT,
    false,
    0,
    0
  );
  gl.bufferData(gl.ARRAY_BUFFER, vectorsArray, gl.STATIC_DRAW);
  const indexBuffer = gl.createBuffer();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indicesArray, gl.STATIC_DRAW);
  gl.bindVertexArray(null);
  return {
    vao,
    count: indices.length,
    size: vectorsArray.byteLength + positionsArray.byteLength + indicesArray.byteLength
  };
}

function usePathProgram (program, projection, viewModel, pixelResolution) {
  const {
    context: gl
  } = program;
  gl.useProgram(program.program);
  gl.uniformMatrix4fv(program.uniforms['projection'], false, projection);
  gl.uniformMatrix4fv(program.uniforms['viewModel'], false, viewModel);
  gl.uniform2fv(program.uniforms['pixelResolution'], pixelResolution);
}

function useDefaultProgram (program, projection) {
  const {
    context: gl
  } = program;
  gl.useProgram(program.program);
  gl.uniformMatrix4fv(program.uniforms['projection'], false, projection);
  gl.uniformMatrix4fv(program.uniforms['viewModel'], false, IDENTITY_MATRIX);
}

function useCircleProgram (program, projection, pixelResolution) {
  const {
    context: gl
  } = program;
  gl.useProgram(program.program);
  gl.uniformMatrix4fv(program.uniforms['projection'], false, projection);
  gl.uniform2fv(program.uniforms['pixelResolution'], pixelResolution);
  gl.uniform1f(program.uniforms['border'], 0);
}

const IDENTITY_MATRIX = new Float32Array(buildIdentityMatrix());

function drawPath (
  program,
  buffer,
  options = {}
) {
  const {
    width = 1.0,
    color = '#000000',
    alpha,
    viewModel
  } = options || {};
  const {
    context: gl
  } = program;
  if (viewModel) {
    gl.uniformMatrix4fv(program.uniforms['viewModel'], false, viewModel);
  }
  gl.uniform1f(program.uniforms['width'], width);
  let {
    r, g, b, a
  } = parseColor(color);
  a = alpha === undefined ? a : alpha;
  gl.uniform4fv(program.uniforms['color'], new Float32Array([r / 255.0, g / 255.0, b / 255.0, a]));
  const {
    vao,
    count
  } = buffer;
  gl.bindVertexArray(vao);
  gl.drawElements(
    gl.TRIANGLES,
    count,
    gl.UNSIGNED_SHORT,
    0
  );
}

function calculateAngle (vector) {
  const angle = Math.atan2(vector.y, vector.x);
  if (angle < 0) {
    return angle + 2 * Math.PI;
  }
  return angle;
}

function drawLine (
  program,
  buffer,
  from,
  to,
  options = {}
) {
  const {
    x: fx,
    y: fy
  } = from;
  const {
    x: tx,
    y: ty
  } = to;
  const vector = {x: tx - fx, y: ty - fy};
  const length = Math.sqrt(vector.x ** 2 + vector.y ** 2);
  const angle = calculateAngle(vector);
  const viewModel = buildTranslationScaleRotateMatrix(
    from,
    {x: length, y: 1},
    angle
  );
  drawPath(program, buffer, {viewModel, ...(options || {})});
}

function drawRectangle (
  program,
  buffer,
  x,
  y,
  width,
  height,
  color = '#000000',
  alpha
) {
  const viewModel = buildTranslationScaleRotateMatrix(
    {x, y},
    {x: width, y: height},
    0.0
  );
  const {
    context: gl
  } = program;
  gl.uniformMatrix4fv(program.uniforms['viewModel'], false, viewModel);
  const {
    r, g, b, a
  } = parseColor(color);
  gl.uniform4fv(
    program.uniforms['color'],
    new Float32Array([r / 255.0, g / 255.0, b / 255.0, alpha !== undefined ? alpha : a])
  );
  const {
    vao,
    count
  } = buffer;
  gl.bindVertexArray(vao);
  gl.drawElements(
    gl.TRIANGLES,
    count,
    gl.UNSIGNED_SHORT,
    0
  );
}

function drawCircle (
  program,
  buffer,
  strokeBuffer,
  center,
  radius,
  options = {}
) {
  const {
    fill,
    stroke,
    strokeWidth = 1
  } = options;
  const {
    context: gl
  } = program;
  gl.uniform1f(program.uniforms['radius'], radius);
  gl.uniform2fv(program.uniforms['center'], new Float32Array(center));
  if (fill) {
    const {
      r, g, b, a
    } = parseColor(fill || '#000000');
    gl.uniform4fv(
      program.uniforms['color'],
      new Float32Array([r / 255.0, g / 255.0, b / 255.0, a])
    );
    const {
      vao,
      count
    } = buffer;
    gl.bindVertexArray(vao);
    gl.drawElements(
      gl.TRIANGLES,
      count,
      gl.UNSIGNED_SHORT,
      0
    );
  }
  if (stroke) {
    gl.uniform1f(program.uniforms['border'], strokeWidth);
    const {
      r, g, b, a
    } = parseColor(stroke || '#000000');
    gl.uniform4fv(
      program.uniforms['color'],
      new Float32Array([r / 255.0, g / 255.0, b / 255.0, a])
    );
    const {
      vao,
      count
    } = strokeBuffer;
    gl.bindVertexArray(vao);
    gl.drawElements(
      gl.TRIANGLES,
      count,
      gl.UNSIGNED_SHORT,
      0
    );
  }
}

function initializeProgram (gl, program) {
  const vertexShader = gl.createShader(gl.VERTEX_SHADER);
  gl.shaderSource(vertexShader, program.vertex);
  gl.compileShader(vertexShader);
  if (!gl.getShaderParameter(vertexShader, gl.COMPILE_STATUS)) {
    console.warn(gl.getShaderInfoLog(vertexShader));
    throw new Error(gl.getShaderInfoLog(vertexShader) || 'Error compiling shader');
  }

  // Create fragment shader
  const fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
  gl.shaderSource(fragmentShader, program.fragment);
  gl.compileShader(fragmentShader);
  if (!gl.getShaderParameter(fragmentShader, gl.COMPILE_STATUS)) {
    console.warn(gl.getShaderInfoLog(fragmentShader));
    throw new Error(gl.getShaderInfoLog(fragmentShader) || 'Error compiling shader');
  }

  // Create shader program
  const glProgram = gl.createProgram();
  gl.attachShader(glProgram, vertexShader);
  gl.attachShader(glProgram, fragmentShader);
  gl.linkProgram(glProgram);
  gl.useProgram(glProgram);

  const attributes = {};
  const uniforms = {};

  (program.attributes || []).forEach((attribute) => {
    attributes[attribute] = gl.getAttribLocation(glProgram, attribute);
  });
  (program.uniforms || []).forEach((uniform) => {
    uniforms[uniform] = gl.getUniformLocation(glProgram, uniform);
  });

  return {
    context: gl,
    program: glProgram,
    attributes,
    uniforms
  };
}

export {
  buildPathVAO,
  buildLineVAO,
  buildRectangleVAO,
  buildCircleVAO,
  buildStrokeCircleVAO,
  buildOrthoMatrix,
  buildIdentityMatrix,
  initializeProgram,
  usePathProgram,
  useDefaultProgram,
  useCircleProgram,
  drawCircle,
  drawRectangle,
  drawPath,
  drawLine,
  IDENTITY_MATRIX,
  pathProgram,
  defaultProgram,
  circleProgram
};
