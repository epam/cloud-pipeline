import apiCall from '../base/api-call';

export default function getToolImage (tool) {
  return new Promise((resolve, reject) => {
    apiCall(`tool/${tool}/icon`, {}, 'GET', undefined, {isBlob: true})
      .then(blob => {
        const reader = new FileReader();
        reader.onloadend = function() {
          resolve(reader.result);
        }
        reader.readAsDataURL(blob);
      })
      .catch(reject);
  });
}
