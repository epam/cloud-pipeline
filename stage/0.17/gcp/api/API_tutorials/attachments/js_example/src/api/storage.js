import request from './request';

const PAGE_SIZE = 1000;

function info(id) {
  return request(`datastorage/${id}/load`);
}

function list(id, path, page = 1) {
  if (!path) {
    return request(`datastorage/${id}/list/page?showVersion=false&page=${page}&pageSize=${PAGE_SIZE}`)
  }
  return request(`datastorage/${id}/list/page?path=${path}&showVersion=false&page=${page}&pageSize=${PAGE_SIZE}`);
}

function generatePreSignedUrl(id, path) {
  return request(`datastorage/${id}/generateUrl?path=${encodeURIComponent(path)}&contentDisposition=INLINE`);
}

export {info, list, generatePreSignedUrl};
