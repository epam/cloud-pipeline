import {useCallback, useEffect, useState, useMemo} from 'react';
import { useApplicationTypeSettings } from '../use-settings';
import fetchMountsForPlaceholders from '../../models/parse-limit-mounts-placeholders-config';
import folderApplicationPublish from '../../models/folder-application-publish';
import folderApplicationUpdate from '../../models/folder-application-update';
import folderApplicationRemove from '../../models/folder-application-remove';
import useApplicationIcon from './use-application-icon';
import fetchFolderApplication from "../../models/folder-applications/fetch-folder-application";
import {useApplicationSession} from '../../models/validate-folder-application';

let newAttributeId = 0;

function getInfoFieldTitle (field, settings) {
  if (settings && settings.limitMountsPlaceholders && settings.limitMountsPlaceholders.hasOwnProperty(field)) {
    return settings.limitMountsPlaceholders[field].title || field;
  }
  if (field === 'users') {
    return 'Permissions';
  }
  if (field === 'mounts') {
    return 'Mount storages';
  }
  return field;
}

function getInfoFieldType (field, settings) {
  if (settings && settings.limitMountsPlaceholders && settings.limitMountsPlaceholders.hasOwnProperty(field)) {
    return 'select';
  }
  if (field === 'instance') {
    return 'select';
  }
  if (field === 'source') {
    return 'source';
  }
  if (field === 'users') {
    return 'users';
  }
  if (field === 'mounts') {
    return 'mounts';
  }
  return 'text';
}

function getInfoFieldValuesPromise (field, settings) {
  if (settings && settings.limitMountsPlaceholders && settings.limitMountsPlaceholders.hasOwnProperty(field)) {
    const config = settings.limitMountsPlaceholders[field];
    return new Promise((resolve) => {
      fetchMountsForPlaceholders([{config, placeholder: field}])
        .then(o => {
          resolve((o[field] || []).map(storage => storage.name));
        });
    });
  }
  if (field === 'instance' && settings.appConfigNodeSizes) {
    return Promise.resolve(Object.keys(settings.appConfigNodeSizes || {}));
  }
  return Promise.resolve([]);
}

export default function useEditableApplication(application) {
  const settings = useApplicationTypeSettings(application?.appType);
  const [pending, setPending] = useState(true);
  const [name, setName] = useState(undefined);
  const [description, setDescription] = useState(undefined);
  const [fullDescription, setFullDescription] = useState(undefined);
  const [nameInitial, setNameInitial] = useState(undefined);
  const [descriptionInitial, setDescriptionInitial] = useState(undefined);
  const [fullDescriptionInitial, setFullDescriptionInitial] = useState(undefined);
  const [appIconPath, setAppIconPath] = useState(undefined);
  const [icon, setIcon] = useState(undefined);
  const [iconFile, setIconFile] = useState(undefined);
  const [initialSource, setInitialSource] = useState(undefined);
  const [infoFields, setInfoFields] = useState({});
  const [disabled, setDisabled] = useState(false);
  const [operation, setOperation] = useState(false);
  const [attributes, setNewAttributes] = useState([]);
  const [redistribute, setRedistribute] = useState(false);
  const [pathInfo, setPathInfo] = useState({});
  const [pathInfoInitial, setPathInfoInitial] = useState({});
  const [newOwnerInfo, setNewOwnerInfo] = useState(undefined);
  const setInfoField = useCallback((key, value) => {
    setInfoFields(o => Object.assign({}, o, {[key]: value}));
  }, [setInfoFields]);
  const appType = application?.appType;
  const onChangeSource = useCallback(source => {
    const appPath = source || initialSource;
    if (appPath && settings) {
      setDisabled(true);
      fetchFolderApplication(appPath, settings, appType)
        .then(redistributeApplication => {
          if (redistributeApplication) {
            const {
              iconFile = {},
              name,
              description,
              fullDescription,
              info = {},
              pathInfo: redistributeApplicationPathInfo = {}
            } = redistributeApplication || {};
            setIcon(undefined);
            setIconFile(undefined);
            setAppIconPath(iconFile.path);
            setName(name);
            setDescription(description);
            setFullDescription(fullDescription);
            setNewAttributes([]);
            setPathInfo(redistributeApplicationPathInfo);
            setInfoFields(o => Object.assign(
              {},
              Object.keys(o || {})
                .map(key => ({
                  [key]: {
                    ...o[key],
                    value: info[key] || ''
                  }
                }))
                .reduce((r, c) => ({...r, ...c}), {}),
              {
                source: {
                  ...(o.source || {}),
                  value: info?.path || o?.source?.value
                }
              }
            ));
          }
          setDisabled(false);
        });
    }
  }, [
    setDisabled,
    initialSource,
    settings,
    setName,
    setDescription,
    setFullDescription,
    setInfoField,
    setIcon,
    setIconFile,
    setPathInfo,
    setNewOwnerInfo,
    appType
  ]);
  const onChange = useCallback((key, value) => {
    if (/^name$/i.test(key)) {
      setName(value);
    } else if (/^description$/i.test(key)) {
      setDescription(value);
    } else if (/^fullDescription$/i.test(key)) {
      setFullDescription(value);
    } else if (/^ownerInfo$/i.test(key)) {
      setNewOwnerInfo(value);
    } else if (/^icon$/i.test(key)) {
      const fileReader = new FileReader();
      fileReader.onload = function () {
        setIcon(this.result);
        setIconFile(value);
      };
      fileReader.readAsDataURL(value);
    } else if (/^pathInfo$/i.test(key)) {
      setPathInfo(value);
    } else if (/^source$/i.test(key)) {
      onChangeSource(value);
    } else {
      setInfoFields(o => Object.assign(
        {},
        o,
        {
          [key]: {
            ...(o[key] || {}),
            value
          }
        }
      ));
    }
  }, [
    setName,
    setDescription,
    setFullDescription,
    setInfoField,
    setIcon,
    setIconFile,
    setPathInfo,
    setNewOwnerInfo,
    onChangeSource
  ]);
  const onRedistribute = useCallback((value) => {
    if (!value) {
      setName(nameInitial);
      setDescription(descriptionInitial);
      setFullDescription(fullDescriptionInitial);
      setIconFile(undefined);
      setIcon(undefined);
      setAppIconPath(undefined);
      setPathInfo(pathInfoInitial);
      setNewOwnerInfo(undefined);
      setInfoFields(o => Object.assign(
        {},
        Object.keys(o || {})
          .map(key => ({
            [key]: {
              ...o[key],
              value: o[key].initialValue
            }
          }))
          .reduce((r, c) => ({...r, ...c}), {}),
        Object.values(settings?.folderApplicationPathAttributes || {})
          .filter(pathKey => !/^(name|user)$/i.test(pathKey))
          .map(pathKey => ({
            [pathKey]: {
              ...(o[pathKey]),
              value: (o[pathKey] || {}).initialValue
            }
          }))
          .reduce((r, c) => ({...r, ...c}), {}),
        {
          source: {
            ...(o.source || {}),
            redistribute: false,
            value: (o.source || {}).initialValue
          },
          user: {
            ...(o.user || {}),
            value: (o.user || {}).initialValue
          }
        }
      ));
    } else {
      setInfoFields(o => Object.assign(
        {},
        o,
        {
          source: {
            ...(o.source || {}),
            redistribute: true
          },
        }
      ));
      onChangeSource();
    }
    setRedistribute(!!value);
  }, [
    setAppIconPath,
    setInfoField,
    setRedistribute,
    setPathInfo,
    pathInfoInitial,
    settings,
    setNewOwnerInfo,
    onChangeSource,
    setName,
    setDescription,
    setFullDescription,
    nameInitial,
    descriptionInitial,
    fullDescriptionInitial,
    setIconFile,
    setIcon
  ]);
  const addAttribute = useCallback(() => {
    setNewAttributes(o => ([...(o || []), {id: ++newAttributeId}]));
  }, [setNewAttributes]);
  const removeAttribute = useCallback((attribute) => {
    setNewAttributes(o => (o || []).filter(oo => oo.id !== attribute.id));
  }, [setNewAttributes]);
  const onChangeAttribute = useCallback((attribute) => {
    setNewAttributes(o => (o || []).map(oo => oo.id === attribute.id ? attribute : oo));
  }, [setNewAttributes]);
  const isCommonAttributeName = useCallback((name) => {
    return /^\s*(name|description|ownerinfo|fullDescription)\s*$/i.test(name);
  }, []);
  const {
    icon: defaultIcon,
    clearCache
  } = useApplicationIcon(
    application?.storage,
    appIconPath || application?.iconFile?.path
  )
  useEffect(() => {
    if (application && settings) {
      setPending(true);
      setDisabled(true);
      const apply = (data) => {
        const {
          name,
          description,
          fullDescription,
          info = {},
          readOnlyAttributes = [],
          pathInfo: initialPathInfo = {}
        } = data || {};
        setName(name);
        setNameInitial(name);
        setDescription(description);
        setDescriptionInitial(description);
        setFullDescription(fullDescription);
        setFullDescriptionInitial(fullDescription);
        setNewAttributes([]);
        setPathInfo(initialPathInfo);
        setPathInfoInitial(initialPathInfo);
        const nodeSizes = Object.keys(settings.appConfigNodeSizes || {});
        const limitMountsPlaceholders = Object.keys(settings.limitMountsPlaceholders || {});
        setInfoFields(
          Object.keys({
            ...(
              nodeSizes.length > 0
                ? {instance: ''}
                : {}
            ),
            ...limitMountsPlaceholders.reduce((r, c) => ({...r, [c]: ''}), {}),
            ...info
          })
            .filter(key => !isCommonAttributeName(key))
            .reduce((r, c) => ({
              ...r,
              [c]: {
                title: getInfoFieldTitle(c, settings),
                value: info[c],
                initialValue: info[c],
                readOnly: readOnlyAttributes.includes(c),
                type: getInfoFieldType(c, settings),
                valuesPromise: getInfoFieldValuesPromise(c, settings)
              }
            }), {})
        );
        setInitialSource(info?.source);
        setPending(false);
      };
      apply(application);
      fetchFolderApplication(
        application?.info?.path || application?.info?.source,
        settings,
        application?.appType
      )
        .catch(() => application)
        .then(apply)
        .then(() => setDisabled(false));
    }
  }, [
    settings,
    application,
    setName,
    setNameInitial,
    setDescription,
    setDescriptionInitial,
    setFullDescription,
    setFullDescriptionInitial,
    setNewAttributes,
    setInfoField,
    isCommonAttributeName,
    setPending,
    setDisabled,
    setPathInfo,
    setPathInfoInitial
  ]);
  const isReservedName = useCallback((name) => {
    const names = Object.keys(infoFields || {})
      .concat(['name', 'description', 'fullDescription']);
    const regExp = new RegExp(`^\s*(${names.join('|')})\s*$`, 'i')
    return regExp.test(name);
  }, [infoFields]);
  const publish = useCallback(() => {
    setDisabled(true);
    setOperation({
      name: !application.published || redistribute ? 'Publishing...' : 'Updating...'
    });
    const applicationInfo = {
      source: application?.info?.source || application?.info?.path,
      ownerInfo: application?.info?.ownerInfo,
      ...(
        Object.entries(infoFields)
          .filter(([, {value, readOnly}]) => value !== '' && value !== 'undefined' && !readOnly)
          .map(([key, {value}]) => ({[key]: value}))
          .reduce((r, c) => ({...r, ...c}), {})
      ),
      ...(
        attributes
          .map(attribute => ({[attribute.name]: attribute.value}))
          .reduce((r, c) => ({...r, ...c}), {})
      ),
      name,
      description,
      fullDescription,
      user: infoFields?.user?.value || application?.info?.user
    };
    const appPayload = {
      ...application,
      info: {
        ...(application.info || {})
      }
    };
    if (redistribute) {
      applicationInfo.source = infoFields.source?.value || applicationInfo.source;
      appPayload.info.source = infoFields.source?.value || appPayload.info.source;
      appPayload.pathInfo = pathInfo || pathInfoInitial;
      appPayload.info.ownerInfo = newOwnerInfo || appPayload.info.ownerInfo;
      applicationInfo.ownerInfo = newOwnerInfo || appPayload.info.ownerInfo;
    }
    if (iconFile) {
      clearCache();
    }
    const progressCallback = (details, progress) => setOperation(o => ({
      name: o.name,
      details,
      progress: progress || o.progress
    }));
    return new Promise((resolve, reject) => {
      let action = !application.published || redistribute
        ? folderApplicationPublish
        : folderApplicationUpdate;
      action(settings, appPayload, applicationInfo, iconFile, true, progressCallback)
        .then(() => {
          setDisabled(false);
          setOperation(false);
          resolve();
        })
        .catch(e => {
          setDisabled(false);
          setOperation(false);
          reject(e);
        });
    });
  }, [
    settings,
    application,
    setDisabled,
    infoFields,
    attributes,
    name,
    description,
    fullDescription,
    setOperation,
    iconFile,
    redistribute,
    clearCache,
    setPathInfo,
    pathInfo,
    pathInfoInitial,
    newOwnerInfo
  ]);
  const applicationPayload = useMemo(() => {
    const applicationInfo = {
      source: application?.info?.source || application?.info?.path,
      ownerInfo: application?.info?.ownerInfo,
      ...(
        Object.entries(infoFields)
          .filter(([, {value, readOnly}]) => value !== '' && value !== 'undefined' && !readOnly)
          .map(([key, {value}]) => ({[key]: value}))
          .reduce((r, c) => ({...r, ...c}), {})
      ),
      ...(
        attributes
          .map(attribute => ({[attribute.name]: attribute.value}))
          .reduce((r, c) => ({...r, ...c}), {})
      ),
      name,
      description,
      fullDescription,
      user: infoFields?.user?.value || application?.info?.user
    };
    const appPayload = {
      ...application,
      info: {
        ...(application.info || {})
      }
    };
    if (redistribute) {
      applicationInfo.source = infoFields.source?.value || applicationInfo.source;
      appPayload.info.source = infoFields.source?.value || appPayload.info.source;
      appPayload.pathInfo = pathInfo || pathInfoInitial;
      appPayload.info.ownerInfo = newOwnerInfo || appPayload.info.ownerInfo;
      applicationInfo.ownerInfo = newOwnerInfo || appPayload.info.ownerInfo;
    }
    return appPayload;
  }, [
    application,
    infoFields,
    attributes,
    name,
    description,
    fullDescription,
    redistribute,
    pathInfo,
    pathInfoInitial,
    newOwnerInfo
  ]);
  const remove = useCallback(() => {
    if (confirm(`Are you sure you want to remove application?`)) {
      setDisabled(true);
      setOperation({name: 'Removing...'});
      const progressCallback = (details, progress) => setOperation(o => ({
        name: o.name,
        details: details || o.details,
        progress: progress || o.progress
      }));
      return new Promise((resolve, reject) => {
        folderApplicationRemove(application, settings, progressCallback)
          .then(() => {
            setDisabled(false);
            setOperation(false);
            resolve();
          })
          .catch((e) => {
            setDisabled(false);
            setOperation(false);
            reject(e);
          })
      });
    }
  }, [settings, application, setDisabled, setOperation]);
  const {session, validate, stopValidation} = useApplicationSession(applicationPayload);
  return {
    info: {
      name,
      description,
      fullDescription,
      infoFields,
      icon: icon || defaultIcon,
      attributes
    },
    disabled: disabled || (session && session.pending),
    addAttribute,
    removeAttribute,
    onChange,
    onRedistribute,
    onChangeAttribute,
    publish,
    remove,
    isReservedName,
    pending,
    operation,
    applicationPayload,
    validate,
    stopValidation,
    validation: session
  };
}
