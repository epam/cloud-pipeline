# [MANUAL] Launch form shall perform parameter names validation

Test verifies that an invalid parameter name on the launch form is rejected with an error message.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Click the **RUN** button |  |
| 3 | Add a parameter |  |
| 4 | Enter an invalid name (parameter names may contain only upper-case letters, lower-case letters, underscore(s), digits, and must start with a letter) | An error message is displayed near the parameter name input field |
