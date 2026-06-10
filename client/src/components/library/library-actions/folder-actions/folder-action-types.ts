import type {MenuProps} from 'antd';
import type {ReactNode} from 'react';
import type {TemplateDescription} from '../../../../@types/app.ts';

/** Template entry returned by templates/list and templates/folder/list APIs. */
export type FolderActionTemplate = TemplateDescription;

/** Menu item shape used by folder actions (extends antd items with legacy test ids). */
export type FolderActionMenuItem = {
  key: string;
  label?: ReactNode;
  id?: string;
  className?: string;
  type?: 'divider' | 'group';
  children?: FolderActionMenuItem[];
};

export type FolderActionMenuItems = FolderActionMenuItem[];

export function asAntdMenuItems(items: FolderActionMenuItems): MenuProps['items'] {
  return items as MenuProps['items'];
}
