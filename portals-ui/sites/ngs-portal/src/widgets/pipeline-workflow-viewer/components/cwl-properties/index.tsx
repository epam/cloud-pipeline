import { Button } from 'antd';
import type { ReactNode, MouseEvent as ReactMouseEvent } from 'react';
import { useMemo, useState } from 'react';
import './styles.css';
import { XMarkIcon } from '@heroicons/react/24/outline';

type CWLProperty = {
  key: string;
  title?: string;
  buttonTitle?: string;
  component: ReactNode;
  step?: unknown;
};

type Props = {
  properties: CWLProperty[];
  title: string;
  buttonTitle: string;
};

export default function CWLProperties({ properties }: Props) {
  const [visible, setVisible] = useState<string | undefined>();
  const propertiesConfig = useMemo(
    () => properties?.find((aProperties) => visible && aProperties.key === visible),
    [properties, visible],
  );
  const openPropertiesPanel = (aKey: string) => (event: ReactMouseEvent<HTMLElement, MouseEvent>) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    setVisible(aKey);
  };
  const closePropertiesPanel = () => setVisible(undefined);
  if (!propertiesConfig) {
    return properties.map((config) => (
      <Button key={config.key} className="properties-btn" onClick={openPropertiesPanel(config.key)}>
        {config.buttonTitle ?? config.title}
      </Button>
    ));
  }
  return (
    <div className="properties-panel">
      <div className="flex gap-1 justify-between items-center cursor-pointer mb-1">
        <b>{propertiesConfig.title}</b>
        <div onClick={closePropertiesPanel}>
          <XMarkIcon className="w-4 h-4" />
        </div>
      </div>
      <div className="properties-panel-content">{propertiesConfig.component}</div>
    </div>
  );
}
