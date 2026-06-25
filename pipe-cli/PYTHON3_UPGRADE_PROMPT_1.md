You are implementing a Python 2 → Python 3.12 upgrade for pipe-cli. The complete plan is at pipe-cli/PYTHON3_UPGRADE_PLAN.md — read it fully before starting.

## Scope

Implement Tasks 1, 2, 3, 4, 5, 8, 9, and 10. **Skip Tasks 6 and 7 entirely** — they will be done in a separate session. This means:
- Do NOT touch src/utilities/storage/azure.py beyond what Tasks 1i, 1k, 1r specify (the __future__ import removal, try/except ImportError collapse, and integer division fix at line 206)
- Do NOT touch src/utilities/storage/gs.py beyond what Task 1a specifies (the __metaclass__ fix for S3TransferUploadClient and S3TransferDownloadClient)
- Do NOT touch mount/pipefuse/gcp.py beyond what Task 1r specifies (the integer division fix at line 230)
- Do NOT remove or modify the s3transfer, google-resumable-media, google-cloud-storage, or azure-storage-blob version pins in requirements.txt — leave them at current values for now
- Do NOT remove the `from google.auth import _helpers` or `from google.cloud.storage.blob import _get_encryption_headers` imports

## Execution Order

1a → 1b → 1c → 1d → 1e → 1f → 1g → 1h → 1i → 1j → 1k → 1l → 1m → 1n → 1o → 1p → 1q → 1r → 1s → 2 → 3 → 4 → 5 → 8 → 9 → 10

## Critical Rules

1. **Follow the plan exactly.** Every file path, line number, and code change has been verified against the actual codebase. Do not improvise alternative approaches.
2. **One subtask at a time.** Complete 1a, then 1b, etc. Do NOT batch changes across multiple subtasks.
3. **After each numbered task (not subtask), run:** `cd pipe-cli && python3 -c "import pipe"` to verify no import errors were introduced. For Task 1, run this after completing all subtasks 1a–1s.
4. **Never change functionality.** Every public method must behave identically after the change.
5. **Preserve all error handling semantics.** If code caught a specific exception before, it must catch the same logical exception after.
6. **Do not add type hints, docstrings, comments, or refactor** anything not specified in the plan. Minimal diff only.
7. **Commit after each completed numbered task** with message format: `chore(pipe-cli): python3 - task N - <short description>`

## Task-Specific Guidance

**Tasks 1a–1s (mechanical):** Safe replacements. Use editor replace-all where the plan says "global replace-all". For everything else, change only the exact lines referenced. Pay attention to:
- Task 1r has 4 integer division fixes (not just 2) — including azure.py:206 and mpu.py:517
- Task 1k: webdav.py:33-38 is an if/else version check, NOT a try/except — read the plan note
- Task 1p has two files: umount.py AND ssh_operations.py

**Task 2 (botocore.vendored):** The `_SSL_KEYWORDS` inline constant must be defined at module level in s3_proxy_utils.py. Also normalize the `requests.urllib3.disable_warnings()` calls in mount/pipefuse/s3.py and src/utilities/update_cli_version.py to direct `urllib3.disable_warnings()`.

**Task 8 (requirements):**
- Skip azure-storage-blob, google-cloud-storage, google-resumable-media, and s3transfer version changes (those are Task 6/7 scope)
- Pin pypac to `>=0.16.0,<0.18` (not >=0.18.3 — the API changed)
- After updating, run `pip install -r requirements.txt` in a clean Python 3.12 venv to verify resolution
- Remember the click 8 fix (commands= keyword), tzlocal fix (both access_token_validation.py AND date_utilities.py), and mock→unittest.mock fix

**Task 9 (build):** Update scripts only — do NOT run Docker builds. Remove Python 2-only --hidden-import flags, replace python2 references, remove waf blocks.

**Task 10 (testing):** Run what you can: `python3 pipe.py --help`, `python3 -c "import pipe"`, and `pytest tests/ -x -v`. Some tests may fail due to Tasks 6/7 not being done yet (azure/gcs imports) — that's expected.

## Verification (run after all tasks complete)

```bash
cd pipe-cli
python3 -c "import pipe; import src.config; import src.utilities.storage.s3"
python3 pipe.py --help
# This grep must return ZERO results (excluding azure.py, gs.py, gcp.py which are Task 6/7 scope):
grep -r "from future\|import future\|__metaclass__\|xrange\|from StringIO\|from Queue import\|botocore.vendored\|from builtins\|from paramiko.py3compat" --include="*.py" . | grep -v PYTHON3_UPGRADE_PLAN | grep -v "storage/azure.py" | grep -v "storage/gs.py" | grep -v "pipefuse/gcp.py"
```

## If You Get Stuck

- If a line number is off by a few lines (due to earlier edits in the same file), find the pattern by context — the code snippet in the plan is authoritative.
- If you encounter an issue not covered by the plan, stop and tell me. Do not improvise.

Start by reading the full plan file, then begin with Task 1a.
