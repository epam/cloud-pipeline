# [MANUAL] Validation of "Login into cloud registry" dialog

Test verifies the content of the **Login into cloud registry** dialog opened from **How to configure**.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user that has read permissions on the registry |  |
| 2 | Go to **Tools** |  |
| 3 | Select any group |  |
| 4 | Click the gear icon |  |
| 5 | Click **How to configure** | The **Configure docker client** pop-up is displayed, that contains: <li> the **Login into cloud registry** header <li> text like `docker login 18.195.69.178:5000 -u {USER_NAME} -p '{JWT_TOKEN}'` <li> the label **Troubleshooting** <li> the collapsed header `docker login: 'x509: certificate signed by unknown authority'` <li> the collapsed header `docker push/pull: 'x509: certificate signed by unknown authority'` <li> the **OK** button |
