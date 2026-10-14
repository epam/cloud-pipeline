import apiPost from '../base/api-post';
import getSettings from '../base/settings';

function getShareWithIdentities (settings) {
  if (!settings) {
    return [];
  }
  if (settings.isAnonymous) {
    console.log(
      'Sharing with groups:',
      settings?.anonymousAccess?.shareWithGroups,
      '(reading from anonymousAccess.shareWithGroups preference)'
    );
    return (settings?.anonymousAccess?.shareWithGroups || '')
      .split(',')
      .map(u => u.trim())
      .filter(Boolean)
      .map(u => ({name: u, isPrincipal: false}))
  }
  console.log(
    'Sharing with users:',
    settings?.shareWithUsers,
    'Sharing with groups:',
    settings?.shareWithGroups
  );
  return [
    ...(settings?.shareWithUsers || '')
      .split(',')
      .map(u => u.trim())
      .filter(Boolean)
      .map(u => ({name: u, isPrincipal: true})),
    ...(settings?.shareWithGroups || '')
      .split(',')
      .map(u => u.trim())
      .filter(Boolean)
      .map(u => ({name: u, isPrincipal: false})),
  ];
}

export default function shareRun(id) {
  return new Promise((resolve, reject) => {
    getSettings()
      .then((settings) => {
        const shareWith = getShareWithIdentities(settings)
          .map(s => ({...s, accessType: 'ENDPOINT', runId: id}));
        if (shareWith.length > 0) {
          console.log(`Sharing run #${id} with ${shareWith.map(s => s.name).join(', ')}`);
          apiPost(`run/${id}/updateSids`, shareWith)
            .then((result) => {
              const {status, message, payload} = result;
              if (status === 'OK') {
                resolve(payload);
              } else {
                reject(new Error(message || `Error sharing run: status ${status}`));
              }
            })
            .catch(reject);
        } else {
          resolve();
        }
      })
      .catch(reject);
  });
}
