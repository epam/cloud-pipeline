# FS Browser

FS Browser is the `python/flask` service that allows to expose compute node's file system

## Requirements

- Python 2.7
- pipe cli
- pipe common
- flask

## Launching

To run app use the following command:
```
python <path to project>/app.py --working_directory=/root/ --host=0.0.0.0 --port=8080 --log_dir=/root/logs
```

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
        "path": "/root/data/file_name.txt",
        "type": "File" (or "Folder")
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
