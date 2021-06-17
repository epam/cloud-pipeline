import MetadataLoad from '../../../../models/metadata/MetadataLoad';

export default async function getObjectMetadataAttribute (folderId, userInfo, attributeName) {

  const entityClasses = ['FOLDER', 'PIPELINE_USER', 'ROLE'];
  let attributeValue = [];

  for (let key in entityClasses) {
    if (attributeValue.length === 0) {
      const entityId = key === 0 ? folderId : userInfo.id;
      const metadataRequest = new MetadataLoad(entityId, entityClasses[key]);
      try {
        await metadataRequest.fetch();
        if (
          metadataRequest.value &&
          metadataRequest.value.length &&
          metadataRequest.value[0].data &&
          metadataRequest.value[0].data[attributeName] &&
          metadataRequest.value[0].data[attributeName].value
        ) {
          attributeValue = metadataRequest.value[0].data[attributeName].value
            .split(',')
            .filter(item => item)
            .map(item => item.trim());
          if (attributeValue.length) {
            return attributeValue;
          }
        }
      } catch (e) {
        return [];
      }
    }
  }
  return [];
}
