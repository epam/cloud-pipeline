# Check tool accessibility for another user

Test verifies that another user without permissions gets a 401 error opening a run's endpoint.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-494](EPMCMBIBPC-494.md) case |  |
| 2 | Logout |  |
| 3 | Login as another user |  |
| 4 | Open the **Runs** page |  |
| 5 | Click **View other available active runs** |  |
| 6 | Click on the tool launched at step 1 |  |
| 7 | Wait until the hyperlink in the header opposite the **Endpoint** label appears |  |
| 8 | Click the hyperlink that appeared at step 7 | A message with the error `401 Unauthorized` appears |
