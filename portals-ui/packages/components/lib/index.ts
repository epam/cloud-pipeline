import List, { ListHeader } from './components/list';
import type { UserCardProps } from './components/user-card';
import { UserCard } from './components/user-card';
import { StatusIcon } from './components/status-icon';
import type { TagProps } from './components/tag';
import { Tag } from './components/tag';
import '@epam/uui-components/styles.css';
import '@epam/uui/styles.css';
import './style.css';

export { List, UserCard, ListHeader, StatusIcon, Tag };
export type { UserCardProps, TagProps };
export type * from './components/common.types';
export type * from './components/list/types';
