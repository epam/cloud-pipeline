# Create copy of exist storage rule

Test verifies that creating a duplicate storage rule is rejected with a SQL error.

**Prerequisites**:
- Perform the [EPMCMBIBPC-353](EPMCMBIBPC-353.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Add new rule** button |  |
| 2 | Specify `*` into the **File mask** field |  |
| 3 | Set the **Move to STS** checkbox |  |
| 4 | Click **Create** button | <li> the pop-up window is still open <li> the error message `Your operation has been aborted due to SQL error` appears |
