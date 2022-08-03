# Unblock test Users before tests

Test 
- verifies that test users are blocked before start tests
- unblocks test users using api methods

**Prerequisites**:
- Admin user
- Test users (`User1`, `User2`, `User3`)

| Steps | Actions | Expected results |
|:-----:|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | | 
| 3 | Select test tool | |
| 4 | Launch a tool with default settings | |
| 5 | At the **Runs** page, click the just-launched run | | 
| 6 | Wait until the **SSH** hyperlink appears | |
| 7 | In the opened tab, enter and perform the command: `curl -s -k -X GET <server_name>/pipeline/restapi/user?name=<User1>`, where `<User1>` - name of the test User1 from the Prerequisites | The command output contains json with info about `<User1>` and includes `"blocked":true` |
| 8 | Perform the command: `curl -s -k -X PUT <server_name>/pipeline/restapi/user/<User1_ID>/block?blockStatus=false`, where `<User1_ID>` - Id of the test User1 that can be taken in json obtained at step 7 | | 
| 9 | Repeat step 7 | The command output contains json with info about `<User1>` and includes `"blocked":false` |
| 10 | Repeat steps 7-9 for all test users | |

**After:**
- Stop run launched at step 4
