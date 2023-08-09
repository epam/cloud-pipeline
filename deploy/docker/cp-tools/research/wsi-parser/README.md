# Description
**WSI parser** is a tool to process WSI (whole stack image) files. It allows performing:
1. Deep zoom generation
2. Tags mapping - tags are assigned to the original file in a bucket according to mapping rules specified 

## Configuration parameters

- `WSI_TARGET_DIRECTORIES` - comma-separated list of paths, describing directories, that need to be checked
- `WSI_FILE_FORMATS` - comma-separated list of image extensions, that will be processed (default: `vsi`, `mrxs`)
- `WSI_PARSING_DZ_IMAGE_LIMIT` - value used to define the image in the series, that will be used to generate DZ
- `WSI_PARSING_TAG_MAPPING` - comma-separated list, containing mapping between the metadata tags in a processed file and system dictionaries
> tag1=cp_system_dictionary_name_1;...;tagN=cp_system_dictionary_name_N
- `WSI_PARSING_CONVERSION_LIMIT` - an area limit in pixels on a chunk processing during conversion to PNG
- `WSI_PARSING_THREADS` - number of threads, that will be used for file processing (single-thread processing is the default)
- `WSI_ACTIVE_PROCESSING_TIMEOUT_MIN` - amount of minutes a parser will consider a file being processed by another process before starting processing (based on the last modification of temporary progress stat file)
- `WSI_PARSING_MULTI_TISSUE_DELIMITER` - the separator for Tissue metadata values (default: `:`)
- `WSI_PARSING_TAG_DELIMITER` - the tag values separator in case of multiple metadata values (default: `;`)