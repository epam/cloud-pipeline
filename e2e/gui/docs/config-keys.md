# e2e GUI `default.conf` keys: semantics, format, prerequisites, derivation

Sources: `e2e/gui/src/test/java/com/epam/pipeline/autotests/utils/C.java` (83 keys), every usage in the test code, and
the client/api/deploy sources of this repo.
"EXEC" = used by a method that runs (enabled and selected by `testng.xml`), or by a `@Before*/@After*` method or helper
of a class that has such methods. "notexec" = used only by methods that do not run.
The marks were made for a narrower selection of `testng.xml`. On this line `testng.xml` runs whole classes, and more
of them (the WDL, Luigi, commit, NFS, metadata, scan and instance price type classes among them), so a "notexec"
use may run now; the rows of the keys those classes need are updated.
Paths below are relative to `e2e/gui/src/test/java/com/epam/pipeline/autotests/`.

---------------------------------------------------------------------------------------------------------------------

## 0. How the file is read, and format rules that apply to every key

* `C` loads `System.getProperty("com.epam.bfx.e2e.ui.property.path", "default.conf")`. `build.gradle` does not forward
  system properties to the test JVM, so in practice the file is **`default.conf` in the Gradle project dir**
  (`e2e/gui`, mounted at `/headless/e2e/gui` in the runner container). The `prerequisites` Gradle task
  (`PrerequisitesTest`, an empty test that only logs in) runs first and reads the same file.
* `java.util.Properties.load(InputStream)`: the file is **ISO-8859-1**. Write non-ASCII characters as `\uXXXX`,
  newlines as `\n`, a backslash as `\\`. Leading whitespace of a value is dropped, **trailing whitespace is kept**
  (so `30000 ` breaks `Integer.parseInt`). `:` and `=` inside a value are fine.
* **Mandatory keys (the static initializer of `C` throws -> `ExceptionInInitializerError`/`NoClassDefFoundError` in
  every test class, i.e. the whole suite fails)**: everything parsed with `Integer.parseInt` (missing or empty value ->
  `NumberFormatException`): `e2e.ui.default.timeout`, `commit.appearing.timeout`, `ssh.appearing.timeout`,
  `committing.timeout`, `run.completion.timeout`, `buckets.mounting.timeout`, `endpoint.initialization.timeout`,
  `search.timeout.in.minutes`, `login.delay.timeout`, `run.sharing.timeout.in.seconds`,
  `sync.storage.timeout.in.seconds`, `backup.storage.offset` (plain decimal int, max 2147483647), and
  `e2e.ui.backup.storage.names` (`.split(",")` -> NPE if the key is missing; an empty value parses).
* Parsed in the static/instance init of a single test class (that class fails, the rest runs):
  `cluster.instance.hdd.scale.delta.ratio` (`Double.parseDouble`, `LaunchParametersTest`),
  `download.folder` (`URI.create(folder + "/")` in `RoleBasedAccessControlTest`, `PipelineDetailsTest`: no spaces, no
  `file:` scheme), `login` (`LOGIN.substring(0, LOGIN.indexOf("@"))` in `RemotePipelineRepositoryTest`, not in the
  suite, so harmless), `testing.tool` (`substring(lastIndexOf("/")+1)`).
* String flags are compared as strings: `e2e.ui.auth.token` with `"true".equals`/`"false".equals` (case-sensitive; any
  other value, e.g. empty, logs in by **neither** method), `impersonate.auth` with `equalsIgnoreCase("true")`,
  `login.isservice` with `"false".equalsIgnoreCase` (anything else = true).
* Selenide matching (6.16) used by the checks, important for "how exact must the value be":
  `text(x)`/`value(x)` = case-insensitive **substring** with whitespace runs collapsed; `exactText` = case-insensitive
  full match; `byText`/`withText` (XPath) = case-sensitive exact / contains; `matchText` = case-sensitive regex;
  `attribute("href", x)` = exact; plain Java `equals`/`contains` = exact.

---------------------------------------------------------------------------------------------------------------------

## 1. How login works (`AbstractBfxPipelineTest.login`, `mixins/Authorization.java`)

`AbstractBfxPipelineTest.setUp` (`@BeforeClass` of every class) creates a fresh ChromeDriver, deletes all cookies and
calls `login(ROOT_ADDRESS)`. `restartBrowser()` does the same. Three modes:

| mode | `auth.token` | `impersonate.auth` | first login | `loginAs(user)` / `logout()` |
|---|---|---|---|---|
| A. form | `false` | any | opens `root.address`, fills the IdP form `#userName`/`#password`/`#btn-sign-in` (the bundled `cp-idp` form) with `login`/`password` | `sleep(login.delay)` + the form with `another.login/password` or `clean.history.login/password`; logout = UI logout button |
| B. JWT cookie | `true` | `false` | opens `root.address`, adds cookie **`HttpAuthorization=<password>`** (password = a **JWT**), reopens | `sleep(login.delay)`, adds `HttpAuthorization=<account.password>` (JWT of that user), opens root; logout = UI logout + clear cookies |
| C. impersonation (the one `run-e2e.sh` uses) | `true` | `true` | opens `<root.address>restapi`, adds cookie **`SESSION=<password>`, path `/pipeline/`** (password = an **API Spring-Session id**), opens root | for `admin`: no-op; for others: Settings > User management > search `login.toUpperCase()` > Edit > **Impersonate**; logout = "Stop impersonation" (the real session is never logged out) |

In mode C, `RoleBasedAccessControlTest.checkAnonymousAccess` starts **four** ChromeDrivers, in pairs 1-3 s apart
(`setUpConfiguration()` + `restartBrowser()` twice); in the JWT mode it starts two, more than one minute apart.

Per-key consequences (mode C, the recommended one for a platform with SSO):
* `e2e.ui.login`: the user whose session the `SESSION` cookie carries (must be the same user, `loginAs(admin)` is a
  no-op). Must have `ROLE_ADMIN`. Used as the expected identity everywhere (see table).
* `e2e.ui.password`: the raw `SESSION` cookie value of an authenticated session of `login`, **from an SSO sign-in in
  a browser**. It can't be derived from the admin JWT: a session created by a JWT-authenticated
  request is trusted for 60 s only (`JwtAuthenticationToken.TOKEN_SESSION_TIMEOUT = 60`, the authentication stored in
  the session reports `isAuthenticated() == false` afterwards), and a JWT in the `HttpAuthorization` cookie does not
  open the UI pages either (they are behind SAML unless `CP_API_USE_ONLY_JWT_AUTH=true`). A browser session expires
  after `CP_API_SRV_SESSION_TIMEOUT_SEC` (default 1800 s) of **inactivity**; the running tests keep it alive (the
  time of the whole suite on this line is not measured). Check: `curl -sk -b "SESSION=<v>" .../restapi/whoami` must return `login` - also after
  65 s.
* `another.password`, `clean.history.password`: **not used** in mode C (impersonation needs no credentials). Put a JWT
  of that user anyway (`GET /restapi/user/token?name=<user>&expiration=<s>`) so mode B stays possible.
* `another.admin.token`: only used by `RoleBasedAccessControlTest.provideAdminRightsToTheUser` through
  `loginWithToken`; in mode C it builds `Account(another.login, token)` and impersonates `another.login`, so the token
  is **ignored**. In mode B it is the `HttpAuthorization` cookie and must be a JWT whose `roles` claim contains
  `ROLE_ADMIN` (claims are a snapshot taken at issue time, `JwtTokenGenerator`; `CP_API_JWT_VALIDATE_USER` defaults to
  false, so the API uses the claims, `UserAccessService.getJwtUser`). USER1 (permanently `ROLE_ADMIN`) is the natural
  issuer: `GET /restapi/user/token?name=USER1&expiration=...`.
* `invalid.token`: mode C only, `changeSession(INVALID_TOKEN)` puts it as the `SESSION` cookie and expects the
  library button to be absent. Any string that is not a live session id, e.g. `e2e-invalid-session`. Unused in A/B.
* `anonymous.name`/`anonymous.token`: mode C sets cookie **`bearer=<anonymous.token>`** on the edge host and opens the
  endpoint; the edge (`deploy/docker/cp-edge/validate_cookie.lua`) verifies the JWT, requires the `roles` claim to
  intersect the run's shared groups (the test shares with `ROLE_ANONYMOUS_USER`), and rejects anonymous users unless
  `CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER=true`. The e2e-endpoints page prints the token's `sub`, which must contain
  `anonymous.name`. In mode B the token is the `HttpAuthorization` cookie instead. In mode A the test returns early.
* `platform.name` is only used in mode A, `login.delay.timeout` only in modes A/B (see table).
* Mode B is not usable on an SSO platform: `failedAuthentication`/`checkAnonymousAccess` expect a
  `type=Unauthorized, status=401` page (no SSO redirect), and the blocked-user checks expect "User is blocked!", which
  the API only produces with `CP_API_JWT_VALIDATE_USER=true`.

---------------------------------------------------------------------------------------------------------------------

## 2. Key table

Legend for "pre-exist": what must exist on the platform before the run. "Derive" = how the generator gets it.

### 2.1 Timeouts (all mandatory ints)

| key | constant | used by | meaning / unit | derive / recommended |
|---|---|---|---|---|
| `e2e.ui.default.timeout` | DEFAULT_TIMEOUT | EXEC: `Configuration.timeout` of Selenide for every `should*` without explicit timeout (all classes); `RunsMenuAO.stopRun`, `SystemManagementAO` logs, `ShellAO.assertNextStringIsVisible`, `PipelineRunFormAO.waitUntilLaunchButtonAppear` (x2), `MetadataFilePreviewAO` (x2), `PipelinesLibraryAO.removeStorage` | **ms** | static, e.g. `30000` |
| `e2e.ui.commit.appearing.timeout` | COMMIT_APPEARING_TIMEOUT | EXEC: `LogAO.waitForCommitButton` (DockerCommitTest, PipelineDockerCommitTest) | ms | static, e.g. `600000` |
| `e2e.ui.ssh.appearing.timeout` | SSH_APPEARING_TIMEOUT | EXEC: `LogAO.waitForSshLink`, pause/resume button waits, `waitForEndpointLink`, `waitForNestedRunsLink`, `RunsMenuAO` pause/stop button waits | ms; must cover node provisioning + image pull + init (+ FSx Lustre creation in `PlatformPreferencesTest`, ~15 min per test) | static, e.g. `1800000` |
| `e2e.ui.committing.timeout` | COMMITTING_TIMEOUT | EXEC: `LogAO.assertCommittingFinishedSuccessfully` ("COMMIT SUCCEEDED") in `LaunchParametersTest.addSupportForAutoscalingFilesystemForAWS`; notexec DockerCommit* | ms | static, e.g. `1800000` |
| `e2e.ui.run.completion.timeout` | COMPLETION_TIMEOUT | EXEC: `LogAO.waitForCompletion/waitFor/shouldHaveStatus/waitForTask*`, `RunsMenuAO.waitForCompletion/waitUntilResumeButtonAppear`, `LaunchClusterTest.launchAutoScaledClusterTest` | ms | static, e.g. `1800000` |
| `e2e.ui.buckets.mounting.timeout` | BUCKETS_MOUNTING_TIMEOUT | EXEC: `LogAO.waitForMountBuckets` (`LaunchLimitMountsTest.linkStorageWithTheirURLs...`, `RunToolsInSandboxCheckDataStorageTest`) | ms | static, e.g. `900000` |
| `e2e.ui.endpoint.initialization.timeout` | ENDPOINT_INITIALIZATION_TIMEOUT | EXEC: `RunsMenuAO.waitEndpoint` (RBAC, RunToolsInSandBox), `RunsMenuAO.waitForInitializeNode` (wait for the InitializeNode task, i.e. a node being created), and a **fixed `sleep` of the full value** in `PauseResumeTest.pauseAndResumeEndpointValidation` after resume | ms; big = slow PauseResume, small = flaky InitializeNode wait | static, e.g. `600000` |
| `e2e.ui.login.delay.timeout` | LOGIN_DELAY_TIMEOUT | `Authorization.loginAs/logout` sleep before form/cookie login; **unused in mode C** (returns before the sleep) | ms | static, e.g. `3000` |
| `e2e.ui.search.timeout.in.minutes` | SEARCH_TIMEOUT | EXEC: `GlobalSearchTest.issueSearch`, `identicalNamesSearch` (sleep N+2 min), `searchAfterDeleting` (sleep N min); notexec others | **minutes**: indexing delay of `cp-search` (`sync.scheduler.delay=${CP_SEARCH_SYNC_TIMEOUT:30000}` ms) | `ceil(CP_SEARCH_SYNC_TIMEOUT/60000)+1`, e.g. `2` |
| `e2e.ui.run.sharing.timeout.in.seconds` | SHARING_TIMEOUT | **read, never used** | seconds | any int, e.g. `60` |
| `e2e.ui.sync.storage.timeout.in.seconds` | SYNC_STORAGE_TIMEOUT | EXEC: `StorageSynchronizationTest` -> `StorageHandling.waitUntilStorageAppears` | **seconds**; loop of `timeout/60` iterations of ~75 s; < 60 = a single check after 10 s | >= external sync period, e.g. `900` |
| `e2e.ui.backup.storage.offset` | BACKUP_STORAGE_OFFSET | EXEC: `StorageBackupsTest` | **hours** subtracted from UTC now to pick the `YYYY-MM-DD` backup folder | see `backup.*` below |

### 2.2 Login / users / roles

| key | constant | used by | meaning / format (example) | pre-exist | derive |
|---|---|---|---|---|---|
| `e2e.ui.root.address` | ROOT_ADDRESS | EXEC everywhere (`open()`, `login`, `restartBrowser`, `changeSession`, `ShellAO`) | UI URL **`https://<api host>/pipeline/`**, with the trailing slash; omit `:443`. `DNSrecordForRunsTest` derives the edge as `ROOT.replace("/pipeline/","").replaceFirst("\\.","-edge.")` and compares link `href`s **exactly**, so the edge host must be the API host with `-edge` appended to the first DNS label and the same port notation | API reachable from the runner; `CP_EDGE_EXTERNAL_HOST` = `<first label>-edge.<rest>` | `cp-config-global` `CP_API_SRV_EXTERNAL_HOST` (+`:CP_API_SRV_EXTERNAL_PORT` if not 443); check `CP_EDGE_EXTERNAL_HOST` |
| `e2e.ui.auth.token` | AUTH_TOKEN | EXEC: `login`, `Authorization.loginAs`, `NavigationMenuAO.logout`, RBAC (`failedAuthentication`, `checkAnonymousAccess`, `loginWithToken`, `validateWhileErrorPageMessage`), `SystemLoggingTest.userAuthenticationUnsuccessfulAttempt`, `DataStoragesFeaturesTest` (true = assert `storage.allow.signed.urls` is already false; false = set it), `StorageSynchronizationTest`/`StorageBackupsTest` (false = the test returns without checking), `PipelineHistoryTabAO` (time format); notexec PauseResume | exactly `true` or `false` (lowercase) | - | static `true` |
| `e2e.ui.impersonate.auth` | IMPERSONATE_AUTH | EXEC: `login` (SESSION vs HttpAuthorization cookie), `Authorization.impersonateMode()` used by loginAs/logout and many branches | `true`/`false` (ignore case) | admin can impersonate; users unblocked | static `true` |
| `e2e.ui.login` | LOGIN | EXEC: identity checks `ToolPageAO.validateEndpointPage(LOGIN)` (endpoint page prints the `sub` of the `bearer` JWT: DNSrecord, RunToolsInSandBox, PauseResume), owner checks `getUserNameByAccountLogin(LOGIN)` = part before `@` (RunPipelineTest, PipelineDetailsTest, RunToolsInSandBox), file tag `CP_OWNER` (ObjectMetadataFileTest), system-log rows `admin.login(.toUpperCase())` (SystemLoggingTest), user search for run-as config (RunAsTest, PipeCLITest), `ToolsTest.deletePersonalGroup` (personal group name = login lowercased, `._@` -> `-`), `Authorization.loginAs` (equalsIgnoreCase -> no-op); notexec GlobalSearch, DockerCommit, RemotePipelineRepository (needs an `@`) | platform user name **exactly as stored** (uppercase), no domain, e.g. `ADMIN`. Case matters for the `matchText` system-log regexes | admin user with `ROLE_ADMIN`, unblocked; in mode C the owner of the `SESSION` | `sub` of `CP_API_JWT_ADMIN` / `CP_DEFAULT_ADMIN_NAME`; `GET /restapi/whoami` with that token |
| `e2e.ui.password` | PASSWORD | EXEC: `login` (cookie value), `RoleBasedAccessControlTest.failedAuthentication` (mode A only: wrong password `123<password>`) | mode C: `SESSION` id; mode B: admin JWT; mode A: real password | - | see section 1 |
| `e2e.ui.login.isservice` | ADMIN_TOKEN_IS_SERVICE | EXEC: `SystemManagementAO.switchToSystemLogs` (SystemLoggingTest): unless `false`, ticks "include service account events" | `true` if `login` equalsIgnoreCase `CP_DEFAULT_ADMIN_NAME`: the ELK ingest pipelines set `service_account=true` for that user (`deploy/docker/cp-search-elk/init.sh`) and the API hides those events by default (`LogManager`). `false` with a service admin hides the admin's events -> failures; `true` is always safe | - | compare `login` with `CP_DEFAULT_ADMIN_NAME` |
| `e2e.ui.another.login` | ANOTHER_LOGIN | EXEC (via `Authorization.user`): RunsTest, RoleModelTest, RoleBasedAccessControlTest (block/unblock, add/remove ROLE_ADMIN, impersonate, export check `,<LOGIN>,`), RunToolsInSandBox, SystemLoggingTest (edge log `User: <login>` regex, case-sensitive), PlatformPreferencesTest (Lustre runs), LaunchParametersTest (sets/clears its allowed instance-type masks), DnsHostsManagement, PipeCLITest (run as user), SshPermission, RunAsTest, LaunchParametersRoleModel, DataStoragesFeatures, ToolsTest; `RunsMenuAO.validateOnlyMyPipelines` (excludes rows with this text) | platform user name as stored: `USER2` | **non-admin** user (pipeline/folder manager roles are fine), unblocked, can run the testing tool and DEFAULT_INSTANCE, c6i.xlarge, r6i.* on demand; not in a blocked group | `GET /restapi/user?name=USER2` |
| `e2e.ui.another.password` | ANOTHER_PASSWORD | `Authorization.user` (modes A/B only) | mode B: JWT of another.login; mode A: password | - | `GET /restapi/user/token?name=USER2&expiration=<s>` |
| `e2e.ui.clean.history.login` | CLEAN_HISTORY_LOGIN | EXEC (via `Authorization.userWithoutCompletedRuns`): SystemLoggingTest (block/unblock, "Authentication failed! User X is blocked!", removes and re-adds `role.user`, grants pipeline permissions), RoleModelTest (registry/group/tool ACL checks, `ensureOnlyOneRegistryIsAvailable`, sets the testing tool default command), RunAsTest (run as admin, tool READ/EXECUTE denied), SshPermissionTest (646/647: launches the pipeline run as this user and stops it as this user) | platform user name as stored | non-admin user, unblocked, member of `role.user`, sees exactly one registry, may launch the default instance and price type; the "no completed runs" requirement is gone (that test is disabled) | audit log: `POST /restapi/log/filter` `{"message":"is blocked","messageTimestampFrom":"<date>T00:00:00","includeServiceAccountEvents":true}` -> the blocked user |
| `e2e.ui.clean.history.password` | CLEAN_HISTORY_PASSWORD | `Authorization.userWithoutCompletedRuns` (modes A/B) | JWT / password | - | as another.password |
| `e2e.ui.another.admin.token` | ANOTHER_ADMIN_TOKEN | EXEC `RoleBasedAccessControlTest.provideAdminRightsToTheUser` -> `loginWithToken` | mode B: JWT with `ROLE_ADMIN` in the claims; ignored in mode C | - | `GET /restapi/user/token?name=USER1&expiration=<s>` (USER1 has ROLE_ADMIN) |
| `e2e.ui.anonymous.name` | ANONYMOUS_NAME | EXEC `RoleBasedAccessControlTest.checkAnonymousAccess` (`new ShellAO().assertPageContains(name)` on the endpoint page = the token's `sub`, case-sensitive `withText`) | user name exactly as in the token `sub` | user with role `ROLE_ANONYMOUS_USER`, unblocked; edge env `CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER=true` | `GET /restapi/role/loadAll?loadUsers=true` -> users of `ROLE_ANONYMOUS_USER` |
| `e2e.ui.anonymous.token` | ANONYMOUS_TOKEN | EXEC same test: cookie `bearer` on the edge host (mode C) / `HttpAuthorization` (mode B) | JWT of anonymous.name issued **after** it got `ROLE_ANONYMOUS_USER` (the `roles` claim must contain it), expiry beyond the run | as above | `GET /restapi/user/token?name=<anonymous>&expiration=<s>` |
| `e2e.ui.invalid.token` | INVALID_TOKEN | EXEC `RoleBasedAccessControlTest.failedAuthentication` (mode C) | any non-session string | - | static `e2e-invalid-session-0` |
| `e2e.ui.role.user` | ROLE_USER | EXEC: `SystemLoggingTest.userProfileModifications` (`deleteRoleOrGroup(v)` = find the role row by `text(v)`; `addRoleOrGroup(v)` = dropdown item by **`exactText(v)`**), `RoleModelTest.checkToolsPageByReadOnlyUser` and `checkToolsPageByExecutePermissionUser` (registry permission entries of this group and of clean.history.login: their READ/WRITE/EXECUTE values are recorded, the entries deleted for the test and re-added with the recorded values), `RunAsTest.checkRunAsForGeneralUser` (`share_with_roles: [{"name": v.toUpperCase()}]`, then share link contains `v`); notexec RestrictionsOnInstancePriceType | **the role name as the UI displays it**: predefined roles keep the prefix (`ROLE_USER`), custom roles/groups are shown **without `ROLE_`** (`EditUserRolesDialog.splitRoleName`), so for `ROLE_TEST_USER` the value must be `TEST_USER` | role exists; clean.history.login is a member; ideally it has an ACL entry on default.registry | audit log: `"message":"Assing role"` -> `RoleId=N`, then `GET /restapi/role/N`; or the role(s) of clean.history.login |
| `e2e.ui.platform.name` | PLATFORM_NAME | `RoleBasedAccessControlTest.validateWhileErrorPageMessage` / `Authorization.loginBack` ("login back to the <name>") and `SystemLoggingTest` ("<name> was not able to authorize you"): **mode A only** | text of the API error pages `deploy/docker/cp-api-srv/error-pages/*.html` | - | `CP_ERROR_PLATFORM_NAME`, default `CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME` (`cp-api-srv/init-api`) |

### 2.3 Registry, groups, tools, endpoints

| key | constant | used by | meaning / format (example) | pre-exist | derive |
|---|---|---|---|---|---|
| `e2e.ui.default.registry` | DEFAULT_REGISTRY | EXEC in 25 classes: `ToolsPage.changeRegistryTo` (registry button text **`equals`** this, else picks it from the dropdown), `DockerImageSelection.selectRegistry` (text contains) | the registry **as the UI names it**: `description || externalUrl || path` (`client/src/components/tools/registryName.js`) | the registry with the test groups; the only registry visible to clean.history.login (`RoleModelTest` `ensureOnlyOneRegistryIsAvailable`) | `GET /restapi/dockerRegistry/loadTree` -> `registries[]`: `description // externalUrl // path` |
| `e2e.ui.default.registry.ip` | DEFAULT_REGISTRY_IP | EXEC: `ToolsTest` (`<ip>/<testing.tool>` in launch form/run image, `<ip>/<group>/<tool w/o group>`), `DetachedConfigurationsTest.dockerImageSelection` (`<ip>/<testing.tool>:test`), `Launch_InstanceConfigTest` (`<ip>/<another.group>/<another.testing.tool>`); notexec DockerCommit, SamplesMetadata | registry **path** `host:port`, no scheme, as it prefixes run docker images (not an IP) | - | `loadTree` `registries[].path` |
| `e2e.ui.default.group` | DEFAULT_GROUP | EXEC in 23 classes (tool navigation, image selection, `ToolsTest` image string) | tool group name only: `tests` | contains testing.tool and tool.without.default.settings | group part of testing.tool |
| `e2e.ui.testing.tool` | TESTING_TOOL_NAME | EXEC in 23 classes (main tool for runs, endpoints, SSH, metadata, permissions); `RunAsTest` puts it as `docker_image`; `nameWithoutGroup()` for run names | **`group/tool`**, no registry, no tag: `tests/e2e-endpoints` | see section 4.2 (endpoint, `latest` and **`test`** tags, yum, no metadata, ...) | `loadTree` |
| `e2e.ui.valid.endpoint` | VALID_ENDPOINT | EXEC: `Tools.fallbackToToolDefaultState` (re-creates the tool endpoint with this port), RunToolsInSandBox (`ensure(PORT, value)`), DNSrecord (`pipeline-<run>-<port>-0.<zone>`, restore port), RBAC (`split("pipeline-<run>-<port>-0")` to get the edge URL), SystemLogging (`Application: /pipeline-<run>-<port>-0/`), RunToolsInSandboxCheckDataStorage | nginx **port number** of the testing tool endpoint: `8081` | the image must serve the owner page on it | `GET /restapi/tool/load?image=<path>/tests/e2e-endpoints` -> `endpoints[].nginx.port` |
| `e2e.ui.tool.without.default.settings` | TOOL_WITHOUT_DEFAULT_SETTINGS | EXEC: `ToolsTest` `@BeforeClass validateDeleteTool` (**deletes the tool**) + `validateEnableTool` (re-enables it from the registry), `toolCustomSettingsRunWithoutDefaults` (expects no disk/instance/command, launches with `sleep infinity`, timeout 1, expects FAILURE), `ObjectMetadataToolTest.checkMetadataForTool`; notexec ToolsScan | `group/tool` in default.group: `tests/e2e-empty` | image with `latest` tag in the registry under default.group | `loadTree` |
| `e2e.ui.another.group` | ANOTHER_GROUP | EXEC: RunsTest (rstudio + hard-coded `ubuntu`), DataStoragesCLITest, LaunchLimitMountsTest lifecycle, `Launch_InstanceConfigTest`; notexec LaunchClusterTest | group name only: `library` | contains test.docker.image, another.testing.tool and `ubuntu` | - |
| `e2e.ui.another.testing.tool` | ANOTHER_TESTING_TOOL_NAME | EXEC `RunsTest.platformUsageInfo/Tools` (as another.login, default launch options, stopped after 3 min, must offer RERUN); `Launch_InstanceConfigTest` (`<registry.ip>/<another.group>/<v>` written to config.json; launch form docker image `value()` contains it); notexec LaunchClusterTest | **tool name only**, the group is prepended: `rstudio` | `library/rstudio:latest` runnable by another.login | - |
| `e2e.ui.test.docker.image` | TEST_DOCKER_IMAGE | EXEC: `DataStoragesCLITest` (`yum install attr`, xattrs on `/cloud-data/<storage>`), `LaunchLimitMountsTest` lifecycle (unticks "Allow sensitive storages"); notexec RunToolsInSandBox DinD/Singularity | **tool name only** (another.group prepended): `centos` | `library/centos:latest`, yum works | - |

### 2.4 Instances, price types, cloud

| key | constant | used by | meaning / format | pre-exist | derive |
|---|---|---|---|---|---|
| `e2e.ui.default.instance.type` | DEFAULT_INSTANCE | EXEC in 18 classes: `PipelineRunFormAO.setDefaultLaunchOptions` (dropdown item by `text()` substring), tool default in `Tools` fallback, `{{instance_type}}` in config templates, run instance checks, `LaunchClusterTest` "Additional worker pipeline-X (<type>) has been scaled up", `Launch_InstanceConfigTest` (368 sets it in config.json and asserts it differs from the template's `instance_size`) | instance type: `m5.xlarge` (`default_instance_type` of `scripts/fixtures.json`) | allowed by `cluster.allowed.instance.types(.docker)` for admin and users; differs from the deploy-time `CP_PREF_CLUSTER_INSTANCE_TYPE` of the pipeline templates, or 368 fails | `GET /restapi/cluster/instance/loadAll` |
| `e2e.ui.another.instance.type` | ANOTHER_INSTANCE | EXEC: `DetachedConfigurationsTest` (1544, the node type set in the attached pipeline configuration); notexec others (GlobalSearch, LaunchClusterTest Spark) | instance type different from the default | allowed by `cluster.allowed.instance.types` | any allowed, e.g. `m5.2xlarge` (the generator takes the `.2xlarge` of the default family) |
| `e2e.ui.default.instance.family.name` | DEFAULT_INSTANCE_FAMILY_NAME | EXEC (LaunchClusterTest hybrid, RestrictionsOnInstancePriceTypeTest, RestrictionsOnInstanceTypeTest: the family shown in the instance type lists) | family prefix: `m5` | - | prefix of default.instance.type |
| `e2e.ui.default.instance.price.type` | DEFAULT_INSTANCE_PRICE_TYPE | EXEC: `DetachedConfigurationsTest` (default price type of a new configuration, `text()`), `PipelineConfigurationTest.priceTypeValidation` (selected value of the launch form); notexec others | UI label of the **platform default** price type: `Spot` if preference `cluster.spot`=true, `On-demand` otherwise | - | `GET /restapi/preferences/cluster.spot` |
| `e2e.ui.default.instance.price.type.tool` | DEFAULT_INSTANCE_PRICE_TYPE_TOOL | EXEC: `Tools.fallbackToToolDefaultState` (sets the testing tool's price type, all fallback classes), RunToolsInSandBox, RunToolsInSandboxCheckDataStorage | dropdown label for the tool setting: `On-demand` / `Spot` | - | static `On-demand` |
| `e2e.ui.spot.price.name` | SPOT_PRICE_NAME | EXEC `PipelineConfigurationTest` (1257/1263/1241 menu item / selected value); notexec LaunchCluster, RestrictionsOnInstancePriceType | spot label on AWS: `Spot` | spot in `cluster.allowed.price.types` | static per cloud (`Spot` for AWS) |
| `e2e.ui.cloud.provider` | CLOUD_PROVIDER | EXEC: `ConditionalTestAnalyzer` (registered by `@Listeners`, applies to the whole suite) compares `Cloud.AWS.name().toLowerCase().equals(v)`: **must be lowercase `aws`**, otherwise every `@CloudProviderOnly(AWS)` test is SKIPPED (EBS/Lustre, RunAs, StorageSync, Backups, DataStorages, ...); ObjectMetadataFileTest, CreateStoragePopupAO, StorageContentAO (ignore case) | `aws` | - | `CP_CLOUD_PLATFORM` lowercased |
| `e2e.ui.another.cloud.region` | ANOTHER_CLOUD_REGION | **read, never used** | region name | - | empty or `GET /restapi/cloud/region` |
| `e2e.ui.default.cluster.allowed.instance.types` | DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES | EXEC `PlatformPreferencesTest.checkClusterAllowedInstanceTypes` (`value()` contains); `RestrictionsOnInstancePriceTypeTest` **writes** it back as the default | substring of preference `cluster.allowed.instance.types`, e.g. `m5.*,c5.*,r4.*,t2.*,c6i.*,r6i.*` (the 0.16 default `m5.*,c5.*,r4.*,t2.*` plus `c6i.*` and `r6i.*`) | the preference must allow the family of default.instance.type (`m5`) and `c6i.*` and `r6i.*`, which TC-PARAMETERS-4, TC-PARAMETERS-6 and 3433 hard-code; `RestrictionsOnInstanceTypeTest` reads the allowed families itself | `GET /restapi/preferences/cluster.allowed.instance.types` .value |
| `e2e.ui.default.cluster.allowed.instance.types.docker` | DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER | EXEC `checkClusterAllowedInstanceTypesDocker` | substring of `cluster.allowed.instance.types.docker` | same as above | the preference value |
| `e2e.ui.cluster.allowed.masks` | CLUSTER_ALLOWED_MASKS | EXEC (`RestrictionsOnInstancePriceTypeTest`: `split(",")`, needs >= 4 items, `*` appended) | comma-separated instance-type prefixes, e.g. `m5.,c5.,r4.,m5.l` | - | `<family>.,c5.,r4.,<family>.l`, with the family of default.instance.type |
| `e2e.ui.default.cluster.allowed.price.types` | DEFAULT_CLUSTER_ALLOWED_PRICE_TYPES | EXEC (`RestrictionsOnInstancePriceTypeTest` writes it back to `cluster.allowed.price.types`) | e.g. `spot,on_demand` | - | `cluster.allowed.price.types` value |
| `e2e.ui.cluster.aws.ebs.type` | DEFAULT_CLUSTER_AWS_EBS_TYPE | EXEC `PlatformPreferencesTest.checkConfigureClusterAwsEBSvolumeType`: input value **`equals`** it, eye icon must be "visible"; `checkClusterAwsEBSvolumeTypeOfNewNode` (the InitializeNode log must match `The requested EBS volume type for \D+ device is <v>`) is excluded on this line, whose nodeup logs no volume type | e.g. `gp3` | preference `cluster.aws.ebs.type` set and **visible=true** | `GET /restapi/preferences/cluster.aws.ebs.type` (value; check `visible`) |
| `e2e.ui.cluster.instance.hdd.scale.delta.ratio` | CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO | EXEC `LaunchParametersTest` (static `Double.parseDouble`; `checkHDDpreferences` compares the preference input value with `equals`; expected log "autoscaled XG + floor(X/2)G" hard-codes +50%) | the preference string exactly, in practice `0.5` | `cluster.instance.hdd.scale.enabled`=true and the ratio = 0.5 | `GET /restapi/preferences/cluster.instance.hdd.scale.delta.ratio` .value as string |

### 2.5 Storages, files, search, preferences content

| key | constant | used by | meaning / format | pre-exist | derive |
|---|---|---|---|---|---|
| `e2e.ui.storage.prefix` | STORAGE_PREFIX | EXEC: `DataStoragesTest` (`<v>://` in create dialogs, `StorageContentAO.validateStoragePath` `matchText("<v>://")`), `Launch_DifferentTypesParametersValidationTest` (`<v>://<its own storage>/<folder>` parameter values); notexec Launch_*Output*, StorageRules, SamplesMetadata | storage **scheme without `://`**: `s3` | - | `CP_PREF_STORAGE_SCHEMA` or static per cloud |
| `e2e.ui.storage.name.prefix` | STORAGE_NAME_PREFIX | EXEC `DataStoragesTest` (expected bucket path `<prefix><name>` in messages and "add existing" paths); blank allowed | the bucket-name prefix the platform adds to new storages = preference `storage.object.prefix` | - | `GET /restapi/preferences/storage.object.prefix` .value or empty |
| `e2e.ui.nfs.prefix` | NFS_PREFIX | EXEC (NfsDataStorageTest: expected storage paths and messages) | the path prefix of a new NFS storage as the API gives it, e.g. `<NFS host>:/` | an NFS file share mount in the region | from the overrides (the generator writes it empty and warns) |
| `e2e.ui.download.folder` | DOWNLOAD_FOLDER | EXEC: `DataStoragesTest.downloadFileAndValidate`, `PipelineDetailsTest` (downloaded document), `RoleBasedAccessControlTest.generateListOfUsers` (`export.csv`), and `Utils.createTempFile*` writes upload files there (many classes) | absolute path inside the runner container = Chrome's download dir (`$HOME/Downloads`; no Chrome prefs are set): `/headless/Downloads` | dir exists and is writable by the test user | static |
| `e2e.ui.search.prefix` | SEARCH_PREFIX | EXEC `GlobalSearchAO` (search input located by `placeholder` attribute, exact) for GlobalSearchTest | the full placeholder **`<ui.pipeline.deployment.name> search`** (`client/src/components/search/SearchDialog.js`) | - | `GET /restapi/preferences/ui.pipeline.deployment.name` .value + `" search"` |
| `e2e.ui.environment.notifications` | ENVIRONMENT_NOTIFICATIONS | EXEC `NotificationsTest.validateSystemEventsMenu` (EPMCMBIBPC-1205): the System events table must hold exactly these notifications (by title), or show "No data" when the list is empty | JSON list of titles, e.g. `["Disclaimer"]`; optional, missing = `[]` (escape non-ASCII as `\uXXXX`) | the environment's own notifications (e.g. a disclaimer) | `GET /restapi/notification/list` .title, without the titles NotificationsTest creates (`^(info_notification\|info_edited\|warning_notification\|warning_active\|critical_notification)-YYYY-MM-DD$`, a leftover of an earlier run is reported and still fails 1205) |
| `e2e.ui.notifier.logs.dir` | NOTIFIER_LOGS_DIR | EXEC `RunStatusNotificationTest.noEmailIsSentIfRunStatusDidNotChange` (TC-NOTIFICATION-01): reads `notifier.log` and the rolled `notifier-yyyyMMdd.log` files there for the emails of a run's STOPPED status, instead of a mailbox; EXEC `CertificateExpirationNotificationTest.notificationIsSentForCertificateExpiringWithinAWeek` (2870_1): the same for the "Certificate expires soon" email; EXEC `NodeThresholdNotificationTest.notificationIsSentWhenNodeExceedsThresholds` (2870_2): the same for the "Nodes exceed thresholds" email | **path of a directory** in the container, e.g. `/notifier-logs`; optional, blank makes TC-NOTIFICATION-01 and 2870_2 fail before they launch a run, and 2870_1 before it writes its certificate | the notifier log directory mounted read-only, readable by uid 1000, and written within the last 2 min (the notifier logs its queue check every 30 s); notifier with the subject and all recipients in its email log line | static `/notifier-logs`: `run-e2e.sh` mounts `/opt/notifier/logs` (or `E2E_NOTIFIER_LOGS_DIR`) of its node there |
| `e2e.ui.vm.monitor.logs.dir` | VM_MONITOR_LOGS_DIR | EXEC `CertificateExpirationNotificationTest.notificationIsSentForCertificateExpiringWithinAWeek` (2870_1): reads `vm_monitor.log` and the rolled `vm_monitor-yyyyMMdd.log` files there: the VM monitor checked the test certificate, and the API accepted its notification; EXEC 2870_2: the node monitor checked the node of the run, found its `python` process above the NOFILE threshold, and the API accepted the notification, and when an earlier notification still delays it (`Aborting notifications for <m> more minutes`) | **path of a directory** in the container, e.g. `/vm-monitor-logs`; optional, blank makes 2870_1 fail before it writes its certificate, and 2870_2 before it launches a run | the VM monitor log directory mounted read-only, readable by uid 1000, and written within the last 2 min; for 2870_1, a VM monitor that checks new and changed certificate files every minute, with that check enabled (`CP_VM_MONITOR_PKI_CHANGES_ENABLE` not `false`); for 2870_2, the node monitor on (`CP_VM_MONITOR_NODE_ENABLE=true`) | static `/vm-monitor-logs`: `run-e2e.sh` mounts `/opt/vm-monitor/logs` (or `E2E_VM_MONITOR_LOGS_DIR`) of its node there |
| `e2e.ui.pki.test.dir` | PKI_TEST_DIR | EXEC 2870_1: writes its certificate `cp-e2e-2870-<time>-<id>.pem` there, and deletes it after the test | **path of a directory** in the container, e.g. `/pki-test`; optional, blank makes 2870_1 fail | a folder that the VM monitor scans (a subfolder of `/opt/common/pki` of the node of the `cp-vm-monitor` pod), writable by uid 1000 | static `/pki-test`: `run-e2e.sh` creates `/opt/common/pki/e2e-tests` (or `E2E_PKI_TEST_DIR`) for uid 1000, mounts it there, and removes the test certificates and the folder after the run; an existing folder is used only if an earlier run left it (owned by uid 1000, with only test certificates), so never `/opt/common/pki` itself |
| `e2e.ui.vm.monitor.to.user` | VM_MONITOR_TO_USER | EXEC 2870_1, 2870_2: the email must be sent to the `email` attribute of this user | a user name | the user exists and has an `email` attribute | `CP_VM_MONITOR_TO_USER` of the `cp-config-global` config map (a warning if it is not set) |
| `e2e.ui.vm.monitor.cc.users` | VM_MONITOR_CC_USERS | EXEC 2870_1, 2870_2: the email must have a BCC copy for each of these users that has an `email` attribute | comma separated user names; optional | each user exists: the API rejects a notification of the VM monitor otherwise | `CP_VM_MONITOR_CC_USERS` of the `cp-config-global` config map |
| `e2e.ui.node.requests.dir` | NODE_REQUESTS_DIR | EXEC 2870_2 and 3297 (`NodeUnavailableTest`): write their requests there to label the node of their run and remove the label, or to stop the kubelet of the node and start it again, which `scripts/node-requests.sh` answers | **path of a directory** in the container, e.g. `/node-requests`; optional, blank makes both tests fail before they launch a run | the folder mounted writable for uid 1000, and the helper running: it replaces `.watcher` there every 2 s; for 3297 the helper also needs `E2E_NODE_SSH_KEY` | static `/node-requests`: `run-e2e.sh` creates a folder of its run directory for uid 1000, mounts it there and starts the helper, which acts only on the node of a RUNNING run of a tests account and reverts what it did there after the run |
| `e2e.ui.node.reporter.procs` | NODE_REPORTER_PROCS | EXEC 2870_2: must have `python`, the name of the process the test starts | comma separated process names | `python` among the processes the `cp-node-reporter` pods measure | `CP_NODE_REPORTER_STATS_PROCS_INCLUDE` of the `cp-node-reporter` DaemonSet, else of the `cp-config-global` config map, else the reporter's default `dockerd,docker-containerd,containerd` (a warning without `python`) |
| `e2e.ui.vm.monitor.node.resend.delay.minutes` | VM_MONITOR_NODE_RESEND_DELAY_MINUTES | EXEC 2870_2: how long the VM monitor drops other node threshold notifications after one; the test launches its run only shortly before such a delay ends | minutes; optional, blank means 120 | - | `CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES` of the `cp-config-global` config map, `120` if not set |
| `e2e.ui.help.content` | SUPPORT_CONTENT | EXEC `PlatformPreferencesTest.checkHelpContent`: `value()` of the `ui.support.template` input contains it, and its eye icon must be "visible" | the preference value with **CR/LF removed** (it is rendered in an `<input type=text>`, whose value has line breaks stripped); escape non-ASCII as `\uXXXX` | `ui.support.template` visible=true | `GET /restapi/preferences/ui.support.template` .value, `tr -d '\r\n'` |
| `e2e.ui.lustre.fs.mount.options` | LUSTRE_MOUNT_OPTIONS | EXEC `checkLustreMountOption` (`value()` contains) | substring of `lustre.fs.mount.options`, e.g. `noatime,flock` | preference set | preference value |
| `e2e.ui.launch.container.cpu.resource` | LAUNCH_CONTAINER_CPU_RESOURCES_VALUE | EXEC `checkLaunchContainerCpuResource` (`value()` contains) | the int value, e.g. `1` | preference set | preference value |
| `e2e.ui.launch.system.parameters.path` | LAUNCH_SYSTEM_PARAMETERS_CONFIG_PATH | EXEC `checkLaunchSystemParameters`: `Utils.readFile(path)` (lines joined by `\n`) must be a Java `contains` (exact, case/whitespace sensitive) of the preference row text as rendered by CodeMirror, with the line-number lines removed | **path of a file** (absolute in the container, or relative to `e2e/gui`) holding a verbatim snippet of the stored `launch.system.parameters` JSON; the check is about `CP_FSBROWSER_ENABLED`. CodeMirror shows the raw stored string (not reformatted, `PreferenceGroup.js`); a single trimmed line such as `"name": "CP_FSBROWSER_ENABLED",` copied from the stored value is the most robust; avoid tabs | the preference contains the snippet; file present in the container | `GET /restapi/preferences/launch.system.parameters` .value, cut the snippet, write the file |
| `e2e.ui.pipe.operation.system` | PIPE_OPERATION_SYSTEM | EXEC `PipeCLITest.checkPipeCLIInstallationContent` (OS dropdown item) | a key of the `ui.pipe.cli.install.template` JSON, e.g. `Windows` (2115_1) | the template also needs a `Linux-Binary` key (hard-coded in 2115_2) | `GET /restapi/preferences/ui.pipe.cli.install.template` -> keys |
| `e2e.ui.pipe.installation.content` | PIPE_INSTALLATION_CONTENT | EXEC same test: `readFile(path).trim()` must equal (`exactText()`, ignore case/whitespace) the text of `#pip-install-url-input` | **path of a file** with the template text of that OS after the UI replaces every `${<preference name>}` with the preference value (`PreferencesLoad.replacePlaceholders`) | file present | template[os], substitute `${name}` from `GET /restapi/preferences`, write file |
| `e2e.ui.sync.storage.name` | SYNC_STORAGE_NAME | EXEC `StorageSynchronizationTest` (skipped if auth.token=false): `split("/")`, `cd` into each segment from the library root, the last one is the storage name; the test **unregisters** that storage and waits until it reappears | library path `folder/sub/storageName` (or just `storageName`) | a storage that an **external synchronization process** re-registers (with permissions) within sync timeout; nothing in this repo does it | operator input (not derivable) |
| `e2e.ui.sync.storage.permission.name` | SYNC_STORAGE_PERMISSION_NAME | EXEC same test (row of the storage permission table) | user or group name as shown in the permissions tab | the sync process grants it | operator input / `GET /restapi/permissions?id=<id>&aclClass=DATA_STORAGE` |
| `e2e.ui.sync.storage.permissions` | SYNC_STORAGE_PERMISSIONS | EXEC same test (`split(",")`, each `r|w|e:a|d`) | expected privileges, e.g. `r:a,w:d,e:a` (a=allow, d=deny) | - | operator input / derive from the ACL mask |
| `e2e.ui.backup.storage.names` | BACKUP_STORAGE_NAMES | EXEC `StorageBackupsTest` (skipped if auth.token=false): opens each by **name** from the "All storages" table; with >1 storage also compares file sizes across them | comma-separated storage **names**, no spaces, no scheme (static `split(",")`, mandatory key) | the storages of `CP_BKP_STORAGE_LIST` (or the system storage) holding today's backups | `CP_BKP_STORAGE_LIST` (`s3://a,s3://b`) -> `GET /restapi/datastorage/loadAll`, match `path`, take `name`; fallback preference `storage.system.storage.name` |
| `e2e.ui.backup.storage.path` | BACKUP_STORAGE_PATH | EXEC same test (`split("/")`, `cd`) | folder inside the storage: `backups` (the workers write `backups/<service>/<YYYY-MM-DD>/`) | - | static |
| `e2e.ui.backup.storage.offset` | BACKUP_STORAGE_OFFSET | EXEC same test | hours; folder date = `(UTC now - offset).date`. The workers run at `CP_BKP_SCHEDULE_CRON` (default `0 8 * * *`) and name the folder by the pod's date | a backup folder of that date for `cp-api-srv`, `cp-git`, `cp-api-db` | find the newest `backups/cp-api-srv/<D>` (e.g. `pipe storage ls`), offset = floor(hours from D 00:00 UTC to now); `24` = yesterday is a simple robust choice |
| `e2e.ui.webdav.address` | WEBDAV_ADDRESS | **read, never used; missing from default.conf** | - | - | omit |

### 2.6 Keys only for non-executed classes (all missing from `default.conf`; `null` is harmless today)

| key | constant | used by (all notexec) | format |
|---|---|---|---|
| `e2e.ui.registry.login` / `e2e.ui.registry.password` | REGISTRY_LOGIN_FOR_TOOL / REGISTRY_PASSWORD_FOR_TOOL | RegistryTest, RegistryAdditionDeletionTest | credentials of an external registry |
| `e2e.ui.tool.registry.path` | REGISTRY_PATH_FOR_TOOL | same | registry `host:port` to add/delete |
| `e2e.ui.tool.invalid.registry.path` | INVALID_REGISTRY_PATH_FOR_TOOL | RegistryTest | non-existing registry |
| `e2e.ui.repository` / `e2e.ui.token` | REPOSITORY / TOKEN | RemotePipelineRepositoryTest | git repository URL / git token |
| `e2e.ui.webdav.address` | WEBDAV_ADDRESS | none | - |

Read but unused in any code: `run.sharing.timeout.in.seconds`, `another.cloud.region`, `webdav.address`,
`pipe.config.content.path` (PipeCLITest 2115_2 checks the `Config storing mode` lines itself).
Used only by classes that `testng.xml` does not run: the 6 keys of 2.6. Unused in mode C:
`login.delay.timeout`, `another.password`, `clean.history.password`, `another.admin.token`, `platform.name`.
(`e2e.ui.pass` is read by `ParallelLoadTests` from `parallelLoad.conf`, ignored.)

---------------------------------------------------------------------------------------------------------------------

## 3. Example (mode C), placeholders in `<>`

```properties
e2e.ui.default.timeout=30000
e2e.ui.commit.appearing.timeout=600000
e2e.ui.ssh.appearing.timeout=1800000
e2e.ui.committing.timeout=1800000
e2e.ui.run.completion.timeout=1800000
e2e.ui.buckets.mounting.timeout=900000
e2e.ui.endpoint.initialization.timeout=600000
e2e.ui.login.delay.timeout=3000
e2e.ui.search.timeout.in.minutes=2
e2e.ui.run.sharing.timeout.in.seconds=60
e2e.ui.sync.storage.timeout.in.seconds=900
e2e.ui.root.address=https://<CP_API_SRV_EXTERNAL_HOST>/pipeline/
e2e.ui.auth.token=true
e2e.ui.impersonate.auth=true
e2e.ui.login=<ADMIN>
e2e.ui.password=<SESSION cookie value of ADMIN>
e2e.ui.login.isservice=true
e2e.ui.another.login=USER2
e2e.ui.another.password=<JWT of USER2>
e2e.ui.clean.history.login=<USER3, verify>
e2e.ui.clean.history.password=<JWT of that user>
e2e.ui.another.admin.token=<JWT of USER1>
e2e.ui.anonymous.name=<user with ROLE_ANONYMOUS_USER>
e2e.ui.anonymous.token=<JWT of it, issued after the role was assigned>
e2e.ui.invalid.token=e2e-invalid-session-0
e2e.ui.role.user=<ROLE_USER or TEST_USER, see 2.2>
e2e.ui.platform.name=<CP_ERROR_PLATFORM_NAME>
e2e.ui.download.folder=/headless/Downloads
e2e.ui.default.registry=<description||externalUrl||path of the registry>
e2e.ui.default.registry.ip=<registry host>:<port>
e2e.ui.default.group=tests
e2e.ui.testing.tool=tests/e2e-endpoints
e2e.ui.valid.endpoint=<8081, from the tool endpoint>
e2e.ui.tool.without.default.settings=tests/e2e-empty
e2e.ui.another.group=library
e2e.ui.another.testing.tool=rstudio
e2e.ui.test.docker.image=centos
e2e.ui.default.instance.type=m5.xlarge
e2e.ui.another.instance.type=m5.2xlarge
e2e.ui.default.instance.family.name=m5
e2e.ui.default.instance.price.type=Spot
e2e.ui.default.instance.price.type.tool=On-demand
e2e.ui.spot.price.name=Spot
e2e.ui.cloud.provider=aws
e2e.ui.another.cloud.region=
e2e.ui.storage.prefix=s3
e2e.ui.storage.name.prefix=<storage.object.prefix, empty>
e2e.ui.search.prefix=<ui.pipeline.deployment.name> search
e2e.ui.default.cluster.allowed.instance.types=<cluster.allowed.instance.types>
e2e.ui.default.cluster.allowed.instance.types.docker=<cluster.allowed.instance.types.docker>
e2e.ui.cluster.allowed.masks=m5.,c5.,r4.,m5.l
e2e.ui.default.cluster.allowed.price.types=<cluster.allowed.price.types>
e2e.ui.cluster.aws.ebs.type=<cluster.aws.ebs.type>
e2e.ui.cluster.instance.hdd.scale.delta.ratio=0.5
e2e.ui.help.content=<ui.support.template without CR/LF, \uXXXX-escaped>
e2e.ui.lustre.fs.mount.options=<lustre.fs.mount.options>
e2e.ui.launch.container.cpu.resource=<launch.container.cpu.resource>
e2e.ui.launch.system.parameters.path=/headless/e2e/gui/<dir>/launch_system_parameters.txt
e2e.ui.pipe.operation.system=Windows
e2e.ui.pipe.installation.content=/headless/e2e/gui/<dir>/pipe_install_windows.txt
e2e.ui.sync.storage.name=<folder/.../storage>
e2e.ui.sync.storage.permission.name=<user or group>
e2e.ui.sync.storage.permissions=r:a,w:d,e:a
e2e.ui.backup.storage.names=<names from CP_BKP_STORAGE_LIST>
e2e.ui.backup.storage.path=backups
e2e.ui.backup.storage.offset=<hours>
```

---------------------------------------------------------------------------------------------------------------------

## 4. Platform prerequisites assumed by the executed tests

### 4.1 Users, roles, auth
* Test users unblocked before the run. `run-e2e.sh` (`scripts/test-users.sh`, cases TC-TEST-ENVIRONMENT-1/2, not in the
  GUI code) unblocks User1-3 before and blocks them after: `PUT /restapi/user/<id>/block?blockStatus=false|true`.
* `login` = admin with `ROLE_ADMIN` that can impersonate; `another.login` non-admin (RoleModelTest expects ACL denials
  to apply to it); `clean.history.login` member of `role.user`; anonymous user with `ROLE_ANONYMOUS_USER`.
* The roles flagged `userDefault` must be exactly `ROLE_USER`, `ROLE_PIPELINE_MANAGER`, `ROLE_FOLDER_MANAGER`,
  `ROLE_CONFIGURATION_MANAGER` (`RoleBasedAccessControlTest.addTheUser` creates a user and checks them;
  `GET /restapi/role/loadAll`).
* Must NOT pre-exist (created with fixed names): user `TEST_SYSTEM_USER_NAME` (SystemLoggingTest); group
  `DOMAIN USERS` is **deleted if present**, recreated and deleted (RoleModelTest) - destructive for a real AD group of
  that name.
* `ToolsTest` `@BeforeClass`/`@AfterClass deletePersonalGroup` **deletes the admin's personal tool group**
  (`<login lowercased, ._@ -> ->`, e.g. `admin`) in default.registry, with its tools.
* Edge: `CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER=true`. System logs (ELK `cp-search-elk` API + edge ingest pipelines) must
  work: SystemLoggingTest reads authentication, impersonation, role, permission and edge `[SECURITY]` rows.
* Mode C needs an administrator's SSO browser session per run (see section 1).

### 4.2 Registry / tools (all in default.registry)
* `tests/e2e-endpoints` with tags `latest` and **`test`** (DetachedConfigurationsTest selects `:test`); endpoint on
  valid.endpoint serving a page whose `#owner` shows the `sub` of the `bearer` cookie (`cp-tests/e2e-endpoints/index.html`);
  runs with `/start.sh` and `nginx -g "daemon off;"`; SSH; **yum with EPEL** (`yum install -y stress`, `bind-utils`) -
  the image built from `deploy/docker/cp-tests/e2e-endpoints` is `rockylinux/rockylinux:8.7` with `epel-release`;
  `/ebs` btrfs autoscaling, GridEngine/autoscaler, `conda` capability, pipe CLI install. **No
  metadata** on the tool (ObjectMetadataToolTest counts exactly 3 keys after its own edits). Runnable by
  another.login and clean.history.login. It gets a new tag `test_tag_913_<n>` per run (see 5).
* `tests/e2e-empty:latest` image in the registry (the tool is deleted and re-enabled each run), built from
  `deploy/docker/cp-tests/e2e-empty` (`rockylinux/rockylinux:8.7`).
* `library/centos:latest` (test.docker.image; also hard-coded `CP_CAP_AUTOSCALE_INSTANCE_IMAGE=/library/centos:latest`
  for the SGE hybrid child, `LaunchParametersTest`), `library/centos:7` (SHELL/PYTHON/DEFAULT templates),
  `library/ubuntu:latest` (hard-coded in RunsTest, "Welcome to Ubuntu" in SSH), `library/rstudio:latest`.
* default.registry ACL: RoleModelTest (572, 573) removes the entries of `role.user` and clean.history.login and restores the recorded values;
  clean.history.login must see only this one registry.
* Pipeline templates `DEFAULT`, `PYTHON`, `SHELL` in Create > Pipeline (GitLab working).

### 4.3 Preferences / platform settings
* `cluster.allowed.instance.types` and `.docker`: allow the family of default.instance.type (`m5`, which the 0.16 default
  `m5.*,c5.*,r4.*,t2.*` allows), `c6i.*` (c6i.xlarge in nodeMemoryLimits, the c6i hybrid family) and `r6i.*`
  (r6i.xlarge/8xlarge). RestrictionsOnInstanceTypeTest picks a family that neither preference allows itself.
* `cluster.spot` consistent with default.instance.price.type; `cluster.allowed.price.types` includes spot and
  on_demand (menus must show both).
* `cluster.aws.ebs.type` set and visible; `ui.support.template` set and visible; `lustre.fs.mount.options`,
  `launch.container.cpu.resource` set; `launch.system.parameters` contains the CP_FSBROWSER_ENABLED snippet.
* `cluster.instance.hdd.scale.enabled=true`, `cluster.instance.hdd.scale.delta.ratio=0.5`.
* `storage.allow.signed.urls=false` (DataStoragesFeaturesTest asserts it when auth.token=true).
* `storage.object.prefix` empty (mount-path checks `/cloud-data/<storage name>`).
* `instance.dns.hosted.zone.base` set, with working wildcard DNS for `pipeline-<run>-<port>-0.<zone>` and
  `<friendly>-e2e-endpoint.<zone>` (DNSrecordForRunsTest clicks the sub-domain endpoint).
* `launch.capabilities` contains a `conda` capability (LaunchParametersTest TC-PARAMETERS-7); built-in `NoMachine`
  must produce an endpoint on port 8089 (DNSrecordForRunsTest).
* `ui.pipe.cli.install.template` with pipe.operation.system and `Linux-Binary` keys; `ui.pipe.cli.configure.template`.
* `ui.pipeline.deployment.name` set (search placeholder).
* Email notifications (TC-NOTIFICATION-01): `PIPELINE_RUN_STATUS` enabled, with "Keep owners informed", STOPPED among
  its "Statuses to inform" (or none selected), and the template subject ending with
  `Job #$templateParameters["id"] is now $templateParameters["status"]`; the admin has an `email` attribute. The
  `cp-notifier` pod runs on the node of `run-e2e.sh`, so its `/opt/notifier/logs` can be mounted.
* Certificate expiration emails (2870_1): a VM monitor that checks new and changed certificate files every minute
  (`CP_VM_MONITOR_PKI_CHANGES_ENABLE` not `false`), `CP_VM_MONITOR_PKI_EXPIRATION_DAYS` more than 3 (the installer sets
  7), `CP_VM_MONITOR_PKI_MASKS` with `pem` (the default), `CP_VM_MONITOR_TO_USER` set to a user with an `email`
  attribute, the users of `CP_VM_MONITOR_CC_USERS` existing, and the notifier that logs the subject and all
  recipients. The `cp-vm-monitor` pod runs on the node of `run-e2e.sh`, so its `/opt/vm-monitor/logs` can be mounted and
  `/opt/common/pki`, which it scans, is that node's folder.
* Node threshold emails (2870_2): the node monitor on (`CP_VM_MONITOR_NODE_ENABLE=true`), `python` among the processes
  of `CP_NODE_REPORTER_STATS_PROCS_INCLUDE` of the `cp-node-reporter` DaemonSet, a `cp-node-reporter` pod on the nodes
  of the runs, the NOFILE threshold below 97% (`CP_VM_MONITOR_NODE_THRESHOLDS`, by default `{NOFILE:60}`), SSH into
  a run as root (`system.ssh.default.root.user.enabled`, true by default), the recipients and the notifier as for
  2870_1, and `kubectl` on the node of `run-e2e.sh`, whose helper labels the run's node. The test may wait up to the
  resend delay (`CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES`, 120 min by default) if another node threshold
  email was sent shortly before it. Its timeouts assume that the node monitor checks every minute
  (`CP_VM_MONITOR_NODE_CRON`, `0 * * ? * *` by default).
* Unavailable nodes (3297): `cluster.node.unavailable.grace.period.minutes` of at least 15 (30 by
  default) and a non-empty `system.run.tag.date.suffix` (`_date` by default), which the test reads through the API
  before it launches a run; the node of `run-e2e.sh` able to log in over SSH to the nodes of the runs with
  `E2E_NODE_SSH_KEY` (no default, e.g. the key of `CP_CLUSTER_SSH_KEY` of `cp-api-srv`), as
  `E2E_NODE_SSH_USER` or the user of `commit.username`, which may run `sudo` without a password;
  nodes with `systemd-run` (systemd 218 or later). Its timeouts assume the Kubernetes default of 40 s before a silent
  node is marked unavailable, and the autoscaler every 40 s (`cluster.autoscale.rate`).
* AWS: S3 bucket create/delete by the API (many test buckets), FSx for Lustre PERSISTENT_2 (PlatformPreferencesTest),
  on-demand m5/c6i/r6i capacity, SGE hybrid autoscaling, kube DNS `10.96.0.10` (DnsHostsManagementTest hard-codes
  `Server: 10.96.0.10`).
* `CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN` honoured by the API (2736: a run stuck in its umount must still be running
  after 1 min, and be stopped after 2 min and one `system.resource.monitoring.period` with the parameter set to 2).
  The test sets `launch.run.emergency.termination.action` to `STOP` for itself and sets the previous value back.
* Backups: daily cp-bkp-worker jobs for `cp-api-srv`, `cp-git`, `cp-api-db`; the test expects the GitLab backup name
  `cp-bkp-<n>_<YYYY_MM_DD>_<GitLab version>_gitlab_backup.tar`, any version.
* The storage synchronization service for sync.storage.* (external to this repo).

---------------------------------------------------------------------------------------------------------------------

## 5. What the executed tests change and do not restore (for the cleanup script)

* **Testing tool settings**: every `Tools.fallbackToToolDefaultState` removes all endpoints and adds one with port
  valid.endpoint and **no name** (the original `E2E-Endpoint` name and `isDefault` are lost; DNSrecordForRunsTest sets
  the name `E2E-Endpoint` and later fallbacks remove it again), sets default command `/start.sh`, instance
  default.instance.type (originally `${CP_PREF_CLUSTER_INSTANCE_TYPE}`), price type default.instance.price.type.tool,
  disk 20 (RunToolsInSandBox* use disk 15 and `nginx -g "daemon off;"` / `/start.sh`). The last fallback of the suite
  (ToolsTest `@AfterClass`) wins.
* **Testing tool ACL**: RunToolsInSandboxCheckDataStorageTest adds another.login with EXECUTE allow (never
  removed); RoleModelTest adds entries (group `DOMAIN USERS` is deleted at the end, user entries stay).
* **New docker tag** `tests/e2e-endpoints:test_tag_913_<random>` committed by
  `LaunchParametersTest.addSupportForAutoscalingFilesystemForAWS`, never deleted.
* **`tests/e2e-empty` tool deleted and re-enabled** (new tool id; settings, metadata, ACL, icon, description lost).
* **`library/centos`** (another.group/test.docker.image): "Allow sensitive storages" unticked (LaunchLimitMountsTest
  before and after).
* **Registry ACL**: the entry of role.user is deleted and, only if it existed, recreated as R allow/W deny/E allow
  (original mask not kept); the entry of clean.history.login on the registry is deleted; clean.history.login keeps
  explicit **group** ACL entries on default.group (last state WRITE allow, EXECUTE deny).
* **Users**: another.login gets and loses `ROLE_ADMIN` (left as admin if the test dies in between); another.login and
  clean.history.login are blocked/unblocked (finally-blocks unblock); clean.history.login loses and regains role.user;
  another.login's "Allowed instance types mask" and "Allowed tool instance types mask" are set to `r6i.*` and then to
  **empty** (any previous value is lost); the admin's "run as" list gets and loses another.login and
  clean.history.login (RunAsTest, PipeCLITest); users `rbacTestUser<n>` and `TEST_SYSTEM_USER_NAME` are created and
  deleted (and `<rbacTestUser>-home` storage if `storage.user.home.auto` is on); the admin's "SSH terminal theme"
  (attribute `ui.ssh.theme`) is set to Light if it was another one, as the preparations of TC-TEST-ENVIRONMENT-4
  ask (SshTerminalThemeTest), and never set back.
* **Groups**: `DOMAIN USERS` deleted (if it existed), created, deleted.
* **Admin personal tool group** deleted by ToolsTest.
* **Preferences** (mode C): `launch.capabilities` is re-typed in the UI from the text read at start
  (ToolsParametersTest `@AfterClass`; CodeMirror only renders visible lines, so a long value can be saved truncated -
  back it up by REST and compare after the run); `cluster.docker.extra_multi` re-saved with the read value
  (PauseResumeTest). Modes A/B also switch `storage.user.home.auto` off and mode A `storage.allow.signed.urls` off;
  both are restored in `@AfterClass`.
* **Sync storage**: unregistered and re-registered by the external process (new id).
* **System notifications** created and deleted (NotificationsTest `@AfterClass`).
* Leftovers when a class fails before its `@AfterClass`: runs (stopped in `stopRuns`, nodes removed in
  `removeNodes`), S3 buckets/storages and pipelines/folders/configurations named `epmcmbi-test-*`, `ui-tests-*`,
  `globalSearch*`, `launchLimitMounts*`, `test-storage-*`, `bucket-*`, `role-model-*`, `restrictionTest*`, `SystemLogging*`,
  `runsTest-*`, `ssh-permission-test-*`, `pipeline-editing-*`, `detached-*`, `LaunchClusterTest*`, `*Test<n>`; FSx Lustre
  file systems of the Lustre runs; downloaded files in download.folder.

---------------------------------------------------------------------------------------------------------------------

## 6. How `scripts/generate-config.sh` applies this

* Mode C (impersonation). `e2e.ui.password` is the admin browser session given by the operator
  (`~/.cp-e2e/admin-session` or `E2E_ADMIN_SESSION`), checked to belong to an administrator and to outlive 65 s;
  `e2e.ui.login` is its owner. JWTs of the test users are issued with `--token-ttl` (48 h by default).
* Test users from `e2e/prerequisites/users.json`: another = the non-admin manager (USER2), clean.history = the
  `ROLE_USER`-only user (USER3), another.admin.token = the `ROLE_ADMIN` user (USER1).
* `e2e.ui.role.user`: the dedicated group `ROLE_TEST_USER` (members USER1-3, created by `platform-prepare.sh`; shown as
  `TEST_USER`), granted READ allow / WRITE deny / EXECUTE allow on the registry. It keeps the registry permission away
  from all users of `ROLE_USER`.
* `e2e.ui.anonymous.*`: `E2E_ANONYMOUS_USER` with `ROLE_ANONYMOUS_USER`, created by `platform-prepare.sh` if no
  anonymous user exists, deleted after the run.
* `e2e.ui.sync.storage.*`: the first storage of the user of the storage synchronization service (`sync_storage.owner`
  of `scripts/fixtures.json`, which has no default) permitted to a group matching `TEST` (`sync_storage.permission_pattern`),
  e.g. `<folder>/<storage>` / `<group>` / `r:a,w:a`.
* `e2e.ui.backup.storage.*`: storages of `CP_BKP_STORAGE_LIST`, folder `backups`, offset 24 h (checked: the folder
  of the previous day exists).
* File keys: written to `e2e-files/` next to `default.conf` (relative paths).
* `e2e.ui.notifier.logs.dir`: always `/notifier-logs`, where `run-e2e.sh` mounts the notifier log directory of its node
  (with a warning, and no mount, when that directory is missing).
* `e2e.ui.vm.monitor.logs.dir` and `e2e.ui.pki.test.dir`: always `/vm-monitor-logs` and `/pki-test`, the same way.
* `e2e.ui.node.requests.dir`: always `/node-requests`, where `run-e2e.sh` mounts the requests folder of its node
  helper.
* Anything else: `--overrides <file>` with `key=value` lines.
