import apiPost from '../base/api-post';

export default function launchTool(id, image, settings) {
  return new Promise((resolve, reject) => {
    const params = Object.assign(
      {},
      settings?.parameters,
      {
        APPLICATION: {value: id, type: 'number', required: false}
      }
    );
    const payload = {
      cloudRegionId: settings?.cloudRegionId,
      cmdTemplate: settings?.cmdTemplate,
      dockerImage: image,
      hddSize: settings?.instance_disk,
      instanceType: settings?.instance_size,
      isSpot: settings?.is_spot !== undefined ? `${settings?.is_spot}` === 'true' : undefined,
      nodeCount: settings?.nodeCount !== undefined ? settings?.nodeCount : undefined,
      parentNodeId: settings?.parentNodeId,
      prettyUrl: settings?.prettyUrl,
      params
    };
    apiPost('run', payload)
      .then(result => {
        const {status, message, payload: run} = result;
        if (status === 'OK') {
          resolve(run);
        } else {
          reject(new Error(message || `Error launching a tool: status ${status}`));
        }
      })
      .catch(reject);
  });
}
