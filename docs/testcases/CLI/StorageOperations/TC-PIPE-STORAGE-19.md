# CP operation with folders: --force option

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create 1st test folder (`/resources/tmp/dir_1/`)
3.	Create 2nd test folder (`/resources/tmp/dir_2/`)
4.	Put test folder `dir_2/` to created storage (`cp://storage_name/dir_2/`)
5.	Check that rewriting works: 
    1.	Put test folder `dir_1/` to created storage `[pipe storage cp /resources/tmp/dir_1/ cp://storage_name/dir_2/ --force --recursive]`. 
    2.	Check that files have been updated correctly
    3.	Download test folder `dir_2/` back to the current folder of local machine `[pipe storage cp cp://storage_name/test_file.txt /resources/tmp/dir_2/ --force --recursive]`. 
    4.	Check that files have been updated correctly
    5. Put test folder (`dir_2`, since a copy of `dir_1` is on storage already) to created storage (`cp://storage_name/dir_3/`)
    6.	Rewrite folder to other storage `[pipe storage cp cp://storage_name/dir_1/ cp://other_storage_name/dir_3/ --force --recursive]`
    7.	Check that files have been updated correctly
6.	Delete storages

***
**Expected result:**

*2.*	Files on the storage `storage_name` have been rewritten and correct

*4.*	Files on the local machine have been rewritten and correct

*6.*	Files on the storage `other_storage_name` have been rewritten and correct