# Upgrade GitLab 9.4 to 19.5

This page moves an existing Cloud Pipeline deployment from GitLab CE 9.4 (`CP_GITLAB_VERSION=9`, its database in
`cp-api-db`, PostgreSQL 9.6) to GitLab CE 19.5.3 (`CP_GITLAB_VERSION=19`, its database in its own server
`cp-gitlab-db`, PostgreSQL 17), and keeps all its repositories, users and tokens.

`pipectl` cannot do this upgrade: it deletes and re-creates the `cp-git` deployment. GitLab must pass through a long
list of required versions ("stops"), one at a time. Its database moves out of `cp-api-db`, the PostgreSQL server of
the API, into `cp-gitlab-db`, and is upgraded five times on the way, from 9.6 to 17. So this is a manual procedure. An
administrator with root access to the cluster runs it.

**This procedure has not been run in full.** The part from GitLab 15.11.13 to 19.5.3 is the procedure that moved a
deployment of the `release/0.16` line from GitLab 15.5.4 to 19.5.3. The part before it, from 9.4 to 15.11.13, the
move of the database and the switch of the platform to the GitLab API v4 are written from the GitLab documentation,
and were not run on a deployment. Expect to adapt them in the rehearsal.

**Rehearse it first.** Run the whole procedure on a copy of `/opt/gitlab` and of the GitLab database, in an isolated
cluster, on the same storage type as the real deployment. The copy holds the real SMTP, webhook and SAML settings: the
isolated cluster must not reach the real SMTP server, the platform services or the IdP. Write down how long each step
takes: this gives the length of the maintenance window. Use the same image tags in the rehearsal and in the real run.
If the deployment keeps `/etc/gitlab` in a volume of the container, fix that before you take the copy: otherwise the
copy of `/opt/gitlab` does not hold it (see [Where /etc/gitlab is kept](#where-etcgitlab-is-kept)).

**Do not run `pipectl` for `cp-git`, `cp-gitlab-db` or `cp-api-db`** from the start of the procedure until the install
config has the new values (see [Switch to the GitLab 19 image](#switch-to-the-gitlab-19-image)).

## The path

GitLab must stop at each of these versions, in this order. At each stop GitLab starts, updates its database, and
finishes its background migrations before it moves on. Only the start and the end use a Cloud Pipeline `cp-git`
image. The stops in between use the plain `gitlab/gitlab-ce:<version>-ce.0` images from Docker Hub.

| Label | Image | PostgreSQL while it runs | After the stop |
|---|---|---|---|
| `00-freeze` | the current `cp-git` image (9.4) | 9.6 in `cp-api-db`, then 9.6.24 in `cp-gitlab-db` | freeze, back up, move the database, prepare |
| `01-9.5.10` | `gitlab/gitlab-ce:9.5.10-ce.0` | 9.6 | |
| `02-10.0.7` | `gitlab/gitlab-ce:10.0.7-ce.0` | 9.6 | |
| `03-10.8.7` | `gitlab/gitlab-ce:10.8.7-ce.0` | 9.6 | |
| `04-11.0.6` | `gitlab/gitlab-ce:11.0.6-ce.0` | 9.6 | |
| `05-11.11.8` | `gitlab/gitlab-ce:11.11.8-ce.0` | 9.6 | |
| `06-12.0.12` | `gitlab/gitlab-ce:12.0.12-ce.0` | 9.6 | |
| `07-12.1.17` | `gitlab/gitlab-ce:12.1.17-ce.0` | 9.6 | |
| `08-12.10.14` | `gitlab/gitlab-ce:12.10.14-ce.0` | 9.6 | upgrade PostgreSQL 9.6 to 11 |
| `09-13.0.14` | `gitlab/gitlab-ce:13.0.14-ce.0` | 11 | |
| `10-13.1.11` | `gitlab/gitlab-ce:13.1.11-ce.0` | 11 | |
| `11-13.8.8` | `gitlab/gitlab-ce:13.8.8-ce.0` | 11 | |
| `12-13.12.15` | `gitlab/gitlab-ce:13.12.15-ce.0` | 11 | check the hashed storage, upgrade PostgreSQL 11 to 12 |
| `13-14.0.12` | `gitlab/gitlab-ce:14.0.12-ce.0` | 12 | |
| `14-14.3.6` | `gitlab/gitlab-ce:14.3.6-ce.0` | 12 | |
| `15-14.9.5` | `gitlab/gitlab-ce:14.9.5-ce.0` | 12 | |
| `16-14.10.5` | `gitlab/gitlab-ce:14.10.5-ce.0` | 12 | |
| `17-15.0.5` | `gitlab/gitlab-ce:15.0.5-ce.0` | 12 | |
| `18-15.4.6` | `gitlab/gitlab-ce:15.4.6-ce.0` | 12 | edit `gitlab.rb` for Gitaly, check the conditional stops |
| `19-15.11.13` | `gitlab/gitlab-ce:15.11.13-ce.0` | 12 | upgrade PostgreSQL 12 to 14 |
| `19a-16.0.10` | `gitlab/gitlab-ce:16.0.10-ce.0` | 14 | only if the checks require it |
| `19b-16.1.8` | `gitlab/gitlab-ce:16.1.8-ce.0` | 14 | only if the checks require it |
| `19c-16.2.11` | `gitlab/gitlab-ce:16.2.11-ce.0` | 14 | only if the checks require it |
| `20-16.3.9` | `gitlab/gitlab-ce:16.3.9-ce.0` | 14 | |
| `21-16.7.10` | `gitlab/gitlab-ce:16.7.10-ce.0` | 14 | |
| `22-16.11.10` | `gitlab/gitlab-ce:16.11.10-ce.0` | 14 | |
| `23-17.1.8` | `gitlab/gitlab-ce:17.1.8-ce.0` | 14 | |
| `24-17.3.7` | `gitlab/gitlab-ce:17.3.7-ce.0` | 14 | |
| `25-17.5.5` | `gitlab/gitlab-ce:17.5.5-ce.0` | 14 | |
| `26-17.8.7` | `gitlab/gitlab-ce:17.8.7-ce.0` | 14 | |
| `27-17.11.7` | `gitlab/gitlab-ce:17.11.7-ce.0` | 14 | upgrade PostgreSQL 14 to 16 |
| `28-18.2.8` | `gitlab/gitlab-ce:18.2.8-ce.0` | 16 | |
| `29-18.5.7` | `gitlab/gitlab-ce:18.5.7-ce.0` | 16 | |
| `30-18.8.11` | `gitlab/gitlab-ce:18.8.11-ce.0` | 16 | |
| `31-18.11.14` | `gitlab/gitlab-ce:18.11.14-ce.0` | 16 | upgrade PostgreSQL 16 to 17 |
| `32-19.2.10` | `gitlab/gitlab-ce:19.2.10-ce.0` | 17 | |
| `33-19.5.3` | `gitlab/gitlab-ce:19.5.3-ce.0` | 17 | |
| `34-git-19` | `<registry prefix>:git-19-<version>` | 17 | switch to the Cloud Pipeline image |

Each PostgreSQL upgrade happens at a stop that supports both the old and the new major version. No GitLab version
supports both 9.6 and 12, or 12 and 16, so the database cannot move in fewer steps. The minimum PostgreSQL version is
9.6 from GitLab 10.0, 11 from 13.0, 12 from 14.0, 13 from 16.0, 14 from 17.0, 16 from 18.0 and 17 from 19.0.

The path is from the GitLab upgrade path (`config/upgrade_path.yml` of the GitLab repository, which the
[upgrade path tool](https://gitlab-com.gitlab.io/support/toolbox/upgrade-path/) uses) and the
[upgrade paths](https://docs.gitlab.com/update/upgrade_paths/) page, for a target of 19.5.3. Each stop is the latest
patch of its version on Docker Hub at the time of writing. The last stop must be the exact version of the GitLab 19
image (see [Switch to the GitLab 19 image](#switch-to-the-gitlab-19-image)). If the target is later than 19.5, check
the path again: the next required stop is 19.8.

Why the stops matter for this deployment:

* **9.5.10**: the first plain GitLab image. It reads the hand-edited `/etc/gitlab/gitlab.rb` (see
  [Edit gitlab.rb](#3-edit-gitlabrb)), which now points at `cp-gitlab-db`. The Git preferences check of the platform
  rejects GitLab 9.5 to 9.9 (see [Run the stops](#run-the-stops)).
* **10.0.7**: requires the background migrations of 9.5 to be finished.
* **11.0.6**: the first version without the GitLab API v3. The platform must call the API v4 by now (see
  [Switch the platform to the GitLab API v4](#switch-the-platform-to-the-gitlab-api-v4)).
* **12.10.14**: the last version that runs on PostgreSQL 9.6. It runs on 11 too.
* **13.0.14**: PostgreSQL 11 is required. Puma replaces Unicorn as the web server.
* **13.8.8**: runs the background migration of 13.4, which moves the repositories that are still in the legacy
  storage to the hashed storage.
* **13.12.15**: every repository must be in the hashed storage before 14.0 (see
  [Hashed storage](#hashed-storage)). PostgreSQL 12 is supported here.
* **14.0.12**: PostgreSQL 12 is required. The legacy storage is removed.
* **15.4.6**: the last stop before 15.10, which changes the Gitaly settings of `gitlab.rb`.
* **15.11.13**: from here on the path is the one of the `release/0.16` procedure. PostgreSQL 12 must be 12.10 or
  later here.
* **The first 16.x stop** (16.3.9, or a conditional one before it): PostgreSQL 12 is no longer supported. GitLab now
  opens a second (`ci`) database connection pool, which doubles its database connections.
* **16.7.10**: finalizes a background migration, queued at 15.11.13, that adds the `admin_mode` scope to the active
  tokens of admin users, `git.token` too. The migration fails on a token whose scopes are a Ruby object, which GitLab
  9 created: the procedure rewrites them before the first stop (see [Token scopes](#4-token-scopes)).
* **17.1.8**: the first 17.x. `omnibus_gitconfig` in `gitlab.rb` now stops reconfigure with an error.
* **17.8.7**: the first stop with OpenSSL 3: TLS 1.2 or later, and RSA keys of at least 2048 bits.
* **18.2.8**: the first 18.x. PostgreSQL 16.5 or later is required. In GitLab CE, deleting a project or a group now
  only marks it for deletion.
* **18.5.7**: from 18.4 GitLab requires the `amcheck` extension in its database. The procedure creates it at the
  first PostgreSQL upgrade.
* **18.8.11**: every batched background migration must finish here, before 18.9 or later.
* **18.11.14**: this version upgrades a bundled PostgreSQL to 17 on its own. The bundled PostgreSQL is disabled
  before the first stop, so this does not happen.
* **19.2.10**: the first 19.x. PostgreSQL 17 is required.
* **19.5.3**: a required stop, and the target. Its background migrations must finish before any later upgrade.

Every image checks the version of the data it starts on, and stops when a required stop was skipped. For example,
19.2.10 needs 18.11 or later, and 19.5.3 needs 19.2 or later.

## Known risks and limits

* **Not rehearsed.** See the start of the page. The early stops are old GitLab releases, out of support for years:
  when one of them fails, there is no fix to wait for. The way out is a rollback, and a change of the procedure.
* **Length.** The path has 33 GitLab versions and five PostgreSQL upgrades. Each stop runs its migrations, and the
  checks run `gitlab:git:fsck` over every repository. Plan the window from the rehearsal, with pauses (see
  [Run the stops](#run-the-stops)).
* **Storage.** The [Gitaly](https://docs.gitlab.com/administration/gitaly/) page says: "For repository data, only
  local storage is supported for Gitaly and Gitaly Cluster (Praefect) for performance and consistency reasons.
  Alternatives such as NFS or cloud-based file systems are not supported." When `/opt/gitlab` and
  `/opt/gitlab-postgresql` are on Amazon FSx for Lustre, the deployment is outside GitLab support. The upgrade adds a
  heavy write load (database migrations, five PostgreSQL upgrades). The mitigations are the FSx checks, a copy of the
  data off FSx, PostgreSQL dump and restore instead of `pg_upgrade --link`, and the integrity checks after the stops.
  Moving the repositories and the database to local disks (Amazon EBS) removes the risk. That is a separate decision.
* **Resources.** GitLab recommends 8 vCPU and 16 GB of RAM for the GitLab container (8 GB at the least), besides what
  else runs on the node. The `cp-git` deployment sets no CPU or memory requests or limits. Migrations run slower on a
  smaller node. Plan a node resize separately, if needed. `cp-gitlab-db` is one more PostgreSQL server on the node
  it runs on.
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
  uses more than 1 GiB.
* **Tokens.** The upgrade does not add an expiry date to an existing token that has none: every stop of the path is a
  patch release at or after the ones that stopped doing so (see
  [Non-expiring access tokens](https://docs.gitlab.com/update/deprecations/#non-expiring-access-tokens)). So
  `git.token` keeps working after the upgrade. On GitLab 19.5 a new token must have an expiry date, at most 365 days
  ahead. The platform rotates `git.token` before it expires (the `git.token.rotation.*` preferences). A token without
  an expiry date is never rotated.
* **The GitLab runner.** The GitLab 9 image starts a GitLab runner. The stop images and the GitLab 19 image do not
  have one.

## Before you start

### What you need

* A root `bash` shell on the node that runs `cp-git`, with `kubectl` configured for the cluster, and `git`, `curl`,
  `openssl` and `tar`. Run `cp-gitlab-db` on the same node: the steps on `/opt/gitlab` and on
  `/opt/gitlab-postgresql` then run in one place. Only administrators may have access to this node: some commands pass
  `git.token` on their command line.
* The AWS CLI, with read access to Amazon FSx, for the FSx checks.
* A backup directory **off** the file system that holds `/opt/gitlab` and `/opt/gitlab-postgresql`: for example an
  Amazon EBS volume mounted on the node. See [Space](#space) for its size. It will hold secrets
  (`gitlab-secrets.json`, the database, the SAML private key): restrict access to it.
* These values:
    * the **`git.external.url`** preference: the URL of GitLab
    * the **`git.token`** preference
    * `CP_GITLAB_INTERNAL_PORT`, `GITLAB_DATABASE_PASSWORD`, `GITLAB_DATABASE_USERNAME` and `GITLAB_DATABASE_DATABASE`
      from the install config
    * a pipeline repository to clone in the checks, as `<namespace>/<project>`
* Docker 20.10.24 or later on the node of `cp-git` and `cp-gitlab-db`. With an older Docker, do not start the
  procedure: upgrade Docker first, after [Where /etc/gitlab is kept](#where-etcgitlab-is-kept) (see
  [Docker version](#docker-version)).
* A Cloud Pipeline build that has the GitLab 19 support, deployed to the `api` before the freeze (see the checks).
  The Git users synchronization (`cp-git-sync`) runs from the `api-srv` image, so it gets the support with it.
  Do not deploy `cp-git` from this build before you check [Where /etc/gitlab is kept](#where-etcgitlab-is-kept): a
  new `cp-git` pod can lose `/etc/gitlab`.
* The `git-19-<version>` image of the same build in the registry of the deployment, under the same prefix as the
  current `cp-git` image (`<registry prefix>:git-<version>`, or `<registry prefix>:git-9-<version>` once a build with
  the GitLab 19 support has deployed `cp-git`).
* The `cp-bkp-worker-<version>` image of the same build, for the backup workers. An older worker image is based on
  CentOS 7: it cannot run the `pipe` of this build, and has no backup script for `cp-gitlab-db`.

### Session setup

Run this once in the shell that runs the procedure. Every later step uses these variables and functions. If the
session is lost, run it again, and set `DB_COLLATE` and `DB_CTYPE` again (see [Other facts](#other-facts)).

```bash
set -o pipefail
set +H                                      # no history expansion: some SQL below contains "!"
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

git_pod()    { kubectl get po -l cloud-pipeline/cp-git=true -o jsonpath='{.items[0].metadata.name}'; }
api_db_pod() { kubectl get po -l cloud-pipeline/cp-api-db=true -o jsonpath='{.items[0].metadata.name}'; }
db_pod()     { kubectl get po -l cloud-pipeline/cp-gitlab-db=true -o jsonpath='{.items[0].metadata.name}'; }
# The GitLab database in cp-api-db, where GitLab 9.4 keeps it, and in cp-gitlab-db, after the move
api_db_sql() { kubectl exec "$(api_db_pod)" -- psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -At "$@"; }
db_sql()     { kubectl exec "$(db_pod)" -- psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -At "$@"; }

# A few row counts and the last schema migration, to compare before and after a database move.
# Pass api_db_sql to read the database in cp-api-db
db_sanity() {
  "${1:-db_sql}" -c "SELECT 'projects=' || count(*) FROM projects UNION ALL SELECT 'users=' || count(*) FROM users
                     UNION ALL SELECT 'namespaces=' || count(*) FROM namespaces
                     UNION ALL SELECT 'schema=' || max(version) FROM schema_migrations;"
}

# Background migrations that are left, by the GitLab version of the stop. Both numbers must be 0
bg_left() {
  case "$STOP" in
    9.*|10.*|11.*|12.0.*|12.1.*)
      kubectl exec "$(git_pod)" -- gitlab-rails runner -e production \
        'puts Sidekiq::Queue.new("background_migration").size; puts Sidekiq::ScheduledSet.new.select { |r| r.klass == "BackgroundMigrationWorker" }.size' ;;
    *)
      kubectl exec "$(git_pod)" -- gitlab-rails runner -e production 'puts Gitlab::BackgroundMigration.remaining; puts 0' ;;
  esac
}

# Batched background migrations that are not finished (3) or finalized (6). No output: none left.
# GitLab older than 13.12 has no such table, and then this prints nothing too
bbm() {
  [ "$(db_sql -c "SELECT to_regclass('batched_background_migrations') IS NOT NULL;")" == "t" ] || return 0
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
  db_sql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();"
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
# the version, the git.token details (expires_at, GitLab 15.5 and later), the SAML button of the sign-in page,
# and a clone
smoke() {
  local url
  url="https://$(kubectl get po "$(git_pod)" -o jsonpath='{.status.podIP}'):$GIT_PORT"
  for _ in $(seq 60); do curl -ksf -o /dev/null -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/version" && break; sleep 10; done
  curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/version"; echo
  curl -ksf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$url/api/v4/personal_access_tokens/self" ||
      echo "(no git.token details: GitLab older than 15.5)"; echo
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

`git_stop` prints an error when `cp-git` is already stopped. That is harmless. `bg_left` reads the version of the
stop from `STOP` (see [One stop](#one-stop)).

`smoke` connects to the IP of the `cp-git` pod from the node. During the stops the `cp-git` service is closed (see
[Freeze and back up](#freeze-and-back-up)), so this is the only way in. The clone happens in a private directory,
which `smoke` removes at once.

The backup of a stop dumps the database with the `pg_dump` of `cp-gitlab-db` itself, which always matches the
server. `stop_backup` then reads the whole dump with `pg_restore`, which fails on a dump that was cut short. The
GitLab backup (`gitlab-rake gitlab:backup:create`, or `gitlab-backup create` from 12.2) is used only on 9.4 and on
19.5.3: the `pg_dump` bundled in the intermediate GitLab images is older than the database server at several stops,
and it refuses to dump a newer server. During the stops nobody can reach GitLab through its service, so the
repositories do not change, and the copy of the repositories taken at the freeze stays valid for every stop. The
hashed storage migration at the 13.x stops moves the repositories on disk: from then on a rollback restores the
repositories of the freeze too (see [Roll back](#roll-back)).

### Facts to collect

Collect these facts before the rehearsal, on the running 9.4. Each one changes the procedure as described.

#### Where /etc/gitlab is kept

`/etc/gitlab` holds `gitlab.rb`, `gitlab-secrets.json` and the SSH host keys. `gitlab-secrets.json` holds the keys of
the data that GitLab encrypts in its database. The procedure backs up and edits `/etc/gitlab` on the node, as
`/opt/gitlab/config`. Older `cp-git` deployments do not mount it there: for example, the ones of the builds from
May 2019 to December 2021. Check the deployment. `/etc/gitlab` must be in the first list, and the second list must
show its volume with the path `/opt/gitlab/config`:

```bash
kubectl get deploy cp-git -o jsonpath='{range .spec.template.spec.containers[0].volumeMounts[*]}{.mountPath} {.name}{"\n"}{end}'
kubectl get deploy cp-git -o jsonpath='{range .spec.template.spec.volumes[*]}{.name} {.hostPath.path}{"\n"}{end}'
```

If `/etc/gitlab` is not in the first list, it is a volume of the container. The GitLab image declares that volume.
Each new container gets a new, empty one, and Kubernetes deletes the old one together with the old container. GitLab
then creates new secrets and new SSH host keys at start. So the running container has the only current copy of
`/etc/gitlab`. The backup worker of `cp-git` also keeps copies in the system storage
(`backups/cp-git/<date>/cp-bkp-git-settings-dump-<date>.tgz`, the last 10). But a copy taken before the current
container started holds other secrets. A stop image would start on an empty `/etc/gitlab`: with new secrets, and
without the edits of the procedure. Fix the deployment before the rehearsal copy, and before anything replaces the
`cp-git` pod. The fix restarts GitLab, so do it at a quiet time: while GitLab is down, runs that clone a pipeline
repository fail.

Check how the image writes `gitlab.rb`. `cat >>` means that it appends its settings to the file at each start.
`cat >` means that it writes the file again:

```bash
kubectl exec "$(git_pod)" -- grep -n 'gitlab.rb <<' /update_config.sh
```

Copy `/etc/gitlab` out of the running pod. The archive holds secrets, so keep it in the backup directory. The first
command does not overwrite an earlier copy: that copy can be the only one with the right secrets. The list must show
`gitlab/gitlab-secrets.json` and the `gitlab/ssh_host_*_key` files:

```bash
[ ! -e "$BKP_DIR/etc-gitlab.tar" ] &&
    kubectl exec "$(git_pod)" -- tar -C /etc --numeric-owner -cpf - gitlab > "$BKP_DIR/etc-gitlab.tar"
tar -tvf "$BKP_DIR/etc-gitlab.tar"
```

Put everything except `gitlab.rb` into `/opt/gitlab/config`. If that directory is already there, it is not what GitLab
reads now (it can be left from a deployment older than May 2019): move it aside. Each command runs only when the one
before it worked:

```bash
[ ! -e /opt/gitlab/config ] || mv /opt/gitlab/config "/opt/gitlab/config.unused-$(date +%Y%m%d%H%M)" &&
    mkdir -m 775 /opt/gitlab/config &&
    tar -C /opt/gitlab/config --numeric-owner --strip-components=1 --exclude=gitlab/gitlab.rb \
        -xpf "$BKP_DIR/etc-gitlab.tar" &&
    echo "seeded"
```

`gitlab.rb` is left out on purpose. An image that appends creates the file when it is missing, so the next start
creates it as it is today. With the old file, the next start would add a second copy of the settings. An image that
writes the file again does not need it either.

Compare the pod and the node. Every file except `gitlab.rb` must be the same. This must print `same`:

```bash
diff <(kubectl exec "$(git_pod)" -- sh -c 'cd /etc/gitlab && find . -type f ! -path ./gitlab.rb -exec sha256sum {} + | LC_ALL=C sort -k 2') \
     <(cd /opt/gitlab/config && find . -type f ! -path ./gitlab.rb -exec sha256sum {} + | LC_ALL=C sort -k 2) &&
    echo same
```

Then restart `cp-git` on the same image, with `/opt/gitlab/config` mounted at `/etc/gitlab`. GitLab is down for a few
minutes. `git_stop` scales the deployment to 0 before the patch: two GitLab pods must never run on the same data. The
patch also sets the image pull policy to `IfNotPresent`, so that the start uses the image that is already on the node
([The cp-git deployment](#2-the-cp-git-deployment) sets it too).

```bash
GIT9_IMAGE=$(kubectl get deploy cp-git -o jsonpath='{.spec.template.spec.containers[0].image}')
git_stop
kubectl patch deployment cp-git --type=json -p '[
  {"op": "add", "path": "/spec/template/spec/volumes/-",
   "value": {"name": "git-config", "hostPath": {"path": "/opt/gitlab/config"}}},
  {"op": "add", "path": "/spec/template/spec/containers/0/volumeMounts/-",
   "value": {"name": "git-config", "mountPath": "/etc/gitlab"}},
  {"op": "replace", "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "IfNotPresent"}]' &&
    git_start "$GIT9_IMAGE"
```

If the patch fails, do not start `cp-git` until the mount is there. A start without it loses `/etc/gitlab` again, and
then `/opt/gitlab/config` and the copy in the backup directory are the only ones with the right secrets. Fix the cause
(for example, a volume named `git-config` that is already there), and run the patch and `git_start` again.

Check the result:

* `/etc/gitlab` must be in the first list again, and the second list must show the path `/opt/gitlab/config`.
* The `diff` compares the node with the copy from the old container, and must print `same`. So the secrets and the SSH
  host keys did not change, and the new `gitlab.rb` is the same as the old one. A `gitlab.rb` that differs is expected
  only when the ConfigMap or the certificates changed after the old container started. The `diff` shows secrets: read
  it on the node only.
* `smoke` must print the version 9.4 again.
* The last command must print the `cp-git` pod, not the `cp-git-sync` pod. The pod has a new name now, and the backup
  worker runs the backup in the first pod whose name starts with `cp-git`. If it prints the `cp-git-sync` pod, give
  the `cp-git` pod another name, as in the second block of **Backup** in [After the upgrade](#after-the-upgrade).

```bash
kubectl get deploy cp-git -o jsonpath='{range .spec.template.spec.containers[0].volumeMounts[*]}{.mountPath} {.name}{"\n"}{end}'
kubectl get deploy cp-git -o jsonpath='{range .spec.template.spec.volumes[*]}{.name} {.hostPath.path}{"\n"}{end}'
mkdir "$BKP_DIR/etc-gitlab" &&
    tar -C "$BKP_DIR/etc-gitlab" --numeric-owner --strip-components=1 -xpf "$BKP_DIR/etc-gitlab.tar"
diff -r "$BKP_DIR/etc-gitlab" /opt/gitlab/config && echo same
smoke
kubectl get po | grep '^cp-git' | head -1
```

If you ran [Checks that must pass](#checks-that-must-pass) before this fix, run them again.

From now on, an image that appends adds its settings to `gitlab.rb` once more at each start. The last copy wins, so
GitLab gets the same settings, and the edits of the procedure change every copy.

#### Docker version

The procedure needs Docker 20.10.24 or later on the node of `cp-git` and `cp-gitlab-db`. Check it on the node. This
must print `OK`:

```bash
DOCKER_VERSION=$(docker version --format '{{.Server.Version}}'); echo "Docker $DOCKER_VERSION"
printf '%s\n' 20.10.24 "$DOCKER_VERSION" | sort -V -C && echo OK || echo "STOP: upgrade Docker first"
```

If it prints `STOP`, do not start the procedure: upgrade Docker first. Two problems of older versions are known:

* Docker older than 18.06 cannot pull an image that Docker Hub serves as an OCI image index. The pull fails with
  "missing signature key". The PostgreSQL images of the procedure (`11.22-bookworm`, `12.22`, `14.24`, `16.15` and
  `17.11`) and the GitLab images from `18.11.14` on are served this way.
* The default seccomp profile of Docker older than 20.10.10 rejects the `clone3` system call of newer images, such as
  the GitLab images from 16.x (Ubuntu 22.04 and 24.04). By default Kubernetes runs pods without a seccomp profile,
  unless an annotation, a pod security policy or the kubelet `--seccomp-default` option sets one. So this hits the
  containers that Docker runs with its own profile.

There is no way back through `yum`: on a current Amazon Linux 2, `yum` cannot install Docker 18.03 (libseccomp
conflicts with it). So:

* Upgrade the rehearsal cluster first, and run the checks below there.
* Take an EBS snapshot of the volumes of the node first: the root volume, and the volume of the Docker data root if it
  is another one.
* Upgrade after [Where /etc/gitlab is kept](#where-etcgitlab-is-kept). The restart of Docker replaces every container
  on the node, `cp-git` too, and a deployment that keeps `/etc/gitlab` in a volume of the container loses it.
* Tell the users first: the platform services on the node restart too. If the Kubernetes API server runs on this
  node, `kubectl` fails for a few minutes.
* The kubelet pulls the image again for every container it re-creates with the pull policy `Always`. The registry of
  the platform images and Docker Hub must be reachable, or those pods stay in `ImagePullBackOff`.

Record the state before the upgrade. The first command must print `/etc/gitlab`. If it prints `STOP`, fix
[Where /etc/gitlab is kept](#where-etcgitlab-is-kept) first. The other commands record the cgroup and storage drivers
of Docker, its packages, and the pods that do not run now. `not_ready` says so when `kubectl` fails.

```bash
kubectl get deploy cp-git -o jsonpath='{range .spec.template.spec.containers[0].volumeMounts[*]}{.mountPath}{"\n"}{end}' |
    grep -x /etc/gitlab || echo "STOP: fix /etc/gitlab first"
not_ready() {
  local pods
  pods=$(kubectl get po --all-namespaces --no-headers) || { echo "kubectl failed: run not_ready again"; return 1; }
  printf '%s\n' "$pods" |
      awk 'NF { split($3, r, "/"); if ($4 != "Completed" && ($4 != "Running" || r[1] != r[2])) print $1, $2, $4 }'
}
docker info --format '{{.CgroupDriver}} {{.Driver}}' | tee "$BKP_DIR/docker-drivers-before.txt"
rpm -q docker containerd runc | tee "$BKP_DIR/docker-packages-before.txt"
not_ready | tee "$BKP_DIR/pods-not-ready-before-docker.txt"
```

Upgrade Docker. These commands are for Amazon Linux 2. It has no package of 20.10.24, so they install 20.10.25. They
also pin two packages that `yum` would otherwise take at their latest versions:

* containerd stays at 1.7.27: the containerd packages of version 2 refuse a Docker older than 25.
* runc stays at 1.3.6: with runc 1.4 and later, `exec` into a container fails.

```bash
amazon-linux-extras enable docker
yum install -y docker-20.10.25-1.amzn2.0.5 containerd-1.7.27-1.amzn2.0.3 runc-1.3.6-1.amzn2
rpm -q docker containerd runc
```

`rpm` must list `docker-20.10.25-1.amzn2.0.5`, `containerd-1.7.27-1.amzn2.0.3` and `runc-1.3.6-1.amzn2`. If runc 1.4
was installed before, `yum install` leaves it at 1.4: run `yum downgrade -y runc-1.3.6-1.amzn2`, and check with
`rpm -q runc` again. Then restart Docker, and lock the three versions: a later `yum update` would move Docker to 25,
containerd to 2 and runc to 1.4 again.

```bash
systemctl daemon-reload
systemctl enable --now containerd
systemctl restart docker
yum install -y yum-plugin-versionlock
yum versionlock add docker containerd runc
```

Check Docker again. The `printf` line must print `OK`, and the `diff` must print `same`: the kubelet needs the same
cgroup driver as before. If the cgroup driver changed, set the old one in `/etc/docker/daemon.json`
(`"exec-opts": ["native.cgroupdriver=<driver>"]`), and restart Docker again.

```bash
printf '%s\n' 20.10.24 "$(docker version --format '{{.Server.Version}}')" | sort -V -C && echo OK
diff "$BKP_DIR/docker-drivers-before.txt" <(docker info --format '{{.CgroupDriver}} {{.Driver}}') && echo same
```

Wait until Kubernetes and the platform are back:

* `kubectl get nodes` must show the node as `Ready`.
* Run `not_ready` until it prints only the pods of `pods-not-ready-before-docker.txt`. A pod in `ImagePullBackOff`
  cannot reach its registry.
* `exec` into the `cp-git` pod must work.
* `smoke` must print the version 9.4 and at least one SAML sign-in link: GitLab is back, with its `/etc/gitlab`.
* Open the Platform GUI and sign in: it must load as before.

```bash
kubectl get nodes
not_ready
kubectl exec "$(git_pod)" -- true && echo "exec OK"
smoke
```

If a check fails and you cannot fix it, restore the node from the EBS snapshot.

#### Repository count and size

```bash
api_db_sql -c "SELECT count(*), pg_size_pretty(sum(repository_size)), pg_size_pretty(sum(lfs_objects_size))
               FROM project_statistics;"
du -sh /opt/gitlab/data/git-data/repositories /opt/gitlab/data/gitlab-rails/uploads
```

Size the backup directory and the free space with it (see [Space](#space)). The time of the copy at the freeze and
of `gitlab:git:fsck` at each stop grows with it: the rehearsal measures both.

#### Database size

```bash
api_db_sql -c "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));"
```

Each PostgreSQL upgrade keeps the old data directory, so the file system of `/opt/gitlab-postgresql` needs free space
for six copies of the database. The dump and restore time grows with the size: the rehearsal measures it.

#### FSx deployment type, backups, data repository and mount options

Skip this when the paths are not on FSx for Lustre.

```bash
mount -t lustre                                  # the file system name (fs-...), its mount point and options
findmnt -T /opt/gitlab/data; findmnt -T /opt
aws fsx describe-file-systems --file-system-ids <fs-id> \
    --query 'FileSystems[0].LustreConfiguration.[DeploymentType,AutomaticBackupRetentionDays,DataRepositoryConfiguration]'
aws fsx describe-data-repository-associations --filters Name=file-system-id,Values=<fs-id>
find /opt/gitlab/data/git-data /opt/gitlab/data/gitlab-rails -type f -print0 |
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
grep -n -E "db_host|db_port" /opt/gitlab/config/gitlab.rb
api_db_sql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB_NAME';"
```

* `run: postgresql` in the first output is expected: the bundled PostgreSQL runs idle. The `gitlab.rb` edit before
  the first stop disables it. Otherwise the 12.0, 12.10 and later images would try to upgrade it at start, and the
  19.x image would start PostgreSQL 17 on its old data.
* `db_host` must be the `cp-api-db` service, and the last query must count GitLab's connections (more than 0). If
  GitLab uses the bundled PostgreSQL instead, **stop**: this procedure does not apply.

#### Sign-up

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/application/settings" | grep -o -E '"signup_enabled":[a-z]+'
```

`signup_enabled` is set to `false` after the upgrade. GitLab 19 also has the setting
`allow_local_requests_from_web_hooks_and_services`, which blocks the webhooks that call an address inside the
cluster when it is off. The webhooks of pipeline repositories call the `elasticsearch-agent` inside the cluster, so
the procedure turns it on after the upgrade, as the installer does.

#### Other facts

Record the rest in the same session. The procedure uses them.

```bash
# Where the pods run, what the node has, the container runtime and the Kubernetes version
kubectl get po -o wide | grep -E '^cp-git|^cp-api-db'
kubectl describe node <node> | grep -A 6 -E '^(Capacity|Allocatable)'
docker version --format '{{.Server.Version}}'
df -h "$(docker info --format '{{.DockerRootDir}}')"
kubectl version --short

# The deployments the procedure scales down, and the current images
kubectl get deploy cp-git cp-git-sync cp-bkp-worker-cp-git
kubectl get deploy cp-git -o jsonpath='{.spec.template.spec.containers[0].image}'; echo

# The GitLab database in cp-api-db: owners, encoding, locale and database-level settings
api_db_sql -c "SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public';"
api_db_sql -c "SELECT datname, pg_encoding_to_char(encoding), datcollate, datctype FROM pg_database WHERE datname = '$DB_NAME';"
api_db_sql -c "SELECT count(*) FROM pg_db_role_setting s LEFT JOIN pg_database d ON d.oid = s.setdatabase
               LEFT JOIN pg_roles r ON r.oid = s.setrole WHERE d.datname = '$DB_NAME' OR r.rolname = '$DB_USER';"
DB_COLLATE=$(api_db_sql -c "SELECT datcollate FROM pg_database WHERE datname = '$DB_NAME';")
DB_CTYPE=$(api_db_sql -c "SELECT datctype FROM pg_database WHERE datname = '$DB_NAME';")
export DB_COLLATE DB_CTYPE
```

* The image of `cp-git` is `<registry prefix>:git-<version>`, or `<registry prefix>:git-9-<version>` once a build with
  the GitLab 19 support has deployed `cp-git`. Note the `<registry prefix>`: the GitLab 19 image is
  `<registry prefix>:git-19-<version>`.
* Each GitLab image is 0.5 GB (9.5) to 1.9 GB to download, and up to 5.5 GB on disk. The Docker disk needs room for two stop
  images at a time.
* From 16.x the GitLab images are based on Ubuntu 22.04, and from 18.x on 24.04. With Docker's default seccomp
  profile they need Docker 20.10.10 or later: see [Docker version](#docker-version).
* The tables must be owned by the GitLab user only, with no row in `pg_db_role_setting`. The database move and the
  PostgreSQL upgrades re-create exactly the GitLab user and its database. Re-create any other setting by hand after
  each restore.

### Checks that must pass

Run these before the freeze. Each one must pass, or be fixed first.

**The version is 9.4, and `smoke` works.** `smoke` must print the version, at least one SAML sign-in link, and the
last commit of the pipeline repository. It also proves that the node reaches the pod IP. GitLab 9.4 has no endpoint
for the token details: `smoke` says so.

```bash
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/version"
smoke
```

**No background migration is pending.** Both numbers must be `0`:

```bash
STOP=9.4 bg_left
```

**The `api` has the GitLab 19 support.** The two preferences must be there: `git.gitlab.api.version` with `v3` until
the next check, and `git.token.rotation.enabled`.

```bash
curl -ksSf -H "Authorization: Bearer <admin JWT>" "https://<api host>/pipeline/restapi/app/info"
curl -ksSf -H "Authorization: Bearer <admin JWT>" "https://<api host>/pipeline/restapi/preferences/git.gitlab.api.version"
curl -ksSf -H "Authorization: Bearer <admin JWT>" "https://<api host>/pipeline/restapi/preferences/git.token.rotation.enabled"
```

#### Switch the platform to the GitLab API v4

GitLab 11.0 and later have no API v3. GitLab 9.4 has both versions, so switch the platform to v4 now, on 9.4, and
check it while a step back is a preference change.

Set **`git.gitlab.api.version`** to `v4` (**Settings** > **Preferences** > **Git**). The `api` reads it at each
request to GitLab, and `cp-git-sync` at its next round (every 5 minutes by default). Then check, in the GUI, on a
test pipeline:

* the list of its versions, and the code and the documents of a version
* a change of a file, and a new version with a description
* a copy of the pipeline, and a delete of the copy with its repository
* the Git credentials of **Settings** > **Git CLI**, with a clone of the repository of the test pipeline

And the users synchronization: a new round must end without a GitLab error.

```bash
kubectl logs deploy/cp-git-sync --tail=200 | grep -i -E "error|exception|Iteration done" | tail -20
```

If a check fails, set **`git.gitlab.api.version`** back to `v3`, and do not go on: GitLab 11.0 and later need the
platform on v4.

**The images are there.** Pull the first stop image on the `cp-git` node, and check the GitLab 19 image of the same
build as the `api`. The registry prefix is the one of the current `cp-git` image, and the version is the one of the
`api-srv` image. The last command must print the GitLab version of the last stop, `19.5.3`.

```bash
docker pull gitlab/gitlab-ce:9.5.10-ce.0
kubectl get deploy cp-api-srv -o jsonpath='{.spec.template.spec.containers[*].image}'; echo
docker pull <registry prefix>:git-19-<version>
docker run --rm --entrypoint cat <registry prefix>:git-19-<version> /opt/gitlab/embedded/service/gitlab-rails/VERSION; echo
```

Use the latest minor of each PostgreSQL major. At the time of writing they were 9.6.24, 11.22, 12.22, 14.24, 16.15
and 17.11. The latest 11.x images have only tags with the Debian release, so the tag for 11 is `11.22-bookworm`: the
plain `11.x` tags end at 11.16. Use 14.14 or later, and 16.5 or later. Check that each tag exists on Docker Hub: every
line must end with `200`. Use the same tags in the rehearsal and in the real run.

```bash
for t in 9.6.24 11.22-bookworm 12.22 14.24 16.15 17.11; do
  echo "$t $(curl -s -o /dev/null -w '%{http_code}' "https://hub.docker.com/v2/repositories/library/postgres/tags/$t")"
done
```

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
(`openssl s_client -connect <host>:<port> -tls1_2 < /dev/null`). Replace a failing certificate before the `26-17.8.7`
stop. A new SAML certificate of GitLab must also be registered in `cp-idp`.

**The root password.** It is needed after the upgrade, for the install config. Sign in as `root` at
`$GIT_URL/users/sign_in?auto_sign_in=false`. If the password is unknown, reset it after the upgrade (see
[After the upgrade](#after-the-upgrade)).

### Space

* The backup directory: the GitLab backup archive (about the size of the repositories, uploads and database), the
  copy of `/opt/gitlab`, and one compressed database dump per stop.
* The file system of `/opt/gitlab`: room for one GitLab backup archive, before it moves to the backup directory.
* The file system of `/opt/gitlab-postgresql`: the database, and five more copies of it.
* `cp-api-db` keeps the GitLab database of 9.4 until the clean-up: it is the rollback to 9.4.
* For a full rollback to 9.4: room for one more copy of `/opt/gitlab` on its file system. The rollback moves the
  current tree aside and unpacks the freeze copy next to it.
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

Scale down the services that call GitLab or read its data, and the backup worker: a scheduled backup must not run in
the middle of a stop. Turn the token rotation off for the window, so that its daily check does not report GitLab as
down: set **`git.token.rotation.enabled`** to `false` (**Settings** > **Preferences** > **Git**).

```bash
mkdir -p "$BKP_DIR/00-freeze"
kubectl get deploy cp-git -o yaml > "$BKP_DIR/00-freeze/deployments.yaml"
kubectl get svc cp-git -o yaml > "$BKP_DIR/00-freeze/service.yaml"
kubectl get configmap cp-config-global -o yaml > "$BKP_DIR/00-freeze/cp-config-global.yaml"
for d in cp-git-sync cp-bkp-worker-cp-git; do
  kubectl scale deployment "$d" --replicas=0
done
```

Close the `cp-git` service: point it at a label that no pod has. Users (through the edge and the node port), the
`api` and the runs all reach GitLab through this service, so nobody can change a repository until it opens again.
The pod IP stays reachable from inside the cluster, which `smoke` uses. `ENDPOINTS` must be `<none>`:

```bash
kubectl patch svc cp-git -p '{"spec": {"selector": {"cloud-pipeline/cp-git": "closed-for-upgrade"}}}'
kubectl get endpoints cp-git
```

Stop the GitLab web and background services too. Gitaly and Redis still run for the backup:

```bash
kubectl exec "$(git_pod)" -- gitlab-ctl stop unicorn
kubectl exec "$(git_pod)" -- gitlab-ctl stop sidekiq
```

### 3. Back up

```bash
# GitLab backup: repositories, uploads and database, on 9.4
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:backup:create
ls -t /opt/gitlab/data/backups/*_gitlab_backup.tar | head -1
mv "$(ls -t /opt/gitlab/data/backups/*_gitlab_backup.tar | head -1)" "$BKP_DIR/00-freeze/"

# Stop GitLab, then dump the GitLab database of cp-api-db, read the dump through, and count the rows
git_stop
kubectl exec "$(api_db_pod)" -- pg_dump -U postgres -Fc "$DB_NAME" > "$BKP_DIR/00-freeze/$DB_NAME.dump"
kubectl exec -i "$(api_db_pod)" -- pg_restore -f /dev/null < "$BKP_DIR/00-freeze/$DB_NAME.dump" && echo "dump OK"
db_sanity api_db_sql > "$BKP_DIR/00-freeze/sanity.txt"
tar -C /opt/gitlab -czf "$BKP_DIR/00-freeze/gitlab-config.tgz" config

# Copy the GitLab tree off the file system
tar -C /opt --numeric-owner -cpf "$BKP_DIR/00-freeze/opt-trees.tar" --exclude=gitlab/data/backups \
    gitlab/config gitlab/data gitlab/pki
```

`gitlab-config.tgz` holds `/etc/gitlab`, with `gitlab.rb` and `gitlab-secrets.json`. Without `gitlab-secrets.json`
no backup can be restored. `tar` warns that it ignores sockets: that is expected.

`cp-api-db` keeps running: the API needs it. The GitLab database in it stays as it is, and nothing uses it from now
on. It is the database of a rollback to 9.4: do not drop it until the clean-up.

If the file system supports FSx backups (see the facts), take a manual one now, while `cp-git` is stopped:

```bash
aws fsx create-backup --file-system-id <fs-id> --tags Key=Name,Value=gitlab-upgrade-00-freeze
```

## Prepare

### 1. The cp-gitlab-db deployment

GitLab 19 needs PostgreSQL 17, and `cp-api-db` runs 9.6 for the API. So the GitLab database moves to its own
PostgreSQL server, `cp-gitlab-db`, as on a new GitLab 19 install. It starts on PostgreSQL 9.6, the version GitLab 9.4
runs on.

Put it on the node of `cp-git`, on an empty data directory. Use the `<shared_buffers>` of `cp-api-db`, or a larger
one (see [Known risks and limits](#known-risks-and-limits)), and set the same value in
`GITLAB_DATABASE_SHARED_BUFFERS` of the install config.

```bash
kubectl label node <cp-git node> cloud-pipeline/cp-gitlab-db=true --overwrite
mkdir -p /opt/gitlab-postgresql/data
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: Service
metadata:
  name: cp-gitlab-db
  namespace: default
  labels:
    cloud-pipeline/cp-gitlab-db: "true"
spec:
  ports:
  - protocol: TCP
    port: 5432
    targetPort: 5432
  selector:
    cloud-pipeline/cp-gitlab-db: "true"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cp-gitlab-db
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      cloud-pipeline/cp-gitlab-db: "true"
  template:
    metadata:
      labels:
        cloud-pipeline/cp-gitlab-db: "true"
    spec:
      nodeSelector:
        cloud-pipeline/cp-gitlab-db: "true"
      tolerations:
      - key: node-role.kubernetes.io/master
        effect: NoSchedule
      containers:
        - name: cp-gitlab-db
          image: postgres:9.6.24
          imagePullPolicy: "IfNotPresent"
          args: ["--shared_buffers=<shared_buffers>", "--max_connections=400", "--max_locks_per_transaction=128"]
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: "postgres"
            - name: POSTGRES_USER
              value: "postgres"
            - name: POSTGRES_PASSWORD
              value: "postgres"
            - name: PGDATA
              value: /var/lib/postgresql/data
          volumeMounts:
            - mountPath: /var/lib/postgresql/data
              name: gitlab-pgdata
            - mountPath: /dev/shm
              name: gitlab-dshm
          readinessProbe:
            tcpSocket:
              port: 5432
            initialDelaySeconds: 5
            periodSeconds: 10
      volumes:
        - name: gitlab-pgdata
          hostPath:
            path: /opt/gitlab-postgresql/data
        - name: gitlab-dshm
          emptyDir:
            medium: Memory
EOF
until kubectl logs "$(db_pod)" 2>/dev/null | grep "PostgreSQL init process complete" > /dev/null; do sleep 5; done
until kubectl exec "$(db_pod)" -- pg_isready -U postgres 2>/dev/null; do sleep 5; done
```

This is the deployment of `cp-gitlab-db-dpl.yaml` and `cp-gitlab-db-svc.yaml`, with the values of the procedure. The
image creates a new server on the empty directory. Wait for "init process complete" before the next step: until then
the image runs a temporary server, which it stops.

Create the GitLab user and database as the installer does, with the encoding and locale of the database in
`cp-api-db`, and restore the dump of the freeze. Then update the statistics and compare the row counts:

```bash
kubectl exec -i "$(db_pod)" -- psql -U postgres -v ON_ERROR_STOP=1 <<EOF
CREATE ROLE $DB_USER WITH LOGIN SUPERUSER CREATEDB PASSWORD '$DB_PASS';
CREATE DATABASE $DB_NAME OWNER $DB_USER ENCODING 'UTF8' LC_COLLATE '$DB_COLLATE' LC_CTYPE '$DB_CTYPE' TEMPLATE template0;
EOF
kubectl exec -i "$(db_pod)" -- pg_restore -U postgres -d "$DB_NAME" --exit-on-error < "$BKP_DIR/00-freeze/$DB_NAME.dump"
echo "pg_restore exit $?"
kubectl exec "$(db_pod)" -- vacuumdb -U postgres --all --analyze-in-stages
diff "$BKP_DIR/00-freeze/sanity.txt" <(db_sanity) && echo "same"
db_sql -c "SELECT version();" -c "SHOW shared_buffers;" -c "SHOW max_connections;" -c "SHOW max_locks_per_transaction;"
```

`pg_restore` must exit with `0`, and the row counts must be the same.

In the install config set `GITLAB_DATABASE_VERSION=9.6.24` and `GITLAB_DATABASE_MAX_CONNECTIONS=400`. Do not change
`GITLAB_DATABASE_HOST` and `CP_GITLAB_VERSION` yet: see [Switch to the GitLab 19 image](#switch-to-the-gitlab-19-image).

### 2. The cp-git deployment

`cp-git` is stopped. Add the 1 GiB `/dev/shm` (the first command lists the volumes: skip the patch of the volume if
`git-dshm` is there). Set the image pull policy to `IfNotPresent`: the stop images are pulled ahead, and a start must
not fail when Docker Hub is slow or not reachable.

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

The Cloud Pipeline 9 image writes `/etc/gitlab/gitlab.rb` again from the environment at every start, or appends to it
(see [Where /etc/gitlab is kept](#where-etcgitlab-is-kept)). The plain GitLab images of the stops do not: they use
the file as it is. So edit it now, on the node
(`/opt/gitlab/config/gitlab.rb`), and do not start the Cloud Pipeline 9 image again, except for a rollback to 9.4.

* Point GitLab at `cp-gitlab-db`.
* Disable the bundled PostgreSQL.
* Keep `omnibus_gitconfig` for now: it is replaced before the `19-15.11.13` stop (see
  [Gitaly settings](#gitaly-settings)).

First look at the current lines. There must be no `postgresql[...]` line: if there is one, merge it by hand.

```bash
grep -n -E "^gitlab_rails\['db_(host|port)'\]|^omnibus_gitconfig|^gitaly\[|^postgresql\[" /opt/gitlab/config/gitlab.rb
```

Then edit the file. The last command must show the new `db_host` and `db_port`, and `postgresql['enable'] = false`.

```bash
sed -i -e "s|^gitlab_rails\['db_host'\] = .*|gitlab_rails['db_host'] = 'cp-gitlab-db.default.svc.cluster.local'|" \
       -e "s|^gitlab_rails\['db_port'\] = .*|gitlab_rails['db_port'] = 5432|" /opt/gitlab/config/gitlab.rb
cat >> /opt/gitlab/config/gitlab.rb <<'EOF'

# GitLab uses cp-gitlab-db, not the bundled PostgreSQL
postgresql['enable'] = false
EOF
grep -n -E "^gitlab_rails\['db_(host|port)'\]|^postgresql\[" /opt/gitlab/config/gitlab.rb
```

### 4. Token scopes

GitLab stores the scopes of each access token as YAML text. GitLab 9 stored the scopes of a token created through its
REST API as a Ruby `Hashie::Array` object, not as a plain list. `pipectl` creates `git.token` through the REST API, so
the deployment has such rows. The `19-15.11.13` stop queues a background migration that reads the scopes of every
active token of an admin user, and the `21-16.7.10` stop finalizes it. The migration fails on such a row (see
[Troubleshooting](#tried-to-load-unspecified-class-hashiearray)).

Rewrite these scopes as plain lists, as GitLab writes them. The first command lists the rows, and keeps a record in the
backup directory. Where there is no such row, it prints nothing, and the update changes nothing.

```bash
db_sql -c "SELECT id, user_id, name, impersonation, revoked, expires_at, replace(scopes, E'\n', ' ')
           FROM personal_access_tokens WHERE scopes LIKE '%!ruby/%' ORDER BY id;" | tee -a "$BKP_DIR/00-freeze/pat-scopes.txt"
db_sql -c "UPDATE personal_access_tokens SET scopes = replace(scopes, '--- !ruby/array:Hashie::Array', '---')
           WHERE scopes LIKE '--- !ruby/array:Hashie::Array%' RETURNING id;"
```

Then count what is left on the tokens that the migration reads. This must print `0`:

```bash
db_sql -c "SELECT count(*) FROM personal_access_tokens t JOIN users u ON u.id = t.user_id
           WHERE u.admin AND t.revoked IS NOT TRUE AND (t.expires_at IS NULL OR t.expires_at > now())
             AND t.scopes LIKE '%!ruby/%';"
```

If it does not, one of the listed rows holds another Ruby object. Write its scopes by hand as a plain list of the same
scopes, then count again. For example, for a token with the `api` scope only:

```bash
db_sql -c "UPDATE personal_access_tokens SET scopes = E'---\n- api\n' WHERE id = <id>;"
```

Back up the prepared state as a stop of its own. A rollback to an early stop starts from it:

```bash
stop_backup 00-prepared
```

## Run the stops

Run [One stop](#one-stop) for each line of [the path](#the-path), from `01-9.5.10` to `33-19.5.3`. After these stops,
run the extra step before the next stop:

* `08-12.10.14`: [Upgrade PostgreSQL](#upgrade-postgresql) from 9.6 to 11
* `12-13.12.15`: [Hashed storage](#hashed-storage), then [Upgrade PostgreSQL](#upgrade-postgresql) from 11 to 12
* `18-15.4.6`: [Gitaly settings](#gitaly-settings), and [Conditional stops](#conditional-stops)
* `19-15.11.13`: [Upgrade PostgreSQL](#upgrade-postgresql) from 12 to 14
* `27-17.11.7`: [Upgrade PostgreSQL](#upgrade-postgresql) from 14 to 16
* `31-18.11.14`: [Upgrade PostgreSQL](#upgrade-postgresql) from 16 to 17

The procedure can pause after the backup of any stop, except `01-9.5.10`: the Git preferences check of the platform
rejects GitLab 9.5 to 9.9, so the Git preferences cannot be saved there. To give GitLab back to the users in a pause,
start the same stop again, open the `cp-git` service, scale the services of the freeze back up, and turn the token
rotation back on (see [After the upgrade](#after-the-upgrade)). Users then change the repositories, so the backups of
the earlier stops no longer match them. Before you go on, freeze again (steps 1 and 2 of
[Freeze and back up](#freeze-and-back-up)), but skip the three `kubectl get ... -o yaml` lines: a full rollback reads
the files of `00-freeze`. Then stop GitLab with `git_stop`, take the backup of that stop again, and copy the
repositories again:

```bash
tar -C /opt --numeric-owner -cpf "$BKP_DIR/$LABEL/opt-gitlab-data.tar" --exclude=gitlab/data/backups gitlab/data
```

A rollback then goes back no further than the pause, with this copy of the repositories. A rollback to 9.4 loses the
changes of the pause.

### One stop

Set the stop, and start it. Watch the pod in a second shell: `kubectl get po -l cloud-pipeline/cp-git=true -w` and
`kubectl logs -f <pod>`. The database migrations run inside reconfigure, so it can take long.

```bash
STOP=9.5.10 LABEL=01-9.5.10
git_start "gitlab/gitlab-ce:$STOP-ce.0"
```

Read the end of the reconfigure output in the log: it lists the deprecated settings. A removed setting stops
reconfigure with an error, and the pod restarts (see [Troubleshooting](#troubleshooting)).

Wait for the background migrations. Both numbers of `bg_left` must be `0`, and `bbm` must print nothing. A `bbm` row
with status `4` is a failed migration: see [Troubleshooting](#troubleshooting). `bg_left` is for the stops up to
`28-18.2.8`: later versions have no such migrations. From 18.5, `gitlab-rake gitlab:background_migrations:status`
(18.5 to 18.8) or `...:list` (18.9 and later) shows the batched ones, with the progress.

```bash
bg_left
while [ -n "$(bbm)" ]; do bbm; echo; sleep 60; done
```

Run `bg_left` again until it prints `0` twice. The migrations run in Sidekiq, which `git_start` starts with GitLab.

Check that GitLab works:

```bash
smoke
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:git:fsck
db_sql -c "SELECT count(*) FROM pg_stat_activity;"
```

* `smoke` prints the version of the stop, the `git.token` details from 15.5 on, the SAML sign-in links, and the last
  commit of the pipeline repository. `expires_at` of `git.token` must stay `null`, and the SAML count must be at
  least 1. The count only shows that GitLab loaded the SAML settings, not that a sign-in works. A real SAML sign-in
  needs the service open, so it is checked after the upgrade. The certificate checks before the freeze cover the
  OpenSSL 3 change of 17.8.7. If the SAML sign-in fails after the upgrade, fix the certificates or the settings on
  19.5.3: it is no reason to roll back.
* `gitlab:git:fsck` must report no error. It checks every repository, so it takes time: the rehearsal shows how much.
* Record the connection count, and compare it with `max_connections`.

Stop GitLab and back up the stop:

```bash
git_stop
stop_backup "$LABEL"
```

While the migrations run, pull the image of the next stop on the node (`docker pull gitlab/gitlab-ce:<next>-ce.0`).
After a stop, remove the image of the stop before it (`docker rmi gitlab/gitlab-ce:<previous>-ce.0`). Keep the
current `cp-git` image of 9.4 until the upgrade is done: a full rollback needs it.

### Upgrade PostgreSQL

Run this after the backup of `08-12.10.14` (9.6 to 11), `12-13.12.15` (11 to 12), `19-15.11.13` (12 to 14),
`27-17.11.7` (14 to 16) and `31-18.11.14` (16 to 17). `cp-git` is stopped, and the backup of the stop is the dump to
restore.

The procedure dumps the database and restores it into an empty data directory of the new version. The old data
directory stays as it is, as a fallback. Do not use `pg_upgrade --link` or `--clone` on FSx: link mode leaves no
fallback, and clone mode needs reflinks, which Lustre does not have. `pg_upgrade` in copy mode also works, but the
official `postgres` images do not have the old and the new binaries in one container.

Set the versions, the stop and its backup label, then stop the database and start the new version on an empty
directory. The first upgrade, for example:

```bash
OLD=9.6 NEW_TAG=11.22-bookworm STOP=12.10.14 LABEL=08-12.10.14
db_stop
mv /opt/gitlab-postgresql/data "/opt/gitlab-postgresql/data-pg$OLD"
mkdir /opt/gitlab-postgresql/data
kubectl set image deployment/cp-gitlab-db cp-gitlab-db="postgres:$NEW_TAG"
kubectl scale deployment cp-gitlab-db --replicas=1
until kubectl logs "$(db_pod)" 2>/dev/null | grep "PostgreSQL init process complete" > /dev/null; do sleep 5; done
until kubectl exec "$(db_pod)" -- pg_isready -U postgres 2>/dev/null; do sleep 5; done
```

The values of the other upgrades: `OLD=11 NEW_TAG=12.22 STOP=13.12.15 LABEL=12-13.12.15`,
`OLD=12 NEW_TAG=14.24 STOP=15.11.13 LABEL=19-15.11.13`, `OLD=14 NEW_TAG=16.15 STOP=17.11.7 LABEL=27-17.11.7` and
`OLD=16 NEW_TAG=17.11 STOP=18.11.14 LABEL=31-18.11.14`, with the tags found in the checks.

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

At the first upgrade, to 11, create the `amcheck` extension: GitLab 18.4 and later require it in their database, and
do not create it. PostgreSQL 9.6 does not have it. The dumps carry it through the next upgrades, and `pg_amcheck`
uses it too.

```bash
db_sql -c "CREATE EXTENSION IF NOT EXISTS amcheck;"
```

Update the statistics and compare the row counts. From PostgreSQL 14 on, also check the tables and indexes:

```bash
kubectl exec "$(db_pod)" -- vacuumdb -U postgres --all --analyze-in-stages
diff "$BKP_DIR/$LABEL/sanity.txt" <(db_sanity) && echo "same"
# PostgreSQL 14 and later only
kubectl exec "$(db_pod)" -- pg_amcheck -U postgres --install-missing --heapallindexed "$DB_NAME"; echo "pg_amcheck exit $?"
```

The row counts must be the same, and `pg_amcheck` must exit with `0`.

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

### Hashed storage

Run this at the `12-13.12.15` stop, after its background migrations, while GitLab runs. GitLab 14.0 removes the
legacy storage, so every repository and every attachment must be in the hashed storage. The background migration of
13.4, which ran at the `11-13.8.8` stop, moves the repositories. Check that it moved all of them. Both counts must be
`0`:

```bash
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:storage:legacy_projects
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:storage:legacy_attachments
```

If a count is not `0`, list them (`gitlab:storage:list_legacy_projects`, `gitlab:storage:list_legacy_attachments`),
and schedule the move. Sidekiq runs it, in the `hashed_storage:hashed_storage_project_migrate` queue. Wait until the
queue is empty, then count again. Errors are in the Sidekiq log.

```bash
kubectl exec "$(git_pod)" -- gitlab-rake gitlab:storage:migrate_to_hashed
kubectl exec "$(git_pod)" -- gitlab-rails runner -e production \
    'puts Sidekiq::Queue.new("hashed_storage:hashed_storage_project_migrate").size'
```

Then go on with the checks of the stop. The hashed storage changes where the repositories are on disk: from here on a
rollback to an earlier stop restores the repositories of the freeze too (see [Roll back](#roll-back)).

### Gitaly settings

Run this after the backup of `18-15.4.6`, before the `19-15.11.13` stop. `cp-git` is stopped.

* Remove `omnibus_gitconfig`: GitLab 17.0 stops reconfigure on it, and 18.2 no longer knows it.
* Set its `pack.packSizeLimit` through Gitaly, in the format of GitLab 15.10 and later. Its `pack.windowMemory`
  cannot be set: Gitaly always uses 100m, so the old value never applied.

First look at the current lines. Note the `packSizeLimit` value of the `omnibus_gitconfig` line. There must be no
`gitaly[...]` line: if there is one, merge it by hand.

```bash
grep -n -E "^omnibus_gitconfig|^gitaly\[" /opt/gitlab/config/gitlab.rb
```

Then edit the file. Put the noted value in place of `512m`. The last command must show no `omnibus_gitconfig`.

```bash
sed -i '/^omnibus_gitconfig\[/d' /opt/gitlab/config/gitlab.rb
cat >> /opt/gitlab/config/gitlab.rb <<'EOF'

# Replaces omnibus_gitconfig, which GitLab 17.0 and later reject
gitaly['configuration'] = {
  git: {
    config: [
      { key: 'pack.packSizeLimit', value: '512m' }
    ]
  }
}
EOF
grep -n -E "omnibus_gitconfig|packSizeLimit" /opt/gitlab/config/gitlab.rb
```

Copy the edited file into the backup of the stop: a rollback to `18-15.4.6` restores the file of the stop, without
the edit.

```bash
cp -p /opt/gitlab/config/gitlab.rb "$BKP_DIR/18-15.4.6/gitlab.rb.gitaly"
```

### Conditional stops

Run this after the backup of `18-15.4.6`. Some stops of 16.x are required only for some data. The tables of these
queries are there from 15.x:

```bash
db_sql -c "SELECT 'users=' || count(*) FROM users
           UNION ALL SELECT 'ci_pipeline_variables=' || count(*) FROM ci_pipeline_variables
           UNION ALL SELECT 'npm_packages=' || count(*) FROM packages_packages WHERE package_type = 2
           UNION ALL SELECT 'ci_pipeline_messages=' || count(*) FROM ci_pipeline_messages;"
```

* `users` over 30,000: add the `19a-16.0.10` stop.
* `ci_pipeline_variables` over 0: add the `19a-16.0.10` and `19c-16.2.11` stops. GitLab names no threshold for a
  "large pipeline variables history", and the platform does not use GitLab CI, so any row is unexpected.
* `npm_packages` over 0: add the `19b-16.1.8` stop.
* `ci_pipeline_messages` over 1.5 million: the `23-17.1.8` stop is in the path anyway, but its migration processes
  about 1.5 to 2 million rows an hour. Plan the time.

## Switch to the GitLab 19 image

After the backup of `33-19.5.3`, switch to the Cloud Pipeline image of the same GitLab version. This image writes
`gitlab.rb` again from the environment at start: the database of `GITLAB_DATABASE_HOST`, the new nginx settings with
the SSL ciphers, the object storage with its region, no GitLab runner. The GitLab data is already on 19.5.3, so no
migration runs.

The pods read the install config values from the `cp-config-global` ConfigMap. `GITLAB_DATABASE_HOST` there still
points at `cp-api-db`, where the database of 9.4 is. **Update the ConfigMap before the start**, or GitLab 19.5 starts
on the database of 9.4:

```bash
kubectl patch configmap cp-config-global --type merge -p '{"data": {
    "CP_GITLAB_VERSION": "19",
    "GITLAB_DATABASE_HOST": "cp-gitlab-db.default.svc.cluster.local",
    "GITLAB_DATABASE_PORT": "5432",
    "GITLAB_DATABASE_VERSION": "<the 17.x tag>"}}'
kubectl get configmap cp-config-global -o jsonpath='{.data.GITLAB_DATABASE_HOST}'; echo
```

This image comes from the registry of the deployment, so set the image pull policy back to `Always`, as
`cp-git-dpl.yaml` has it. `cp-git` is stopped after the backup of the stop.

```bash
GIT19_IMAGE=<registry prefix>:git-19-<version>
cp -p /opt/gitlab/config/gitlab.rb "$BKP_DIR/33-19.5.3/gitlab.rb"
kubectl patch deployment cp-git --type=json -p '[{"op": "replace",
    "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "Always"}]'
git_start "$GIT19_IMAGE"
grep -n "db_host" /opt/gitlab/config/gitlab.rb
diff "$BKP_DIR/33-19.5.3/gitlab.rb" /opt/gitlab/config/gitlab.rb
```

`db_host` must be `cp-gitlab-db.default.svc.cluster.local`. If it is not, run `git_stop` at once, and check the
ConfigMap. The diff shows secrets: read it on the node only. Expect the `nginx` settings under
`gitlab_rails['nginx']`, the SSL ciphers, and the object storage settings.

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
* `GITLAB_DATABASE_HOST=cp-gitlab-db.default.svc.cluster.local`
* `GITLAB_DATABASE_VERSION=<the 17.x tag>`
* `GITLAB_DATABASE_MAX_CONNECTIONS=400`
* `GITLAB_DATABASE_SHARED_BUFFERS=<shared_buffers>`
* `GITLAB_ROOT_PASSWORD=<the real root password>`: on GitLab 17 and later the installer replaces `Passw0rd` with a
  random password, which GitLab never applies to an existing database. The installer then clones pipeline
  repositories with the wrong password.

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
If the `root` password is unknown, set a new strong one, and put it in `GITLAB_ROOT_PASSWORD` of the install config:

```bash
kubectl exec -it "$(git_pod)" -- gitlab-rake "gitlab:password:reset[root]"
```

**Services.** Set the image of the backup worker of `cp-git` to the one of the same build (see
[What you need](#what-you-need)). Then scale the services of the freeze back up, and set
**`git.token.rotation.enabled`** back to `true`.

```bash
kubectl set image deployment/cp-bkp-worker-cp-git \
    cp-bkp-worker-cp-git=<registry prefix>:cp-bkp-worker-<version>
for d in cp-git-sync cp-bkp-worker-cp-git; do
  kubectl scale deployment "$d" --replicas=1
done
```

Create the backup worker of `cp-gitlab-db`, as the installer does for a new GitLab 19. This is
`cp-bkp-worker-dpl.yaml` with the values of `cp-gitlab-db`:

```bash
kubectl apply -f - <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cp-bkp-worker-cp-gitlab-db
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      cloud-pipeline/cp-bkp-worker: "cp-gitlab-db"
  template:
    metadata:
      labels:
        cloud-pipeline/cp-bkp-worker: "cp-gitlab-db"
    spec:
      nodeSelector:
        cloud-pipeline/cp-gitlab-db: "true"
      tolerations:
      - key: node-role.kubernetes.io/master
        effect: NoSchedule
      containers:
        - name: cp-bkp-worker-cp-gitlab-db
          image: <registry prefix>:cp-bkp-worker-<version>
          imagePullPolicy: "Always"
          envFrom:
            - configMapRef:
                name: cp-config-global
          env:
            - name: CP_BKP_SERVICE_NAME
              value: cp-gitlab-db
            - name: CP_BKP_SERVICE_WD
              value: /opt/gitlab-postgresql/data/bkp/bkp-worker-wd
            - name: CP_BKP_SERVICE_KUBECTL_ARGS
              value: ""
          volumeMounts:
            - mountPath: /opt/gitlab-postgresql/data/bkp/bkp-worker-wd
              name: bkp-wd
            - mountPath: /opt/bkp-worker/logs
              name: bkp-logs
      volumes:
        - name: bkp-wd
          hostPath:
            path: /opt/gitlab-postgresql/data/bkp/bkp-worker-wd
        - name: bkp-logs
          hostPath:
            path: /opt/bkp-worker/logs
      imagePullSecrets:
        - name: cp-distr-docker-registry-secret
EOF
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

The backup of `cp-git` runs `gitlab-backup create`, as `CP_GITLAB_VERSION` is now `19` in the ConfigMap. Back up the
GitLab database with its own worker too. It runs `pg_dump` in the `cp-gitlab-db` pod, now on PostgreSQL 17, with the
user that the PostgreSQL upgrade re-created. The backup must exit with `0`. The log is on the `cp-gitlab-db` node.

```bash
gdb_bkp_pod() { kubectl get po -l cloud-pipeline/cp-bkp-worker=cp-gitlab-db -o jsonpath='{.items[0].metadata.name}'; }
until kubectl exec "$(gdb_bkp_pod)" -- test -f /backup.env 2>/dev/null; do sleep 10; done
kubectl exec "$(gdb_bkp_pod)" -- bash /backup.sh; echo "backup exit $?"
tail -20 /opt/bkp-worker/logs/backup-cp-gitlab-db.log
```

**Token.** `smoke` shows `expires_at` of `git.token`: `null` means no expiry, and the rotation has nothing to do. The
**`git.token.rotation.*`** preferences must be there, with the rotation enabled.

**Application settings.** Let the webhooks call the services inside the cluster, as the installer does for GitLab 19,
and turn sign-up off. Then check both:

```bash
curl -ksSf -X PUT -H "PRIVATE-TOKEN: $GIT_TOKEN" \
    "$GIT_URL/api/v4/application/settings?allow_local_requests_from_web_hooks_and_services=true&signup_enabled=false" > /dev/null
curl -ksSf -H "PRIVATE-TOKEN: $GIT_TOKEN" "$GIT_URL/api/v4/application/settings" |
    grep -o -E '"(allow_local_requests_from_web_hooks_and_services|signup_enabled)":[a-z]+'
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

**The platform.** Repeat the checks of [Switch the platform to the GitLab API v4](#switch-the-platform-to-the-gitlab-api-v4)
on GitLab 19.5, and check the log of `cp-git-sync` again.

**The system event.** Remove the system event of the window.

**Clean up.** After the agreed time, remove what the upgrade left: the `data-pg9.6`, `data-pg11`, `data-pg12`,
`data-pg14` and `data-pg16` directories, the GitLab stop images on the node, and the old backups. If
[Where /etc/gitlab is kept](#where-etcgitlab-is-kept) needed a fix, also remove `/opt/gitlab/config.unused-*`, and
`etc-gitlab.tar` and `etc-gitlab` of the backup directory: they hold secrets. Keep the backup of
`33-19.5.3` and the freeze copy until then. Then drop the GitLab database of 9.4 and its user from `cp-api-db`. This
cannot be undone: it removes the rollback to 9.4.

```bash
kubectl exec "$(api_db_pod)" -- psql -U postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE $DB_NAME;" -c "DROP ROLE $DB_USER;"
```

On GitLab 18.0 and later, the temporary `TMP_FORK_*` group that a pipeline copy uses is only scheduled for deletion,
with its projects. GitLab removes it after the deletion delay (30 days by default). These groups are expected in
**Admin** > **Groups**.

## Roll back

Roll back to the last stop that passed its checks. A stop can only be restored on exactly its GitLab version and its
PostgreSQL major version. Keep the `cp-git` service closed until the rollback is checked, then open it as in
[After the upgrade](#after-the-upgrade).

### To an intermediate stop

`S` is the label of the stop, for example `23-17.1.8`, `S_STOP` its GitLab version, and `S_TAG` the postgres tag it
ran on. At the stops before a PostgreSQL upgrade, the dump restores into the old and the new version alike.

```bash
S=23-17.1.8 S_STOP=17.1.8 S_TAG=14.24
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

To roll back to a stop before `12-13.12.15`, also restore the repositories of the freeze: the hashed storage moved
them on disk, and the database of the earlier stop has them in the legacy storage. Before `git_start`:

```bash
mv /opt/gitlab/data "/opt/gitlab/data.failed-$TS"
tar -C /opt --numeric-owner -xpf "$BKP_DIR/00-freeze/opt-trees.tar" gitlab/data
```

The repositories do not change between the stops otherwise. If `gitlab:git:fsck` reports damage, restore
`gitlab/data/git-data` from `opt-trees.tar` of the freeze, or from the copy taken after a pause.

A rollback to `18-15.4.6` restores the `gitlab.rb` of the stop, without the Gitaly edit. Run the
[Gitaly settings](#gitaly-settings) step again before the `19-15.11.13` stop.

For a rollback to `33-19.5.3` after the switch to the GitLab 19 image, run `git_stop`, then
`git_start gitlab/gitlab-ce:19.5.3-ce.0`. The pull policy is `Always` again by then, so the node pulls the image from
Docker Hub.

### To 9.4

The database of 9.4 is still in `cp-api-db`, as it was at the freeze. Restore the GitLab tree from the freeze, stop
`cp-gitlab-db`, and start the old image. The 9.4 image writes `gitlab.rb` again at start, from the ConfigMap: put the
ConfigMap of the freeze back first, in case it was changed at the switch. An image that appends to `gitlab.rb` (see
[Where /etc/gitlab is kept](#where-etcgitlab-is-kept)) would add to the restored file, so remove the file before the
start: the image then writes it once.

```bash
TS=$(date +%Y%m%d%H%M)
git_stop
kubectl scale deployment cp-gitlab-db --replicas=0
kubectl patch configmap cp-config-global --type merge -p "{\"data\": {
    \"CP_GITLAB_VERSION\": \"9\",
    \"GITLAB_DATABASE_HOST\": \"$(grep -E '^  GITLAB_DATABASE_HOST:' "$BKP_DIR/00-freeze/cp-config-global.yaml" | awk '{print $2}')\"}}"
for d in config data pki; do mv "/opt/gitlab/$d" "/opt/gitlab/$d.failed-$TS"; done
tar -C /opt --numeric-owner -xpf "$BKP_DIR/00-freeze/opt-trees.tar"
rm /opt/gitlab/config/gitlab.rb
git_start "<the cp-git image of 9.4, from deployments.yaml of the freeze>"
grep -n "db_host" /opt/gitlab/config/gitlab.rb
smoke
```

`db_host` must be the `cp-api-db` service again. Keep **`git.gitlab.api.version`** at `v4`: GitLab 9.4 has it, and
the checks before the freeze ran on it. Then set back the install config values. Open the `cp-git` service, scale the
services of the freeze back up, turn the token rotation back on, and remove the system event. Keep the data of
`cp-gitlab-db` until the cause of the rollback is known, then remove the deployment, its service and
`/opt/gitlab-postgresql`.

## Troubleshooting

### "missing signature key" when an image is pulled

Docker on the node is older than 18.06, and the image is served as an OCI image index. Upgrade Docker: see
[Docker version](#docker-version). If the procedure is already running:

* Upgrade between two stops, with `cp-git` stopped. Skip the `exec` and `smoke` checks of that section: they need
  `cp-git`.
* Stop `cp-gitlab-db` first with `db_stop`. It fails on its first line when the database does not run: that is
  harmless. A restart of Docker while `cp-gitlab-db` creates a new database on an empty data directory leaves a
  broken directory.
* After the checks, start `cp-gitlab-db` again. If the pull failed in [Upgrade PostgreSQL](#upgrade-postgresql), the
  data directory is still empty: go on with that block from `kubectl scale deployment cp-gitlab-db --replicas=1`, and
  wait for "init process complete". Between two stops, run `db_start <its tag>`.

### The pod restarts during reconfigure

Read `kubectl logs --previous "$(git_pod)"`. A removed setting in `gitlab.rb` is named there. Remove or change it on
the node, and the next start runs reconfigure again.

### "Command timed out after 3600s"

The database migrations took longer than an hour, reconfigure failed, and the pod restarts. The next start runs the
remaining migrations again. A single migration that takes over an hour never finishes this way. Then run the
migrations outside reconfigure, with the web server and Sidekiq stopped, so that nothing else takes locks on the
tables. The web server is `unicorn` before 13.0 and `puma` from 13.0. The log stays on the node, and its last line is
the exit code of the migration:

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

Then remove the setting, and start the stop again with the web server and Sidekiq. Go on with the stop from the
background migrations. See also
[the GitLab troubleshooting page](https://docs.gitlab.com/update/package/package_troubleshooting/#error-command-timed-out-after-3600s).

```bash
git_stop
sed -i "/^gitlab_rails\['auto_migrate'\] = false$/d" /opt/gitlab/config/gitlab.rb
git_start "gitlab/gitlab-ce:$STOP-ce.0"
```

### "It is required to upgrade to the latest ... version first"

A stop was skipped. Start the stop that was skipped.

### GitLab 14.0 finds projects in the legacy storage

[Hashed storage](#hashed-storage) was skipped, or did not move everything. Roll back to `12-13.12.15` (see
[To an intermediate stop](#to-an-intermediate-stop)), run that step, and go on from there.

### "Tried to load unspecified class: Hashie::Array"

A token still has its scopes as a Ruby object: [Token scopes](#4-token-scopes) was skipped, or a backup taken before
that step was restored. The background migration `BackfillAdminModeScopeForPersonalAccessTokens` then fails:

* From `19-15.11.13` to `20-16.3.9`, `bbm` shows it with status `4`, and a retry fails again.
* At `21-16.7.10`, reconfigure fails in the migration `FinalizeBackfillAdminModeScopeForPersonalAccessTokens`, and the
  pod restarts.

Run `git_stop`, then the commands of that step, and start the stop again. At `21-16.7.10` GitLab retries the failed
batch on its own. At an earlier stop, retry the migration (see
[A background migration failed](#a-background-migration-failed-status-4)).

### A background migration failed (status 4)

If its `job_class_name` is `BackfillAdminModeScopeForPersonalAccessTokens`, fix the token scopes first (see
["Tried to load unspecified class: Hashie::Array"](#tried-to-load-unspecified-class-hashiearray)).

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
