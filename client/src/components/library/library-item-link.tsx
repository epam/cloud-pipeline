import {MouseEvent, ReactNode, useCallback} from 'react';
import {Link} from 'react-router';
import classNames from 'classnames';
import {CommonProps} from '../../@types/common.ts';
import {LibraryItem} from './types.ts';

function LibraryItemLink(
  props: CommonProps & {
    item: LibraryItem;
    onClick?: (event: MouseEvent) => void;
    children?: ReactNode;
  },
) {
  const {className, style, item, onClick, children} = props;
  const onClickWrapper = useCallback(
    (event: MouseEvent) => {
      if (onClick) {
        event.preventDefault();
        onClick(event);
      }
    },
    [onClick],
  );
  if (item.url && item.interactive) {
    return (
      <Link
        className={classNames(className, 'cp-text')}
        style={style}
        to={item.url}
        onClick={onClickWrapper}
        target={onClick ? '_blank' : undefined}
        data-library-tree-item-id={item.id}
      >
        {children}
      </Link>
    );
  }
  return (
    <div
      className={classNames(className, 'cp-text')}
      style={style}
      onClick={item.interactive ? onClick : undefined}
      data-library-tree-item-id={item.id}
    >
      {children}
    </div>
  );
}

export {LibraryItemLink};
