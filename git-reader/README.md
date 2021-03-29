# Cloud Pipeline GitReader

GitReader is the `python/flask` service that expose an additional REST API for GitLab instance

## Requirements

- Python 3.6
- flask
- git
- GitPython 3.1.14

## Launching

To run app use the following command:
```
python <path to project>/application.py --git_root=/<git>/<repo>/<root> --host=0.0.0.0 --port=8080
```

## Command line options
- --git_root (Required) - the directory in the local file system, where all git repos are located 
- --host - the host where this service will be launched. Default: 127.0.0.1
- --port - the port where this service will be launched. Default: 5000

## Launching with uWSGI

To be able to run this service in production mode uWSGI server is required

Install virtual env:
```
python3.6 -m venv gitreaderenv
source gitreaderenv/bin/activate
pip install wheel
pip install uwsgi flask
```

Install GitReader app
```
python setup.py install
```

Run GitReader with uWSGI
```
uwsgi --socket 0.0.0.0:<port> --protocol=http -w wsgi_starter:app -M -p <number of processes>
```

Or with file socket:
```
uwsgi --socket <path to socket file> -w wsgi_starter:app -M -p <number of processes>
```

## RESTful API methods

This service provides RESTful API with the following methods:
- `GET` `/git/<path:repo>/ls_tree` - Lists files via specified path on the specific ref(default `HEAD`). `repo` - the path to git repo relative to `git_root`.

Possible params:

- `path` - path inside of repo to list 
- `page` - number of page for response (default - 0)
- `page_size` - size of a page for response (default - 20)
- `ref` - specific reference on which list operation will be performed (default `HEAD`) 

Request example:
```
curl http://127.0.0.1:8080/git/<path:repo>/ls_tree
```
Response example:
```
"payload": {
        "listing": [
            {
                "id": "README.md",
                "mode": "100644",
                "name": "README.md",
                "path": "README.md",
                "type": "blob"
            },
            {
                "id": "api",
                "mode": "040000",
                "name": "api",
                "path": "api",
                "type": "tree"
            },
            {
                "id": "docs",
                "mode": "040000",
                "name": "docs",
                "path": "docs",
                "type": "tree"
            }
        ],
        "max_page": 1,
        "page": 0,
        "page_size": 20
    },
    "status": "OK"
}
```


- `GET` `/git/<path:repo>/logs_tree` - List specific path in repo on specific reference, with additional information for last commit for each listing entry. Paged response.

Possible params:

- `path` - path inside of repo to list 
- `page` - number of page for response (default - 0)
- `page_size` - size of a page for response (default - 20)
- `ref` - specific reference on which list operation will be performed (default `HEAD`) 


  
Request example:
```
curl http://127.0.0.1:8080/git/<path:repo>/logs_tree
```
Response example:
```
"payload": {
        "listing": [
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2020-11-18 14:19:53 +0300",
                "commit_message": "******",
                "id": "README.md",
                "mode": "100644",
                "name": "README.md",
                "path": "README.md",
                "type": "blob"
            },
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2021-03-09 23:08:24 +0300",
                "commit_message": "******",
                "id": "api",
                "mode": "040000",
                "name": "api",
                "path": "api",
                "type": "tree"
            }
        ],
        "max_page": 1,
        "page": 0,
        "page_size": 20
    },
    "status": "OK"
}
```

- `POST` `/git/<path:repo>/logs_tree` - List specific paths in repo on specific reference, with additional information for last commit for each listing entry.

Possible params:

- `ref` - specific reference on which list operation will be performed (default `HEAD`) 


Request data: 
```
["api", "README.md", "config/checkstyle"]
```

  
Request example:
```
curl -X POST --data '["api", "README.md", "config/checkstyle"]' http://127.0.0.1:8080/git/<path:repo>/logs_tree
```
Response example:
```
{
    "payload": {
        "has_next": false,
        "listing": [
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2021-03-09 23:08:24 +0300",
                "commit_message": "******",
                "id": "api",
                "mode": "040000",
                "name": "api",
                "path": "api",
                "type": "tree"
            },
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2020-11-18 14:19:53 +0300",
                "commit_message": "******",
                "id": "README.md",
                "mode": "100644",
                "name": "README.md",
                "path": "README.md",
                "type": "blob"
            },
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2021-01-28 16:36:28 +0300",
                "commit_message": "******",
                "id": "config/checkstyle",
                "mode": "040000",
                "name": "checkstyle",
                "path": "config/checkstyle",
                "type": "tree"
            }
        ],
        "page": 0,
        "page_size": 3
    },
    "status": "OK"
}
```


- `POST` `/git/<path:repo>/commits` - returns commits are related to specific path, author, dates. 
  
Request example:
```
curl http://127.0.0.1:8080/git/<path:repo>/commits
```

Request data: 
```
{
    "filter": {
      "path_masks": ["client/*.js"],
      "authors": ["Pavel", "ekaterina"],
      "date_to": "2021-02-01",
      "date_from": "2020-01-01",
      "ref": "develop"
    }
}
```

Response example:
```
"payload": {
        "listing": [
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2020-12-23 20:51:12 +0300",
                "commit_message": "******"
            },
            {
                "author": "******",
                "author_email": "******",
                "commit": "******",
                "commit_date": "2020-01-27 13:54:20 +0300",
                "commit_message": "******"
            }
        ],
        "max_page": 0,
        "page": 0,
        "page_size": 20
    },
    "status": "OK"
}
```
- `POST` `/git/<path:repo>/diff` - Sends diff information for each commits for specific path, author, dates. 
Request example:
```
curl -X POST --data "{...}" http://127.0.0.1:8080/git/<path:repo>/diff
```

Possible params:

- `include_diff` - if true will include output of command `git diff` 


Request data: 
```
{
    "filter": {
      "path_masks": ["client/*.js"],
      "authors": ["Pavel", "ekaterina"],
      "date_to": "2021-02-01",
      "date_from": "2020-01-01",
      "ref": "develop"
    }
}
```

Response example:
```
{
    "payload": {
        "entries": [
            {
                "commit": {
                    "author": "******",
                    "author_email": "******",
                    "commit": "******",
                    "commit_date": "2020-12-23 20:51:12 +0300",
                    "commit_message": "******"
                },
                "diff":"*****"
            },
            {
                "commit": {
                    "author": "******",
                    "author_email": "******",
                    "commit": "******",
                    "commit_date": "2019-09-18 12:50:49 +0300",
                    "commit_message": "******"
                },
                 "diff":"*****"
            }
        ],
        "filters": {
            "authors": ["Pavel"],
            "date_from": null,
            "date_to": "2021-02-01",
            "path_masks": ["*.js"],
            "ref": "HEAD"
        }
    },
    "status": "OK"
}
}
```

