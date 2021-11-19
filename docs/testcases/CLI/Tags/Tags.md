# Tags testcases

This folder contains testcases to check different operations with attributes for Cloud Pipeline objects (sub-commands of the command `pipe tag`) and attributes for files in data storages (commands `pipe storage get-object-tags`, `pipe storage set-object-tags` and `pipe storage delete-object-tags`).

| Case ID | Description/name |
|---|---|
| [**TC-PIPE-TAG-1**](TC-PIPE-TAG-1.md) | PIPELINE: Set, get, delete tags specified by pipeline id |
| [**TC-PIPE-TAG-2**](TC-PIPE-TAG-2.md) | PIPELINE: Set, get, delete tags specified by pipeline name |
| [**TC-PIPE-TAG-3**](TC-PIPE-TAG-3.md) | FOLDER: Set, get, delete tags specified by folder id |
| [**TC-PIPE-TAG-4**](TC-PIPE-TAG-4.md) | FOLDER: Set, get, delete tags specified by folder name |
| [**TC-PIPE-TAG-5**](TC-PIPE-TAG-5.md) | STORAGE: Set, get, delete tags specified by data storage id |
| [**TC-PIPE-TAG-6**](TC-PIPE-TAG-6.md) | STORAGE: Set, get, delete tags specified by name |
| [**TC-PIPE-TAG-7**](TC-PIPE-TAG-7.md) | PIPELINE: role model for tags specified by pipeline id |
| [**TC-PIPE-TAG-8**](TC-PIPE-TAG-8.md) | PIPELINE: role model for tags specified by pipeline name |
| [**TC-PIPE-TAG-9**](TC-PIPE-TAG-9.md) | FOLDER: role model for tags specified by folder id |
| [**TC-PIPE-TAG-10**](TC-PIPE-TAG-10.md) | FOLDER: role model for tags specified by folder name |
| [**TC-PIPE-TAG-11**](TC-PIPE-TAG-11.md) | STORAGE: role model for tags specified by data storage id |
| [**TC-PIPE-TAG-12**](TC-PIPE-TAG-12.md) | STORAGE: role model for tags specifies be data storage name |
| [**TC-PIPE-TAG-13**](TC-PIPE-TAG-13.md) | \[Negative\] Set/get/delete tags for a non-existing object |
| [**TC-PIPE-TAG-14**](TC-PIPE-TAG-14.md) | \[Negative\] Set/get/delete tags for a non-existing class |
| [**TC-PIPE-TAG-15**](TC-PIPE-TAG-15.md) | \[Negative\] Set tags with invalid value (without '=' delimiter) |
| [**TC-PIPE-TAG-16**](TC-PIPE-TAG-16.md) | \[Negative\] Delete non-existing key from an object |
| [**TC-PIPE-TAG-17**](TC-PIPE-TAG-17.md) | \[Negative\] Delete key/all keys from an object without metadata at all |
| [**TC-PIPE-TAG-18**](TC-PIPE-TAG-18.md) | Validation of set, get, update, delete tag for object storages (user should be admin and shouldn't be owner of storage) |
| [**TC-PIPE-TAG-19**](TC-PIPE-TAG-19.md) | Validation of set, get, update, delete tag for object storages using relative path (user should be admin and shouldn't be owner of storage) |
| [**TC-PIPE-TAG-20**](TC-PIPE-TAG-20.md) | Validation of set, get, update, delete tag for specified version of object storages (user should be admin and shouldn't be owner of storage) |
| [**TC-PIPE-TAG-21**](TC-PIPE-TAG-21.md) | Validation of set, get, update, delete tag for specified version of object storages using relative path (user should be admin and shouldn't be owner of storage) |
| [**TC-PIPE-TAG-22**](TC-PIPE-TAG-22.md) | Validation of set, get, update, delete tag for object storages (user shouldn't be admin and should be owner of storage) |
| [**TC-PIPE-TAG-23**](TC-PIPE-TAG-23.md) | Validation of set, get, update, delete tag for object storages using relative path (user shouldn't be admin and should be owner of storage) |
| [**TC-PIPE-TAG-24**](TC-PIPE-TAG-24.md) | Validation of set, get, update, delete tag for specified version of object storages (user shouldn't be admin and should be owner of storage) |
| [**TC-PIPE-TAG-25**](TC-PIPE-TAG-25.md) | Validation of set, get, update, delete tag for specified version of object storages using relative path (user shouldn't be admin and should be owner of storage) |
| [**TC-PIPE-TAG-26**](TC-PIPE-TAG-26.md) | Validation of get tag for object storages by non-admin and non-owner user |
| [**TC-PIPE-TAG-27**](TC-PIPE-TAG-27.md) | Validation of set, update, delete tag for object storages by non-admin and non-owner user |
| [**TC-PIPE-TAG-28**](TC-PIPE-TAG-28.md) | \[Negative\] Set/get/delete tags for a non-existing object |
| [**TC-PIPE-TAG-29**](TC-PIPE-TAG-29.md) | \[Negative\] Set tags with invalid value (without '=' delimiter) |
| [**TC-PIPE-TAG-30**](TC-PIPE-TAG-30.md) | \[Negative\] Delete non-existing key from an object |
