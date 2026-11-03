# [MANUAL] [NEGATIVE] Set of allow checkbox validation

Test verifies that checking/unchecking one privilege checkbox does not incorrectly affect the "read" checkbox's state.

**Prerequisites**:
- Complete the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case
- Check the "execute" checkbox in the allow column

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Uncheck the **execute** checkbox in the **allow** column | The **read** checkbox in the **allow** column remains checked |
| 2 | Check the **execute** checkbox in the **deny** column | The **read** checkbox in the **allow** column remains checked |
| 3 | Check the **read** checkbox in the **deny** column | The **read** checkbox in the **deny** column is checked |
| 4 | Uncheck all checkboxes | All checkboxes are unchecked |
| 5 | Check the **write** checkbox in the **allow** column | The **read** checkbox in the **allow** column remains checked, same as in step 1 |
| 6 | Repeat action steps 1-4 with the **write** checkbox |  |
| 7 | Check the **execute** checkbox | The **read** checkbox in the **allow** column remains checked |
| 8 | Check the **write** checkbox |  |
