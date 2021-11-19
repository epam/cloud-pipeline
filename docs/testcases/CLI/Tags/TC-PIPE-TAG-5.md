# STORAGE: Set, get, delete tags specified by data storage id

**Actions**:

Create storage: pipe storage create

1.	Create tags for object: `pipe tag set data_storage <storage ID> key_1=value_1 key_2=value_2`
2.	Delete keys: `pipe tag delete data_storage <storage ID> key_1 key_3`
3.	Update existing key: `pipe tag set data_storage <storage ID> key_1=new_value`
4.	Add new keys: `pipe tag set data_storage <storage ID> key_3=value_3 key_4=value_4`
5.	Delete all data: `pipe tag delete data_storage <storage ID>`

Delete storage

***

**Expected result**:
1.	Message: 'Metadata for storage <storage ID> updated.'
make sure data really has been uploaded: `pipe tag get storage <storage ID>`
2.	Message: 'Deleted keys from metadata for storage <storage ID>: key_1, key_3 '
make sure key_1 has been deleted: `pipe tag get storage <storage ID>`
3.	Message: 'Metadata for storage <storage ID> updated.'
make sure data really has been updated: `pipe tag get storage <storage ID>`
4.	Message: 'Metadata for storage <storage ID> updated.'
make sure data really has been updated: `pipe tag get storage <storage ID>`
5.	Message: 'Metadata for storage <storage ID> deleted.'
make sure data really has been deleted: `pipe tag get storage <storage ID>`