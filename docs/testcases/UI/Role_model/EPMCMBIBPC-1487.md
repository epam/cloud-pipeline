# [MANUAL] Check accessibility of read-denied tool from detached configuration

Test verifies that a tool with read denied does not appear in the Docker image picker of a detached configuration, even though the user has read on its group.

**Prerequisites**:
- The user has "read" right on the tools group
- The user has "read" denied on a tool in that group
- The [EPMCMBIBPC-1642](EPMCMBIBPC-1642.md) case is completed

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Navigate to the detached configuration from the [EPMCMBIBPC-1642](EPMCMBIBPC-1642.md) case |  |
| 3 | Click the **Docker image** field |  |
| 4 | Select the tools group from the prerequisites in the pop-up that appears | There is no tool from the prerequisites in the list |
