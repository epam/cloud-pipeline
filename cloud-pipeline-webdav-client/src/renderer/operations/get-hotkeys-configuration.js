import types from './types';
import getCurrentPlatform from '../common/current-platform';

export default async function getHotKeysConfiguration() {
  // eslint-disable-next-line
  const currentPlatform = await getCurrentPlatform();
  // todo: platform-specific hotkeys
  return {
    [types.createDirectory]: 'F7',
    [types.copy]: 'F5',
    [types.move]: 'F6',
    [types.remove]: 'F8',
    [types.refresh]: 'F2',
  };
}
