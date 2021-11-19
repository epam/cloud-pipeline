# Validation of set, get, update, delete tag for object storages (user should be admin and shouldn't be owner of storage)

**Prerequisites**:

Create data storage with files in root folder.

**Actions**:
1.	Perform command `pipe storage set-object-tags {storage_name} {file_name} key1=value1`
2.	Perform command `pipe storage get-object-tags {storage_name} {file_name}`
3.	Perform command `pipe storage set-object-tags {storage_name} {file_name} key2=value2 key3=value3`
4.	Perform command `pipe storage get-object-tags {storage_name} {file_name}`
5.	Perform command `pipe storage set-object-tags {storage_name} {file_name} key1=value_new`
6.	Perform command `pipe storage get-object-tags {storage_name} {file_name}`
7.	Perform command `pipe storage delete-object-tags {storage_name} {file_name} key1`
8.	Perform command `pipe storage get-object-tags {storage_name} {file_name}`

***

**Expected result**:
1.	Command finishes with status 0.
2.	The command output contains the table with key1 in "**Tag name**" column and value1 in "**Value**" column.
3.	Command finishes with status 0.
4.	The command output contains the table with key1, key2, key3 in "**Tag name**" column and value1, value2, value3 in "**Value**" column.
5.	Command finishes with status 0.
6.	The command output contains the table with key1 in "**Tag name**" column and value_new in "**Value**" column
7.	Command finishes with status 0.
8.	The command output contains the table with key2, key3 in "**Tag name**" column and value2, value3 in "**Value**" column