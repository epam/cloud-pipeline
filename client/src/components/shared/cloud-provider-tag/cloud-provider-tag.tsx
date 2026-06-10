import classNames from 'classnames';
import {CommonProps} from '../../../@types/common.ts';
import './cloud-provider-tag.css';
import {useCloudRegion} from '../../../queries/cloud-regions/hooks.ts';

type CloudProviderTagProps = CommonProps & {
  provider?: string;
  regionId?: string | number;
};

function CloudProviderTag(props: CloudProviderTagProps) {
  const {className, style, provider, regionId} = props;
  const region = useCloudRegion(regionId);
  const pr = provider ?? region?.provider;
  if (!pr) {
    return null;
  }
  return (
    <span
      className={classNames(className, 'cloud-provider-tag', 'provider', pr.toLowerCase())}
      style={style}
    />
  );
}

export {CloudProviderTag};
