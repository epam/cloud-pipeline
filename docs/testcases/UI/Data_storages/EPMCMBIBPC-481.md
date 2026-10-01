# Validate of cancel button for edit bucket menu

Test verifies that canceling the edit-bucket pop-up leaves the alias and description unchanged.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Change the values of the **Alias** and **Description** fields |  |
| 2 | Click **Cancel** button | <li> the pop-up window closes <li> the storage's name and its description don't change |
