/* eslint-disable no-unused-vars, no-empty */
const stream = require('stream');

/**
 * @param {Readable|Writable} aStream
 */
function safelyDestroyStream(aStream) {
  try {
    if (typeof aStream.end === 'function') {
      aStream.end();
    }
  } catch (_) {
  }
  try {
    if (typeof aStream.destroy === 'function') {
      aStream.destroy();
    }
  } catch (_) {
  }
}

/**
 * @param {Writable|Readable} aStream
 * @returns {Promise<*>}
 */
function waitForStreamClosed(aStream) {
  if (!aStream || aStream.closed) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    aStream.on('close', () => resolve());
  });
}

/**
 * Tries to close end destroy the stream and waits for "close" event
 * @param {Readable|Writable} aStream
 * @returns {Promise<*>}
 */
async function releaseStream(aStream) {
  safelyDestroyStream(aStream);
  await waitForStreamClosed(aStream);
}

class ReadableStreamFromBuffer extends stream.Readable {
  /**
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    super();
    this.buffer = buffer;
    this.idx = 0;
  }

  // eslint-disable-next-line no-underscore-dangle
  _read(size) {
    const from = this.idx;
    const length = this.buffer.byteLength;
    this.idx = Math.min((size > 0 ? (this.idx + size) : length), length);
    const to = this.idx;
    if (from >= this.buffer.length || to > this.buffer.length) {
      this.push(null);
    } else {
      this.push(this.buffer.slice(from, to));
    }
  }
}

/**
 * @param {string|Buffer|stream.Readable} data
 * @returns {ReadableStreamFromBuffer|Readable}
 */
function getReadableStream(data) {
  if (
    data instanceof stream.Stream
    // eslint-disable-next-line no-underscore-dangle
    && typeof data._read === 'function'
    // eslint-disable-next-line no-underscore-dangle
    && typeof data._readableState === 'object'
  ) {
    // readable stream
    return data;
  }
  if (typeof data === 'string') {
    return new ReadableStreamFromBuffer(Buffer.from(data));
  }
  if (Buffer.isBuffer(data)) {
    // buffer
    return new ReadableStreamFromBuffer(data);
  }
  return undefined;
}

module.exports = {
  waitForStreamClosed,
  releaseStream,
  getReadableStream,
  ReadableStreamFromBuffer,
};
