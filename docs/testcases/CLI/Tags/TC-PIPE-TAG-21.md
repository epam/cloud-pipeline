# Validation of set, get, update, delete tag for specified version of object storages using relative path (user should be admin and shouldn't be owner of storage)

**Prerequisites**:

Create data storage with files in the subfolder.
File shall have several versions and shall not have any tags.

**Actions**:
1.	Perform command `pipe storage ls cp://{storage_name}/{\file_name} -v -l` and save **Version** field value with label STANDARD and without label "latest"
2.	Perform command `pipe storage set-object-tags {storage_name} {folder_name/file_name} key1=value1 -v {version}`
3.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name}`
4.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name} -v {version}`
5.	Perform command `pipe storage set-object-tags {storage_name} {folder_name/file_name} key2=value2`
6.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name}`
7.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name} -v {version}`
8.	Perform command `pipe storage set-object-tags {storage_name} {folder_name/file_name} key1=value_new -v {version}`
9.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name} -v {version}`
10.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name}`
11.	Perform command `pipe storage delete-object-tags {storage_name} {folder_name/file_name} key1 -v {version}`
12.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name} -v {version}`
13.	Perform command `pipe storage get-object-tags {storage_name} {folder_name/file_name}`

***

**Expected result**:
1.	Command output contains info about file that includes numbers of file versions
2.	Message "`Tags for data storage {storage_name} updated.`"
3.	Message that file doesn't have any tags is shown.
4.	The command output contains the table with key1 in "**Tag name**" column and value1 in "**Value**" column.
5.	Message "`Tags for data storage {storage_name} updated.`" is shown.
6.	The command output contains the table with key2 in "**Tag name**" column and value2 in "**Value**" column.
7.	The command output contains the table with key1 in "**Tag name**" column and value1 in "**Value**" column.
8.	Message "`Tags for data storage {storage_name} updated.`" is shown.
9.	The command output contains the table with key1 in "**Tag name**" column and value1 in "**Value**" column.
10.	The command output contains the table with key2 in "**Tag name**" column and value2 in "**Value**" column.
11.	Message "`Deleted tags for data storage {data_storage}: {key1}.`" is shown.
12.	Message that file doesn't have any tags is shown.
13.	The command output contains the table with key2 in "**Tag name**" column and value2 in "**Value**" column.