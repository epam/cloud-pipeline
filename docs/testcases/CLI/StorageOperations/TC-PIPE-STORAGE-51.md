# RM operation with folders

**Actions**:
1.	Create storage
2.	Create test folder `test/`. Put file `file.txt` and folder `new/` with file `new/new.txt` into
3.	Put `test/` folder to storage `cp://storage_name/test.txt`
4.	Delete `test/` folder from storage: `pipe storage rm cp://storage_name/test`
    1.	Check that error message that `--recursive` option is required is returned
5.	Delete `test/` folder from storage: `pipe storage rm cp://storage_name/test/ --recursive`
    1.	Check that all objects in folder have been deleted
6.	Delete storage

***
**Expected result:**

5. There aren't any files or folders on the storage. 