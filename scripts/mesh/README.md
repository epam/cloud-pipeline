# Mesh structure uploader

## Requirements

* Python 2.7 or Python 3.6

## Command line arguments

* `--url` - the url to download structure.
* `--type` - possible values: `qual` for `Qualifiers` and `desc` for `Desctiptors`.
* `--tmp_path` [Optional] - the temporary path where structures shall be downloaded. The default value is `~/.tmp`.

## Examples

To upload `Qualifiers` structure the following command can be used:
```
mesh_uploader.py --type qual --url ftp://nlmpubs.nlm.nih.gov/online/mesh/MESH_FILES/xmlmesh/qual2020.xml
```

To upload `Descriptors` structure the following command can be used:
```
mesh_uploader.py --type desc --url ftp://nlmpubs.nlm.nih.gov/online/mesh/MESH_FILES/xmlmesh/desc2020.xml
```
