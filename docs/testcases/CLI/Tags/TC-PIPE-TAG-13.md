# [Negative] Set/get/delete tags for a non-existing object

**Actions**:
1. Call `pipe tag set folder NON_EXISTING key=value`
2. Call `pipe tag get folder NON_EXISTING`
3. Call `pipe tag delete folder NON_EXISTING key`
4. Call `pipe tag delete folder NON_EXISTING`

***

**Expected result**:

1-4. Should return appropriate error message and exit with 1