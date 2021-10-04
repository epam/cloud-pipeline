import {FileOutlined, FolderOutlined} from '@ant-design/icons';

const getDataStorageItemIcon = (item) => {
  if (item.type.toLowerCase() === 'folder') {
    return FolderOutlined;
  }
  if (item.type.toLowerCase() === 'file') {
    return FileOutlined;
  }
  return FileOutlined;
};

export default getDataStorageItemIcon;
