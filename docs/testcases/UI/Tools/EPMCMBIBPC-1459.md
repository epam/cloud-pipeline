# [MANUAL] Parameter name duplication validation

Test verifies that entering the same valid name into several parameter name fields reports a duplicate-name error for each field except the first.

**Preparations**:
1. Complete the [EPMCMBIBPC-1446](EPMCMBIBPC-1446.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | For each parameter name field, click the **Name** field |  |
| 2 | Enter the same valid name (e.g. `newName_of_NEWparameter123`) |  |
| 3 | Click off the field | <li> error message `No duplicates are allowed` appears under every name field except the first <li> the names are kept in the fields |
