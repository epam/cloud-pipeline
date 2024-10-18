import DataStorageRequest from "../../../../models/dataStorage/DataStoragePage";
import DataStorageItemContent from "../../../../models/dataStorage/DataStorageItemContent";
import {base64toString} from "../../../../utils/base64";
import dataStorages from "../../../../models/dataStorage/DataStorages";

export function getNgbFileName (path) {
  return (path || '').split(/[\/\\]/).pop();
}

export function getNgbFileExtension (path) {
  const fileName = getNgbFileName(path);
  const extensions = fileName.split('.');
  let extension = extensions.pop();
  if (/^gz$/i.test(extension)) {
    extension = extensions.pop();
  }
  return (extension || '').toLowerCase();
}

const NGB_SETTINGS_FILE_DEFAULT_NAME = 'ngb.settings';

export function getNgbSettingsFilePath (path, ngbSettingsFileName = NGB_SETTINGS_FILE_DEFAULT_NAME) {
  const parts = (path || '').split(/[\/\\]/);
  parts.pop();
  return `${parts.join('/')}/${ngbSettingsFileName}`;
}

const NGB_FILES_SUPPORTED_EXTENSIONS = ['bam', 'vcf', 'bed'];

export function isNgbFile (path, supportedExtensions = NGB_FILES_SUPPORTED_EXTENSIONS) {
  const extension = getNgbFileExtension(path);
  return supportedExtensions.includes(extension);
}

function appendExtension(path, newExtension) {
  return `${path}.${newExtension}`;
}

function replaceExtension(path, newExtension) {
  const parts = path.split(/[\/\\]/);
  const fileName = parts.pop();
  const n = fileName.split('.');
  n.pop();
  return `${parts.join('/')}/${n.join('.')}.${newExtension}`;
}

const NGB_FILE_INDEX_PATH = {
  bam: [
    (path) => appendExtension(path, 'bai'),
    (path) => replaceExtension(path, 'bai')
  ],
  vcf: [
    (path) => appendExtension(path, 'tbi'),
    (path) => appendExtension(path, 'idx')
  ],
};

export function getNgbFileIndexPaths(ngbFilePath) {
  const extension = getNgbFileExtension(ngbFilePath);
  return (NGB_FILE_INDEX_PATH[extension] || []).map((rule) => rule(ngbFilePath));
}

export async function findIndexFile(storageId, ngbFilePath) {
  const findIndexFileForCandidate = async (candidate) => {
    const request = new DataStorageRequest(
      storageId,
      decodeURIComponent(candidate),
      false,
      false,
      100,
    );
    try {
      await request.fetchPage(undefined);
      if (!request.loaded) {
        return undefined;
      }
      const items = ((request.value || {}).results || []).map((result) => result.path);
      return items.find((i) => i.toLowerCase() === candidate.toLowerCase());
    } catch {
      // noop
    }
    return undefined;
  };
  const candidates = getNgbFileIndexPaths(ngbFilePath);
  const iterate = async (index = 0) => {
    if (index >= candidates.length) {
      return undefined;
    }
    const result = await findIndexFileForCandidate(candidates[index]);
    if (result) {
      return result;
    }
    return iterate(index + 1);
  };
  return iterate();
}

async function readFileContents(storageId, path) {
  const request = new DataStorageItemContent(storageId, path);
  await request.fetch();
  if (request.loaded) {
    const {
      content
    } = request.value;
    if (content) {
      try {
        return base64toString(content);
      } catch (e) {
        // eslint-disable-next-line
        throw new Error(`Error reading content for path ${path} (storage #${storageId}): ${e.message}`);
      }
    } else {
      throw new Error(`Empty content for path ${path} (storage #${storageId})`);
    }
  } else {
    throw new Error(request.error);
  }
}

async function readNgbSettings(storageId, ngbFilePath, ngbSettingsFileName = NGB_SETTINGS_FILE_DEFAULT_NAME) {
  try {
    const content = await readFileContents(storageId, getNgbSettingsFilePath(ngbFilePath, ngbSettingsFileName));
    if (content) {
      return JSON.parse(content);
    }
    return {};
  } catch {
    return {};
  }
}

/**
 * @typedef {Object} OpenNgbFileOptions
 * @property {number} storageId
 * @property {string} path
 * @property {Object} preferences
 */

/**
 * @param {OpenNgbFileOptions} options
 * @returns {Promise<void>}
 */
export async function openNgbFile (options) {
  const {
    storageId,
    path,
  } = options;
  const fileName = (path || '').split(/[\/\\]/).pop();
  const indexFile = await findIndexFile(storageId, path);
  const settings = await readNgbSettings(storageId, path);
  const {
    server,
    reference,
    annotation_tracks = [],
    annotationTracks = annotation_tracks,
    [fileName]: fileNameSettings = {},
    dataset = false,
    ...rest
  } = settings || {};
  if (!server) {
    throw new Error('NGB server not specified');
  }
  if (!reference) {
    throw new Error('Reference not specified');
  }
  const storageRequest = dataStorages.load(storageId);
  await storageRequest.fetchIfNeededOrWait();
  if (storageRequest.error) {
    throw new Error(`Error fetching storage info: ${storageRequest.error}`);
  }
  const {
    pathMask,
  } = storageRequest.value || {};
  let storagePathMask = pathMask || '';
  if (!storagePathMask.endsWith('/')) {
    storagePathMask = storagePathMask.concat('/');
  }
  const {
    chromosome,
    chr = chromosome,
    start,
    from = start,
    end,
    to = end,
  } = fileNameSettings || {};
  const tracks = [{
    b: reference,
    n: reference,
    l: true,
    f: 'REFERENCE',
    p: reference,
  }, ...annotationTracks];
  const getFilePath = (storagePath) => storagePath.startsWith('/') ? `${storagePathMask}${storagePath.slice(1)}` : `${storagePathMask}${storagePath}`;
  const extension = getNgbFileExtension(path);
  const trackFilePath = getFilePath(path);
  const trackIndexFilePath = indexFile ? getFilePath(indexFile) : undefined;
  tracks.push({
    b: trackFilePath,
    n: trackFilePath,
    i1: trackIndexFilePath,
    f: extension.toUpperCase(),
    l: true,
    p: reference,
  });
  if (dataset && settings[fileName]) {
    const root = path.split(/[\/\\]/).slice(0, -1).join('/');
    Object.entries(rest).forEach(([track, settings]) => {
      const datasetTrackPath = `${root}/${track}`;
      const datasetTrackUrl = getFilePath(datasetTrackPath);
      const datasetTrackIndex = getNgbFileIndexPaths(datasetTrackPath)[0];
      const datasetTrackIndexUrl = datasetTrackIndex ? getFilePath(datasetTrackIndex) : undefined;
      const datasetTrackExtension = getNgbFileExtension(datasetTrackPath);
      tracks.push({
        b: datasetTrackUrl,
        n: datasetTrackUrl,
        i1: datasetTrackIndexUrl,
        f: datasetTrackExtension.toUpperCase(),
        l: true,
        p: reference,
      })
    });
  }
  let ngbServer = server;
  if (!ngbServer.endsWith('/')) {
    ngbServer = ngbServer.concat('/');
  }
  ngbServer = ngbServer.concat('#/').concat(reference).concat('/');
  if (chr !== undefined && from !== undefined && to !== undefined) {
    ngbServer = ngbServer.concat(`${chr}/${from}/${to}`);
  } else if (chr !== undefined) {
    ngbServer = ngbServer.concat(`${chr}/`);
  }
  const url = ngbServer.concat(`?tracks=${encodeURIComponent(JSON.stringify(tracks))}`);
  window.open(url, '_blank');
}
