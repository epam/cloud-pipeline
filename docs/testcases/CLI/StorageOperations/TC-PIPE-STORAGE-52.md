# RM operation delete file from non existing storage

**Actions**:
Run rm command on a non existing storage `[pipe storage rm --yes cp://do-not-exists/test.txt]`

***
**Expected result:**

Command should fail ans print appropriate error message. 