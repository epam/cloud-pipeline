import classNames from 'classnames';
import {CommonProps} from '../../../@types/common.ts';
import {
  FolderContents,
  LibraryFolderContentsCommonProps,
  RootLibraryContents,
} from './library-folder-contents.tsx';
import {LibraryItem} from '../types.ts';
import './library-contents.css';

function LibraryContents(
  props: CommonProps &
    LibraryFolderContentsCommonProps & {
      folder?: number;
    },
) {
  const {className, folder, ...commonProps} = props;
  if (folder === undefined) {
    return (
      <RootLibraryContents
        key="root-library"
        className={classNames(className, 'library-contents')}
        {...commonProps}
      />
    );
  }
  return (
    <FolderContents
      key="folder-library"
      className={classNames(className, 'library-contents')}
      folder={folder}
      {...commonProps}
    />
  );
}

export {LibraryContents};
