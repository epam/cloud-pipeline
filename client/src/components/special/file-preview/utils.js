import dataStorageAvailable from '../../../models/dataStorage/DataStorageAvailable';
import {renderers} from './renderers';
import {getStorageLinkInfo} from '../data-storage-link/utilities';
import dataStorageCache from '../../../models/dataStorage/DataStorageCache';
import PlainTextRenderer from './renderers/plain-text-renderer';
import DataStorageItemSize from '../../../models/dataStorage/DataStorageItemSize';
import displaySize from '../../../utils/displaySize';

const BYTE = 1;
const KB = 1024 * BYTE;
const MB = 1024 * KB;

const MAX_SIZE_TO_PREVIEW = 20 * MB;

export async function getFilePreviewConfiguration(filePath) {
  const ext = filePath.split('.').pop().toLowerCase();
  await dataStorageAvailable.fetchIfNeededOrWait();
  const info = getStorageLinkInfo({
    path: filePath,
    storages: dataStorageAvailable.value ?? [],
    isFolder: false,
  });
  const {storage, relativePath} = info ?? {};
  if (!storage) {
    throw new Error('storage not found');
  }
  const renderer = renderers.find(
    (renderer) => typeof renderer.testExtension === 'function' && renderer.testExtension(ext),
  );
  if (renderer) {
    return {
      storage,
      renderer,
      path: relativePath,
    };
  }
  const content = dataStorageCache.getContent(storage.id, relativePath);
  const getSize = async () => {
    const sizeRequest = new DataStorageItemSize();
    let p = storage.pathMask;
    if (p.endsWith('/')) {
      p = p.slice(0, -1);
    }
    await sizeRequest.send([`${p}/${relativePath}`]);
    if (sizeRequest.error) {
      throw new Error(`error fetching file size (${sizeRequest.error})`);
    }
    const {size = 0} = (sizeRequest.value || [])[0] || {};
    return size;
  };
  const [, size] = await Promise.all([content.fetch(), getSize()]);
  if (content.error) {
    throw new Error(content.error);
  }
  if (size > MAX_SIZE_TO_PREVIEW) {
    throw new Error(`file size too large for preview (${displaySize(size)})`);
  }
  const {mayBeBinary} = content.value;
  if (mayBeBinary) {
    throw new Error('preview not available for binary files');
  }
  return {
    storage,
    renderer: PlainTextRenderer,
    path: relativePath,
  };
}

export function externalPreviewConfiguration(filePath) {
  const info = getStorageLinkInfo({
    path: filePath,
    storages: dataStorageAvailable.value ?? [],
    isFolder: false,
  });
  const {storage, relativePath} = info ?? {};
  const ext = filePath.split('.').pop().toLowerCase();
  const renderer = renderers.find(
    (renderer) => typeof renderer.testExtension === 'function' && renderer.testExtension(ext),
  );
  return renderer && renderer.getPreviewConfiguration
    ? renderer.getPreviewConfiguration({storage, relativePath})
    : undefined;
}
