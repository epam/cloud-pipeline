# [MANUAL] Validation of create empty active notification

Test verifies creating a notification with an empty Body field.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1224](EPMCMBIBPC-1224.md) case, without filling in the **Body** field | The notification is displayed: <li>Severity - Warning <li>the title is equal to the **Title** value <li>the text is equal to the **Body** value |
