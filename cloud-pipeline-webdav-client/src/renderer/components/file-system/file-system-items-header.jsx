import React, { useContext } from 'react';
import { FileSystemContentsContext } from './hooks/use-file-system-contents';
import {
  SplitPanelRow,
  SplitPanelColumn,
  SplitPanelGrid,
} from '../../common/split-panel';
import { SortingProperty } from './utilities/sorting';
import SortingIcon from './utilities/sorting-icon';
import { NAME, SIZE, CHANGED } from './utilities/columns';
import './file-system-contents.css';

function FileSystemItemsHeader() {
  const {
    onSortByName,
    onSortByChangedDate,
    onSortBySize,
    sorting,
  } = useContext(FileSystemContentsContext);
  return (
    <SplitPanelGrid
      className="directory-contents-header"
      resizerClassName="header-resizer"
    >
      <SplitPanelRow row="header">
        <SplitPanelColumn
          column={NAME}
          className="element-header"
          onClick={onSortByName}
        >
          <span>Name</span>
          <SortingIcon
            sorting={sorting}
            property={SortingProperty.name}
          />
        </SplitPanelColumn>
        <SplitPanelColumn
          column={SIZE}
          className="element-header"
          onClick={onSortBySize}
        >
          <span>Size</span>
          <SortingIcon
            sorting={sorting}
            property={SortingProperty.size}
          />
        </SplitPanelColumn>
        <SplitPanelColumn
          column={CHANGED}
          className="element-header"
          onClick={onSortByChangedDate}
        >
          <span>Modified</span>
          <SortingIcon
            sorting={sorting}
            property={SortingProperty.changed}
          />
        </SplitPanelColumn>
      </SplitPanelRow>
    </SplitPanelGrid>
  );
}

export default FileSystemItemsHeader;
