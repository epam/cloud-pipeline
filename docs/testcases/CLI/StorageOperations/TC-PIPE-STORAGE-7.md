# CP operation with files: --force option

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage create --path storage_name...`]
2.	Create test file (`/resources/tmp/test_file.txt`)
3.	Create other test file (`/resources/tmp/other_test_file.txt`)
4.	Put `other_test_file.txt` to storage (`cp://storage_name/other_test_file.txt`)
5.	Check that rewriting works: 
    1.	Put test file `other_test_file.txt` to created storage `[pipe storage cp /resources/tmp/other_test_file.txt cp://storage_name/test_file.txt --force]`. 
    2.	Check that test file is updated and it's correct.
    3.	Download file back to the local machine to the existing file  `[pipe storage cp cp://storage_name/test_file.txt /resources/tmp/test_file.txt --force]`. 
    4.	Check that test file is updated and it's correct.
    5.	Rewrite file to the other storage `[pipe storage cp cp://storage_name/test_file.txt cp://other_storage_name/test_file.txt --force]`
    6.	Check that test file is updated and it's correct.
6.	Delete storage.

***
**Expected result:**

*2.*	File `test_file.txt` has been updated on storage `storage_name`.

*4.*	File `test_file.txt` has been rewritten on the local machine.

*6.*	File `test_file.txt` has been updated on storage `other_storage_name`.