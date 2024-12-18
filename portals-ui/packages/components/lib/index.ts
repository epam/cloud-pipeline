import List, { ListHeader } from './components/list';
import type { UserCardProps } from './components/user-card';
import { UserCard } from './components/user-card';
import { StatusIcon } from './components/status-icon';
import type { TagProps } from './components/tag';
import { Tag } from './components/tag';
import { Price, RunPrice } from './components/price';
import '@epam/uui-components/styles.css';
import '@epam/uui/styles.css';
import './style.css';

export { List, UserCard, ListHeader, StatusIcon, Tag, RunPrice, Price };
export type { UserCardProps, TagProps };
export type * from './components/common.types';
export type * from './components/list/types';
export type * from './components/price/types';
