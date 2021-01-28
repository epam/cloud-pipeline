# LS operation [List folder with trailing delimiter]

**Actions**:
1.	Create storage (via pipe cli to register in the base) `[pipe storage save --path {storage_name} ]`
2.	Create folder `(resources/)`. Put file `test_file.txt` and subfolder `test_folder` with file `test_file2.txt` into.
3.	Upload test folder to created storage `cp://storage_name/test-case/`: `[pipe storage cp resources/ cp://storage-name/test-case/ --recursive]`
4.	Perform `ls` commands with '/' in the path: `[pipe storage ls cp://storage-name/test-case/ --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/]`
5.	Perform `ls` command without details and with '/' in the path: `[pipe storage ls cp://storage-name/test-case/]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/]`
6.	Perform `ls` command with '/' in the path and with `--recursive` option: `[pipe storage ls cp://storage-name/test-case/ --recursive --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/ --recursive]`
7.	Perform `ls` command without details, with '/' in the path and with `--recursive` option: `[pipe storage ls cp://storage-name/test-case/ --recursive]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/test-case/ --recursive]`
8.	Perform `ls` command for storage root: `[pipe storage ls cp://storage-name/ --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/]`
9.	Perform `ls` command for storage root without details: `[pipe storage ls cp://storage-name/ ]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/]`
10.	Perform `ls` command for storage and with `--recursive` option: `[pipe storage ls cp://storage-name/ --recursive --show_details]`, `[{cloud} {Cloud-specific path} ls {Cloud-specific path}://storage-name/ --recursive]`
11.	Delete storage

***
**Expected result:**

1.	Only created and uploaded on steps 2-3 data should be returned in all results. 
2.	Output commands `pipe storage ls` with `--show_details` option and `{cloud} {Cloud-specific path} ls` should match on the following points: number, order, name, size, last modified for all files.
3.	Output commands `pipe storage ls` without `--show_details` option and `{cloud} {Cloud-specific path} ls` should match on the following points: number, order, name for all files.
4.	Output command `ls` without `--recursive` option should return folder content without parent folder name in path.
5.	Output command `ls` with `--recursive` option should return folder content with parent folder name in path.