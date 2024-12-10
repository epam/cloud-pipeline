import { ItemKey, ItemKeyResolved } from './types';
import { useCallback } from 'react';

export function useItemKey<Item>(
  itemKey?: ItemKey<Item>,
): ItemKeyResolved<Item> {
  return useCallback(
    (item: Item, index: number): string => {
      if (itemKey && typeof itemKey === 'function') {
        return String(itemKey(item, index));
      }
      if (itemKey && Object.hasOwnProperty.call(item, itemKey)) {
        return String(item[itemKey as keyof Item]);
      }
      return `key_${index}`;
    },
    [itemKey],
  );
}
