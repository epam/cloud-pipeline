# Block test Users after tests

Test 
- blocks test users using API methods
- verifies that test users are blocked after finish tests

**Prerequisites**:
- Admin user
- Test users (`User1`, `User2`, `User3`)
- Perform [TC-TEST-ENVIRONMENT-1](TC-TEST-ENVIRONMENT-1.md) case

| Steps | Actions                                                                                                                                                                                                                                                                                                                                                                    | Expected results |
|:---:|---|---|
| 1 | Login as the admin user from the prerequisites                                                                                                                                                                                                                                                                                                                             | |
| 2 | Open the **Tools** page                                                                                                                                                                                                                                                                                                                                                    | | 
| 3 | Select test tool                                                                                                                                                                                                                                                                                                                                                           | |
| 4 | Launch a tool with default settings                                                                                                                                                                                                                                                                                                                                        | |
| 5 | At the **Runs** page, click the just-launched run                                                                                                                                                                                                                                                                                                                          | | 
| 6 | Wait until the **SSH** hyperlink appears                                                                                                                                                                                                                                                                                                                                   | |
| 7 | In the opened tab, enter and perform the command: `curl -s -k -X GET --header 'Authorization: Bearer '<API_TOKEN> <server_name>/pipeline/restapi/user?name=<User1>`, <br> where `<User1>` is name of the test User1 from the Prerequisites, <br> `<API_TOKEN>` is *Access key* stored at step 6 of the [TC-TEST-ENVIRONMENT-1](TC-TEST-ENVIRONMENT-1.md) case Preparations | The command output contains json with info about `<User1>` and includes `"blocked":false"` |
| 8 | Perform the command: `curl -s -k -X PUT --header 'Authorization: Bearer '<API_TOKEN> <server_name>/pipeline/restapi/user/<User1_ID>/block?blockStatus=true`, where `<User1_ID>` - Id of the test User1 that can be taken in json obtained at step 7                                                                                                                        | | 
| 9 | perform the command: `curl -s -k -X GET --header 'Authorization: Bearer '<API_TOKEN> <server_name>/pipeline/restapi/user?name=<User1>`, where `<User1>` - name of the test User1 from the Prerequisites                                                                                                                                                                    | The command output contains json with info about `<User1>` and includes `"blocked":true` |
| 10 | Repeat steps 7-9 for all test users                                                                                                                                                                                                                                                                                                                                        | |

**After:**
- Stop run launched at step 4
