# FS Browser

FS Browser is the `python/flask` service that allows to expose compute node's file system

## Requirements

- Python 3.6
- pipe cli

## Launching

To run app with python 3 use the following command:
```
pip install -r requirements.txt
python <path to project>/app.py \
    --working_directory=/root/ \
    --vs_working_directory=/git-workdir/ \
    --host=0.0.0.0 \
    --port=8080 \
    --log_dir=/root/logs \ 
    --transfer_storage <stoarge path>
```

To build executable use the following commands:

```
export FSBROWSER_SOURCES_DIR=/path/to/fsbrowser/src/dir
export FSBROWSER_DIST_PATH=/path/to/dist
export FSBROWSER_RUNTIME_TMP_DIR=/tmp
export PYINSTALLER_PATH=/pyinstaller
bash /path/to/fsbrowser/src/dir/build.sh
```
and then run:
```
/path/to/dist/app/app \
    --working_directory=/root/ \
    --vs_working_directory=/git-workdir/ \
    --host=0.0.0.0 \
    --port=8080 \
    --log_dir=/root/logs \ 
    --transfer_storage <stoarge path>
```

## Command line options
- --working_directory (Required) - the directory on compute node 
- --vs_working_directory (Required) - the directory where versioned storages shall be stored
- --transfer_storage (Required) - the cloud path for transferring data: <storage name>/<path to cloud directory>. If the <path to cloud directory> is not specified a bucket root will be used
- --host - the host where this service will be launched. Default: 127.0.0.1
- --port - the port where this service will be launched. Default: 5000
- --process_count - the number of threads available for this service. Default: 2
- --log_dir - the path to log directory
- --run_id - the run ID for Cloud Pipeline logger. If that value is not specified the stdout will be used. 

## RESTful API methods

This service provides RESTful API with the following methods:
-  `/view/<path>` - lists files via specified path. `path` - the path to file/folder relative to `working_directory`. The `working_directory` is the path to directory that will be shared for user. This is the required parameter that should be specified when application is started. 
Request example:
```
curl http://127.0.0.1:8080/view/data
```
Response example:
```
{    
    "payload": [{
        "name": "file_name.txt",
        "path": "data/file_name.txt",
        "type": "File" (or "Folder"),
        "size": 1 (size in bytes, avilable for Files only)
    }],
    "status":"OK"
}
```
- `/download/<path>` - transfers file/folder via specified path on common object storage. If the user initiate to download the folder the `.tar.gz`  file will be provided for user. This action is long running so launches asynchronously.  The result of this request is the `task_id` that should be used for task status monitoring. When the download operation is completed a new folder with unique name `task_id` will be created on common object storage. The result of the whole download operation is the sign url to the file in `task_id` bucket's folder (e.g. sing url will be generated for object that located with s3://CP_CAP_EXPOSE_FS_STORAGE/3fcecb8986a34c54939dbaf8c4a2238b/data.tar.gz).
Request example:
```
curl http://127.0.0.1:8080/download/data
```
Response example:
```
{    
    "payload": {
        "task": "3fcecb8986a34c54939dbaf8c4a2238b"
    },
    "status":"OK"
}
```
- `/status/<task_id>` - returns specified task status. Allowed states:  'pending', 'success', 'running', 'failure' or 'canceled'. If the task is finished with status 'failure' an error message will be returned. If the task is completed successfully the task result will be returned.
Request example:
```
curl http://127.0.0.1:8080/status/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
       "status":"success",
	    "result": {
		    "expires":"2019-05-30 08:53:49.602",
		    "url":"..."
	    }
    },
    "status":"OK"
}
```
- `/cancel/<task_id>` - cancels task computation. Cleanups data if needed. 
Request example:
```
curl http://127.0.0.1:8080/cancel/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
       "status":"canceled"
	}
    },
    "status":"OK"
}
```
- `/uploadUrl/<path>` - returns presigned url to upload file to storage by path cp://CP_CAP_EXPOSE_FS_STORAGE/<task_id>/<path> where `task_id` - random generated string, `path` - file location on compute node.
Request example:
```
curl http://127.0.0.1:8080/uploadUrl/data/file.txt
```
Response example:
```
{    
    "payload": {
       "task":"3fcecb8986a34c54939dbaf8c4a2238b",
	"url": {
		"expires":"2019-05-30 08:53:49.602",
		"url":"..."
	}
    },
    "status":"OK"
}
```
- `/upload/<task_id>` - transfers uploaded file from bucket to compute node. If the current task is still in progress but was canceled the created file will be removed.   
Request example:
```
curl http://127.0.0.1:8080/upload/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
       "task":"3fcecb8986a34c54939dbaf8c4a2238b"
    },
    "status":"OK"
}
```
- `/delete/<path>` - removes file/folder from compute node.
Request example:
```
curl http://127.0.0.1:8080/delete/data/file.txt
```
Response example:
```
{    
    "payload": {
       "path":"data/file.txt"
    },
    "status":"OK"
}
```
- `GET /vs/list` - loads all versioned storage specified for current run

Request example:
```
curl http://127.0.0.1:8080/vs/list
```
Response example:
```
{    
    "payload": {
       [
            {
                "id": "1",
                "name": "repo_name",
                "path": "/path/to/repo",
                "revision": "xxxx",
                "detached": false
            }, ...
        ]
    },
    "status":"OK"
}
```
- `POST /vs/<id>/clone?[revision=<revision_number>]` - clones versioned storage specified by `ID`. Optionally supports 
revision. If revision was specified a `READ ONLY` regime will be enabled. To switch `READ ONLY` regime fetch data 
from server. This operation returns task ID since may take a long time. Use `status/<task_id>` method to check result.

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/clone
```
Response example:
```
{    
    "payload": {
       "task": "3fcecb8986a34c54939dbaf8c4a2238b"
    },
    "status":"OK"
}
```
Then wait for task completion and get task result:
```
curl http://127.0.0.1:8080/status/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
        "result": "/path/to/repo",
        "status": "success"
    },
    "status":"OK"
}
```
- `GET /vs/<id>/detached` - checks if regime `READ ONLY` was specified (in this situation `HEAD` is detached). If this 
check returns `true` the `commit` operation shall not be available.

Request example:
```
curl http://127.0.0.1:8080/vs/1/detached
```
Response example:
```
{    
    "payload": {
       "detached": false
    },
    "status":"OK"
}
```
- `POST /vs/<id>/fetch` - refreshes repository. If head detached reverts all local changes. If head not detached and conflicts were detected an error with conflicted files will be returned. This operation returns task ID since may take a long time. Use `status/<task_id>` method to check result.
 
Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/fetch
```
Response example:
```
{    
    "payload": {
       "task": "3fcecb8986a34c54939dbaf8c4a2238b"
    },
    "status":"OK"
}
```
Then wait for task completion and get task result:
```
curl http://127.0.0.1:8080/status/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
        "status": "success"
    },
    "status":"OK"
}
```

- `POST /vs/<id>/conflicts` - registers changes after conflicts resolving. If `path` request parameter specified 
performs `git add` operation. If specified file has no conflicts an error will be occurred. If `path` is not 
specified and HEAD detached this method performs `git checkout HEAD` operation to set HEAD after refresh. 
This method shall be called after all conflicts resolving caused by the `fetch` operation.

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/conflicts?path=<path/to/file>
```

- `POST /vs/<id>/checkout?revision=<revision_number>` - checkouts to specified revision number. After checkout 
operation a `READ ONLY` regime will be enabled. To switch `READ ONLY` regime fetch data from server. Reverts 
all local changes if any.

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/checkout?revision=aaa
```
- `GET /vs/<id>/status` - loads repository current status: changed files, merge state and unsaved changes existence

Request example:
```
curl http://127.0.0.1:8080/vs/1/diff
```
Response example:
```
{    
    "payload": {
       "files": [
          { "file": "relative/file_name1"; "status": "modified", "binary": false, "new_size": 1, "old_size": 2 },
          { "file": "relative/file_name2"; "status": "created",  "binary": true, "new_size": 1, "old_size": null },
          { "file": "relative/file_name3"; "status": "deleted", "binary": true, "new_size": null, "old_size": 2 }
       ],
       "merge_in_progress": false,
       "unsaved": false
    },
    "status":"OK"
}
```
- `GET /vs/<id>/diff/files?path=<relative_path_to_file>[&raw=<true/false>&lines_count=1]` - loads diff for specified
file. If `raw` request parameter specified this method returns `git diff` output. Specify `lines_count` to adjust 
additional lines count into the output (default: 3).

Request example:
```
curl http://127.0.0.1:8080/vs/1/diff/files?path=file.test
```
Response example if `raw` not specified:
```
{    
    "payload": {
        "binary": false,
        "deletions": 1,
        "insertions": 1,
        "new_name": "file.test",
        "old_name": "file.test",
        "lines": [ 
            { 
                "content": "this line was not changed\n", 
                "content_offset": -1,  
                "new_lineno": 1, 
                "num_lines": 1, 
                "old_lineno": 1, 
                "origin": " "
            },
            { 
                "content": "this line was removed\n", 
                "content_offset": 26,  
                "new_lineno": -1, 
                "num_lines": 1, 
                "old_lineno": 2, 
                "origin": "-"
            },
            { 
                "content": "this line was added\n", 
                "content_offset": 26,  
                "new_lineno": 2, 
                "num_lines": 1, 
                "old_lineno": -1, 
                "origin": "+"
            }, ...
        ]
    },
    "status": "OK"
}
```
Response example if `raw` specified:
```
{    
    "payload": {
        "binary": false,
        "deletions": 1,
        "insertions": 1,
        "new_name": "file.test",
        "old_name": "file.test",
        "raw_output": "diff --git a/file.test b/file.test\nindex xxxx..xxxx xxx\n--- a/file.test\n+++ b/file.test\n@@ -1,3 +1,3 @@\n this line was not changed\n-this line was removed\n+this line was added\n \n"
    },
    "status": "OK"
}
```
Response example for binary files:
```
{    
    "payload": {
        "binary": true,
        "new_name": "file.test",
        "old_name": "file.test",
        "new_size": 12,
        "old_size": 34
    },
    "status": "OK"
}
```

- `GET /vs/<id>/diff/conflicts?path=<relative_path_to_file>[&revision=<revision>&raw=<true/false>&lines_count=1]` -
returns `git diff` for specified `path` when merge is in progress. Returns diff between `revision` and last common
commit between local and remote trees. If `revision` not specified the `HEAD` will be used.
The response is similar to `GET /vs/<id>/diff/files` request.

- `GET /vs/<id>/diff/fetch/conflicts?path=<relative_path_to_file>[&raw=<true/false>&lines_count=1&remote=<true/false>]` -
returns `git diff` for specified `path` for conflicts after `fetch` operation. If `remote` parameter is true returns 
diff between newly loaded changes and commit before stash. If `remote` is false (default) returns diff from stash.
The response is similar to `GET /vs/<id>/diff/files` request.

- `POST /vs/<id>/commit?message=<message>[&files=file1,file2]` - saves local changes to remote: fetches repo, adds 
changed files, commits changes and pushes to remote. This operation returns task ID since may take a long time.
 Use `status/<task_id>` method to check result.

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/commit?message=test
```
Response example:
```
{    
    "payload": {
       "task": "3fcecb8986a34c54939dbaf8c4a2238b"
    },
    "status": "OK"
}
```
Then wait for task completion and get task result:
```
curl http://127.0.0.1:8080/status/3fcecb8986a34c54939dbaf8c4a2238b
```
Response example:
```
{    
    "payload": {
        "status": "success"
    },
    "status": "OK"
}
```
If conflicts detected an error with conflicted files will be returned:
```
{    
    "payload": {
        "status": "failure",
        "message": "Automatic merge failed; fix conflicts and then commit the result.",
        "conflicts": ["/path/to/conflicted/file1", "/path/to/conflicted/file2"]
    },
    "status": "OK"
}
```
- `POST /vs/<id>/files?path=<path_to_file>` - saves file content after conflicts were resolved. This operation 
returns task ID since may take a long time. Use `status/<task_id>` method to check result.
- `GET /vs/<id>/files?path=<path_to_file>` - returns local current file content
- `POST /vs/<id>/revert` - reverts all local changes

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/revert
```
- `POST /vs/<id>/remove` - removes versioned storage from run

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/remove
```

- `POST /vs/<id>/merge/abort` - aborts merge process. If not merge process found an error will be occurred.

Request example:
```
curl -X POST http://127.0.0.1:8080/vs/1/merge/abort
```

- `POST /vs/<vs_id>/checkout/path?path=<file path>&remote=<true/false>` - provides ability to accepts 
`theirs` or `ours` changes. This method checkouts specified file. This file shall contain conflicts. 
If `remote` flag (default: false) specified the remote (or `theirs`) changes shall be accepted. 
Otherwise local (or `ours`).
