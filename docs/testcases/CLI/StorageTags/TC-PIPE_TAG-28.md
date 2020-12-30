# [Negative] Set/get/delete tags for a non-existing object

**Actions**:
1. Call `pipe storage set-object-tags NON_EXISTING key=value `
2. Call `pipe storage get-object-tags NON_EXISTING `
3. Call `pipe storage delete-object-tags NON_EXISTING key`

***

**Expected result**:

1-3. Should return appropriate error message and exit with 1