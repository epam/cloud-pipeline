import {CommonProps} from '../../@types/common.ts';
import {LibraryItem, LibraryItemType} from './types.ts';
import {
  AppstoreOutlined,
  ClockCircleOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  ForkOutlined,
  HddOutlined,
  InboxOutlined,
  LoadingOutlined,
  SettingOutlined,
  SolutionOutlined,
  TagFilled,
} from '@ant-design/icons';
import classNames from 'classnames';
import {isPipeline} from '../../utilities/guards.ts';
import {DataStorage} from '../../@types/library.ts';
import React from 'react';

function LibraryItemIcon(
  props: CommonProps & {
    item: LibraryItem;
  },
) {
  const {className, style, item} = props;
  switch (item.type) {
    case LibraryItemType.loading:
      return <LoadingOutlined className={classNames(className, 'text-faded')} style={style} />;
    case LibraryItemType.back:
      return <FolderOutlined className={className} style={style} />;
    case LibraryItemType.project:
      return <SolutionOutlined className={className} style={style} />;
    case LibraryItemType.library:
    case LibraryItemType.folder:
      return item.expanded ? (
        <FolderOpenOutlined className={className} style={style} />
      ) : (
        <FolderOutlined className={className} style={style} />
      );
    case LibraryItemType.storage: {
      const dataStorage = item.object as DataStorage;
      switch (dataStorage.type) {
        case 'NFS':
          return (
            <HddOutlined
              className={classNames(className, {'cp-sensitive': dataStorage.sensitive})}
              style={style}
            />
          );
        default:
          return (
            <InboxOutlined
              className={classNames(className, {'cp-sensitive': dataStorage.sensitive})}
              style={style}
            />
          );
      }
    }
    case LibraryItemType.storages:
      return <InboxOutlined className={className} style={style} />;
    case LibraryItemType.pipeline: {
      if (isPipeline(item.object) && item.object.pipelineType === 'VERSIONED_STORAGE') {
        return (
          <InboxOutlined className={classNames(className, 'cp-versioned-storage')} style={style} />
        );
      }
      return <ForkOutlined className={className} style={style} />;
    }
    case LibraryItemType.pipelineVersion:
      return <TagFilled className={className} style={style} />;
    case LibraryItemType.pipelines:
      return <ForkOutlined className={className} style={style} />;
    case LibraryItemType.configuration:
      return <SettingOutlined className={className} style={style} />;
    case LibraryItemType.metadata:
    case LibraryItemType.metadataClass:
      return <AppstoreOutlined className={className} style={style} />;
    case LibraryItemType.projectHistory:
      return <ClockCircleOutlined className={className} style={style} />;
    default:
      return <FileOutlined className={className} style={style} />;
  }
}

export {LibraryItemIcon};
