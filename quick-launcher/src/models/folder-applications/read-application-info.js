import fetchFolderApplicationInfo from "../fetch-folder-application-info";
import removeExtraSlash from "../utilities/remove-slashes";
import combineUrl from "../base/combine-url";
import processString from "../process-string";

export default function readApplicationInfo (application, user, settings) {
  return new Promise((resolve) => {
    fetchFolderApplicationInfo(application.storage, application.path)
      .then(info => {
        if (info) {
          const applicationInfoFromPath = application.info || {};
          const pathInfo = {
            ...applicationInfoFromPath,
            [settings.folderApplicationUserPlaceholder || 'user']: user ? user.userName : undefined
          };
          const publishedPathInfo = {
            ...applicationInfoFromPath,
            [settings.folderApplicationUserPlaceholder || 'user']: settings?.serviceUser || user?.userName || ''
          };
          applicationInfoFromPath[settings.folderApplicationUserPlaceholder || 'user'] = user
            ? user.userName
            : undefined;
          if (settings.folderApplicationPathAttributes) {
            Object.keys(settings.folderApplicationPathAttributes)
              .forEach(key => {
                if (applicationInfoFromPath.hasOwnProperty(key)) {
                  applicationInfoFromPath[settings.folderApplicationPathAttributes[key]] =
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
            (user.userName || '').toLowerCase() === (settings.serviceUser || '').toLowerCase();
          resolve({
            ...application,
            id: `${application.storage}_${application.path}`,
            icon: application.icon
              ? combineUrl(
                settings.api,
                `datastorage/${application.storage}/downloadRedirect?path=${application.icon.path}&contentDisposition=INLINE`
              )
              : undefined,
            iconFile: application.icon,
            info: applicationInfo,
            readOnlyAttributes: Object.keys(applicationInfoFromPath || {})
              .concat(['source', 'path', 'user']),
            pathInfo,
            url: published
              ? processString(settings.folderApplicationLaunchLinkFormat, pathInfo)
              : undefined,
            rawUrl: processString(settings.folderApplicationLaunchLinkFormat, publishedPathInfo),
            name: applicationInfo.name,
            description: info.description,
            fullDescription: info.fullDescription,
            version: info.version,
            user,
            published
          });
        } else {
          resolve(undefined);
        }
      });
  });
}
