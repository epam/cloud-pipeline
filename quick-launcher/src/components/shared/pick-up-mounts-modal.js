import React, {useCallback, useEffect, useState} from 'react';
import classNames from 'classnames';
import Modal from './modal';
import './pick-up-folder-application-modal.css';
import './pick-up-users-modal.css';
import getAvailableDataStorages from '../../models/cloud-pipeline-api/data-storage-available';
import Check from "./check";

function filterMountFn (filter) {
  return (mount) => !filter || mount.name.toLowerCase().includes(filter.toLowerCase());
}

export default function PickUpMountsModal (
  {
    mounts = [],
    visible,
    onClose,
    onChange
  }
) {
  const [dataStorages, setDataStorages] = useState([]);
  const [selected, setSelected] = useState([]);
  const [unknown, setUnknown] = useState([]);
  const [error, setError] = useState([]);
  const [filter, setFilter] = useState(undefined);
  const [pending, setPending] = useState(true);
  const onFilterChange = useCallback((e) => setFilter(e.target.value), [setFilter]);
  useEffect(() => {
    if (visible) {
      setPending(true);
      getAvailableDataStorages
        .then((result) => {
          setDataStorages(result);
        })
        .catch(e => {
          console.error(e.message);
          setError(e.message);
        })
        .then(() => {
          setPending(false);
        })
    }
  }, [
    setError,
    setPending,
    visible,
    setDataStorages
  ]);
  useEffect(() => {
    if (visible) {
      setUnknown((mounts || []).filter(m => !dataStorages.find(o => o.id === Number(m))));
      setSelected((mounts || []).map(o => Number(o)));
    }
  }, [
    visible,
    dataStorages,
    mounts,
    setSelected,
    setUnknown
  ]);
  const toggleMount = useCallback(mountId => {
    if (selected.includes(Number(mountId))) {
      setSelected(selected.filter(o => o !== Number(mountId)));
    } else {
      setSelected([...selected, Number(mountId)]);
    }
  }, [setSelected, selected]);
  const mountIsSelected = useCallback(o => {
    return selected.includes(Number(o));
  }, [selected]);
  const onClear = useCallback(() => {
    setSelected([]);
  }, [setSelected]);
  const onSave = useCallback(() => {
    if (onChange) {
      onChange(selected);
    }
    if (onClose) {
      onClose();
    }
  }, [onChange, selected, onClose]);
  return (
    <Modal
      visible={visible}
      onClose={onClose}
      title={
        pending
          ? false
          : 'Select mounts'
      }
      className="pick-up-application-modal pick-up-mounts-modal"
      closeButton
    >
      {
        pending && (
          <div className="pick-up-loading">
            Loading...
          </div>
        )
      }
      {
        !pending && (
          <div className="mounts-modal-content">
            <div className="filter">
              <input
                className="filter-input"
                value={filter || ''}
                onChange={onFilterChange}
              />
            </div>
            {
              dataStorages.length === 0 && (
                <div className="pick-up-loading">
                  Mounts not found
                </div>
              )
            }
            <div className="pick-up-applications">
              {
                unknown
                  .filter(id => !filter || `${id}`.includes(filter.toLowerCase()))
                  .map(mount => (
                    <div
                      className={
                        classNames(
                          'pick-up-application',
                          'mount',
                          {'selected': mountIsSelected(mount)}
                        )
                      }
                      key={mount}
                      onClick={() => toggleMount(mount)}
                    >
                      <span className="mount-title">#{mount}</span>
                      <Check
                        className="check"
                      />
                    </div>
                  ))
              }
              {
                dataStorages
                  .filter(filterMountFn(filter))
                  .map(mount => (
                    <div
                      className={
                        classNames(
                          'pick-up-application',
                          'mount',
                          {'selected': mountIsSelected(mount.id)}
                        )
                      }
                      key={mount.id}
                      onClick={() => toggleMount(mount.id)}
                    >
                      <span
                        className={
                          classNames(
                            'mount-title',
                            {
                              sensitive: mount.sensitive
                            }
                          )
                        }
                      >
                        {mount.name}
                      </span>
                      <Check
                        className="check"
                      />
                    </div>
                  ))
              }
            </div>
          </div>
        )
      }
      <div className="pick-up-users-modal-actions">
        <div
          className="pick-up-users-modal-action"
          onClick={onClose}
        >
          CANCEL
        </div>
        <div style={{display: 'inline-flex'}}>
          {
            selected.length > 0 && (
              <div
                className={
                  classNames(
                    'pick-up-users-modal-action',
                    'primary'
                  )
                }
                onClick={onClear}
                style={{
                  marginRight: 5
                }}
              >
                CLEAR
              </div>
            )
          }
          <div
            className={
              classNames(
                'pick-up-users-modal-action',
                'primary'
              )
            }
            onClick={onSave}
          >
            SAVE
          </div>
        </div>
      </div>
    </Modal>
  );
}
