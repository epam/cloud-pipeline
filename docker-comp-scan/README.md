# Docker Component Scan

Provides information about docker container installed packages:

 * Python
 * Java
 * R
 * Ruby
 * etc.

# 1. How to install

### Build Jar

Run command
~~~
./gradlew docker-comp-scan::bootRepackage
~~~

and jar will be available as `build/libs/docker-comp-scan-1.0.jar`

### Application properties

```
worker.threads.count - Number of layers that can be scanned at the same time
```
```
number.cached.scans - Number of image scans that stored in the cache 
```
```
expire.cached.scan.time - After this time cached result will be invalidated and removed from cache (in hours)
```
```
base.working.dir - Directory for temporary unzipped docker layers, see Section 3 for more details 
```
```
enable.analyzers - List of analyser names (splitted by comma) that should be enable for serching dependencies 
```
```
ssl.insecure.enable - Disable check of ssl sertificate sign
```

# 2. Analyzers

~~~
ANALYZER_JAR
ANALYZER_ARCHIVE
ANALYZER_NODE_PACKAGE
ANALYZER_PYTHON_DISTRIBUTION
ANALYZER_PYTHON_PACKAGE
ANALYZER_AUTOCONF
ANALYZER_CMAKE
ANALYZER_NUSPEC
ANALYZER_NUGETCONF
ANALYZER_ASSEMBLY
ANALYZER_BUNDLE_AUDIT
ANALYZER_OPENSSL
ANALYZER_COMPOSER_LOCK
ANALYZER_NSP_PACKAGE
ANALYZER_RETIREJS
ANALYZER_SWIFT_PACKAGE_MANAGER
ANALYZER_COCOAPODS
ANALYZER_RUBY_GEMSPEC
ANALYZER_CENTRAL
ANALYZER_NEXUS
ANALYZER_R_PACKAGE
~~~

# 3. Working directory

It possible to use [tmpfs](http://man7.org/linux/man-pages/man5/tmpfs.5.html) for performance reasons. 
For using this, run with sudo script `configure_tmpfs.sh`, it will configure tmpfs in your system and mount directory `/tmp/dockercompscan` in a RAM memory (maximum size of the directory = RAM/2).
You can specify maximum size of this directory by passing the parameter: `configure_tmpfs.sh <size>`

* The size may have a k, m, or g suffix for Ki, Mi, Gi (binary kilo (kibi), binary mega (mebi) and binary giga (gibi)).
~~~
configure_tmpfs.sh 2g
~~~

* The size may also have a % suffix to limit this instance to a percentage of physical RAM.  
~~~
configure_tmpfs.sh 40%
~~~

after that you should overwrite parameter `base.working.dir` for that reason put near to jar file application.properties with line:
~~~
base.working.dir=/tmp/dockercompscan
~~~

# 4. API

### Run a layer scan.

##### [POST] /scan

If requst paremeter block set to true, requset will be blocked until scanning will be done.

body:
~~~
{
    "layer" : {
        "name" : <Layer sha256 value>,
		"path" : <Path to the docker layer in a docker registry>,
		"parentName" : <Parent layer sha256 value>,
		"headers" : {
            "Authorization" : "Bearer <token>"
        }
    }
}
~~~

### Get result of scanning process for all layers in an image.

##### [GET] /scan

Response:
~~~
{
    "layerScanList": [
        {
            "layerId": "sha256:b3d5090c598636d01ff1580d24725d99f0e54a3ea9a7c6e07218ccaa33f1600c",
            "status": "SUCCESSFUL",
            "parentId": null,
            "dependencies": [
                {
                    "id": 1,
                    "layerId": "sha256:b3d5090c598636d01ff1580d24725d99f0e54a3ea9a7c6e07218ccaa33f1600c",
                    "version": "1.5.1",
                    "name": "tensorflow-tensorboard"
                },
                ...
            ]
        },
        {...}
    ]
}
~~~
