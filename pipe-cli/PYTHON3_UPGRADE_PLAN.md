# pipe-cli: Python 2 → Python 3.12 Upgrade Plan

## Context

pipe-cli is the Cloud Pipeline command-line tool. Its entire Linux build pipeline is currently hardcoded to Python 2.7 (`python:2.7-stretch` Docker image, a custom PyInstaller fork compiled via `waf`). The source code is a **hybrid py2/py3 dual-target codebase**: it already uses `sys.version_info` guards and `future`-library shims to run on both versions, but contains a number of hard blockers that prevent it from working on Python 3 in practice — primarily around dependencies pinned to 2017–2019 era versions and one complete subsystem (Azure storage) that must be fully rewritten.

The `pipe-omics` sub-binary inside the same repo was already migrated to Python 3.10 and serves as the reference build pattern.

**Recommended execution order:** 6 → 7 → 1 → 2 → 3 → 4 → 5 → 8 → 9 → 10

---

## Complexity Overview

| Task | Complexity | Risk | Effort |
|---|---|---|---|
| 1 — Syntax / compat shims (1a–1s) | Low | Low | ~3h |
| 2 — botocore.vendored imports + SSL_KEYWORDS | Low | Medium | ~1.5h |
| 3 — paramiko.py3compat | Low | Low | ~30min |
| 4 — PyJWT API | Low | Low | ~30min |
| 5 — easywebdav2 swap | Low | Medium | ~1h |
| 6 — Azure storage v1→v12 | **High** | **High** | ~2 days |
| 7 — GCS s3transfer removal + private API fixes (7b,7c,7d) | **High** | **High** | ~1.5 days |
| 8 — Requirements + click 8 + tzlocal + mock | Medium | Medium | ~1 day |
| 9 — Build system (9a–9g) | Medium | Medium | ~1 day |
| 10 — Testing | Medium | Medium | ~1 day |

**Total estimated effort: ~7–9 days of engineering work.**

---

## Tasks

---

### Task 1 — Source: pure syntax / compat-shim fixes (no logic change)

All mechanical fixes that need no understanding of business logic. Safe to do in one pass. (Subtasks 1a–1s)

#### 1a. `__metaclass__ = ABCMeta` → `class Foo(metaclass=ABCMeta):` (18 classes, 11 files)

In Python 3, `__metaclass__` is a plain class attribute — ABCMeta is silently not applied and `@abstractmethod` enforcement breaks. Fix each class declaration:

```python
# Before
class Foo(object):
    __metaclass__ = ABCMeta

# After
class Foo(metaclass=ABCMeta):
```

| File | Classes |
|---|---|
| `src/utilities/storage/common.py` | `AbstractTransferManager`, `AbstractListingManager`, `AbstractDeleteManager`, `AbstractRestoreManager` |
| `src/utilities/storage/gs.py` | `S3TransferUploadClient`, `S3TransferDownloadClient` |
| `src/utilities/storage/mount.py` | `AbstractMount` |
| `src/utilities/update_cli_version.py` | `CLIVersionUpdater` |
| `src/common/audit.py` | `AuditContainer`, `AuditConsumer` |
| `src/model/data_storage_wrapper.py` | `LocationWrapper`, `CloudDataStorageWrapper` |
| `mount/pipefuse/chain.py` | `ChainingService` |
| `mount/pipefuse/fsclient.py` | `FileSystemClient` |
| `mount/pipefuse/fslock.py` | `FileSystemLock` |
| `mount/pipefuse/mpu.py` | `MultipartUpload`, `_PartialChunk` |
| `mount/pipefuse/storage.py` | `StorageLowLevelFileSystemClient` |

#### 1b. `xrange` → `range`

- `mount/pipefuse/fuseutils.py:48–52` — replace the entire `lazy_range` compat function body with `return range(start, end)`, removing the `try/except`.

#### 1c. `xml.etree.cElementTree` → `xml.etree.ElementTree`

- `mount/pipefuse/webdav.py:18` — `import xml.etree.cElementTree as xml` → `import xml.etree.ElementTree as xml`

#### 1d. `unquote(...).decode('utf8')` — `str` has no `.decode()` in Python 3

- `mount/pipefuse/webdav.py:129` — `prop_value()` method:
  ```python
  # Before
  return default if child is None or child.text is None else unquote(child.text).decode('utf8')
  # After
  return default if child is None or child.text is None else unquote(child.text)
  ```

#### 1e. Test file: `from StringIO import StringIO` + `it.next()` + `lfilter`

All in `tests/test_utils/stdout_parsers.py`:
- Line 16: `from StringIO import StringIO` → `from io import StringIO`
- Line 18: `import future.utils` → remove entirely (after replacing all `future.utils.lfilter` calls below)
- 33+ occurrences of `it.next()` → `next(it)` (global replace-all)
- ~41 occurrences of `future.utils.lfilter(None, ...)` → `list(filter(None, ...))` (global replace-all)

#### 1f. `future.utils.lfilter` in source files

- `src/utilities/acl_operations.py:39` — `future.utils.lfilter(...)` → `list(filter(...))`; also remove both `import future` (line 18) and `from future.utils import iteritems` (line 20) after fixing all call-sites in this file
- `mount/pipe-fuse.py:82` — `future.utils.lfilter(...)` → `list(filter(...))`; also remove `import future.utils` (line 23)

#### 1g. `future.utils.iteritems` → `.items()` (7 files, 8 call-sites)

Remove `from future.utils import iteritems` and replace `iteritems(d)` with `d.items()`:

| File | Call-site(s) |
|---|---|
| `src/api/metadata.py:65` | `for key, data in iteritems(metadata_entry.data):` |
| `src/utilities/acl_operations.py:132` | `for entity_type, entities in iteritems(available_entities):` |
| `src/utilities/datastorage_operations.py:665,804` | two `iteritems(...)` calls |
| `src/utilities/metadata_operations.py:119` | `for (key, entry) in iteritems(metadata):` |
| `src/utilities/progress_bar.py:46` | `for (unit, divider) in iteritems(self.units):` |
| `src/utilities/storage/umount.py:30` | `for cmd, log in iteritems(cmd_logs):` |
| `mount/pipefuse/fslock.py:33` | `for path, lock in iteritems(locks)` |

#### 1l. `long()` builtin — does not exist in Python 3

- `src/utilities/datastorage_operations.py:819` — `size = long(float(splitted[1]))` → `size = int(float(splitted[1]))`

#### 1m. `from builtins import int` — `future` library shim, remove

- `src/utilities/progress_bar.py:17` — `from builtins import int` — this imports from the `future` package. Remove the line (stdlib `int` already has arbitrary precision in Python 3).

#### 1n. `map()` returns iterator in Python 3 — wrap with `list()` where list behavior is expected

- `src/model/data_storage_wrapper.py:253` — `return map(...)` → `return list(map(...))`
- `src/model/data_storage_wrapper.py:256` — `return map(...)` → `return list(map(...))`

#### 1h. `future.standard_library.install_aliases()` — no-op on Python 3, remove

- `src/api/data_storage.py:16–17` — remove both lines
- `src/model/data_storage_wrapper.py:20–25` — remove the block; the `from urllib.parse import urlparse` that follows stays

#### 1i. `from __future__ import` — dead on Python 3, remove

- `src/utilities/pipe_shell.py:20` — `from __future__ import print_function`
- `src/utilities/storage/azure.py:15` — `from __future__ import absolute_import`

#### 1j. `sys.version_info` guards — collapse to Python 3 branch

Delete the `if sys.version_info` check and the py2 `else` branch, keeping only the py3 code:

| File | Lines | Action |
|---|---|---|
| `pipe.py:165–168` | Delete `else:` branch with `unicode(runtime_error)` | Keep `click.echo(u'Error: {}'.format(str(runtime_error)), err=True)` |
| `mount/pipefuse/record.py:25–28` | Collapse to py3 `_BYTE_TYPES` | Keep py3 branch |
| `src/config.py:183–185` | `codec and sys.version_info[0] >= 3` → `codec` | Always true on py3 |
| `src/utilities/pipe_shell.py:33` | `PYTHON3 = sys.version_info.major == 3` | Delete constant, collapse all branches using it |
| `src/utilities/pkce.py:24–28` | Collapse to py3 branch | Keep py3 branch |
| `src/utilities/encoding_utilities.py:18–95` | Delete lines 54–95 (py2 else block) | Keep lines 18–53, unwrap the `if` |

#### 1o. `reload(sys)` + `sys.setdefaultencoding()` — does not exist in Python 3

`src/config.py:231–237` — the `change_encoding` method calls `reload(sys)` then `sys.setdefaultencoding(codec)`. In Python 3:
- `reload` is not a builtin (moved to `importlib.reload`)
- `sys.setdefaultencoding` does not exist (Python 3 defaults to UTF-8)
- The `except NameError: pass` silently swallows the failure

Since Python 3 uses UTF-8 by default and `sys.setdefaultencoding` cannot be called, replace the method body with a no-op:

```python
@classmethod
def change_encoding(cls, codec):
    pass
```

The `get_encoding` method at line 240 (`return sys.getdefaultencoding()`) remains valid — it returns `'utf-8'` on Python 3.

#### 1p. `subprocess.Popen` returns `bytes` by default in Python 3

**`src/utilities/storage/umount.py:39–42`** — `umount_proc.communicate()` returns `(bytes, bytes)` in Python 3. The `stderr` value is later used in string formatting (`'%s: %s' % (cmd, log)` at line 32). Fix by decoding:

```python
# Before
stdout, stderr = umount_proc.communicate()
exit_code = umount_proc.wait()
return exit_code, stderr

# After
stdout, stderr = umount_proc.communicate()
exit_code = umount_proc.wait()
return exit_code, stderr.decode('utf-8', errors='replace') if stderr else stderr
```

**`src/utilities/ssh_operations.py:1352`** — `perform_command()` function. `communicate()` returns `(bytes, bytes)` when `stdout=subprocess.PIPE`. The return value `out` is passed to callers expecting `str`, and in the error path it's formatted into a string at line 1356 (prints as `b'...'`). Fix by adding `text=True`:

```python
# Before (line 1348-1349)
command_proc = subprocess.Popen(executable, stdout=subprocess.PIPE, stderr=subprocess.PIPE, ...)

# After
command_proc = subprocess.Popen(executable, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, encoding='utf-8', errors='replace', ...)
```

This makes `communicate()` return `(str, str)` directly, fixing both the error message formatting and the return value to callers.

#### 1q. `sys.maxint` — removed in Python 3

- `mount/pipefuse/mpu.py:417` — `self._first_chunk = sys.maxint` → `self._first_chunk = sys.maxsize`

Note: `mount/pipefuse/gcp.py:199–201` already has a `try/except` guard and is safe.

#### 1r. Integer division `/` → `//` where integer result is required

In Python 3, `/` always returns `float`. These must use `//` (floor division) to produce `int`:

- `mount/pipefuse/gcp.py:230` — `self._max_chunk = self._max_size / self._chunk_size` → `self._max_chunk = self._max_size // self._chunk_size`
- `mount/pipefuse/mpu.py:228` — `mid_chunk = first_chunk + (last_chunk - first_chunk) / 2` → `mid_chunk = first_chunk + (last_chunk - first_chunk) // 2`
- `mount/pipefuse/mpu.py:517` — `return (part_number - 1) / self._max_composite_parts + 1` → `return (part_number - 1) // self._max_composite_parts + 1`
  - Result is used as a dict key in `self._mpus.get(mpu_number)` and in path construction. A float key (`1.0`) will never match an int key (`1`), causing duplicate MPU creation and corrupted multipart uploads.
- `src/utilities/storage/azure.py:206` — `_POLLS_ATTEMPTS = _POLLS_LIMIT / _POLLS_TIMEOUT` → `_POLLS_ATTEMPTS = _POLLS_LIMIT // _POLLS_TIMEOUT`
  - Used at line 248 in `range(0, _POLLS_ATTEMPTS)`. Python 3 `range()` does not accept floats — raises `TypeError`. This crashes every Azure blob-to-blob copy.

All four are critical: the first two affect GCS multipart, the third silently corrupts multipart upload tracking, and the fourth crashes Azure copy operations.

#### 1s. `dict.items()` returns a view — cannot index with `[0]`

- `src/utilities/user_operations_manager.py:53` — `limit_entry = active_limits.items()[0]` → `limit_entry = list(active_limits.items())[0]`

#### 1k. `try/except ImportError` dual-import blocks — collapse to Python 3 import

| File | Keep |
|---|---|
| `src/common/audit.py:27–30` | `from queue import Queue` |
| `src/model/data_storage_wrapper.py:44–47` | `from urllib.request import urlopen` |
| `src/utilities/storage/gs.py:31–43` | py3 `http.client` + `urllib.parse` imports |
| `src/utilities/storage/azure.py:30–33` | `from urllib.request import urlopen` |
| `src/utilities/storage/local.py:19–22` | `from urllib.request import urlopen` |
| `src/utilities/network_utilities.py:24–27` | `import http.client` |
| `src/utilities/storage/s3_proxy_utils.py:22–28` | `import http.client` |
| `pipe.py:106–109` | `import http.client` |
| `src/api/tool.py:15–18` | `from urllib.parse import quote` directly (NOTE: this file has the py2 `from urllib import quote` as the primary try, reversed from other files) |
| `mount/pipefuse/webdav.py:33–38` | **NOTE: This is NOT a try/except block.** It uses `py_version, _, _ = platform.python_version_tuple()` then `if py_version == '2':`. Delete the `platform.python_version_tuple()` call (line 33), the `if py_version == '2':` branch (lines 34-36), the `else:` (line 37), and un-indent the py3 imports: `from urllib.parse import urlparse, quote, unquote` |

---

### Task 2 — Fix `botocore.vendored` imports

`botocore.vendored` was removed in botocore 1.12 (2018). Replace with direct imports.

**`src/utilities/storage/s3_proxy_utils.py:17–20`:**
```python
# Remove
from botocore.vendored.requests.adapters import HTTPAdapter
from botocore.vendored.requests.packages.urllib3 import ProxyManager
from botocore.vendored.requests.packages.urllib3.connection import VerifiedHTTPSConnection
from botocore.vendored.requests.packages.urllib3.poolmanager import pool_classes_by_scheme, SSL_KEYWORDS

# Add
from requests.adapters import HTTPAdapter
from urllib3 import ProxyManager
from urllib3.connection import VerifiedHTTPSConnection
from urllib3.poolmanager import pool_classes_by_scheme, SSL_KEYWORDS
```

**IMPORTANT:** `SSL_KEYWORDS` was **removed** in urllib3 2.x (which is pulled in by `boto3>=1.34`). The stripping logic in `AwsProxyManager._new_pool()` at lines 84–87 must be preserved but rewritten to inline the constant:

```python
# Replace the SSL_KEYWORDS import with an inline constant:
_SSL_KEYWORDS = ('key_file', 'cert_file', 'cert_reqs', 'ca_certs',
                 'ssl_version', 'ssl_minimum_version', 'ssl_maximum_version',
                 'ca_cert_dir', 'ssl_context', 'key_password')

# In _new_pool():
if scheme == 'http':
    kwargs = self.connection_pool_kw.copy()
    for kw in _SSL_KEYWORDS:
        kwargs.pop(kw, None)
```

Also remove `SSL_KEYWORDS` from the import line:
```python
from urllib3.poolmanager import pool_classes_by_scheme
```

**`src/utilities/storage/s3.py`:**
```python
# Remove (line 51-52)
import botocore.vendored.requests.packages.urllib3 as boto_urllib3
boto_urllib3.disable_warnings()

# Keep (line 50 — already uses the correct direct import):
# requests.urllib3.disable_warnings()  ← this line is already correct, leave it

# Add (replaces the removed botocore.vendored import):
import urllib3
urllib3.disable_warnings()
```

Note: `s3.py` has TWO `disable_warnings()` calls — `requests.urllib3.disable_warnings()` (line 50, already correct) and `boto_urllib3.disable_warnings()` (line 52, needs replacement). Only the botocore.vendored one needs changing.

**Additional `requests.urllib3.disable_warnings()` call-sites** (working today but fragile — `requests.urllib3` is an internal alias that may change in future requests versions; prefer `urllib3.disable_warnings()` directly):
- `mount/pipefuse/s3.py:27` — `requests.urllib3.disable_warnings()` → `import urllib3; urllib3.disable_warnings()`
- `src/utilities/update_cli_version.py:48` — `requests.urllib3.disable_warnings()` → `urllib3.disable_warnings()`

These work with `requests>=2.31` today but should be normalized to direct `urllib3` imports for robustness.

---

### Task 3 — Fix `paramiko.py3compat` import

`paramiko.py3compat` was removed in paramiko 3.0. The `u()` function was a no-op on Python 3 (identity function).

**`src/utilities/pipe_shell.py:29`:**
- Remove `from paramiko.py3compat import u` — this is dead code (`u` is imported but never called anywhere in this file)
- Remove the `PYTHON3` constant (line 33); collapse all branches that check it to the py3 path
- Remove `from __future__ import print_function` (line 20)

---

### Task 4 — Fix PyJWT 2.x API

`jwt.decode(token, verify=False)` was removed in PyJWT 2.0. New signature requires `algorithms` and uses `options` dict.

Replace in **3 files**:
```python
# Before
jwt.decode(token, verify=False)

# After (internal CP token — signature not verified)
jwt.decode(token, algorithms=["RS256", "HS256"], options={"verify_signature": False})
```

Files:
- `src/config.py:294`
- `src/utilities/access_token_validation.py:29`
- `src/utilities/storage/common.py:193`

---

### Task 5 — Replace `easywebdav` with `easywebdav2`

`easywebdav==1.2.0` is Python 2-only (uses `urllib2`, `httplib`). `easywebdav2==1.3.0` is a Python 3 compatible fork that installs as the `easywebdav` module (same import name), so `import easywebdav` in `webdav.py` works unchanged.

**`mount/requirements.txt`:**
```
# Replace
easywebdav==1.2.0
# With
easywebdav2==1.3.0
```

No changes needed in `mount/pipefuse/webdav.py` for the swap itself — the fixes from Task 1 (cElementTree, unquote decode) cover all remaining issues.

Verify:
- `easywebdav.Client.__init__()` initializes `self.session`, `self.baseurl`, `self.cwd` identically (confirmed from easywebdav2 source)
- `easywebdav.OperationFailed(method, path, expected_code, actual_code)` constructor signature is unchanged (confirmed)

---

### Task 6 — Rewrite Azure storage layer

This is the largest single rewrite. `azure-storage-blob==1.5.0` (v1 SDK) was entirely replaced by v12 (azure-sdk-for-python track 2). The `BlockBlobService` class and all internal hooks (`_perform_request`, `_apply_host`) no longer exist.

**File to rewrite:** `src/utilities/storage/azure.py`

#### Imports to remove entirely (v1 SDK internals)

- `from azure.storage.blob import BlockBlobService` — replaced by `BlobServiceClient`
- `from azure.storage.common._auth import _StorageSASAuthentication` — private v1 module, no v12 equivalent needed (token refresh handled via `TokenCredential` protocol below)

#### Key API mapping

| Old (v1) | New (v12) |
|---|---|
| `BlockBlobService(account_name, account_key)` | `BlobServiceClient(account_url, credential=account_key)` |
| `BlockBlobService(account_name, sas_token)` | `BlobServiceClient(f"https://{acct}.blob.core.windows.net", credential=sas_token)` |
| `service.list_blobs(container, prefix=p)` | `container_client.list_blobs(name_starts_with=p)` |
| `service.get_blob_to_path(container, blob, path)` | `blob_client.download_blob().readinto(f)` |
| `service.create_blob_from_path(container, blob, path)` | `blob_client.upload_blob(data, overwrite=True)` |
| `service.create_blob_from_stream(...)` | `blob_client.upload_blob(stream, overwrite=True)` |
| `service.get_blob_to_stream(...)` | `blob_client.download_blob().readinto(stream)` |
| `service.delete_blob(container, blob)` | `blob_client.delete_blob()` |
| `service.generate_blob_shared_access_signature(...)` | `generate_blob_sas(account_name, container, blob, ...)` |
| `ContainerPermissions.READ` | `ContainerSasPermissions(read=True)` |
| `Blob` namedtuple | `BlobProperties` |

#### Token refresh mechanism

`RefreshingBlockBlobService` intercepted HTTP requests via `_perform_request` to inject a refreshed SAS token. In v12, implement the `TokenCredential` protocol:

```python
class RefreshingCredential:
    def __init__(self, refresh_fn):
        self._refresh_fn = refresh_fn

    def get_token(self, *scopes, **kwargs):
        token, expiry = self._refresh_fn()
        from azure.core.credentials import AccessToken
        return AccessToken(token, expiry)
```

Pass as `credential=RefreshingCredential(refresh_fn)` to `BlobServiceClient`.

#### Proxy support

`ProxyBlockBlobService._apply_host` overrode the internal host to route through a proxy. In v12:

```python
from azure.core.pipeline.transport import RequestsTransport
transport = RequestsTransport(proxies={"https": proxy_url})
BlobServiceClient(..., transport=transport)
```

**`requirements.txt`:**
```
# Replace
azure-storage-blob==1.5.0
# With
azure-storage-blob>=12.19.0
```

---

### Task 7 — Replace s3transfer orchestration for GCS multipart transfers

`src/utilities/storage/gs.py` imports `MultipartUploader`, `MultipartDownloader`, `OSUtils`, and `TransferConfig` from `s3transfer` (line 23). These private classes were removed in s3transfer 0.2.0 (2018).

The abstract classes `S3TransferUploadClient` (line 76) and `S3TransferDownloadClient` (line 96) with concrete implementations `GsCompositeUploadClient` (line 103) and `GsRangeDownloadClient` (line 172) are an adapter layer that plugged GCS I/O into the s3transfer multipart framework.

**IMPORTANT: `GsCompositeUploadClient` and `GsRangeDownloadClient` logic MUST BE PRESERVED.** They implement the GCS-native Compose API pattern (upload individual parts as temp blobs, then compose them in batches of 32). This is NOT a standard resumable upload — `google.resumable_media.requests.ResumableUpload` does NOT support this pattern. The composition logic (`Blob.compose()` via `google-cloud-storage`) is modern, stable, and correct.

**What needs replacing:** Only the `s3transfer` orchestration layer (`MultipartUploader`, `MultipartDownloader`, `OSUtils`, `TransferConfig`). These are thin wrappers that:
- `MultipartUploader`: splits file into chunks, calls `client.create_multipart_upload()`, `client.upload_part()` for each chunk, then `client.complete_multipart_upload()`
- `MultipartDownloader`: calls `client.get_object(Range=...)` for each chunk range, writes to disk
- `OSUtils`: provides `open_file_chunk_reader()` for reading file in chunks
- `TransferConfig`: holds `multipart_threshold` and `multipart_chunksize`

**Replacement approach — write a local orchestrator:**

```python
from dataclasses import dataclass

@dataclass
class GcsTransferConfig:
    multipart_threshold: int = 8 * 1024 * 1024  # 8MB
    multipart_chunksize: int = 8 * 1024 * 1024  # 8MB
    max_io_queue: int = 100

def _multipart_upload(upload_client, filename, bucket, key, config):
    """Replaces s3transfer.MultipartUploader — sequential chunk upload."""
    upload_client.create_multipart_upload(Bucket=bucket, Key=key)
    part_number = 1
    try:
        with open(filename, 'rb') as f:
            while True:
                chunk = f.read(config.multipart_chunksize)
                if not chunk:
                    break
                from io import BytesIO
                upload_client.upload_part(Bucket=bucket, Key=key, UploadId=key,
                                         PartNumber=part_number, Body=BytesIO(chunk))
                part_number += 1
        parts = [{'PartNumber': i, 'ETag': ''} for i in range(1, part_number)]
        upload_client.complete_multipart_upload(Bucket=bucket, Key=key, UploadId=key,
                                               MultipartUpload={'Parts': parts})
    except Exception:
        upload_client.abort_multipart_upload(Bucket=bucket, Key=key)
        raise

def _multipart_download(download_client, bucket, key, filename, object_size, config, callback=None):
    """Replaces s3transfer.MultipartDownloader — sequential range download."""
    with open(filename, 'wb') as f:
        offset = 0
        while offset < object_size:
            end = min(offset + config.multipart_chunksize - 1, object_size - 1)
            response = download_client.get_object(Bucket=bucket, Key=key,
                                                  Range='bytes=%d-%d' % (offset, end))
            body = response['Body']
            data = body.read() if hasattr(body, 'read') else body
            f.write(data)
            if callback:
                callback(len(data))
            offset = end + 1
```

**Call-site changes:**

`GsUploadManager.transfer()` (lines 842–844):
```python
# Before
uploader = MultipartUploader(client=upload_client, config=transfer_config, osutil=OSUtils())
uploader.upload_file(filename=to_string(source_key), bucket=..., key=..., callback=None, extra_args={})

# After
_multipart_upload(upload_client, to_string(source_key), destination_wrapper.bucket.path,
                  destination_key, transfer_config)
```

`GsDownloadManager.transfer()` (lines 720–724):
```python
# Before
downloader = MultipartDownloader(client=download_client, config=transfer_config, osutil=OSUtils())
downloader.download_file(bucket=..., key=..., filename=..., object_size=size, extra_args={}, callback=...)

# After
_multipart_download(download_client, source_wrapper.bucket.path, source_key,
                    to_string(destination_key), size, transfer_config, callback=progress_callback)
```

**`TransferConfig` replacement:**

Replace `from s3transfer import TransferConfig, MultipartUploader, OSUtils, MultipartDownloader` (line 23) with:
```python
# Remove the s3transfer import entirely. Use the local GcsTransferConfig above.
```

Update `_get_transfer_config()` (~line 853) to return `GcsTransferConfig(multipart_threshold=..., multipart_chunksize=...)` instead of `TransferConfig(...)`.

#### 7d. Fix `upload._bytes_uploaded` private attribute access

`_UploadProgressMixin._do_resumable_upload()` (line 348) accesses `upload._bytes_uploaded` on a `google.resumable_media` upload object. In `google-resumable-media>=2.0`, this was renamed to `bytes_uploaded` (public property).

**`src/utilities/storage/gs.py:348–349`:**
```python
# Before
chunk_bytes_uploaded = upload._bytes_uploaded - bytes_uploaded
bytes_uploaded = upload._bytes_uploaded

# After
chunk_bytes_uploaded = upload.bytes_uploaded - bytes_uploaded
bytes_uploaded = upload.bytes_uploaded
```

#### 7b. Remove `google.auth._helpers.from_bytes()` usage

`google.auth._helpers` is a private module. `_helpers.from_bytes()` was removed in `google-auth>=2.0` (pulled in by `google-cloud-storage>=2.16.0`).

**`src/utilities/storage/gs.py:46,950`:**
```python
# Remove
from google.auth import _helpers

# Line 950 — _RefreshingCredentials.apply():
# Before
headers['authorization'] = 'Bearer {}'.format(_helpers.from_bytes(self.temporary_credentials.session_token))
# After (session_token is already a str in Python 3)
headers['authorization'] = 'Bearer {}'.format(self.temporary_credentials.session_token)
```

**`mount/pipefuse/gcp.py:8,102`:**
```python
# Remove
from google.auth import _helpers

# Line 102 — _RefreshingCredentials.apply():
# Before
headers['authorization'] = 'Bearer {}'.format(_helpers.from_bytes(self.temporary_credentials.session_token))
# After
headers['authorization'] = 'Bearer {}'.format(self.temporary_credentials.session_token)
```

In Python 3, `from_bytes()` was a no-op (identity function for `str` input). Removing it is safe.

#### 7c. Replace `google.cloud.storage.blob._get_encryption_headers`

This private function was removed in `google-cloud-storage 2.x`.

**`src/utilities/storage/gs.py:49,283`:**
```python
# Remove
from google.cloud.storage.blob import _get_encryption_headers

# Replace with a local helper (replicating the original logic):
import hashlib
import base64

def _get_encryption_headers(key):
    """Build customer-supplied encryption headers for GCS."""
    if key is None:
        return {}
    key_bytes = key if isinstance(key, bytes) else key.encode('utf-8')
    key_hash = hashlib.sha256(key_bytes).digest()
    return {
        'X-Goog-Encryption-Algorithm': 'AES256',
        'X-Goog-Encryption-Key': base64.b64encode(key_bytes).decode('utf-8'),
        'X-Goog-Encryption-Key-Sha256': base64.b64encode(key_hash).decode('utf-8'),
    }
```

Used at line 283 in `_StreamingDownloadMixin.get_content_stream()`. The local helper produces identical headers to the removed private function.

**`requirements.txt` and `mount/requirements.txt`:**
```
# Replace
s3transfer==0.1.13
# With
s3transfer>=0.7.0        # kept for boto3 compatibility only; no longer used by GCS

# Replace
google-resumable-media==0.3.2
# With
google-resumable-media>=2.7.0

# Replace
google-cloud-storage==1.15.0
# With
google-cloud-storage>=2.16.0
```

---

### Task 8 — Update all remaining requirements

After Tasks 1–7, update all pinned versions to Python 3.12-compatible releases.

**`requirements.txt`:**

| Package | Current | Target | Notes |
|---|---|---|---|
| `dis3==0.1.3` | → **remove** | Never imported anywhere |
| `mock==2.0.0` | → **remove** | Use `unittest.mock` (stdlib since 3.3) |
| `future` | → **remove** | No imports remain after Task 1 |
| `altgraph==0.16.1` | → remove | Only needed by old custom PyInstaller fork |
| `click==6.7` | → `click>=8.1.7` | Check `CustomAbortHandlingGroup.invoke()` — click 8 changed group invocation |
| `requests==2.20.0` | → `requests>=2.31.0` | |
| `boto3==1.6.9` | → `boto3>=1.34.0` | |
| `botocore==1.9.9` | → `botocore>=1.34.0` | |
| `paramiko==2.6.0` | → `paramiko>=3.4.0` | `py3compat` gone in 3.0 (Task 3) |
| `scp==0.13.3` | → `scp>=0.15.0` | paramiko 3.x compat |
| `cryptography==2.9.2` | → `cryptography>=41.0.0` | Required for Python 3.12 |
| `pyopenssl==19.0.0` | → `pyopenssl>=23.0.0` | Depends on cryptography 41+ |
| `PyJWT==1.6.1` | → `PyJWT>=2.8.0` | API fix in Task 4 |
| `pytz==2018.3` | → `pytz>=2023.3` | |
| `tzlocal==1.5.1` | → `tzlocal>=4.3` | |
| `requests_mock==1.4.0` | → `requests_mock>=1.11.0` | |
| `pytest==3.2.5` | → `pytest>=7.4.0` | |
| `pytest-cov==2.5.1` | → `pytest-cov>=4.1.0` | |
| `beautifulsoup4==4.6.1` | → `beautifulsoup4>=4.12.0` | |
| `pypac==0.8.1` | → `pypac>=0.16.0,<0.18` | See pypac fix below |
| `colorama==0.4.1` | → `colorama>=0.4.6` | |
| `psutil==5.7.3` | → `psutil>=5.9.0` | |
| `treelib==1.5.5` | → `treelib>=1.6.0` | |
| `bcrypt==3.1.7` | → `bcrypt>=4.0.0` | |
| `pynacl==1.4.0` | → `pynacl>=1.5.0` | |
| `protobuf==3.17.3` | → `protobuf>=4.24.0` | |
| `pyasn1==0.4.5` | → `pyasn1>=0.5.0` | |
| `pyasn1-modules==0.2.4` | → `pyasn1-modules>=0.3.0` | |
| `tld==0.10` | → `tld>=0.13` | |
| `pygtrie==2.5.0` | → `pygtrie>=2.5.0` | Already Python 3 compatible; keep pin or allow newer |

**`mount/requirements.txt`:**

| Package | Current | Target |
|---|---|---|
| `easywebdav==1.2.0` | → `easywebdav2==1.3.0` | (Task 5) |
| `dis3==0.1.3` | → **remove** | |
| `altgraph==0.16.1` | → **remove** | Only needed by old custom PyInstaller fork |
| `future==0.18.2` | → **remove** | After Task 1 |
| `fusepy==3.0.1` | → `fusepy>=3.0.1` | Already Py3 compatible; verify 3.12 wheels exist |
| `boto3==1.6.9` | → `boto3>=1.34.0` | |
| `botocore==1.9.9` | → `botocore>=1.34.0` | |
| `requests==2.20.0` | → `requests>=2.31.0` | |
| `google-resumable-media==0.3.2` | → `>=2.7.0` | (Task 7) |
| `google-cloud-storage==1.15.0` | → `>=2.16.0` | (Task 7) |
| `pytz==2018.3` | → `pytz>=2023.3` | |
| `python-dateutil==2.6.1` | → `python-dateutil>=2.8.2` | |
| `python-intervals==1.10.0` | → `python-intervals>=1.10.0` | Verify Python 3.12 compat; consider `portion` if unmaintained |
| `cachetools==3.1.1` | → `cachetools>=5.3.0` | |
| `pygtrie==2.5.0` | → `pygtrie>=2.5.0` | Already Py3 compatible |

**click 8 compatibility fix:**

`src/utilities/custom_abort_click_group.py` — `CustomAbortHandlingGroup` overrides `__call__` (not `invoke`) and calls `self.main(standalone_mode=...)`. The `main()` signature is stable across click versions, but the class also calls `click.Group.__init__(self, name, commands, **attrs)` with `commands` as a positional argument. In click 8, `Group.__init__` changed `commands` to keyword-only in some code paths. Fix:

```python
# Before
click.Group.__init__(self, name, commands, **attrs)

# After
click.Group.__init__(self, name, commands=commands, **attrs)
```

Also verify that `click.exceptions.Abort`, `MissingParameter`, and `ClickException` exception handling still works (signatures are stable in click 8).

**tzlocal 4.x compatibility fix:**

`tzlocal>=3.0` changed `get_localzone()` to return a `ZoneInfo`-based object instead of a `pytz` timezone. Code that calls `.localize()` (a pytz-only method) on the result will crash with `AttributeError`.

**`src/utilities/access_token_validation.py:34,48`:**
```python
# Before (line 34)
tz = tzlocal.get_localzone()

# Line 48 — uses pytz.utc.localize() then .astimezone(tz):
def print_date_time(naive_time):
    return pytz.utc.localize(naive_time, is_dst=None).astimezone(tz).strftime('%Y-%m-%d %H:%M')

# After — use timezone-aware datetime directly (works with both pytz and ZoneInfo):
import tzlocal
from datetime import timezone

tz = tzlocal.get_localzone()

def print_date_time(naive_time):
    return naive_time.replace(tzinfo=timezone.utc).astimezone(tz).strftime('%Y-%m-%d %H:%M')
```

This avoids calling `.localize()` entirely. `datetime.replace(tzinfo=timezone.utc)` is stdlib and `.astimezone(tz)` works with any `tzinfo`-compatible object (both pytz zones and `ZoneInfo` objects).

**`src/utilities/date_utilities.py:59`** — `to_uts()` function (also called by `now_utc()` at line 94):
```python
# Before (line 59)
date_with_time_zone = Config.instance().timezone().localize(date, is_dst=None)

# After — avoid .localize() which is pytz-only:
tz = Config.instance().timezone()
if hasattr(tz, 'localize'):
    date_with_time_zone = tz.localize(date, is_dst=None)
else:
    date_with_time_zone = date.replace(tzinfo=tz)
```

This is a **critical** fix: `Config.instance().timezone()` returns `tzlocal.get_localzone()` when timezone is non-UTC (src/config.py:302). With `tzlocal>=3.0`, `get_localzone()` returns a `ZoneInfo` object that has no `.localize()` method. The `hasattr` check supports both pytz zones (which have `.localize()`) and `ZoneInfo` objects (which use `.replace(tzinfo=)`).

All other callers of `Config.instance().timezone()` use only `.astimezone()` (confirmed safe):
- `date_utilities.py:39,53`, `extension/omics.py:133`, `storage/s3.py:844`, `storage/azure.py:138`, `storage/gs.py:458`

**`src/config.py:302`** — `return tzlocal.get_localzone()` — this returns the timezone object for external use. The only dangerous caller was `date_utilities.py:59` (fixed above).

**`mock` package removal — test file fix:**

**`tests/test_pipe.py:18`:**
```python
# Before
from mock import patch

# After
from unittest.mock import patch
```

This import must be updated when `mock==2.0.0` is removed from `requirements.txt`, otherwise the test suite will fail with `ModuleNotFoundError`.

**pypac compatibility fix:**

`pypac>=0.18` deprecated `ProxyResolver.get_proxy_for_requests()` in favor of `get_proxies()`. Pin to `pypac>=0.16.0,<0.18` for now (0.16+ supports Python 3.12 but retains the old API). Alternatively, update the call-site:

**`src/config.py:359`:**
```python
# Before
return proxy_resolver.get_proxy_for_requests(url_to_resolve)

# After (if upgrading to pypac>=0.18):
proxies = proxy_resolver.get_proxies(url_to_resolve)
if proxies:
    return proxies.get('https') or proxies.get('http')
return None
```

The safer approach is pinning `<0.18` since PAC proxy is a rarely-used feature and the API change is non-trivial to verify without a PAC server.

---

### Task 9 — Update build system

#### 9a. New Linux build Docker image

Create `pipe-cli/docker/pipe-cli-builder/Dockerfile` — analogous to the existing `pipe-cli/docker/pipe-omics-builder/Dockerfile` (CentOS 7 + Python 3.10) but for Python 3.12:

- Base: `centos:7` or `rockylinux:8`
- Install Python 3.12 from source or SCL
- `pip install pyinstaller>=6.0` (standard PyPI release — no custom fork, no waf)

#### 9b. Update `build_linux.sh`

- Change `_BUILD_DOCKER_IMAGE` default from `python:2.7-stretch` to the new Python 3.12 builder image
- Delete the waf bootloader compilation block (~lines 95–101)
- Replace all `python2` → `python3`, `python2 -m pip install` → `pip install`
- Replace `python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py` → `pyinstaller`
- Remove Python 2-only `--hidden-import` flags: `UserList`, `UserString`, `commands`, `ConfigParser`, `UserDict`, `__builtin__`, `future.backports.misc`, `pkg_resources.py2_warn`

#### 9c. Update `mount/build_mount.sh`

This file is **hardcoded** to `python:2.7-stretch` (line 18, no env var override unlike `build_linux.sh`). Required changes:
- Line 18: Change `_BUILD_DOCKER_IMAGE="python:2.7-stretch"` to the new Python 3.12 builder image
- Line 32: Delete `python2 ./waf all` (waf bootloader compilation)
- Line 37: `python2 -m pip install ...` → `pip install ...`
- Line 43: `python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py ...` → `pyinstaller ...`
- Remove Python 2-only `--hidden-import` flags: `UserList`, `UserString`, `commands`, `ConfigParser`, `UserDict`, `__builtin__`, `future.backports.misc`

#### 9d. Update `build_windows.sh`

- **Step 1** (ntlmaps, `pyinstaller-win32-py2`): Assess whether ntlmaps still needs Python 2. If not, update `docker/win32/Dockerfile` to Python 3.12 in Wine. If ntlmaps is no longer needed, remove step entirely.
- **Step 3** (main pipe-cli, `pyinstaller-win64`): `docker/win64/Dockerfile` is already Python 3.6. Update to Python 3.12. Remove Python 2 `--hidden-import` flags.

#### 9e. Update `build_mac_arm.sh`

- Remove Python 2-only `--hidden-import` flags
- Upgrade from `pyinstaller==5.13.2` to `pyinstaller>=6.0`

#### 9f. Update GitHub Actions packaging (if applicable)

Note: `deploy/github_actions/gha_pack_dist.sh` does not currently exist in the repo. If a CI/CD packaging script is created or exists at a different path, ensure it:
- Uses Python 3.12 (via `actions/setup-python` or the new builder image)
- References the new `_BUILD_DOCKER_IMAGE` tag
- Does not use Miniconda2 or any Python 2 bootstrap

#### 9g. Update `setup.py`

Add Python version constraint:
```python
setup(
    ...
    python_requires='>=3.8',
    ...
)
```

---

### Task 10 — Test and validate

1. **Run test suite under Python 3.12:**
   ```bash
   cd pipe-cli
   pip install -r requirements.txt
   pytest tests/ -v
   ```

2. **Smoke test the CLI:**
   ```bash
   python3 pipe.py --help
   python3 pipe.py storage ls --help
   ```

3. **Verify abstract method enforcement** — after Task 1a `__metaclass__` fix, confirm that a subclass missing a `@abstractmethod` implementation raises `TypeError` on instantiation.

4. **Frozen binary build end-to-end:**
   ```bash
   docker build -t pipe-cli-builder pipe-cli/docker/pipe-cli-builder/
   docker run --rm -v $(pwd):/src pipe-cli-builder ./build_linux.sh
   ```
   Verify the resulting `dist/dist-file/pipe` binary runs on a CentOS 7 host.

5. **Azure storage integration test** — upload, download, list against Azure Blob Storage after Task 6 rewrite.

6. **GCS integration test** — upload, download, list against a GCS bucket after Task 7 rewrite.
