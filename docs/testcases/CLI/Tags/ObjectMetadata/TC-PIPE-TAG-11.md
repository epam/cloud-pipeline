# STORAGE: role model for tags specified by data storage id

**Actions**:

Create data storage by User1 (User2 shouldn't have any permissions for this data storage)

1.	Create tags for object by User1: `pipe tag set data storage <data storage ID> key_1=value_1 key_2=value_2`
2.	Try to get tags for object by User2 (non-admin): `pipe tag get data storage <data storage ID>`
3.	Grant read permissions for User2
4.	Try to get tags for object by User2 (non-admin): `pipe tag get data storage <data storage ID>`
5.	Try to perform all write operations: 
    1.	Try to create tags for object by User2 (non-admin): `pipe tag set data storage <data storage ID> key_1=value_1 key_2=value_2`
    2.	Try to delete keys for object by User2 (non-admin): `pipe tag delete data storage <data storage ID> key_1 key_3`
    3.	Try to update existing key for object by User2 (non-admin): `pipe tag set data storage <data storage ID> key_1=new_value`
    4.	Try to add new keys for object by User2 (non-admin): `pipe tag set data storage <data storage ID> key_3=value_3 key_4=value_4`
    5.	Try to delete all data for object by User2 (non-admin): `pipe tag delete data storage <data storage ID>`
6.	Grant write permissions for User2
7.	Try to perform all write operations
8.	Try to perform all read operations

Delete data storage

***

**Expected result**:
1.	Tags created successfully
2.	Access denied
3.	Permissions granted successfully
4.	Tags listed successfully
5.	Access denied
6.	Permissions granted successfully
7.	Access denied
8.	Tags listed successfully