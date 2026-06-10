import {CommonProps} from '../../../@types/common.ts';
import {LibraryItem, LibraryItemType} from '../types.ts';
import {Configuration, DataStorage, Folder, Pipeline} from '../../../@types/library.ts';
import {isConfiguration, isDataStorage, isFolder, isPipeline} from '../../../utilities/guards.ts';
import {useQuery} from '@tanstack/react-query';
import {
  configurationQueryOptions,
  dataStorageQueryOptions,
  folderQueryOptions,
  pipelineQueryOptions,
} from '../../../queries';
import {UserName} from '../../shared/user-name/user-name.tsx';
import './library-header.css';

function LibraryPipelineOwner(props: CommonProps & {pipeline: Pipeline}) {
  const {className, style, pipeline} = props;
  const {data: pipelineObj} = useQuery(pipelineQueryOptions(pipeline.id));
  const owner = (pipelineObj ?? pipeline).owner;
  return <UserName className={className} style={style} userName={owner} showIcon />;
}

function LibraryStorageOwner(props: CommonProps & {storage: DataStorage}) {
  const {className, style, storage} = props;
  const {data: storageObj} = useQuery(dataStorageQueryOptions(storage.id));
  const owner = (storageObj ?? storage).owner;
  return <UserName className={className} style={style} userName={owner} showIcon />;
}

function LibraryConfigurationOwner(props: CommonProps & {configuration: Configuration}) {
  const {className, style, configuration} = props;
  const {data: configurationObj} = useQuery(configurationQueryOptions(configuration.id));
  const owner = (configurationObj ?? configuration).owner;
  return <UserName className={className} style={style} userName={owner} showIcon />;
}

function LibraryFolderOwner(props: CommonProps & {folder: Folder}) {
  const {className, style, folder} = props;
  const {data: folderObj} = useQuery(folderQueryOptions(folder.id));
  const owner = (folderObj ?? folder).owner;
  return <UserName className={className} style={style} userName={owner} showIcon />;
}

function LibraryItemOwner(
  props: CommonProps & {
    item: LibraryItem;
  },
) {
  const {className, style, item} = props;
  if (
    (item.type === LibraryItemType.pipeline || item.type === LibraryItemType.pipelineVersion) &&
    isPipeline(item.object)
  ) {
    return <LibraryPipelineOwner className={className} style={style} pipeline={item.object} />;
  }
  if (item.type === LibraryItemType.storage && isDataStorage(item.object)) {
    return <LibraryStorageOwner className={className} style={style} storage={item.object} />;
  }
  if (
    (item.type === LibraryItemType.folder ||
      item.type === LibraryItemType.project ||
      item.type === LibraryItemType.metadata ||
      item.type === LibraryItemType.metadataClass) &&
    isFolder(item.object)
  ) {
    return <LibraryFolderOwner className={className} style={style} folder={item.object} />;
  }
  if (item.type === LibraryItemType.configuration && isConfiguration(item.object)) {
    return (
      <LibraryConfigurationOwner className={className} style={style} configuration={item.object} />
    );
  }
  return null;
}

export {LibraryItemOwner};
