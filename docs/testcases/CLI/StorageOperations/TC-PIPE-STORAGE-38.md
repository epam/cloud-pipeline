# LS operation [List single file]

**Actions**:
1.	Create storage (via pipe cli to register in the base) `[pipe storage save --path {storage_name} --onCloud]`
2.	Create folder (`resources/test-case/`). Put file `test_file.txt` into.
3.	Upload test file to created storage `cp://storage_name/test-case/`: `[pipe storage cp resources/test-case/test_file.txt cp://storage-name/test-case/test_file.txt]`
4.	Upload test files to root of created storage `cp://storage_name/`: `[pipe storage cp resources/test_file.txt cp://storage-name/test_file.txt]`
5.	Perform `ls` command for file in folder: `[pipe storage ls cp://storage-name/test-case/test_file.txt --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/test_file.txt]`
6.	Perform `ls` command for file in folder without details: `[pipe storage ls cp://storage-name/test-case/test_file.txt]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case --recursive]`
7.	Perform `ls` command without details, without '/' in the path and with `--recursive` option: `[pipe storage ls cp://storage-name/test-case --recursive]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/test_file.txt --recursive]`
8.	Perform `ls` command for file in root: `[pipe storage ls cp://storage-name/test_file.txt --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test_file.txt]`
9.	Perform `ls` command for file in root without details: `[pipe storage ls cp://storage-name/test_file.txt --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test_file.txt]`
10.	Perform `ls` command for file in root and with `--recursive` option: `[pipe storage ls cp://storage-name/test_file.txt --recursive --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test_file.txt --recursive]`
11.	Delete storage

***
**Expected result:**

1.	Only 1 file should be returned in all results. 
2.	Output commands `pipe storage ls` with `--show_details` option and `{cloud} {Cloud-specific path} ls` should match on the following points: number, order, name, size, last modified for all files.
3.	Output commands `pipe storage ls` without `--show_details` option and `{cloud} {Cloud-specific path} ls` should match on the following points: number, order, name for all files.
4.	Output command `ls` without `--recursive` option should return without relative path, with `--recursive` option - with relative path.