# Validation of move storage to folder

**Actions**:
1.	Create folder `/test_folder/{test_folder1}`
2.	Create folder `/test_folder/{test_folder2}`
3.	Create storage in `{test_folder1}`
4.	Call `pipe storage mvtodir -n {storage_name} -d /test_folder/{test_folder2}`
5.	Call `pipe storage mvtodir --name {storage_name} --directory /test_folder/{test_folder1}`

***
**Expected result:**

4. Storage `{storage_name}` is displayed in `/test_folder/{test_folder2}` and data storage parent ID equals `test_folder2` ID
5. Storage `{storage_name}` is displayed in `/test_folder/{test_folder1}` and data storage parent ID equals `test_folder1` ID