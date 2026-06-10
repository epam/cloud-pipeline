import type {ComponentType} from 'react';
import {useLocation} from 'react-router-dom';
import {GeneralReport, InstanceReport, StorageReport} from '../../components/billing/reports';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

const sectionComponents: Record<string, ComponentType<Record<string, unknown>>> = {
  instance: InstanceReport,
  storage: StorageReport,
};

function BillingReportSectionPage() {
  const {pathname} = useLocation();
  const sectionKey = pathname.split('/').filter(Boolean)[2];
  const Component = sectionKey ? sectionComponents[sectionKey] : GeneralReport;

  return <LegacyComponentBridge component={Component as never} />;
}

export {BillingReportSectionPage};
