import {FacetedSearchPage} from '../../components/search';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function SearchPage() {
  return <LegacyComponentBridge component={FacetedSearchPage} />;
}

export {SearchPage};
