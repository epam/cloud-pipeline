import {BillingReports} from '../../components/billing';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function BillingReportsPage() {
  return <LegacyComponentBridge component={BillingReports as never} />;
}

export {BillingReportsPage};
