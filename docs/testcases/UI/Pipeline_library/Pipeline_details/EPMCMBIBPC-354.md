# Storage rule create

Test verifies creating a second storage rule.

**Prerequisites**:
- Perform the [EPMCMBIBPC-353](EPMCMBIBPC-353.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Add new rule** button | A pop-up window appears; in that window, there is a disabled **Pipeline** field that contains a pipeline name |
| 2 | Specify a value into the **File mask** field |  |
| 3 | Set the **Move to STS** checkbox |  |
| 4 | Click **Create** button | In the table, a second record appears: <li> the **Mask** field shows the value specified at step 2 <li> the **Created** field shows the current time and date <li> the **Move to Short-Term Storage** field shows a checked, disabled checkbox |
