# GitLab versions

`pipectl` installs GitLab CE as the `cp-git` service. Two values of the install config select the versions:

* `CP_GITLAB_VERSION`: the GitLab version. It also selects the `cp-git` image, `git-<CP_GITLAB_VERSION>-<version>`.
* `GITLAB_DATABASE_VERSION`: the tag of the `postgres` image of `cp-gitlab-db`, the PostgreSQL server that GitLab 19
  keeps its database in.

| `CP_GITLAB_VERSION` | GitLab CE | The GitLab database |
|---|---|---|
| `9` (the default) | 9.4.0 | in `cp-api-db`, the PostgreSQL 9.6 server of the API. `GITLAB_DATABASE_VERSION` is not used |
| `19` | 19.5.3 | in `cp-gitlab-db`, on `GITLAB_DATABASE_VERSION`: `17` (the default), or a `17.x` tag |

`19` is the latest GitLab version the platform supports.

For `19`, `pipectl` prints a warning and goes on if `GITLAB_DATABASE_VERSION` is not a PostgreSQL 17 tag, and the
install may fail. The `17` tag gives the latest PostgreSQL 17 release at the time the node first pulls the image. To
pin the version, set a full `17.x` tag.

Set the values in the install config, or pass them to `pipectl install` with `-env`. A `-env` value overrides the
install config:

```bash
~/.pipe/pipectl install \
    -env CP_GITLAB_VERSION=19 \
    -env GITLAB_DATABASE_VERSION=17 \
    ...
```

## The GitLab database

`GITLAB_DATABASE_HOST` and `GITLAB_DATABASE_PORT` set the server that GitLab uses. Their defaults point at `cp-api-db`.
When `CP_GITLAB_VERSION` is not `9` and `GITLAB_DATABASE_HOST` has its default value
`cp-api-db.default.svc.cluster.local`, the installer sets it to `cp-gitlab-db.default.svc.cluster.local`, in the
install config too. A host that is set to another value is kept.

`cp-gitlab-db` runs on the node labelled `cloud-pipeline/cp-gitlab-db`: the node `CP_GITLAB_DB_KUBE_NODE_NAME`, or the
Kubernetes master by default. Its data is in `/opt/gitlab-postgresql/data` of that node.

## GitLab 19

When `CP_GITLAB_VERSION` is `19`, the installer also does the following.

* **It deploys `cp-gitlab-db`**, with its backup worker `cp-bkp-worker-cp-gitlab-db`, which dumps the GitLab database
  with `pg_dump` and copies the dump to the backup storages. For `9`, `cp-gitlab-db` is not deployed.

* **It generates the GitLab `root` password**, if `GITLAB_ROOT_PASSWORD` has its default value `Passw0rd`. GitLab
  rejects that password as too weak. The installer writes the new password to `GITLAB_ROOT_PASSWORD` in the install
  config and in the `cp-config-global` ConfigMap. Read it there to sign in as `root` at
  `https://<GitLab host>:<CP_GITLAB_EXTERNAL_PORT>/users/sign_in?auto_sign_in=false`, the "Login/Pass Auth" line of
  the install summary. To choose the password yourself, set `GITLAB_ROOT_PASSWORD`.

* **It creates its tokens with an expiry date, 364 days ahead.** These are the personal access token of `root`, which
  only the installer uses, and the impersonation token of `root`, which becomes the **`git.token`** preference. GitLab
  19.5 does not create a token without an expiry date, and accepts at most 365 days. One day less leaves a margin for
  a time zone difference. The platform rotates **`git.token`** before it expires: see the **`git.token.rotation.*`**
  preferences in [Manage system-level settings](../../manual/12_Manage_Settings/12.10._Manage_system-level_settings.md#git).
  The personal access token of `root` is not rotated. GitLab 19 has no session API, so the installer creates the
  token of `root` with `gitlab-rails runner` inside the `cp-git` pod.

* **It creates the `amcheck` extension** in the GitLab database, as the PostgreSQL superuser. GitLab 18.4 and later
  require it. GitLab creates it only in the PostgreSQL bundled into its image, and the platform does not use that one.
  If this fails, `pipectl` prints a warning and goes on. Then create it by hand, in the database named by
  `GITLAB_DATABASE_DATABASE` (`gitlabhq_production` by default):

        kubectl exec "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o jsonpath='{.items[0].metadata.name}')" -- \
            psql -U postgres -d gitlabhq_production -c "CREATE EXTENSION IF NOT EXISTS amcheck;"

* **It sets the `git.gitlab.api.version` preference to `v4`.** GitLab 11.0 and later have no API v3. For `9`, the
  preference keeps its default `v3`.

* **It lets the GitLab webhooks call services inside the cluster** (`allow_local_requests_from_web_hooks_and_services`):
  the `elasticsearch-agent` indexes the repositories through them. It also turns off the sign-up of new users in
  GitLab (`signup_enabled`).

## The GitLab 19 image

* It is built on `gitlab/gitlab-ce:19.5.3-ce.0`.
* It has no GitLab runner. The `9` image still has it.
* When the LFS object storage is on (`CP_GITLAB_OBJ_STORE_ENABLED=true`, AWS only; it is off by default), its region
  is `CP_GITLAB_OBJ_STORE_REGION`, or the region of the deployment (`CP_CLOUD_REGION_ID`) when that is not set.

## Resources

On every GitLab version:

* `cp-git` mounts a memory `emptyDir` at `/dev/shm`, with a 1 GiB size limit: GitLab needs at least 256 MB, and the
  container default is 64 MB. On Kubernetes older than 1.22 the limit does not set the size of the file system: the
  kubelet evicts the pod when it uses more than 1 GiB.
* `cp-gitlab-db` runs with `max_locks_per_transaction=128`. With the PostgreSQL default of 64, loading the GitLab 19
  schema fails with "out of shared memory".

GitLab recommends 8 vCPU and 16 GB of RAM for the GitLab container. The `cp-git` deployment sets no CPU or memory
requests or limits, so pick a node with this much free CPU and memory.

## An existing deployment

`pipectl` does not upgrade GitLab. GitLab must pass through a list of required versions, one at a time, and its
database must be upgraded to new PostgreSQL major versions between them. For example, the GitLab 19.5 image does not
start on data older than GitLab 19.2. So do not change `CP_GITLAB_VERSION` of an existing deployment and re-deploy
`cp-git`. To move a deployment from GitLab 9.4 to 19.5, follow [Upgrade GitLab 9.4 to 19.5](upgrade_gitlab_9_to_19.md).

Without the `-e` (`--erase-data`) option, a re-deploy of `cp-git` keeps the existing GitLab data. On `19`, before such
a re-deploy:

* Set the real `root` password in `GITLAB_ROOT_PASSWORD`. If the value is still `Passw0rd`, the installer writes a new
  random password to the install config, but GitLab sets the `root` password only on an empty database. The
  installer then uses the wrong password to push the sources of the pipelines it registers, such as the data
  transfer pipeline.
* Plan to revoke the old tokens. The re-deploy creates a new `git.token`, with an expiry date, and does not revoke the
  old one. Every install names this impersonation token of `root` `CloudPipeline`. After the re-deploy, revoke the
  older `CloudPipeline` token in GitLab: it has an earlier expiry date, or none if it was created on GitLab 9. If an
  older personal access token `CloudPipelineRootToken` of `root` exists, revoke it too.
