import Billing from '../../components/billing';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function BillingLayout() {
  return <LegacyComponentBridge component={Billing} />;
}

export {BillingLayout};
