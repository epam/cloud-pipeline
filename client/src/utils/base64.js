function base64ToArrayBuffer (base64) {
  const binaryString = atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

function arrayBufferToBase64 (buffer) {
  let binaryString = '';
  for (let i = 0; i < buffer.length; i++) {
    binaryString += String.fromCharCode(buffer[i]);
  }
  return btoa(binaryString);
}

const textDecoder = new TextDecoder();
const textEncoder = new TextEncoder();

export function base64toString (base64string) {
  const buffer = base64ToArrayBuffer(base64string);
  return textDecoder.decode(buffer);
}

export function stringToBase64 (originalString) {
  const buffer = textEncoder.encode(originalString);
  return arrayBufferToBase64(buffer);
}
