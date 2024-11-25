import { TabButton } from '@epam/uui';
import { RoutePath } from '../../shared/constants/routes';
import { useMatch } from 'react-router';
import type { HeaderRouteLinkProps } from './types.ts';

export const HeaderRouteLink = (props: HeaderRouteLinkProps) => {
  const { link } = props;
  const { route, caption } = link;
  const pathname = RoutePath[route];
  const o = useMatch(pathname);
  return (
    <TabButton
      cx="text-white"
      link={{ pathname }}
      caption={caption}
      isLinkActive={Boolean(o)}
    />
  );
};
