function base64ToArrayBuffer (base64) {
  const binaryString = atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

const textDecoder = new TextDecoder();

export function base64toString (base64string) {
  const buffer = base64ToArrayBuffer(base64string);
  return textDecoder.decode(buffer);
}
