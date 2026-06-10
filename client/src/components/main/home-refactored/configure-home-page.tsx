import {useEffect, useMemo, useState} from 'react';
import {Button, Checkbox, Modal, Tooltip} from 'antd';
import {QuestionCircleFilled} from '@ant-design/icons';
import {useStore} from 'zustand';
import PanelIcons from '../home/layout/panel-icons';
import PanelInfos from '../home/layout/panel-informations';
import PanelTitles from '../home/layout/panel-titles';
import Panels from '../home/layout/panels';
import {getDisplayOnlyFavourites, setDisplayOnlyFavourites} from '../home/utils/favourites';
import localization from '../../../utils/localization';
import {homeStore} from './store/home-store';
import {useHomePanelsLayout} from './store/hooks';

type ConfigurePanelRow = {
  key: string;
  title: React.ReactNode;
  info?: React.ReactNode;
  icon: React.ReactNode;
  visible: boolean;
};

type ConfigureHomePageProps = {
  visible: boolean;
  onCancel: () => void;
  onSave: () => void;
};

function ConfigureHomePage({visible, onCancel, onSave}: ConfigureHomePageProps) {
  const panelsLayout = useHomePanelsLayout();
  const addPanels = useStore(homeStore, (state) => state.addPanels);
  const removePanel = useStore(homeStore, (state) => state.removePanel);
  const restoreDefaultLayout = useStore(homeStore, (state) => state.restoreDefaultLayout);
  const syncPanelsLayout = useStore(homeStore, (state) => state.syncPanelsLayout);
  const [panels, setPanels] = useState<ConfigurePanelRow[]>([]);
  const [displayOnlyFavourites, setDisplayOnlyFavouritesState] = useState(false);

  const localizedString = localization.localization.localizedString;

  const visiblePanelKeys = useMemo(
    () => new Set(panelsLayout.map((item) => item.i)),
    [panelsLayout],
  );

  useEffect(() => {
    if (!visible) {
      return;
    }
    const nextPanels: ConfigurePanelRow[] = [];
    for (const key in Panels) {
      if (!Object.hasOwn(Panels, key)) {
        continue;
      }
      const panelKey = Panels[key as keyof typeof Panels];
      let title = PanelTitles[panelKey as keyof typeof PanelTitles];
      if (typeof title === 'function') {
        title = title(localizedString);
      }
      let info = PanelInfos[panelKey as keyof typeof PanelInfos];
      if (typeof info === 'function') {
        info = info(localizedString);
      }
      const PanelIcon = PanelIcons[panelKey as keyof typeof PanelIcons];
      nextPanels.push({
        key: panelKey,
        title,
        info,
        icon: PanelIcon ? (
          <PanelIcon
            style={{
              fontSize: 'larger',
              marginRight: 5,
            }}
          />
        ) : null,
        visible: visiblePanelKeys.has(panelKey),
      });
    }
    setPanels(nextPanels);
    setDisplayOnlyFavouritesState(getDisplayOnlyFavourites());
  }, [visible, visiblePanelKeys, localizedString]);

  const onChangeVisibility = (key: string) => (event: {target: {checked: boolean}}) => {
    setPanels((current) =>
      current.map((panel) =>
        panel.key === key ? {...panel, visible: event.target.checked} : panel,
      ),
    );
  };

  const handleSave = () => {
    const visibleKeys = panels.filter((panel) => panel.visible).map((panel) => panel.key);
    const removedPanels = panelsLayout
      .filter((item) => !visibleKeys.includes(item.i))
      .map((item) => item.i);
    const addedPanels = visibleKeys.filter((key) => !panelsLayout.some((item) => item.i === key));
    removedPanels.forEach((panel) => removePanel(panel));
    if (addedPanels.length > 0) {
      addPanels(addedPanels);
    } else {
      syncPanelsLayout();
    }
    setDisplayOnlyFavourites(displayOnlyFavourites);
    onSave();
  };

  const handleRestoreDefaultLayout = () => {
    restoreDefaultLayout();
    onSave();
  };

  return (
    <Modal
      width="33%"
      className="cp-dashboard-configure"
      title="Configure dashboard"
      open={visible}
      onCancel={onCancel}
      footer={
        <div style={{display: 'flex', justifyContent: 'space-between', width: '100%'}}>
          <Button onClick={handleRestoreDefaultLayout}>Restore default layout</Button>
          <div>
            <Button onClick={onCancel}>Cancel</Button>
            <Button type="primary" onClick={handleSave}>
              OK
            </Button>
          </div>
        </div>
      }
    >
      <table style={{borderCollapse: 'collapse', width: '100%'}}>
        <tbody>
          {panels.map((panel) => (
            <tr key={panel.key} className="cp-even-odd-element" style={{height: 22}}>
              <td style={{borderCollapse: 'separate'}}>
                <Checkbox
                  disabled={panel.visible && panels.filter((item) => item.visible).length === 1}
                  checked={panel.visible}
                  onChange={onChangeVisibility(panel.key)}
                >
                  {panel.icon}
                  {panel.title}
                </Checkbox>
              </td>
              <td style={{textAlign: 'right'}}>
                {panel.info ? (
                  <Tooltip title={panel.info} placement="left">
                    <QuestionCircleFilled />
                  </Tooltip>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{marginTop: 10}}>
        <Checkbox
          checked={displayOnlyFavourites}
          onChange={(event) => setDisplayOnlyFavouritesState(event.target.checked)}
        >
          Show only favourites
        </Checkbox>
      </div>
    </Modal>
  );
}

export {ConfigureHomePage};
