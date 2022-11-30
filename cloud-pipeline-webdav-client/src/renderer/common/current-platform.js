import ipcResponse from './ipc-response';

let cache;

export default async function getCurrentPlatform() {
  if (!cache) {
    cache = ipcResponse('getCurrentPlatform');
  }
  return cache;
}
