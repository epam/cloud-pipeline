# Release Notes Agent

## Application description
**Release Notes Agent** is a command line tool that notifies users of the Cloud Pipeline version change with a description of the changes.

Changes are considered from two types of sources:
- GitHub issues
- Jira issues

**_Note_**: If an issue is duplicated in both sources at the same time, only GitHub issue will be considered.

The **Release Notes Agent** provides `send-release-notes` command which performs:
1. Fetches a current Cloud Pipeline version.
2. Gets an old Cloud Pipeline version is stored in a file specified in the `application.properties` file. 
3. Compares the current and old versions:
   - If the Cloud Pipeline major version (e.g. 0.16) has been changed just admins will be notified without description of the changes.
   - If the Cloud Pipeline minor version has been changed subscribes will be notified of the changes.
   - If the Cloud Pipeline version has not been changed - no action
4. Fetches all commits between the old version and the new version if the minor version has been changed.
5. Parses commit messages of the fetched commits and gets issue numbers according to the pattern specified in the `application.properties` file.
6. Fetches Jira issues by custom field with the current Cloud Pipeline version.
7. Filters duplicated Jira issues on GitHub.
8. Sends an email with issues that were included in the latest release to subscribers.
9. Updates the current version in version file.

There are requirements on the git commits and Jira issues so that Release Notes Agent can detect 
issues were included in the latest release:

- Commit messages should contain the `Issue #<issue number>` or `issue #<issue number>`. 
  See [Git Hooks](#git-hooks) for details.
- Jira's issues should be marked by a Jira custom field contains the current Cloud Pipeline version.
  The custom field id `jira.version.custom.field.id` is specified in the `application.properties` file.
  Besides, a Jira issue can be marked a GitHub issue number for duplication exclusion (`jira.github.custom.field.id` in `application.properties`).

Email templates can be customized and specify in the `application.properties` file:
- `release.notes.agent.major.version.changed.subscriber.emails` template for notifying of a major version change 
- `release.notes.agent.minor.version.changed.subscriber.emails` template for notifying of a minor version change 
- `release.notes.agent.templates.dir` template directory

## Configuration

### Application properties

All **Release Notes Agent** settings should be specified in the `application.properties` file through environment variables or inline.

Required properties:
```
pipeline.api.url - Cloud Pipeline restapi url
```
```
pipeline.api.token - Cloud Pipeline api token
```
```
pipeline.api.version.file.path - A version file path
```
```
github.token - GitHub token
```
```
jira.base.url - Jira base url
```
```
jira.auth.token - Jira api token in `Basic %token%` format, where %token% is 'user:password' in Base64 encode 
```
```
jira.version.custom.field.id - Jira custom release version field id
```
```
jira.github.custom.field.id - Jira custom GitHub issue number field id
```
```
release.notes.agent.major.version.changed.subscriber.emails - List of subscribers (admins) for notifying major version change
```
```
release.notes.agent.minor.version.changed.subscriber.emails - List of subscribers (users) for notifying minor version change
```
```
spring.mail.host - SMTP server host
```
```
spring.mail.port - SMTP server port
```
```
spring.mail.username - Login user to SMTP server
```
```
spring.mail.password - Login password to SMTP server
```
```
spring.mail.properties.mail.smtp.auth - SMTP authentication should be used (true/false)
```

## How to run

### Command line

~~~
# Build jar
./gradlew :release-notes-agent:bootJar
~~~

Jar file will be available as `build/libs/release-notes-agent.jar`
~~~ 
# Run Release Notes Agent
java -jar release-notes-agent.jar send-release-notes
~~~ 

### Kubernetes deployment

**Release Notes Agent** can be deployed as a Kubernetes CronJob.
- The CronJob manifest for **Release Notes Agent** could be found [here](deploy/contents/k8s/cp-release-notes/cp-release-notes-dpl.yaml)
  - **_Note_**: Schedule _${CP_RELEASE_NOTES_SCHEDULE}_ is specified in the spec should be change to a value or set as ENV to ConfigMap for example.
- Dockerfile for deployment could be found [here](deploy/docker/cp-release-notes/Dockerfile) 
  - **_Note_**: `application.properties` file with predefined properties could be found [here](deploy/docker/cp-release-notes/config/application.properties).
    The specified ENVs should be available in application container.
  
## Git Hooks

Git hooks were added for checking the commit message and preparing a commit message template in respecting format conventions.
The commit message should include an issue number in the `Issue #<issue number>` or `issue #<issue number>` format or `#no issue#` format, 
otherwise, the script rejects the commit. 
The `#no issue#` format was introduced for exceptional cases and should be avoided.
The hook scripts could be found [here](.hooks).
To apply hooks in the project:

~~~
git config core.hooksPath $PROJECT_DIR/.hooks
~~~
