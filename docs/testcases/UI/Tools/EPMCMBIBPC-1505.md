# [MANUAL] Validation "docker login trouble shooting" on "Login into cloud registry" dialog

Test verifies the docker login troubleshooting panel of the **Login into cloud registry** dialog.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user that has read permissions on the registry |  |
| 2 | Do the [EPMCMBIBPC-1504](EPMCMBIBPC-1504.md) case |  |
| 3 | Click `docker login: 'x509: certificate signed by unknown authority'` | Text labels are displayed that contain: <li> `Ask administrator to download registry certificate from URL:`<br>`curl -k -s --header "Authorization: Bearer {JWT}" -o ca.crt {API_HOST}/dockerRegistry/{REGISTRY_ID}/cert"` <li> `And place it into the docker daemon directory:`<br>`mkdir -p /etc/docker/certs.d/{REGISTRY_ADDRESS}`<br>`cp ca.crt /etc/docker/certs.d/{REGISTRY_ADDRESS}/ca.crt` <li> the collapsed header `docker push/pull: 'x509: certificate signed by unknown authority'` <li> the **OK** button |
