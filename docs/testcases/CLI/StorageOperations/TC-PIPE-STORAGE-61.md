# CP operation with tags

**Actions**:
1.  Create storage
2.	Upload file to created storage with tags: `pipe storage cp source destination --tags key1=value1 --tags key2=value2`
3.	Check tags via aws: `aws cpapi get-object-tagging --storage storage_name --key file_name`
4.  Delete storage

***
**Expected result:**

2.	File is uploaded
3.	All tags are added