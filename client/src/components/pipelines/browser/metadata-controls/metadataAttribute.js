import MetadataMultiLoad from '../../../../models/metadata/MetadataMultiLoad';

export default async function getObjectMetadataAttribute (folderId, userInfo, attributeName) {

  const requestBody = [
    {
      entityClass: 'FOLDER',
      entityId: folderId
    }, {
      entityClass: 'PIPELINE_USER',
      entityId: userInfo.id
    },
    ...userInfo.roles.map(role => ({
      entityClass: 'ROLE',
      entityId: role.id
    }))
  ];

  try {
    const metadataRequest = new MetadataMultiLoad(requestBody);
    await metadataRequest.fetch();
    if (metadataRequest.value && metadataRequest.value.length) {
      const entityClasses = ['FOLDER', 'PIPELINE_USER', 'ROLE'];
      let attributeValue = [];
      for (let key in entityClasses) {
        if (attributeValue.length === 0) {
          const metadataRequestValue = metadataRequest.value
            .filter(item => item &&
              item.entity.entityClass === entityClasses[key] &&
              item.data[attributeName]
            )[0];
          if (metadataRequestValue && metadataRequestValue.data) {
            attributeValue = metadataRequestValue.data[attributeName].value
              .split(',')
              .filter(item => item)
              .map(item => item.trim());
            if (attributeValue.length) {
              return attributeValue;
            }
          }
        }
      }
    }
  } catch (e) {
    return [];
  }
  return [];
}
