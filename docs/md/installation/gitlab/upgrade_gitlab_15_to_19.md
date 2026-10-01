# Upgrade GitLab 15.5 to 19.4

This page moves an existing Cloud Pipeline deployment from GitLab CE 15.5.4 (`CP_GITLAB_VERSION=15`, PostgreSQL 12)
to GitLab CE 19.4.1 (`CP_GITLAB_VERSION=19`, PostgreSQL 17), and keeps all its repositories, users and tokens.

`pipectl` cannot do this upgrade: it deletes and re-creates the `cp-git` deployment. GitLab must pass through a
list of required versions ("stops"), one at a time. The external PostgreSQL database of GitLab (`cp-gitlab-db`)
must be upgraded three times on the way, from 12 to 17. So this is a manual procedure. An administrator with root
access to the cluster runs it.

**Rehearse it first.** Run the whole procedure on a copy of `/opt/gitlab` and `/opt/gitlab-postgresql`, in an
isolated cluster, on the same storage type as the real deployment. The copy holds the real SMTP, webhook and SAML
settings: the isolated cluster must not reach the real SMTP server, the platform services or the IdP. Write down how
long each step takes: this gives the length of the maintenance window. Use the same image tags in the rehearsal and
in the real run.

**Do not run `pipectl` for `cp-git` or `cp-gitlab-db`** from the start of the procedure until the install config has the
new values (see [Switch to the GitLab 19 image](#switch-to-the-gitlab-19-image)).

## The path

GitLab must stop at each of these versions, in this order. At each stop GitLab starts, updates its database, and
finishes its background migrations before it moves on. Only the start and the end use a Cloud Pipeline `cp-git`
image. The stops in between use the plain `gitlab/gitlab-ce:<version>-ce.0` images from Docker Hub.

| Label | Image | PostgreSQL while it runs | After the stop |
|---|---|---|---|
| `00-freeze` | the current `cp-git` image (15.5.4) | 12.4, then the latest 12.x | freeze, back up, prepare |
| `01-15.11.13` | `gitlab/gitlab-ce:15.11.13-ce.0` | 12 | upgrade PostgreSQL 12 to 14 |
| `01a-16.0.10` | `gitlab/gitlab-ce:16.0.10-ce.0` | 14 | only if the checks require it |
| `01b-16.1.8` | `gitlab/gitlab-ce:16.1.8-ce.0` | 14 | only if the checks require it |
| `01c-16.2.11` | `gitlab/gitlab-ce:16.2.11-ce.0` | 14 | only if the checks require it |
| `02-16.3.9` | `gitlab/gitlab-ce:16.3.9-ce.0` | 14 | |
| `03-16.7.10` | `gitlab/gitlab-ce:16.7.10-ce.0` | 14 | |
| `04-16.11.10` | `gitlab/gitlab-ce:16.11.10-ce.0` | 14 | |
| `05-17.1.8` | `gitlab/gitlab-ce:17.1.8-ce.0` | 14 | |
| `06-17.3.7` | `gitlab/gitlab-ce:17.3.7-ce.0` | 14 | |
| `07-17.5.5` | `gitlab/gitlab-ce:17.5.5-ce.0` | 14 | |
| `08-17.8.7` | `gitlab/gitlab-ce:17.8.7-ce.0` | 14 | |
| `09-17.11.7` | `gitlab/gitlab-ce:17.11.7-ce.0` | 14 | upgrade PostgreSQL 14 to 16 |
| `10-18.2.8` | `gitlab/gitlab-ce:18.2.8-ce.0` | 16 | |
| `11-18.5.7` | `gitlab/gitlab-ce:18.5.7-ce.0` | 16 | |
| `12-18.8.11` | `gitlab/gitlab-ce:18.8.11-ce.0` | 16 | |
| `13-18.11.12` | `gitlab/gitlab-ce:18.11.12-ce.0` | 16 | upgrade PostgreSQL 16 to 17 |
| `14-19.2.7` | `gitlab/gitlab-ce:19.2.7-ce.0` | 17 | |
| `15-19.4.1` | `gitlab/gitlab-ce:19.4.1-ce.0` | 17 | |
| `16-git-19` | `lifescience/cloud-pipeline:git-19-<version>` | 17 | switch to the Cloud Pipeline image |

Each PostgreSQL upgrade happens at a stop that supports both the old and the new major version. No GitLab version
supports both 12 and 16, so the database cannot move in one step.

The path is from the GitLab [upgrade path tool](https://gitlab-com.gitlab.io/support/toolbox/upgrade-path/) and
[upgrade paths](https://docs.gitlab.com/update/upgrade_paths/) page, for a target of 19.4.1. GitLab 19.5 is a
required stop: if the target is later than 19.4, check the path again and add 19.5.

Why the stops matter for this deployment:

* **15.11.13**: the first plain GitLab image. It reads the hand-edited `/etc/gitlab/gitlab.rb` (see
  [Edit gitlab.rb](#3-edit-gitlabrb)). PostgreSQL 12 must be 12.10 or later here.
* **The first 16.x stop** (16.3.9, or a conditional one before it): PostgreSQL 12 is no longer supported. GitLab now
  opens a second (`ci`) database connection pool, which doubles its database connections.
* **17.1.8**: the first 17.x. `omnibus_gitconfig` in `gitlab.rb` now stops reconfigure with an error.
* **17.8.7**: the first stop with OpenSSL 3: TLS 1.2 or later, and RSA keys of at least 2048 bits.
* **18.2.8**: the first 18.x. PostgreSQL 16.5 or later is required. In GitLab CE, deleting a project or a group now
  only marks it for deletion.
* **18.5.7**: from 18.4 GitLab requires the `amcheck` extension in its database. The procedure creates it before the
  first stop.
* **18.8.11**: every batched background migration must finish here, before 18.9 or later.
* **18.11.12**: this version upgrades a bundled PostgreSQL to 17 on its own. The bundled PostgreSQL is disabled
  before the first stop, so this does not happen.
* **19.2.7**: the first 19.x. PostgreSQL 17 is required.

Every image checks the version of the data it starts on, and stops when a required stop was skipped. For example,
19.2.7 needs 18.11 or later, and 19.4.1 needs 19.2 or later.

## Known risks and limits

* **Storage.** The [Gitaly](https://docs.gitlab.com/administration/gitaly/) page says: "For repository data, only
  local storage is supported for Gitaly and Gitaly Cluster (Praefect) for performance and consistency reasons.
  Alternatives such as NFS or cloud-based file systems are not supported." When `/opt/gitlab` and
  `/opt/gitlab-postgresql` are on Amazon FSx for Lustre, the deployment is outside GitLab support on 15.5 already.
  The upgrade adds a heavy write load (database migrations, three PostgreSQL upgrades). The mitigations are the FSx
  checks, a copy of the data off FSx, PostgreSQL dump and restore instead of `pg_upgrade --link`, and the integrity
  checks after the stops. Moving the repositories and the database to local disks (Amazon EBS) removes the risk. That
  is a separate decision.
* **Resources.** GitLab recommends 8 vCPU and 16 GB of RAM for the GitLab container (8 GB at the least), besides what
  else runs on the node. The `cp-git` deployment sets no CPU or memory requests or limits. Migrations run slower on a
  smaller node. Plan a node resize separately, if needed.
* **Database settings.** For an external PostgreSQL, GitLab asks for at least `shared_buffers` 2 GB, `work_mem` 8 MB,
  `maintenance_work_mem` 64 MB, `max_connections` 400, and a `statement_timeout` of 15 to 60 seconds
  ([required settings](https://docs.gitlab.com/administration/postgresql/tune/#required-settings-for-external-instances)).
  `cp-gitlab-db` sets `shared_buffers` from `GITLAB_DATABASE_SHARED_BUFFERS` (128MB by default) and leaves the rest at
  the PostgreSQL defaults. The procedure sets `max_connections` to 400. Raise `shared_buffers` if the node has the
  RAM for it: the rehearsal shows the effect. `work_mem` and `statement_timeout` are not set: the deployment spec
  does not set them. Do not set `statement_timeout` during the procedure: its restore, vacuum and check commands run
  statements longer than a minute.
* **`/dev/shm`.** GitLab needs at least 256 MB of `/dev/shm`, and the container default is 64 MB. The procedure mounts
  a 1 GiB memory `emptyDir`, as the current `cp-git-dpl.yaml` does. On Kubernetes older than 1.22 `sizeLimit` does
  not set the size of that memory file system (it is half of the node RAM), but the kubelet evicts the pod when it
  uses more than 1 GiB. An idle GitLab 19.4 uses about 11 MB.
* **Tokens.** The upgrade does not add an expiry date to an existing token that has none: every stop of the path is a
  patch release at or after the ones that stopped doing so (see
  [Non-expiring access tokens](https://docs.gitlab.com/update/deprecations/#non-expiring-access-tokens)). So
  `git.token` keeps working after the upgrade. On GitLab 19.4 a new token must have an expiry date, at most 365 days
  ahead. The platform rotates `git.token` before it expires (the `git.token.rotation.*` preferences). A token without
  an expiry date is never rotated.

## Before you start

### What you need

* A root `bash` shell on the node that runs `cp-git` and `cp-gitlab-db`, with `kubectl` configured for the cluster,
  and `git`, `curl`, `openssl` and `tar`. If the two deployments run on different nodes, run the steps on
  `/opt/gitlab` on the `cp-git` node and the steps on `/opt/gitlab-postgresql` on the `cp-gitlab-db` node. Only
  administrators may have access to this node: some commands pass `git.token` on their command line.
* The AWS CLI, with read access to Amazon FSx, for the FSx checks.
* A backup directory **off** the file system that holds `/opt/gitlab` and `/opt/gitlab-postgresql`: for example an
  Amazon EBS volume mounted on the node. See [Space](#space) for its size. It will hold secrets
  (`gitlab-secrets.json`, the database, the SAML private key): restrict access to it.
* These values:
    * the **`git.external.url`** preference: the URL of GitLab
    * the **`git.token`** preference
    * `CP_GITLAB_INTERNAL_PORT`, `GITLAB_DATABASE_PASSWORD`, `GITLAB_DATABASE_USERNAME` and `GITLAB_DATABASE_DATABASE`
      from the install config
    * the GitLab `root` password
    * a pipeline repository to clone in the checks, as `<namespace>/<project>`
* A Cloud Pipeline build that has the GitLab 19 support, deployed to the `api` before the first stop (see the checks).
* The `lifescience/cloud-pipeline:git-19-<version>` image of the same build in the registry of the deployment.

### Session setup

Run this once in the shell that runs the procedure. Every later step uses these variables and functions. If the
session is lost, run it again, and set `DB_COLLATE` and `DB_CTYPE` again (see [Other facts](#other-facts)).

```bash
set -o pipefail
export BKP_DIR=/mnt/gitlab-upgrade          # off the file system of /opt/gitlab and /opt/gitlab-postgresql
export GIT_URL=https://git.example.com:443  # the git.external.url preference
export GIT_PORT=30080                       # CP_GITLAB_INTERNAL_PORT
export SMOKE_REPO=root/my-pipeline          # a pipeline repository, <namespace>/<project>
export DB_NAME=gitlabhq_production          # GITLAB_DATABASE_DATABASE
export DB_USER=gitlab                       # GITLAB_DATABASE_USERNAME
read -rsp 'git.token: ' GIT_TOKEN; echo
read -rsp 'GITLAB_DATABASE_PASSWORD: ' DB_PASS; echo
export GIT_TOKEN DB_PASS
mkdir -p "$BKP_DIR" && chmod 700 "$BKP_DIR"

git_pod() { kubectl get po -l cloud-pipeline/cp-git=true -o jsonpath='{.items[0].metadata.name}'; }
db_pod()  { kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o jsonpath='{.items[0].metadata.name}'; }
db_sql()  { kubectl exec "$(db_pod)" -- psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -At "$@"; }

# A few row counts and the last schema migration, to compare before and after a database move
db_sanity() {
  db_sql -c "SELECT 'projects=' || count(*) FROM projects UNION ALL SELECT 'users=' || count(*) FROM users
             UNION ALL SELECT 'namespaces=' || count(*) FROM namespaces
             UNION ALL SELECT 'schema=' || max(version) FROM schema_migrations;"
}

# Batched background migrations that are not finished (3) or finalized (6). No output: none left
bbm() {
  db_sql -c "SELECT id, status, job_class_name, table_name, column_name, job_arguments
             FROM batched_background_migrations WHERE status NOT IN (3, 6) ORDER BY id;"
}

# Stop GitLab cleanly, then scale cp-git down and wait until the pod is gone
git_stop() {
  kubectl exec "$(git_pod)" -- gitlab-ctl stop
  kubectl scale deployment cp-git --replicas=0
  while [ -n "$(kubectl get po -l cloud-pipeline/cp-git=true -o name)" ]; do sleep 5; done
}

# Start cp-git on an image, and wait until reconfigure (and so the database migrations) has finished.
# It refuses to run while a cp-git pod exists: two GitLab pods must never run on the same data
git_start() {
  [ -z "$(kubectl get po -l cloud-pipeline/cp-git=true -o name)" ] || { echo "cp-git runs: git_stop first"; return 1; }
  kubectl set image deployment/cp-git cp-git="$1"
  kubectl scale deployment cp-git --replicas=1
  until kubectl logs "$(git_pod)" 2>/dev/null | grep "gitlab Reconfigured!" > /dev/null; do sleep 30; done
  kubectl exec "$(git_pod)" -- gitlab-ctl status
}

# Scale cp-gitlab-db down and wait. The first line must print 0: no client is connected
db_stop() {
  db_sql -c "SELECT count(*) FROM pg_stat_activity WHERE backend_type = 'client backend' AND pid <> pg_backend_pid();"
  kubectl scale deployment cp-gitlab-db --replicas=0
  while [ -n "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o name)" ]; do sleep 5; done
}

# Start cp-gitlab-db on a postgres image tag, on the existing data directory
db_start() {
  [ -z "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o name)" ] || { echo "cp-gitlab-db runs: db_stop first"; return 1; }
  kubectl set image deployment/cp-gitlab-db cp-gitlab-db="postgres:$1"
  kubectl scale deployment cp-gitlab-db --replicas=1
  until kubectl exec "$(db_pod)" -- pg_isready -U postgres 2>/dev/null; do sleep 5; done
}

# Checks GitLab through the IP of its pod, so they work while the cp-git service is closed:
# the version, the git.token details (expires_at), the SAML button of the sign-in page, and a clone
smoke() {
  local url
  url="https://$(kubectl get po "$(git_pod)" -o jsonpath='{.status.podIP}'):$GIT_PORT"
  for _ in $(seq 60); do curl -ksf -o /dev/null -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/version" && break; sleep 10; done
  curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/version"; echo
  curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/personal_access_tokens/self"; echo
  echo "SAML sign-in links: $(curl -ksSf "$url/users/sign_in?auto_sign_in=false" | grep -c 'users/auth/saml')"
  ( umask 077; rm -rf /tmp/gitlab-smoke &&
    git -c http.sslVerify=false clone -q "https://root:${GIT_TOKEN}@${url#https://}/${SMOKE_REPO}.git" /tmp/gitlab-smoke &&
    git -C /tmp/gitlab-smoke log -1 --oneline
    rm -rf /tmp/gitlab-smoke )
}

# The backup of a stop: a database dump, read through to its end, the /etc/gitlab archive and the row counts.
# Run it with cp-git stopped
stop_backup() {
  mkdir -p "$BKP_DIR/$1" &&
    kubectl exec "$(db_pod)" -- pg_dump -U postgres -Fc "$DB_NAME" > "$BKP_DIR/$1/$DB_NAME.dump" &&
    kubectl exec -i "$(db_pod)" -- pg_restore -f /dev/null < "$BKP_DIR/$1/$DB_NAME.dump" &&
    tar -C /opt/gitlab -czf "$BKP_DIR/$1/gitlab-config.tgz" config &&
    db_sanity > "$BKP_DIR/$1/sanity.txt" &&
    ls -l "$BKP_DIR/$1" && echo "backup $1 OK"
}
```

`git_stop` prints an error when `cp-git` is already stopped. That is harmless.

`smoke` connects to the IP of the `cp-git` pod from the node. During the stops the `cp-git` service is closed (see
[Freeze and back up](#freeze-and-back-up)), so this is the only way in. The clone happens in a private directory,
which `smoke` removes at once.

The backup of a stop dumps the database with the `pg_dump` of `cp-gitlab-db` itself, which always matches the
server. `stop_backup` then reads the whole dump with `pg_restore`, which fails on a dump that was cut short.
`gitlab-backup create` is used only on 15.5.4 and on 19.4.1: the `pg_dump` bundled in the intermediate GitLab images
is older than the database server at several stops, and it refuses to dump a newer server. During the stops nobody
can reach GitLab through its service, so the repositories do not change, and the copy of the repositories taken at
the freeze stays valid for every stop.

### Facts to collect

Collect these facts before the rehearsal, on the running 15.5.4. Each one changes the procedure as described.

#### Repository count and size

```bash
db_sql -c "SELECT count(*), pg_size_pretty(sum(repository_size)), pg_size_pretty(sum(lfs_objects_size)),
           pg_size_pretty(sum(uploads_size)) FROM project_statistics;"
du -sh /opt/gitlab/data/git-data/repositories /opt/gitlab/data/gitlab-rails/uploads
```

Size the backup directory and the free space with it (see [Space](#space)). The time of the copy at the freeze and
of `gitlab:git:fsck` at each stop grows with it: the rehearsal measures both.

#### Database size

```bash
db_sql -c "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));"
```

Each PostgreSQL upgrade keeps the old data directory, so the file system of `/opt/gitlab-postgresql` needs free space
for three more copies of the database. The dump and restore time grows with the size: the rehearsal measures it.

#### FSx deployment type, backups, data repository and mount options

Skip this when the paths are not on FSx for Lustre.

```bash
mount -t lustre                                  # the file system name (fs-...), its mount point and options
findmnt -T /opt/gitlab/data; findmnt -T /opt/gitlab-postgresql/data
aws fsx describe-file-systems --file-system-ids <fs-id> \
    --query 'FileSystems[0].LustreConfiguration.[DeploymentType,AutomaticBackupRetentionDays,DataRepositoryConfiguration]'
aws fsx describe-data-repository-associations --filters Name=file-system-id,Values=<fs-id>
find /opt/gitlab/data/git-data /opt/gitlab/data/gitlab-rails /opt/gitlab-postgresql/data -type f -print0 |
    xargs -0 lfs hsm_state | grep -c released
```

* A `SCRATCH_*` deployment type does not replicate data. **Stop**: do not upgrade on it. Move the data to a
  `PERSISTENT_*` file system or to local disks first. That is a decision for the platform owner.
* A data repository association, or an import or export path, that covers these paths can release file contents to
  Amazon S3. Remove it, or turn off its automatic release, for these paths first. The last command must print `0`.
  It checks every file of the trees, so it takes time.
* FSx backs up only a `PERSISTENT_*` file system that is not linked to an Amazon S3 data repository. On such a file
  system the procedure also takes a manual FSx backup at the freeze, and automatic backups, if they are on, are one
  more restore point. A file system linked to S3 has no FSx backups at all: the copy off FSx is then the only full
  copy.
* Without the `flock` mount option, file locks are not shared between clients. Remount with `flock` before the
  upgrade. This needs a stop of every pod that uses the mount.
* `/opt/gitlab/config`, `/opt/gitlab/data`, `/opt/gitlab/logs`, `/opt/gitlab/pki` and `/opt/gitlab-postgresql/data`
  must not be mount points themselves: the procedure renames them. If one is, adapt the `mv` steps.

#### The bundled PostgreSQL inside cp-git

```bash
kubectl exec "$(git_pod)" -- gitlab-ctl status
grep -n "db_host" /opt/gitlab/config/gitlab.rb
db_sql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB_NAME';"
```

* `run: postgresql` in the first output is expected: the bundled PostgreSQL runs idle, next to `cp-gitlab-db`. The
  `gitlab.rb` edit before the first stop disables it. Otherwise the 15.11 and 16.11 images would try to upgrade it at
  start, and the 19.x image would start PostgreSQL 17 on its old data.
* `db_host` must be the `cp-gitlab-db` service, and the last query must count GitLab's connections (more than 0). If
  GitLab uses the bundled PostgreSQL instead, **stop**: this procedure does not apply.

#### Local requests from webhooks, and sign-up

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/application/settings" |
    grep -o -E '"(allow_local_requests_from_web_hooks_and_services|signup_enabled)":[a-z]+'
```

* `allow_local_requests_from_web_hooks_and_services` should be `true`: the webhooks of pipeline repositories call the
  `elasticsearch-agent` inside the cluster. If it is `true`, check after the upgrade that it still is. If it is
  `false`, repository indexing through webhooks does not work today either. Leave it as it is, and raise it with the
  platform owner: the upgrade does not change it.
* `signup_enabled` is set to `false` after the upgrade.

#### Other facts

Record the rest in the same session. The procedure uses them.

```bash
# Where the pods run, what the node has, and the container runtime
kubectl get po -o wide | grep -E '^cp-git|^cp-gitlab'
kubectl describe node <node> | grep -A 6 -E '^(Capacity|Allocatable)'
docker version --format '{{.Server.Version}}'
df -h "$(docker info --format '{{.DockerRootDir}}')"

# The deployments the procedure scales down, and the current images
kubectl get deploy cp-git cp-gitlab-db cp-git-sync cp-gitlab-reader cp-bkp-worker-cp-git cp-bkp-worker-cp-gitlab-db
kubectl get deploy cp-git cp-gitlab-db -o jsonpath='{range .items[*]}{.metadata.name} {.spec.template.spec.containers[0].image} {.spec.template.spec.containers[0].args}{"\n"}{end}'

# Database roles, encoding, locale and database-level settings
db_sql -c "SELECT rolname FROM pg_roles WHERE rolname !~ '^pg_' ORDER BY 1;"
db_sql -c "SELECT datname, pg_encoding_to_char(encoding), datcollate, datctype FROM pg_database WHERE datname = '$DB_NAME';"
db_sql -c "SELECT count(*) FROM pg_db_role_setting;"
DB_COLLATE=$(db_sql -c "SELECT datcollate FROM pg_database WHERE datname = '$DB_NAME';")
DB_CTYPE=$(db_sql -c "SELECT datctype FROM pg_database WHERE datname = '$DB_NAME';")
export DB_COLLATE DB_CTYPE

# Object storage for LFS
grep -n "object_store" /opt/gitlab/config/gitlab.rb
db_sql -c "SELECT file_store, count(*) FROM lfs_objects GROUP BY 1;"
```

* Each GitLab image is 1.4 to 1.5 GB to download and 3.5 to 5.5 GB on disk. The Docker disk needs room for two stop
  images at a time.
* From 16.x the GitLab images are based on Ubuntu 22.04, and from 18.x on 24.04. With Docker's default seccomp
  profile they need Docker 20.10.10 or later. By default Kubernetes runs pods without a seccomp profile, unless an
  annotation, a pod security policy or the kubelet `--seccomp-default` option sets one. So an older Docker is a
  finding for the rehearsal, not a blocker.
* The roles must be `postgres` and the GitLab user only, with no row in `pg_db_role_setting`. The PostgreSQL upgrade
  re-creates exactly these. Re-create any other role or database setting by hand after each restore.
* If `lfs_objects` has rows with `file_store = 2`, LFS objects are in object storage. The current `gitlab.rb` sets the
  region `us-east-1`. The GitLab 19 image sets `CP_GITLAB_OBJ_STORE_REGION`, or `CP_CLOUD_REGION_ID` when that is not
  set. Check the bucket region (`aws s3api get-bucket-location --bucket <bucket>`). If it is not that value, set
  `CP_GITLAB_OBJ_STORE_REGION` in the `cp-config-global` ConfigMap and the install config before the switch to the
  GitLab 19 image.

### Checks that must pass

Run these before the freeze. Each one must pass, or be fixed first.

**The version is 15.5.4, and `smoke` works.** `smoke` must print the version, the token, at least one SAML sign-in
link, and the last commit of the pipeline repository. It also proves that the node reaches the pod IP.

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/version"
smoke
```

**No background migration is pending.** `bbm` prints nothing, and this prints `0`:

```bash
bbm
kubectl exec "$(git_pod)" -- gitlab-rails runner -e production 'puts Gitlab::BackgroundMigration.remaining'
```

**The `api` has the GitLab 19 support.** The version of the `api` is `0.16.0.<build>.<commit>`. In a checkout of the
Cloud Pipeline repository, the second command lists the GitLab fixes up to that commit: token rotation, the release
description, the project deletion, the temporary fork group, the missing tree path, the fork namespace, the token
expiry cap, the version check and the token format. The third command must return the preference.

```bash
curl -ksSf -H "Authorization: Bearer <admin JWT>" "https://<api host>/pipeline/restapi/app/info"
git log --oneline --grep 'Issue 4618' <commit> -- api core
curl -ksSf -H "Authorization: Bearer <admin JWT>" "https://<api host>/pipeline/restapi/preferences/git.token.rotation.enabled"
```

**The images are there.** Pull the first stop image on the `cp-git` node, and check the GitLab 19 image of the same
build as the `api`. The registry prefix is the one of the current `cp-git` image, and the version is the one of the
`api-srv` image:

```bash
docker pull gitlab/gitlab-ce:15.11.13-ce.0
kubectl get deploy cp-api-srv -o jsonpath='{.spec.template.spec.containers[*].image}'; echo
docker pull <registry prefix>lifescience/cloud-pipeline:git-19-<version>
```

Find the latest minor of each PostgreSQL major with the command below. At the time of writing they were 12.22, 14.24,
16.15 and 17.11. Use 14.14 or later, and 16.5 or later. Use the same tags in the rehearsal and in the real run.

```bash
for m in 12 14 16 17; do
  curl -s "https://hub.docker.com/v2/repositories/library/postgres/tags?page_size=100&name=$m." |
      grep -o "\"name\":\"$m\.[0-9]*\"" | cut -d'"' -f4 | sort -V | tail -1
done
```

**Conditional stops.** Run:

```bash
db_sql -c "SELECT 'users=' || count(*) FROM users
           UNION ALL SELECT 'ci_pipeline_variables=' || count(*) FROM ci_pipeline_variables
           UNION ALL SELECT 'npm_packages=' || count(*) FROM packages_packages WHERE package_type = 2
           UNION ALL SELECT 'ci_pipeline_messages=' || count(*) FROM ci_pipeline_messages;"
```

* `users` over 30,000: add the `16.0.10` stop.
* `ci_pipeline_variables` over 0: add the `16.0.10` and `16.2.11` stops. GitLab names no threshold for a "large
  pipeline variables history", and the platform does not use GitLab CI, so any row is unexpected.
* `npm_packages` over 0: add the `16.1.8` stop.
* `ci_pipeline_messages` over 1.5 million: the `17.1.8` stop is in the path anyway, but its migration processes about
  1.5 to 2 million rows an hour. Plan the time.

**Certificate keys.** From 17.7 GitLab uses OpenSSL 3 at security level 2. An RSA key under 2048 bits, or a
certificate signed with SHA-1, is rejected.

```bash
for f in /opt/gitlab/pki/ssl-public-cert.pem /opt/gitlab/pki/sso-public-cert.pem \
         /opt/idp/pki/idp-public-cert.pem /opt/common/pki/ca-public-cert.pem; do
  [ -f "$f" ] && echo "== $f" && openssl x509 -in "$f" -noout -text | grep -E 'Public-Key|Signature Algorithm' | sort -u
done
```

Every key must be 2048 bits or more, and no signature `sha1WithRSAEncryption`. Check the same for the services GitLab
connects to over TLS: the host of the **`git.repository.hook.url`** preference, and the SMTP server, if it uses TLS
(`openssl s_client -connect <host>:<port> -tls1_2 < /dev/null`). Replace a failing certificate before the `17.8.7`
stop. A new SAML certificate of GitLab must also be registered in `cp-idp`.

**Database connections.** GitLab 16 doubles its database connections. Record the limit and the current use:

```bash
db_sql -c "SHOW max_connections;" -c "SHOW max_locks_per_transaction;" -c "SELECT count(*) FROM pg_stat_activity;"
```

The procedure sets `max_connections` to 400, the value GitLab requires for an external database. Record the
connection count at each stop in the rehearsal. If it gets close to 400, raise the limit. The procedure also sets
`max_locks_per_transaction` to 128, the value of the bundled PostgreSQL: with the default 64, loading the GitLab 19
schema fails with "out of shared memory".

**The root password.** Sign in as `root` at `$GIT_URL/users/sign_in?auto_sign_in=false`. If the password is unknown,
or still `Passw0rd`, set a new strong one:

```bash
kubectl exec -it "$(git_pod)" -- gitlab-rake "gitlab:password:reset[root]"
```

### Space

* The backup directory: the `gitlab-backup` archive (about the size of the repositories, uploads and database), the
  copy of `/opt/gitlab` and `/opt/gitlab-postgresql/data`, and one compressed database dump per stop.
* The file system of `/opt/gitlab`: room for one `gitlab-backup` archive, before it moves to the backup directory.
* The file system of `/opt/gitlab-postgresql`: three more copies of the database.
* For a full rollback to 15.5.4: room for one more copy of both trees on their file systems. The rollback moves the
  current trees aside and unpacks the freeze copy next to them.
* For each pause (see [Run the stops](#run-the-stops)): one more copy of `/opt/gitlab/data` in the backup directory.

Enlarge them before the freeze. An FSx for Lustre file system can be enlarged while it is in use.

## Freeze and back up

### 1. Tell the users

Add a system event with the time of the window (see [Add a new system event](../../manual/12_Manage_Settings/12.1._Add_a_new_system_event.md)).
Say that pipelines and versioned storages cannot be created, changed or launched during the window, and that runs
which clone a pipeline repository or a versioned storage fail.

Tell the administrators too: the VM monitor reports `cp-git` and `cp-git-sync` as down while they are stopped. That is
expected during the window.

### 2. Close GitLab

Scale down the services that call GitLab or read its data, and the backup workers: a scheduled backup must not run in
the middle of a stop. Turn the token rotation off for the window, so that its daily check does not report GitLab as
down: set **`git.token.rotation.enabled`** to `false` (**Settings** > **Preferences** > **Git**).

```bash
mkdir -p "$BKP_DIR/00-freeze"
kubectl get deploy cp-git cp-gitlab-db -o yaml > "$BKP_DIR/00-freeze/deployments.yaml"
kubectl get svc cp-git -o yaml > "$BKP_DIR/00-freeze/service.yaml"
for d in cp-git-sync cp-gitlab-reader cp-bkp-worker-cp-git cp-bkp-worker-cp-gitlab-db; do
  kubectl scale deployment "$d" --replicas=0
done
```

Close the `cp-git` service: point it at a label that no pod has. Users (through the edge and the node port), the
`api` and the runs all reach GitLab through this service, so nobody can change a repository until it opens again.
The GitLab components inside the pod talk to each other over local sockets. Only the GitLab agent server (KAS) uses
the service address, and it logs connection errors during the window: that is harmless. The pod IP stays reachable
from inside the cluster, which `smoke` uses. `ENDPOINTS` must be `<none>`:

```bash
kubectl patch svc cp-git -p '{"spec": {"selector": {"cloud-pipeline/cp-git": "closed-for-upgrade"}}}'
kubectl get endpoints cp-git
```

Stop the GitLab web and background services too. Gitaly and Redis still run for the backup:

```bash
kubectl exec "$(git_pod)" -- gitlab-ctl stop puma
kubectl exec "$(git_pod)" -- gitlab-ctl stop sidekiq
```

### 3. Back up

```bash
# GitLab backup: repositories, uploads and database, on 15.5.4
kubectl exec "$(git_pod)" -- gitlab-backup create
ls -t /opt/gitlab/data/backups/*_gitlab_backup.tar | head -1
mv "$(ls -t /opt/gitlab/data/backups/*_gitlab_backup.tar | head -1)" "$BKP_DIR/00-freeze/"

# Stop GitLab, then dump the whole PostgreSQL server, and take the same backup as after every stop
git_stop
kubectl exec "$(db_pod)" -- pg_dumpall -U postgres | gzip > "$BKP_DIR/00-freeze/pg_dumpall.sql.gz"
gzip -dc "$BKP_DIR/00-freeze/pg_dumpall.sql.gz" | tail -5 | grep -c "database cluster dump complete"
stop_backup 00-freeze

# Stop the database, and copy both data trees off the file system
db_stop
tar -C /opt --numeric-owner -cpf "$BKP_DIR/00-freeze/opt-trees.tar" --exclude=gitlab/data/backups \
    gitlab/config gitlab/data gitlab/pki gitlab-postgresql/data
```

The `grep -c` must print `1`: `pg_dumpall` writes that line last, so a dump that was cut short does not have it.
`gitlab-config.tgz` holds `/etc/gitlab`, with `gitlab.rb` and `gitlab-secrets.json`. Without `gitlab-secrets.json`
no backup can be restored. `tar` warns that it ignores sockets: that is expected.

If the file system supports FSx backups (see the facts), take a manual one now, while both deployments are stopped:

```bash
aws fsx create-backup --file-system-id <fs-id> --tags Key=Name,Value=gitlab-upgrade-00-freeze
```

## Prepare

### 1. PostgreSQL: the latest 12.x, the new limits and amcheck

`cp-gitlab-db` is stopped. Set its arguments. For `--shared_buffers` use the current value, or a larger one (see
[Known risks and limits](#known-risks-and-limits)), and set the same value in `GITLAB_DATABASE_SHARED_BUFFERS` of the
install config. Then start the latest 12.x on the same data directory. A minor version runs on the same data files,
so this is an image change only.

```bash
kubectl patch deployment cp-gitlab-db --type=json -p '[{"op": "replace",
    "path": "/spec/template/spec/containers/0/args",
    "value": ["--shared_buffers=<shared_buffers>", "--max_connections=400", "--max_locks_per_transaction=128"]}]'
db_start 12.22
db_sql -c "SELECT version();" -c "SHOW shared_buffers;" -c "SHOW max_connections;" -c "SHOW max_locks_per_transaction;"
```

The 12.4 image runs on Debian 10, and the latest 12.x image on Debian 12. The C library of the new image sorts some
text in a different order, so the text indexes must be rebuilt. Then compare the row counts.

GitLab 18.4 and later require the `amcheck` extension in their database, and do not create it. Create it now: the
dumps carry it through the PostgreSQL upgrades, and `pg_amcheck` uses it too.

```bash
db_sql -c "REINDEX DATABASE $DB_NAME;"
diff "$BKP_DIR/00-freeze/sanity.txt" <(db_sanity) && echo "same"
db_sql -c "CREATE EXTENSION IF NOT EXISTS amcheck;"
```

In the install config set `GITLAB_DATABASE_VERSION=12.22` (the tag you used) and `GITLAB_DATABASE_MAX_CONNECTIONS=400`.

### 2. The cp-git deployment

`cp-git` is stopped. Add the 1 GiB `/dev/shm`, if the deployment does not have it yet (the first command lists the
volumes). Set the image pull policy to `IfNotPresent`: the stop images are pulled ahead, and a start must not fail
when Docker Hub is slow or not reachable.

```bash
kubectl get deploy cp-git -o jsonpath='{.spec.template.spec.volumes[*].name}'; echo
kubectl patch deployment cp-git --type=json -p '[
  {"op": "add", "path": "/spec/template/spec/volumes/-",
   "value": {"name": "git-dshm", "emptyDir": {"medium": "Memory", "sizeLimit": "1Gi"}}},
  {"op": "add", "path": "/spec/template/spec/containers/0/volumeMounts/-",
   "value": {"name": "git-dshm", "mountPath": "/dev/shm"}},
  {"op": "replace", "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "IfNotPresent"}]'
```

Change the deployment only while `cp-git` is scaled to 0, as here and in every step below. Then Kubernetes never runs
an old and a new GitLab pod on the same data at the same time.

### 3. Edit gitlab.rb

The Cloud Pipeline 15 image writes `/etc/gitlab/gitlab.rb` again from the environment at every start. The plain GitLab
images of the stops do not: they use the file as it is. So edit it once, now, on the node
(`/opt/gitlab/config/gitlab.rb`), and do not start the Cloud Pipeline 15 image again, except for a rollback.

* Remove `omnibus_gitconfig`: GitLab 17.0 stops reconfigure on it, and 18.2 no longer knows it.
* Set its `pack.packSizeLimit` through Gitaly. Its `pack.windowMemory` cannot be set: Gitaly always uses 100m, so the
  old value never applied.
* Disable the bundled PostgreSQL.

First look at the current lines. Note the `packSizeLimit` value of the `omnibus_gitconfig` line. There must be no
`gitaly[...]` or `postgresql[...]` line: if there is one, merge it by hand.

```bash
grep -n -E "^omnibus_gitconfig|^gitaly\[|^postgresql\[" /opt/gitlab/config/gitlab.rb
```

Then edit the file. Put the noted value in place of `512m`. The last command must show no `omnibus_gitconfig`.

```bash
sed -i '/^omnibus_gitconfig\[/d' /opt/gitlab/config/gitlab.rb
cat >> /opt/gitlab/config/gitlab.rb <<'EOF'

# GitLab uses cp-gitlab-db, not the bundled PostgreSQL
postgresql['enable'] = false
# Replaces omnibus_gitconfig, which GitLab 17.0 and later reject
gitaly['configuration'] = {
  git: {
    config: [
      { key: 'pack.packSizeLimit', value: '512m' }
    ]
  }
}
EOF
grep -n -E "omnibus_gitconfig|^postgresql\[|packSizeLimit" /opt/gitlab/config/gitlab.rb
```

## Run the stops

Run [One stop](#one-stop) for each line of [the path](#the-path), from `01-15.11.13` to `15-19.4.1`. After
`01-15.11.13`, `09-17.11.7` and `13-18.11.12`, run [Upgrade PostgreSQL](#upgrade-postgresql) before the next stop.

The procedure can pause after the backup of any stop. To give GitLab back to the users in a pause, start the same
stop again, open the `cp-git` service, scale the services of the freeze back up, and turn the token rotation back on
(see [After the upgrade](#after-the-upgrade)). Users then change the repositories, so the backups of the earlier
stops no longer match them. Before you go on, freeze again (steps 1 and 2 of [Freeze and back up](#freeze-and-back-up)),
but skip the two `kubectl get ... -o yaml` lines: a full rollback reads the files of `00-freeze`. Then stop GitLab
with `git_stop`, take the backup of that stop again, and copy the repositories again:

```bash
tar -C /opt --numeric-owner -cpf "$BKP_DIR/$LABEL/opt-gitlab-data.tar" --exclude=gitlab/data/backups gitlab/data
```

A rollback then goes back no further than the pause, with this copy of the repositories. A rollback to 15.5.4 loses
the changes of the pause.

### One stop

Set the stop, and start it. Watch the pod in a second shell: `kubectl get po -l cloud-pipeline/cp-git=true -w` and
`kubectl logs -f <pod>`. The database migrations run inside reconfigure, so it can take long.

```bash
STOP=16.3.9 LABEL=02-16.3.9
git_start "gitlab/gitlab-ce:$STOP-ce.0"
```

Read the end of the reconfigure output in the log: it lists the deprecated settings. A removed setting stops
reconfigure with an error, and the pod restarts (see [Troubleshooting](#troubleshooting)).

Wait for the background migrations. `bbm` must print nothing. A row with status `4` is a failed migration: see
[Troubleshooting](#troubleshooting). On the stops up to `10-18.2.8`, the second command must also print `0` (later
versions do not have it). From 18.5, `gitlab-rake gitlab:background_migrations:status` (18.5 to 18.8) or `...:list`
(18.9 and later) shows the same, with the progress.

```bash
while [ -n "$(bbm)" ]; do bbm; echo; sleep 60; done
kubectl exec "$(git_pod)" -- gitlab-rails runner -e production 'puts Gitlab::BackgroundMigration.remaining'
```

Check that GitLab works:

```bash
smoke
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:git:fsck
db_sql -c "SELECT count(*) FROM pg_stat_activity;"
```

* `smoke` prints the version of the stop, the `git.token` details, the SAML sign-in links, and the last commit of the
  pipeline repository. `expires_at` of `git.token` must stay `null`, and the SAML count must be at least 1. The count
  only shows that GitLab loaded the SAML settings, not that a sign-in works. A real SAML sign-in needs the service
  open, so it is checked after the upgrade. The certificate checks before the freeze cover the OpenSSL 3 change of
  17.8.7. If the SAML sign-in fails after the upgrade, fix the certificates or the settings on 19.4.1: it is no reason
  to roll back.
* `gitlab:git:fsck` must report no error. It checks every repository, so it takes time: the rehearsal shows how much.
* Record the connection count, and compare it with `max_connections`.

Stop GitLab and back up the stop:

```bash
git_stop
stop_backup "$LABEL"
```

While the migrations run, pull the image of the next stop on the node (`docker pull gitlab/gitlab-ce:<next>-ce.0`).
After a stop, remove the image of the stop before it (`docker rmi gitlab/gitlab-ce:<previous>-ce.0`). Keep the
current `cp-git` image of 15.5.4 until the upgrade is done: a full rollback needs it.

### Upgrade PostgreSQL

Run this after the backup of `01-15.11.13` (12 to 14), `09-17.11.7` (14 to 16) and `13-18.11.12` (16 to 17). `cp-git`
is stopped, and the backup of the stop is the dump to restore.

The procedure dumps the database and restores it into an empty data directory of the new version. The old data
directory stays as it is, as a fallback. Do not use `pg_upgrade --link` or `--clone` on FSx: link mode leaves no
fallback, and clone mode needs reflinks, which Lustre does not have. `pg_upgrade` in copy mode also works, but the
official `postgres` images do not have the old and the new binaries in one container.

Set the versions, the stop and its backup label, then stop the database and start the new version on an empty
directory:

```bash
OLD=12 NEW_TAG=14.24 STOP=15.11.13 LABEL=01-15.11.13
db_stop
mv /opt/gitlab-postgresql/data "/opt/gitlab-postgresql/data-pg$OLD"
mkdir /opt/gitlab-postgresql/data
kubectl set image deployment/cp-gitlab-db cp-gitlab-db="postgres:$NEW_TAG"
kubectl scale deployment cp-gitlab-db --replicas=1
until kubectl logs "$(db_pod)" 2>/dev/null | grep "PostgreSQL init process complete" > /dev/null; do sleep 5; done
until kubectl exec "$(db_pod)" -- pg_isready -U postgres 2>/dev/null; do sleep 5; done
```

`db_stop` must print `0` client connections. The image creates a new server on the empty directory, with the
`postgres` password from the deployment. Wait for "init process complete" before the next step: until then the image
runs a temporary server, which it stops.

Create the GitLab user and database as the installer does, with the encoding and locale of the old database, and
restore the dump:

```bash
kubectl exec -i "$(db_pod)" -- psql -U postgres -v ON_ERROR_STOP=1 <<EOF
CREATE ROLE $DB_USER WITH LOGIN SUPERUSER CREATEDB PASSWORD '$DB_PASS';
CREATE DATABASE $DB_NAME OWNER $DB_USER ENCODING 'UTF8' LC_COLLATE '$DB_COLLATE' LC_CTYPE '$DB_CTYPE' TEMPLATE template0;
EOF
kubectl exec -i "$(db_pod)" -- pg_restore -U postgres -d "$DB_NAME" --exit-on-error < "$BKP_DIR/$LABEL/$DB_NAME.dump"
echo "pg_restore exit $?"
```

`pg_restore` must exit with `0`. From PostgreSQL 14 a new password is stored as SCRAM, and the image accepts only
SCRAM for connections from other pods, such as GitLab. This is why the user is created with its password here, and
not copied from the old server, where it is stored as MD5.

Update the statistics, check the tables and indexes, and compare the row counts:

```bash
kubectl exec "$(db_pod)" -- vacuumdb -U postgres --all --analyze-in-stages
kubectl exec "$(db_pod)" -- pg_amcheck -U postgres --install-missing --heapallindexed "$DB_NAME"; echo "pg_amcheck exit $?"
diff "$BKP_DIR/$LABEL/sanity.txt" <(db_sanity) && echo "same"
```

`pg_amcheck` must exit with `0`, and the row counts must be the same.

In the install config set `GITLAB_DATABASE_VERSION` to the new tag. Then start the same stop again on the new
database, check it, and stop it:

```bash
git_start "gitlab/gitlab-ce:$STOP-ce.0"
smoke
git_stop
```

Then go on with the next stop: [One stop](#one-stop). The dump taken before the upgrade is also the backup of this
stop on the new version: the data is the same.

Keep every `data-pg<version>` directory until the upgrade is done and checked.

If the new version fails to start or the restore fails, go back to the old version:

```bash
kubectl scale deployment cp-gitlab-db --replicas=0
while [ -n "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o name)" ]; do sleep 5; done
mv /opt/gitlab-postgresql/data "/opt/gitlab-postgresql/data.failed-$(date +%Y%m%d%H%M)"
mv "/opt/gitlab-postgresql/data-pg$OLD" /opt/gitlab-postgresql/data
db_start <the old tag>
```

## Switch to the GitLab 19 image

After the backup of `15-19.4.1`, switch to the Cloud Pipeline image of the same GitLab version. This image writes
`gitlab.rb` again from the environment at start: the new nginx settings with the SSL ciphers, the object storage with
its region, no GitLab runner. The GitLab data is already on 19.4.1, so no migration runs.

This image comes from the registry of the deployment, so set the image pull policy back to `Always`, as
`cp-git-dpl.yaml` has it. `cp-git` is stopped after the backup of the stop.

```bash
GIT19_IMAGE=<registry prefix>lifescience/cloud-pipeline:git-19-<version>
cp -p /opt/gitlab/config/gitlab.rb "$BKP_DIR/15-19.4.1/gitlab.rb"
kubectl patch deployment cp-git --type=json -p '[{"op": "replace",
    "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "Always"}]'
git_start "$GIT19_IMAGE"
diff "$BKP_DIR/15-19.4.1/gitlab.rb" /opt/gitlab/config/gitlab.rb
```

The diff shows secrets: read it on the node only. Expect the `nginx` settings under `gitlab_rails['nginx']`, the SSL
ciphers, and more object types disabled in the object storage. The object storage region must be the region of the
bucket (see the facts).

Run the final checks:

```bash
smoke
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:git:fsck
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:uploads:check
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:lfs:check
kubectl exec "$(db_pod)" -- pg_amcheck -U postgres "$DB_NAME"; echo "pg_amcheck exit $?"
bbm
```

Update the install config, so that a later `pipectl` run uses the new versions:

* `CP_GITLAB_VERSION=19`
* `GITLAB_DATABASE_VERSION=<the 17.x tag>`
* `GITLAB_DATABASE_MAX_CONNECTIONS=400`
* `GITLAB_ROOT_PASSWORD=<the real root password>`: on GitLab 17 and later the installer replaces `Passw0rd` with a
  random password, which GitLab never applies to an existing database. The installer then clones pipeline
  repositories with the wrong password.

The pods read the same values from the `cp-config-global` ConfigMap, which `pipectl` builds from the install config.
Update it now too:

```bash
kubectl patch configmap cp-config-global --type merge \
    -p '{"data": {"CP_GITLAB_VERSION": "19", "GITLAB_DATABASE_VERSION": "<the 17.x tag>"}}'
```

A later `pipectl` re-deploy of `cp-git` creates a new `git.token` with an expiry date, and does not revoke the old
one. Revoke the old `CloudPipeline` impersonation token of `root` in GitLab after such a re-deploy.

## After the upgrade

**Open GitLab.** Point the `cp-git` service at the GitLab pod again. `ENDPOINTS` must show the pod:

```bash
kubectl patch svc cp-git -p '{"spec": {"selector": {"cloud-pipeline/cp-git": "true"}}}'
kubectl get endpoints cp-git
```

Sign in to `$GIT_URL` in a browser with **SSO Login**, as a platform user: this checks SAML through `cp-idp`. Sign in
as `root` too, and check that all migrations are finished in **Admin** > **Monitoring** > **Background migrations**.

**Services.** Scale the services of the freeze back up, and set **`git.token.rotation.enabled`** back to `true`.

```bash
for d in cp-git-sync cp-gitlab-reader cp-bkp-worker-cp-git cp-bkp-worker-cp-gitlab-db; do
  kubectl scale deployment "$d" --replicas=1
done
```

**Backup.** Take a full backup with the backup worker, and check its log. The worker is ready when it has written
`/backup.env`: it first downloads `pipe` from the `api`, which can take some minutes. The worker runs the backup in
the first pod whose name starts with `cp-git`. The new `cp-git` pod has a new name: check that the first line below
is the `cp-git` pod, not `cp-git-sync`. If it is not, the scheduled backup fails. Until the worker is fixed, change
the pod template of `cp-git` (the second block) to get another pod name, and check again.

```bash
bkp_pod() { kubectl get po -l cloud-pipeline/cp-bkp-worker=cp-git -o jsonpath='{.items[0].metadata.name}'; }
until kubectl exec "$(bkp_pod)" -- test -f /backup.env 2>/dev/null; do sleep 10; done
BKP_POD=$(bkp_pod)
kubectl get po | grep '^cp-git' | head -1
kubectl exec "$BKP_POD" -- bash /backup.sh
tail -20 /opt/bkp-worker/logs/backup-cp-git.log
```

```bash
git_stop
kubectl patch deployment cp-git -p "{\"spec\": {\"template\": {\"metadata\": {\"annotations\": {\"cloud-pipeline/restarted\": \"$(date +%s)\"}}}}}"
kubectl scale deployment cp-git --replicas=1
```

**Token.** `smoke` shows `expires_at` of `git.token`: `null` means no expiry, and the rotation has nothing to do. The
**`git.token.rotation.*`** preferences must be there, with the rotation enabled.

**Application settings.** Check them, and turn sign-up off. `allow_local_requests_from_web_hooks_and_services` must
have the value it had before the upgrade.

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/application/settings" |
    grep -o -E '"(allow_local_requests_from_web_hooks_and_services|signup_enabled)":[a-z]+'
curl -ksSf -X PUT -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/application/settings?signup_enabled=false" > /dev/null
```

**Webhooks and search.** Check the webhooks of a pipeline repository. On a self-managed GitLab, a failing project
webhook is not disabled by default, so this is a check, not an expected fix. The hook URL is the
**`git.repository.hook.url`** preference, and `alert_status` must be `executable`.

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/projects/$(echo "$SMOKE_REPO" | sed 's#/#%2F#g')/hooks"
```

Change a file of a test pipeline in the platform, and search for the change. From GitLab 19.0 webhooks send
timestamps in ISO 8601 with milliseconds: the `elasticsearch-agent` log must show no error for the push event. Check
that log for pipeline synchronization errors during the window too, and search for a few pipelines. If a pipeline is
missing from the search, change it in the platform (for example its description): this indexes it and its code
again.

**The system event.** Remove the system event of the window.

**Clean up.** After the agreed time, remove what the upgrade left: the `data-pg12`, `data-pg14` and `data-pg16`
directories, the GitLab stop images on the node, and the old backups. Keep the backup of `15-19.4.1` and the freeze
copy until then.

On GitLab 18.0 and later, the temporary `TMP_FORK_*` group that a pipeline copy uses is only scheduled for deletion,
with its projects. GitLab removes it after the deletion delay (30 days by default). These groups are expected in
**Admin** > **Groups**.

## Roll back

Roll back to the last stop that passed its checks. A stop can only be restored on exactly its GitLab version and its
PostgreSQL major version. Keep the `cp-git` service closed until the rollback is checked, then open it as in
[After the upgrade](#after-the-upgrade).

### To an intermediate stop

`S` is the label of the stop, for example `05-17.1.8`, `S_STOP` its GitLab version, and `S_TAG` the postgres tag it
ran on. At the stops before a PostgreSQL upgrade, the dump restores into the old and the new version alike.

```bash
S=05-17.1.8 S_STOP=17.1.8 S_TAG=14.24
TS=$(date +%Y%m%d%H%M)
git_stop

# /etc/gitlab and gitlab-secrets.json of the stop
mv /opt/gitlab/config "/opt/gitlab/config.failed-$TS"
tar -C /opt/gitlab -xzpf "$BKP_DIR/$S/gitlab-config.tgz"

# An older Redis cannot read the data file of a newer one. It holds only caches, sessions and queued jobs
mv /opt/gitlab/data/redis/dump.rdb "/opt/gitlab/data/redis/dump.rdb.failed-$TS"

# The database of the stop, into an empty data directory
db_stop
mv /opt/gitlab-postgresql/data "/opt/gitlab-postgresql/data.failed-$TS"
mkdir /opt/gitlab-postgresql/data
kubectl set image deployment/cp-gitlab-db cp-gitlab-db="postgres:$S_TAG"
kubectl scale deployment cp-gitlab-db --replicas=1
until kubectl logs "$(db_pod)" 2>/dev/null | grep "PostgreSQL init process complete" > /dev/null; do sleep 5; done
until kubectl exec "$(db_pod)" -- pg_isready -U postgres 2>/dev/null; do sleep 5; done
kubectl exec -i "$(db_pod)" -- psql -U postgres -v ON_ERROR_STOP=1 <<EOF
CREATE ROLE $DB_USER WITH LOGIN SUPERUSER CREATEDB PASSWORD '$DB_PASS';
CREATE DATABASE $DB_NAME OWNER $DB_USER ENCODING 'UTF8' LC_COLLATE '$DB_COLLATE' LC_CTYPE '$DB_CTYPE' TEMPLATE template0;
EOF
kubectl exec -i "$(db_pod)" -- pg_restore -U postgres -d "$DB_NAME" --exit-on-error < "$BKP_DIR/$S/$DB_NAME.dump"
echo "pg_restore exit $?"
kubectl exec "$(db_pod)" -- vacuumdb -U postgres --all --analyze-in-stages
diff "$BKP_DIR/$S/sanity.txt" <(db_sanity) && echo "same"

# GitLab of the stop
git_start "gitlab/gitlab-ce:$S_STOP-ce.0"
smoke
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:git:fsck
```

`db_stop` fails on its first line when the database does not run: that is harmless. Set `GITLAB_DATABASE_VERSION` in
the install config to `S_TAG`.

The repositories do not change between the stops. If `gitlab:git:fsck` reports damage, restore
`gitlab/data/git-data` from `opt-trees.tar` of the freeze, or from the copy taken after a pause.

For a rollback to `15-19.4.1` after the switch to the GitLab 19 image, run `git_stop`, then
`git_start gitlab/gitlab-ce:19.4.1-ce.0`. The pull policy is `Always` again by then, so the node pulls the image from
Docker Hub.

### To 15.5.4

Restore both data trees from the freeze, and start the old images:

```bash
TS=$(date +%Y%m%d%H%M)
git_stop
kubectl scale deployment cp-gitlab-db --replicas=0
while [ -n "$(kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o name)" ]; do sleep 5; done
for d in config data pki; do mv "/opt/gitlab/$d" "/opt/gitlab/$d.failed-$TS"; done
mv /opt/gitlab-postgresql/data "/opt/gitlab-postgresql/data.failed-$TS"
tar -C /opt --numeric-owner -xpf "$BKP_DIR/00-freeze/opt-trees.tar"
db_start <the postgres tag of the freeze, from deployments.yaml of the freeze>
git_start "<the cp-git image of 15.5.4, from deployments.yaml of the freeze>"
smoke
```

Then set back the install config values, and the `cp-config-global` ConfigMap if it was changed at the switch. Open
the `cp-git` service, scale the services of the freeze back up, turn the token rotation back on, and remove the
system event. The 15.5.4 image writes `gitlab.rb` again at start.

## Troubleshooting

### The pod restarts during reconfigure

Read `kubectl logs --previous "$(git_pod)"`. A removed setting in `gitlab.rb` is named there. Remove or change it on
the node, and the next start runs reconfigure again.

### "Command timed out after 3600s"

The database migrations took longer than an hour, reconfigure failed, and the pod restarts. The next start runs the
remaining migrations again. A single migration that takes over an hour never finishes this way. Then run the
migrations outside reconfigure, with Puma and Sidekiq stopped, so that nothing else takes locks on the tables. The log
stays on the node, and its last line is the exit code of the migration:

```bash
git_stop
echo "gitlab_rails['auto_migrate'] = false" >> /opt/gitlab/config/gitlab.rb
git_start "gitlab/gitlab-ce:$STOP-ce.0"
kubectl exec "$(git_pod)" -- gitlab-ctl stop puma
kubectl exec "$(git_pod)" -- gitlab-ctl stop sidekiq
kubectl exec "$(git_pod)" -- sh -c 'nohup sh -c "gitlab-rake db:migrate; echo db:migrate exit \$?" > /var/log/gitlab/db-migrate.log 2>&1 &'
tail -f /opt/gitlab/logs/db-migrate.log      # until the line "db:migrate exit ..."
```

The last line must be `db:migrate exit 0`. Then check that no migration is left: the status command must exit with
`0`, count some `up` lines, and count `0` `down` lines.

```bash
kubectl exec "$(git_pod)" -- gitlab-rake db:migrate:status > "$BKP_DIR/migrate-status.txt"; echo "exit $?"
grep -c '^\s*up' "$BKP_DIR/migrate-status.txt"; grep -c '^\s*down' "$BKP_DIR/migrate-status.txt"
```

Then remove the setting, and start the stop again with Puma and Sidekiq. Go on with the stop from the background
migrations. See also
[the GitLab troubleshooting page](https://docs.gitlab.com/update/package/package_troubleshooting/#error-command-timed-out-after-3600s).

```bash
git_stop
sed -i "/^gitlab_rails\['auto_migrate'\] = false$/d" /opt/gitlab/config/gitlab.rb
git_start "gitlab/gitlab-ce:$STOP-ce.0"
```

### "It is required to upgrade to the latest ... version first"

A stop was skipped. Start the stop that was skipped.

### A background migration failed (status 4)

Retry it in **Admin** > **Monitoring** > **Background migrations**, or finish it with the values that `bbm` prints:
`gitlab-rake gitlab:background_migrations:finalize[<job_class_name>,<table_name>,<column_name>,'<job_arguments>']`.
Escape every comma in `job_arguments` with a backslash. See
[background migrations](https://docs.gitlab.com/update/background_migrations/).

### GitLab cannot connect to the database after a PostgreSQL upgrade

Check the user and its password through the pod IP. The image trusts connections from `127.0.0.1` without a password,
so a check through `127.0.0.1` proves nothing. The password must be `GITLAB_DATABASE_PASSWORD`.

```bash
DB_IP=$(kubectl get po "$(db_pod)" -o jsonpath='{.status.podIP}')
kubectl exec "$(db_pod)" -- psql "host=$DB_IP user=$DB_USER password=$DB_PASS dbname=$DB_NAME" -c 'select 1'
```

### "out of shared memory" in a migration

`max_locks_per_transaction` is too low: check that it is 128.
