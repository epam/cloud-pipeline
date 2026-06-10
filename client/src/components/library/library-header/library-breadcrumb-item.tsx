import {ReactNode, useCallback} from 'react';
import type {MouseEvent} from 'react';
import {CommonProps} from '../../../@types/common.ts';
import {LibraryItem} from '../types.ts';
import {LibraryItemLink} from '../library-item-link.tsx';

function LibraryBreadcrumbItem(
  props: CommonProps & {
    item: LibraryItem;
    onClick?: (item: LibraryItem) => void;
    children?: ReactNode;
  },
) {
  const {className, style, item, onClick, children} = props;
  const wrapClick = useCallback(
    (event: MouseEvent) => {
      if (onClick) {
        event.stopPropagation();
        event.preventDefault();
        onClick(item);
      }
    },
    [onClick, item],
  );
  return (
    <LibraryItemLink
      className={className}
      style={style}
      item={item}
      onClick={onClick ? wrapClick : undefined}
    >
      {children}
    </LibraryItemLink>
  );
}

export {LibraryBreadcrumbItem};
