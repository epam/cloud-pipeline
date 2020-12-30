# Validation of set, get, update, delete tag for S3 objects using relative path (user should be admin and shouldn't be owner of bucket)

**Prerequisites**:

Create data storage with files in the subfolder.

**Actions**:
1.	Perform command `pipe storage set-object-tags {bucket_name} {folder_name/file_name} key1=value1`
2.	Perform command `pipe storage get-object-tags {bucket_name} {folder_name/file_name}`
3.	Perform command `pipe storage set-object-tags {bucket_name} {folder_name/file_name} key2=value2`
4.	Perform command `pipe storage get-object-tags {bucket_name} {folder_name/file_name}`
5.	Perform command `pipe storage set-object-tags {bucket_name} {folder_name/file_name} key1=value_new`
6.	Perform command `pipe storage get-object-tags {bucket_name} {folder_name/file_name}`
7.	Perform command `pipe storage delete-object-tags {bucket_name} {folder_name/file_name} key1`
8.	Perform command `pipe storage get-object-tags {bucket_name} {folder_name/file_name}`

***

**Expected result**:
1.	Message "`Tags for data storage {bucket_name} updated.`" is shown
2.	The command output contains the table with key1 in "**Tag name**" column and value1 in "**Value**" column.
3.	Message "`Tags for data storage {bucket_name} updated.`" is shown
4.	The command output contains the table with key1, key2 in "**Tag name**" column and value1, value2 in "**Value**" column.
5.	Message "`Tags for data storage {bucket_name} updated.`" is shown
6.	The command output contains the table with key1 in "**Tag name**" column and value_new in "**Value**" column.
7.	Message "`Deleted tags for data storage {data_storage}: {key1}.`" is shown
8.	The command output contains the table with key2 in "**Tag name**" column and value2 in "**Value**" column.