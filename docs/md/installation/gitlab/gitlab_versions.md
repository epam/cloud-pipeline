# GitLab versions

`pipectl` installs GitLab CE as the `cp-git` service, with its own PostgreSQL database, `cp-gitlab-db`. Two values of
the install config select their versions:

* `CP_GITLAB_VERSION`: the GitLab version. It also selects the `cp-git` image,
  `lifescience/cloud-pipeline:git-<CP_GITLAB_VERSION>-<version>`.
* `GITLAB_DATABASE_VERSION`: the tag of the `postgres` image of `cp-gitlab-db`.

| `CP_GITLAB_VERSION` | GitLab CE | `GITLAB_DATABASE_VERSION` |
|---|---|---|
| `9` (the default) | 9.4.0 | `9.6` (the default) |
| `15` | 15.5.4 | `12.4` |
| `17` | 17.4.1 | `14.11` |
| `19` | 19.4.1 | `17`, or a `17.x` tag |

Take both values from one row. If they do not match, `pipectl` prints a warning and goes on, and the install may fail.
For `19` it checks only the PostgreSQL major version. The `17` tag gives the latest PostgreSQL 17 release at the time
the node first pulls the image. To pin the version, set a full `17.x` tag.

`19` is the latest GitLab version the platform supports.

Set the values in the install config, or pass them to `pipectl install` with `-env`. A `-env` value overrides the
install config:

```bash
~/.pipe/pipectl install \
    -env CP_GITLAB_VERSION=19 \
    -env GITLAB_DATABASE_VERSION=17 \
    ...
```

## GitLab 17 and later

When `CP_GITLAB_VERSION` is `17` or later, the installer also does the following.

* **It generates the GitLab `root` password**, if `GITLAB_ROOT_PASSWORD` has its default value `Passw0rd`. GitLab
  rejects that password as too weak. The installer writes the new password to `GITLAB_ROOT_PASSWORD` in the install
  config and in the `cp-config-global` ConfigMap. Read it there to sign in as `root` at
  `https://<GitLab host>:<CP_GITLAB_EXTERNAL_PORT>/users/sign_in?auto_sign_in=false`, the "Login/Pass Auth" line of
  the install summary. To choose the password yourself, set `GITLAB_ROOT_PASSWORD`.

* **It creates its tokens with an expiry date, 364 days ahead.** These are the personal access token of `root`, which
  only the installer uses, and the impersonation token of `root`, which becomes the **`git.token`** preference. GitLab
  19.4 does not create a token without an expiry date, and accepts at most 365 days. One day less leaves a margin for
  a time zone difference. The platform rotates **`git.token`** before it expires: see the **`git.token.rotation.*`**
  preferences in [Manage system-level settings](../../manual/12_Manage_Settings/12.10._Manage_system-level_settings.md#git).
  The personal access token of `root` is not rotated.

* **It creates the `amcheck` extension** in the GitLab database, as the PostgreSQL superuser. GitLab 18.4 and later
  require it. GitLab creates it only in the PostgreSQL bundled into its image, and the platform does not use that one.
  The installer creates it for `17` too, so that a later upgrade finds it. If this fails, `pipectl` prints a warning
  and goes on. Then create it by hand, in the database named by `GITLAB_DATABASE_DATABASE` (`gitlabhq_production` by
  default):

        kubectl exec "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o jsonpath='{.items[0].metadata.name}')" -- \
            psql -U postgres -d gitlabhq_production -c "CREATE EXTENSION IF NOT EXISTS amcheck;"

* **It turns on the `git.gitlab.hashed.repo.support` preference.** From GitLab 14.0 every repository is in hashed
  storage, and `git-reader` finds a repository there only with this preference on. On `15` the installer does not set
  it: turn it on by hand after the install (**Settings** > **Preferences** > **Git**).

On every version except `9`, the installer also lets the GitLab webhooks call services inside the cluster
(`allow_local_requests_from_web_hooks_and_services`): the `elasticsearch-agent` indexes the repositories through
them. It also turns off the sign-up of new users in GitLab (`signup_enabled`).

## The GitLab 19 image

* It is built on `gitlab/gitlab-ce:19.4.1-ce.0`.
* It has no GitLab runner. The `9`, `15` and `17` images still have it.
* When the LFS object storage is on (`CP_GITLAB_OBJ_STORE_ENABLED=true`, AWS only; it is off by default), its region
  is `CP_GITLAB_OBJ_STORE_REGION`, or the region of the deployment (`CP_CLOUD_REGION_ID`) when that is not set. The
  `9`, `15` and `17` images always use `us-east-1`.

## Resources

On every GitLab version:

* `cp-git` mounts a memory `emptyDir` at `/dev/shm`, with a 1 GiB size limit: GitLab needs at least 256 MB, and the
  container default is 64 MB. On Kubernetes older than 1.22 the limit does not set the size of the file system: the
  kubelet evicts the pod when it uses more than 1 GiB (see
  [Known risks and limits](upgrade_gitlab_15_to_19.md#known-risks-and-limits)).
* `cp-gitlab-db` runs with `max_locks_per_transaction=128`. With the PostgreSQL default of 64, loading the GitLab 19
  schema fails with "out of shared memory".

GitLab recommends 8 vCPU and 16 GB of RAM for the GitLab container. The `cp-git` deployment sets no CPU or memory
requests or limits, so pick a node with this much free CPU and memory.

## An existing deployment

`pipectl` does not upgrade GitLab. GitLab must pass through a list of required versions, one at a time, and its
database must be upgraded to new PostgreSQL major versions between them. For example, the GitLab 19.4 image does not
start on data older than GitLab 19.2. So do not change `CP_GITLAB_VERSION` of an existing deployment and re-deploy
`cp-git`. To move a deployment from GitLab 15.5 to 19.4, follow [Upgrade GitLab 15.5 to 19.4](upgrade_gitlab_15_to_19.md).

Without the `-e` (`--erase-data`) option, a re-deploy of `cp-git` keeps the existing GitLab data. On `17` or later,
before such a re-deploy:

* Set the real `root` password in `GITLAB_ROOT_PASSWORD`. If the value is still `Passw0rd`, the installer writes a new
  random password to the install config, but GitLab sets the `root` password only on an empty database. The
  installer then uses the wrong password to push the sources of the pipelines it registers, such as the data
  transfer and system jobs pipelines.
* Plan to revoke the old tokens. The re-deploy creates a new `git.token`, with an expiry date, and does not revoke the
  old one. Every install names this impersonation token of `root` `CloudPipeline`. After the re-deploy, revoke the
  older `CloudPipeline` token in GitLab: it has an earlier expiry date, or none if it was created on GitLab 15 or
  earlier. If an older personal access token `CloudPipelineRootToken` of `root` exists, revoke it too.
