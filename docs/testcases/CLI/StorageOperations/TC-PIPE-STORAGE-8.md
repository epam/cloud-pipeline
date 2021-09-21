# MV operation with files: --force option

**Actions**:

1.	Create storage (via pipe cli to register in the base) [`pipe storage save --path storage_name...`]
2.	Create test file (`/resources/test_file.txt`)
3.	Create other test file (`/resources/tmp/other_test_file.txt`)
4.	Put `other_test_file.txt` to storage (`cp://storage_name/other_test_file.txt`)
5.	Check that rewriting works: 
    1.	Move test file `other_test_file.txt` to created storage `[pipe storage mv /resources/tmp/other_test_file.txt cp://storage_name/test_file.txt --force]`. 
    2.	Check that test file is updated and it's correct.
    3.	Move file back to the local machine to the existing file  `[pipe storage mv cp://storage_name/test_file.txt /resources/tmp/test_file.txt --force]`. 
    4.	Check that test file is updated and it's correct.
    5.	Move file to the other storage `[pipe storage mv cp://storage_name/test_file.txt cp://other_storage_name/test_file.txt --force]`
    6.	Check that test file is updated and it's correct.
6.	Delete storage.

***
**Expected result:**

*2.*	File `test_file.txt` has been updated on storage `storage_name` (at the same time it has been removed from local machine).

*4.*	File `test_file.txt` has been rewritten on the local machine (at the same time it has been removed from storage `storage_name`).

*6.*	File `test_file.txt` has been updated on storage `other_storage_name` (at the same time it has been removed from storage `storage_name`).