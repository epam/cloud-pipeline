import {LibraryItem} from '../../types.ts';

export type LibraryInlineActionsProps = {
  item: LibraryItem;
  onIssuesClick?: () => void;
};
