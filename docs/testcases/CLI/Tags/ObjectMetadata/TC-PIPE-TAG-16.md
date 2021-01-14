# [Negative] Delete non-existing key from an object

**Actions**:
1. Create Folder
2. Add tags `pipe tag set key=value`
3. Try to delete tags `pipe tag delete other-key`
4. Call `pipe tag get {object_type} {object_name}`

***

**Expected result**:

After step 3 should return appropriate error message and exit with 1

After step 4 should return key=value pair from step 2