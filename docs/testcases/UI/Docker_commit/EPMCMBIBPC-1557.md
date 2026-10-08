# [MANUAL] [NEGATIVE] Invalid tool name in commit pop-up error validation

Test verifies that invalid tool names in the commit pop-up are rejected with the expected error message.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Complete the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 2 | Enter an invalid name for the tool (e.g. `test-`) |  |
| 3 | Click **COMMIT** button | When an incorrect tool name is entered, an error message appears below the address field: `Image name should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator` |
| 4 | Repeat steps 2-3 with: `TEST`, `_test`, `test!ng` | After step 3, nothing happens |
