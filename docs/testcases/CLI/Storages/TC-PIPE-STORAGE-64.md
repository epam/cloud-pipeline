# [Negative] CP operation with tags

**Actions**:
1.  Create storage
2.	Try to upload file to storage: `pipe storage cp source destination --tags key1`
3.	Try to upload file to storage: `pipe storage cp source destination --tags key1=`
4.	Try to upload file to storage: `pipe storage cp source destination --tags key1=wr#ong`
5.  Delete storage

***
**Expected result:**
1.	File is uploaded successfully
2.	All tags are added