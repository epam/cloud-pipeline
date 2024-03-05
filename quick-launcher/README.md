## Client build instructions
### Prerequisites
* Nodejs (v15) & npm (v7)
* The following assets should be copied to the **assets** directory:
  * **background.png** - application background;
  * **favicon.png** - application icon;
  * **logo.png** - application logo;
* Optionally set the following env vars:
  * `CP_APPLICATIONS_TITLE` - application title; if not presented, "*Applications*" title will be used;
  * `CP_APPLICATIONS_API` - API endpoint (see below); if not presented, `/restapi` will be used;
  * `PUBLIC_URL` - a URL where the application will be hosted, i.e. "https://server.com", "https://server.com/applications", "/applications"; default is "/" (application will be hosted at the root of the server).
  * `USE_CLOUD_PIPELINE_API` - whether to use (`=1` or `=true`) Cloud Pipeline API for fetching configurations and launching the apps, default us `true`; if `USE_CLOUD_PIPELINE_API` is set to `true`, `CP_APPLICATIONS_API` will be treated as Cloud Pipeline API endpoint;
  * `BACKGROUND` - relative path to application's background image;
  * `LOGO` - relative path to the application's logo image (displayed at the bottom-right);
  * `FAVICON` - relative path to the application's favicon file.

`BACKGROUND`, `LOGO` and `FAVICON` env vars should be of `assets/<path-to-image>` format;
these files should be copied to the `assets` directory.
  
### Building application
To build application, execute
```shell script
npm install
npm run build
```

Build artifacts will be copied to the **build** directory.

## Usage of Cloud Pipeline API
If `USE_CLOUD_PIPELINE_API` is set, application will use Cloud Pipeline' detached configurations with `TYPE`=`APPLICATION` as "Applications". The following API methods will be used:
* GET `/whoami` - for fetching user's info;
* GET `/configuration/loadAll` - for fetching the list of all available detached configurations;
* POST `/metadata/load` - for fetching configuration attributes;
* GET `/dockerRegistry/loadTree` - for fetching tools;
* GET `/tool/<TOOL_IDENTIFIER>/icon` - for fetching tool icon;
* POST `/runConfiguration` - for launching the detached configuration;
* GET `/run/<RUN_ID>` - for fetching run info;
* POST `/run/<RUN_ID>/status` - for stopping the run.

**Authentication**

`bearer` cookie will be used for request authentication, e.g. `Authorization: Bearer <cookie value>` header will be sent to the API.


## Dedicated API
### Error handling
Each API endpoint should send errors in the following format (response body):
```json
{
  "error": "Something went wrong. This message will be displayed to the user"
}
```
### Fetching application info
GET `/api/applications` - fetching the list of applications
Response example:
```json
{
  "applications": [
    {
      "id": "rstudio.3.6",
      "name": "RStudio",
      "version": "3.6",
      "description": "Integrated development environment for R",
      "icon": "/api/icon/rstudio",
      "background": "/api/background/rstudio"
    },
    {
      "id": "jupyter",
      "name": "Jupyter",
      "description": "Jupyter Notebook with CPU-only support to create documents that contain live code, equations, visualizations and narrative text",
      "icon": "/api/icon/jupyter",
      "background": "/api/background/jupyter",
      "backgroundStyle": {
        "opacity": 0.1
      }
    }
  ],
  "userInfo": {
    "userName": "PIPE_ADMIN"
  }
}
```
where
* **applications** - array of objects:
  * **id** string, required - unique identifier, will be displayed in URL, i.e. `https://applications.server.com/rstudio.3.6` and will be used for launching the app or fetching app's info;
  * **name** string, required - application name;
  * **version** string - application version;
  * **description** string - application short description;
  * **icon** string, absolute url - application icon url; this url must be absolute (e.g. `https://applications.server.com/api/icon/rstudio.3.6` or `/api/icon/rstudio.3.6`);
  * **background** string, absolute url - application background image url (will be displayed on hover); this url must be absolute (e.g. `https://applications.server.com/api/background/rstudio.3.6` or `/api/background/rstudio.3.6`);
  * **backgroundStyle** json - additional background image properties (css properties in camelCase, i.e. `backgroundSize: cover;` )
* **userInfo** required:
  * **userName** string, required.

GET `/api/icon/<app id>` - fetching application's icon (image)

GET `/api/background/<app id>` - fetching application's background image

### Launching the application
GET `/api/application/<app id>/<user id>/launch`  - launching the application
Response example:
```json
{
  "status": "ready",
  "url": "https://cloud.pipeline.server.com/pipeline/pipeline-1-8080-0"
}
```
where:
* **status** string, required - `pending` / `ready`;
* **url** string, required when status is `ready` - application's url to redirect.
