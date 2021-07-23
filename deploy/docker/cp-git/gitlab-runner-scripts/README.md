# Configure GitLab runner
* Run /gitlab-runner-scripts/init-gitlab-runner.sh with a "Shared runner registration token" (https://<GITLAB_URL>/admin/runners)

# Configure project
* Create a new project in the GitLab or choose an existing one
* Go to `Settings -> Pipelines`
* Add `API` and `API_TOKEN` project variables to `Secret variables`. These can be retrieved from the Cloud Pipeline GUI settings

# Add CI config to the project
* Create `.gitlab-ci.yml` file at the root of the repository
* Add the following contents:
```
variables:
  DOCKER_IMAGE: "library/centos:latest"
  INSTANCE_TYPE: "m5.large"

test:
 stage: test
 script:
   - echo $HOSTNAME
```
* Note that the `DOCKER_IMAGE` and `INSTANCE_TYPE` variables shall be specified to let the GitLab runner know, which environment shall be used to run the CI scripts
* Check that a job is running in the `Pipelines` section of the GitLab project