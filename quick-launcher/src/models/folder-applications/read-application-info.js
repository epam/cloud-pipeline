import fetchFolderApplicationInfo from "../fetch-folder-application-info";
import removeExtraSlash from "../utilities/remove-slashes";
import combineUrl from "../base/combine-url";
import processString from "../process-string";
import { getApplicationTypeSettings } from "../folder-application-types";

export default function readApplicationInfo (application, user, settings, appType) {
  const appTypeSettings = getApplicationTypeSettings(settings, appType);
  return new Promise((resolve) => {
    fetchFolderApplicationInfo(application.storage, application.path)
      .then(info => {
        if (info) {
          const applicationInfoFromPath = application.info || {};
          const pathInfo = {
            ...applicationInfoFromPath,
            [appTypeSettings?.folderApplicationUserPlaceholder || 'user']: user ? user.userName : undefined
          };
          const publishedPathInfo = {
            ...applicationInfoFromPath,
            [appTypeSettings?.folderApplicationUserPlaceholder || 'user']: appTypeSettings?.serviceUser || user?.userName || ''
          };
          applicationInfoFromPath[appTypeSettings?.folderApplicationUserPlaceholder || 'user'] = user
            ? user.userName
            : undefined;
          if (appTypeSettings?.folderApplicationPathAttributes) {
            Object.keys(appTypeSettings?.folderApplicationPathAttributes)
              .forEach(key => {
                if (applicationInfoFromPath.hasOwnProperty(key)) {
                  applicationInfoFromPath[appTypeSettings?.folderApplicationPathAttributes[key]] =
                    applicationInfoFromPath[key];
                  delete applicationInfoFromPath[key];
                }
              });
          }
          applicationInfoFromPath.path = [
            '',
            ...removeExtraSlash(application.path || '').split('/').slice(0, -1)
          ].join('/');
          const applicationInfo = {
            ownerInfo: user?.attributes,
            mounts: info?.mounts || '',
            users: [],
            ...applicationInfoFromPath,
            ...info
          };
          const published = user &&
            (user.userName || '').toLowerCase() === (appTypeSettings?.serviceUser || '').toLowerCase();
          resolve({
            ...application,
            id: `${application.storage}_${application.path}`,
            icon: application.icon
              ? combineUrl(
                appTypeSettings?.api,
                `datastorage/${application.storage}/downloadRedirect?path=${application.icon.path}&contentDisposition=INLINE`
              )
              : undefined,
            iconFile: application.icon,
            info: applicationInfo,
            latest: applicationInfo.latest,
            deprecated: applicationInfo.deprecated,
            readOnlyAttributes: Object.keys(applicationInfoFromPath || {})
              .concat(['source', 'path', 'user']),
            pathInfo,
            url: published
              ? processString(appTypeSettings?.folderApplicationLaunchLinkFormat, pathInfo)
              : undefined,
            rawUrl: processString(appTypeSettings?.folderApplicationLaunchLinkFormat, publishedPathInfo),
            name: applicationInfo.name,
            description: info.description,
            fullDescription: info.fullDescription,
            version: info.version,
            user,
            published,
            appType
          });
        } else {
          resolve(undefined);
        }
      });
  });
}
