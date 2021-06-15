import MetadataLoad from '../../../../models/metadata/MetadataLoad';

export default async function getObjectMetadataAttribute (folderId, userInfo, attributeName) {

  const entityClasses = ['FOLDER', 'PIPELINE_USER', 'ROLE'];
  let attributeValue = [];

  for (let key in entityClasses) {
    if (attributeValue.length === 0) {
      const entityId = key === 'FOLDER' ? folderId : userInfo.id;
      const metadataRequest = new MetadataLoad(entityId, entityClasses[key]);
      await metadataRequest.fetch();
      if (metadataRequest.value && metadataRequest.value.length && metadataRequest.value[0].data) {
        if (attributeName in metadataRequest.value[0].data) {
          attributeValue = metadataRequest.value[0].data[attributeName].value
            .split(',')
            .map(item => item.trim());
        }
      }
    } else {
      return attributeValue;
    }
  }
  return attributeValue;
}
