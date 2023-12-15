function getErrorTitle(resource, message) {
  if (resource) {
    return `"${resource}": ${(message || '').toLowerCase()}`;
  }
  return message;
}

function getErrorMessage(resource, message, details) {
  let title = getErrorTitle(resource, message);
  if (details) {
    if (title.endsWith('.')) {
      title = title.slice(0, -1);
    }
    return `${title}. ${details}`;
  }
  return title;
}

class WebDAVError extends Error {
  constructor(error, resource, details) {
    if ([401, 403].includes(error.status)) {
      super(getErrorMessage(resource, 'Access denied', details));
    } else if ([404].includes(error.status)) {
      super(getErrorMessage(resource, 'Not found', details));
    } else {
      super(getErrorMessage(resource, error.statusText || error.message, details));
    }
  }
}

module.exports = WebDAVError;
