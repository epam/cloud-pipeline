# [MANUAL] Validation "docker push/pull trouble shooting" on "Login into cloud registry" dialog

Test verifies the docker push/pull troubleshooting panel of the **Login into cloud registry** dialog.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user that has read permissions on the registry |  |
| 2 | Do the [EPMCMBIBPC-1504](EPMCMBIBPC-1504.md) case |  |
| 3 | Click `docker push/pull: 'x509: certificate signed by unknown authority'` | The expanded panel displays: <li> the text `Configure docker engine trust to system-wide CAs` <li> instructions *(assuming `/etc/docker/certs.d/{REGISTRY_ADDRESS}` contains `ca.crt`)*:<br>`# For ubuntu`<br>`cd /etc/docker/certs.d/{REGISTRY_ADDRESS}`<br>`cp ca.crt registry-ca.crt`<br>`cat /etc/ssl/certs/ca-certificates.crt registry-ca.crt >> ca.crt`<br>`# For centos/rhel`<br>`cd /etc/docker/certs.d/{REGISTRY_ADDRESS}`<br>`cp ca.crt registry-ca.crt`<br>`cat /etc/pki/tls/certs/ca-bundle.crt registry-ca.crt >> ca.crt` <li> the **OK** button |
