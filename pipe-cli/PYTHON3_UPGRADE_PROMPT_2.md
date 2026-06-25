You are continuing a Python 2 → Python 3.12 upgrade for pipe-cli. Tasks 1–5, 8–9 have already been completed in a prior session. The codebase has been cleaned of all Python 2 patterns EXCEPT in the Azure and GCS storage modules.

The complete plan is at pipe-cli/PYTHON3_UPGRADE_PLAN.md — read Tasks 6 and 7 fully before starting. Also read the current state of these files to understand what the prior session already changed (Task 1a metaclass fixes, Task 1r integer division fixes, and Task 1k import collapses have already been applied).

## Scope

Implement Tasks 6 and 7 only.

## Execution Order

7 → 6

Do Task 7 (GCS) first — it's more contained and lower risk. Task 6 (Azure) is the full SDK rewrite.

## Critical Rules

1. **Read the plan carefully.** Task 7 explicitly says to PRESERVE GsCompositeUploadClient and GsRangeDownloadClient logic. Only replace the s3transfer orchestration wrapper.
2. **Incremental implementation.** For each task, implement one operation at a time, verify it compiles, then move to the next.
3. **Never change the public interface.** The classes GsUploadManager, GsDownloadManager, AzureUploadManager, AzureDownloadManager, AzureListingManager, AzureDeleteManager must expose the same methods with the same signatures.
4. **Commit after each task** with message format: `chore(pipe-cli): python3 - task N - <short description>`

## Task 7 — GCS (do first)

Order of operations:
1. Read src/utilities/storage/gs.py fully to understand current state
2. Remove the s3transfer import (line 23) and replace with the local `GcsTransferConfig` dataclass and `_multipart_upload`/`_multipart_download` functions from the plan
3. Update `GsUploadManager.transfer()` (~line 842) to use `_multipart_upload` instead of `MultipartUploader`
4. Update `GsDownloadManager.transfer()` (~line 720) to use `_multipart_download` instead of `MultipartDownloader`
5. Update `_get_transfer_config()` to return `GcsTransferConfig` instead of `TransferConfig`
6. Apply fix 7b: remove `from google.auth import _helpers`, replace `_helpers.from_bytes(...)` with direct usage in both gs.py and mount/pipefuse/gcp.py
7. Apply fix 7c: replace `from google.cloud.storage.blob import _get_encryption_headers` with the local helper function
8. Apply fix 7d: replace `upload._bytes_uploaded` with `upload.bytes_uploaded`
9. Update requirements.txt: `s3transfer==0.1.13` → `s3transfer>=0.7.0`, `google-resumable-media==0.3.2` → `>=2.7.0`, `google-cloud-storage==1.15.0` → `>=2.16.0`
10. Update mount/requirements.txt: same google-resumable-media and google-cloud-storage changes
11. Verify: `python3 -c "from src.utilities.storage.gs import GsManager"`

**Key constraint:** The `GsCompositeUploadClient.complete_multipart_upload()` method uses `Blob.compose()` — this is the google-cloud-storage public API and is correct. Do NOT change this logic. The local `_multipart_upload` function just needs to call the same client methods (create_multipart_upload, upload_part, complete_multipart_upload) that `MultipartUploader` was calling.

## Task 6 — Azure (do second)

This is a full rewrite of src/utilities/storage/azure.py from azure-storage-blob v1 SDK to v12.

Order of operations:
1. Read src/utilities/storage/azure.py fully to understand all operations
2. Implement the new imports and credential classes:
   - `BlobServiceClient` replaces `BlockBlobService`
   - `RefreshingCredential` (TokenCredential protocol) replaces `RefreshingBlockBlobService._perform_request` token injection
   - Proxy support via `RequestsTransport(proxies=...)` replaces `ProxyBlockBlobService._apply_host`
3. Rewrite operations one at a time in this order:
   a. `list_blobs` — simplest, verify the iteration/prefix behavior matches
   b. `get_blob_properties` / `get_blob_metadata`
   c. `delete_blob`
   d. `get_blob_to_path` (download)
   e. `create_blob_from_path` / `create_blob_from_stream` (upload)
   f. `copy_blob` — note: v12 copy is async, you must poll `copy_status`
   g. `generate_account_shared_access_signature` → `generate_blob_sas` / `generate_account_sas`
   h. `make_blob_url` → construct URL manually: `https://{account}.blob.core.windows.net/{container}/{blob}`
4. Preserve the thread-safe credential refresh with Lock (same pattern as original)
5. The proxy must resolve per-request URL (call `StorageOperations.get_proxy_config(request_url)`)
6. Update requirements.txt: `azure-storage-blob==1.5.0` → `azure-storage-blob>=12.19.0`
7. Verify: `python3 -c "from src.utilities.storage.azure import AzureManager"`

**Key constraints:**
- `_POLLS_ATTEMPTS` integer division fix (line 206) was already applied in the prior session
- The `from __future__ import absolute_import` removal was already applied
- The `try/except ImportError` for urlopen was already collapsed to `from urllib.request import urlopen`
- `Blob` namedtuple references in results must return objects with compatible `.name` and `.properties.content_length` attributes — check all consumers of list_blobs results
- The `TransferBetweenAzureBucketsManager` class uses `copy_blob` and polls completion — in v12, `start_copy_from_url()` returns a dict with `copy_status`, poll until `'success'`

## Verification (run after both tasks complete)

```bash
cd pipe-cli
python3 -c "import pipe; import src.config; import src.utilities.storage.s3; import src.utilities.storage.azure; import src.utilities.storage.gs"
python3 pipe.py --help
python3 pipe.py storage ls --help
# Final grep — should return ZERO results:
grep -r "from future\|import future\|__metaclass__\|xrange\|from StringIO\|from Queue import\|botocore.vendored\|from builtins\|from paramiko.py3compat\|from google.auth import _helpers\|from google.cloud.storage.blob import _get_encryption_headers\|from s3transfer import\|from azure.storage.common" --include="*.py" . | grep -v PYTHON3_UPGRADE_PLAN
```

## If You Get Stuck

- Read the pipe-omics subdirectory (pipe-cli/pipe-omics/) for reference patterns — it's already Python 3.10
- If azure-storage-blob v12 API differs from what the plan describes, check the actual installed package: `python3 -c "import azure.storage.blob; help(azure.storage.blob.BlobServiceClient)"`
- If you encounter an undocumented issue, stop and tell me. Do not improvise.

Start by reading Tasks 6 and 7 in the plan file, then read the current state of gs.py and azure.py.
