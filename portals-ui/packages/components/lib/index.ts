import List, { ListHeader } from './components/list';
import type { UserCardProps } from './components/user-card';
import { UserCard } from './components/user-card';
import '@epam/uui-components/styles.css';
import '@epam/uui/styles.css';
import './style.css';

export { List, UserCard, ListHeader };
export type { UserCardProps };
export type * from './components/common.types';
export type * from './components/list/types';
