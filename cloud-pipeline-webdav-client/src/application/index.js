import React from 'react';
import {Layout} from 'antd';
import FileSystemTab from './components/file-system-tab';
import Operations from './operations';
import useFileSystem from './components/file-system-tab/use-file-system';
import useFileSystemTabActions from './use-file-system-tab-actions';
import Tabs from './file-system-tabs';
import './application.css';

function Application() {
  const leftTab = useFileSystem(Tabs.left);
  const rightTab = useFileSystem(Tabs.right);
  const {
    operations,
    leftTabActive,
    rightTabActive,
    leftTabReady,
    rightTabReady,
    setLeftPath,
    setRightPath,
    setLeftTabActive,
    setRightTabActive,
    onLeftFSCommand,
    onRightFSCommand,
  } = useFileSystemTabActions(leftTab, rightTab);
  const activeOperations = operations.filter(o => !o.finished);
  return (
    <Layout className="layout">
      <Layout.Content className="content">
        <FileSystemTab
          active={leftTabActive}
          error={leftTab.error}
          becomeActive={setLeftTabActive}
          contents={leftTab.contents}
          pending={leftTab.pending}
          path={leftTab.path}
          setPath={setLeftPath}
          selection={leftTab.selection}
          setSelection={leftTab.setSelection}
          lastSelectionIndex={leftTab.lastSelectionIndex}
          setLastSelectionIndex={leftTab.setLastSelectionIndex}
          onRefresh={leftTab.onRefresh}
          className="file-system-tab"
          fileSystem={leftTab.fileSystem}
          oppositeFileSystemReady={leftTabReady}
          onCommand={onLeftFSCommand}
        />
        <FileSystemTab
          active={rightTabActive}
          error={rightTab.error}
          becomeActive={setRightTabActive}
          contents={rightTab.contents}
          pending={rightTab.pending}
          path={rightTab.path}
          setPath={setRightPath}
          selection={rightTab.selection}
          setSelection={rightTab.setSelection}
          lastSelectionIndex={rightTab.lastSelectionIndex}
          setLastSelectionIndex={rightTab.setLastSelectionIndex}
          onRefresh={rightTab.onRefresh}
          className="file-system-tab"
          fileSystem={rightTab.fileSystem}
          oppositeFileSystemReady={rightTabReady}
          onCommand={onRightFSCommand}
        />
        {
          activeOperations.length > 0
            ? (
              <div className="operations-overlay">
                <Operations
                  className="operations"
                  operations={activeOperations}
                />
              </div>
            )
            : undefined
        }
      </Layout.Content>
      <Layout.Footer className="footer">
        {
          activeOperations.length > 0
            ? (
              <span>
                {activeOperations.length} active operations
              </span>
            )
            : undefined
        }
      </Layout.Footer>
    </Layout>
  );
}

export default Application;
