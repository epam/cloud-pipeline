# Check update pipe version in the existing run

Test verifies that in the existing active run launched before the application update  
- the old version of pipe CLI continues to work correctly after updating the application
- the new version  of pipe CLI can be installed and works correctly

**Prerequisites**:
- Admin user

**Preparations:**

Using API methods:
1. Create test tool 
2. Specify an endpoint for the service launched in a Tool
3. Launch test tool created at step 1
4. Update application

| Steps | Actions | Expected results |
| :---: | --- |---|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Click the **CLI** tab | |
| 4 | Click the **Pipe CLI** tab | |
| 5 | Select **`Linux-Binary`** from drop-down for ***Operation system:*** | |
| 6 | Store *Pipe CLI Installation Content* | |
| 7 | Click **Generate access key** button | |
| 8 | Store *CLI configure command* | |
| 9 | Open the **Runs** page | |
| 10 | Click the run launched at step 3 of the Preparations | |
| 11 | Click the **SSH** hyperlink | |
| 12 | In the opened tab, enter and perform the command: `pipe --version` | |
| 13 | Enter and perform the command: `pipe ssh <runID>`, where `<runID>` is ID of run launched at step 3 of the Preparations | Interactive session over the SSH protocol is started |
| 14 | Enter and perform the commands from *Pipe CLI Installation Content* stored at step 6 | |
| 15 | Enter and perform the *CLI configure command* stored at step 8 | |
| 16 | Enter and perform the command: `<pipe_new_version>/pipe --version`, where `<pipe_new_version>` is the path of the new version pipe CLI installed at step 14 |  |
| 17 | Enter and perform the command: `<pipe_new_version>/pipe ssh <runID>` | Interactive session over the SSH protocol is started  |
