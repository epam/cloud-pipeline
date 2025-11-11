# HCS Parser

## Description

Tool for performing search and processing (converting) microscope images of different formats to the ome.tiff.
Currently, the following initial formats are supported:

- TIFF
- CZI

HCS Parser can be run in two modes:

- `Standalone mode` - one node will go through all hcs roots and process it one by one
  To run hcs-parser in this mode, execute `start.sh`
- `Cluster mode` - master node will go through all hcs roots and execute `Standalone mode` for each file on different
  node (by `SGE job` or `pipe run`)
  To run hcs-parser in this mode, execute `start_cluster.sh`

## Input parameters

All input parameters passed as environment variable.

### Common parameters

| Name                                          | Description                                                                                                        |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| HCS_ROOT_TYPE                                 | Type of the input hcs roots which are located in HCS_LOOKUP_DIRECTORIES or HCS_TARGET_PATHS (Supported: TIFF, CZI) |
| HCS_OBJECT_META_FILE                          | Name of the file with metadata about process of Harmony synchronization. TIFF supported only!                      |
| HCS_LOOKUP_DIRECTORIES                        | Directories to search hcs_roots location into. Comma separated list of paths                                       |
| HCS_TARGET_PATHS                              | Paths of hcs_roots location. Comma separated list of paths                                                         |
| HCS_PARSING_LOGS_OUTPUT                       | Datastorage cloud path where processing logs will be uploaded during image processing                              |
| HCS_PARSING_TAG_MAPPING                       | Comma separate list of <XML tag name>=<Cloud-pipeline tag name>                                                    |
| HCS_PARSING_OUTPUT_FOLDER                     | Filesystem local path, where to store result of the processing (hcs files + directory with ome.tiff related files) |
| HCS_PARSING_PREVIEW_FIELDS_USE_ABSOLUTE_PATHS |                                                                                                                    |
| HCS_PARSING_IMAGE_DIR_NAME                    | Name of the folder where tiff images is located inside a hcs_root folder. TIFF supported only!                     |
| HCS_PARSING_INDEX_FILE_NAME                   | Name of the index.xml file inside a hcs_root folder. TIFF supported only!                                          |
| HCS_PARSING_PLATE_DETAILS_DICT                | Json string with plate types details. TIFF supported only!                                                         |
| HCS_SKIP_MARKERS                              | List if file names, comma separated. If such file exists in hcs_root, such hcs root will be skipped.               |
| JAVA_OPTS                                     | Java options that will be propagated to the underling bioformats java processes for image processing               |

### Cluster mode parameters

| Name                                                    | Description                                                                                                                                                                                                                                                                                              |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| HCS_CLUSTER_INSTANCE_SLOT_SIZE                          | Number of SGE slots per node that will be available for process hcs root.                                                                                                                                                                                                                                |
| HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_INSTANCE_SLOT | This parameter is used to calculate memory requirements to process hcs_root as: HCS_CLUSTER_INSTANCE_SLOT_SIZE * HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_INSTANCE_SLOT                                                                                                                                 |
| HCS_PARSING_CLUSTER_PROCESSING_MEMORY_FACTOR            | If HCS_CLUSTER_INSTANCE_SLOT_SIZE is not defined, this factor will be used to define HCS_CLUSTER_INSTANCE_SLOT_SIZE as hcs_root_size / HCS_PARSING_CLUSTER_PROCESSING_MEMORY_FACTOR                                                                                                                      |
| HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_CLUSTER_SLOT  | If HCS_CLUSTER_INSTANCE_SLOT_SIZE is not defined, this value will be used to define memory requirements to process hsc_root as: <br/>hcs_root_size / HCS_PARSING_CLUSTER_PROCESSING_MEMORY_FACTOR * HCS_CLUSTER_PROCESSING_MEMORY_CLUSTER_SLOT / HCS_PARSING_CLUSTER_PROCESSING_MEMORY_PER_INSTANCE_SLOT |
| HCS_WORKER_INSTANCE_TYPE                                | Instance type to be used to run cluster worker with pipe run command. Pipe run cluster option only.                                                                                                                                                                                                      |
| HCS_WORKER_MEMORY_GB                                    | Max memory limit to be propagated to JMV opts for underlying bioformats java processes. Pipe run cluster option only.                                                                                                                                                                                    |

### Notification parameters

| Name                  | Description                                                                          |
|-----------------------|--------------------------------------------------------------------------------------|
| HCS_NOTIFY_USERS      | Comma separated list of emails to send notification to                               |
| HCS_DEPLOY_NAME       | Name of the platform which will be used in notification emails                       |
| HCS_DATA_STORAGE_ID   | Id of the input source datastorage which will be used to generate notification email |
| HCS_MARKUP_STORAGE_ID | Id of the output datastorage which will be used to generate notification email       |
