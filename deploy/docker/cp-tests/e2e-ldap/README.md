## This is a samba LDAP server (rfc2307) used in E2E automated tests to check the lock status of users.
### Prerequisites
* For connections to the server using 389 tcp port;
* Domain: DN=cppl,DN=com;
* Login: administrator@cppl.com;
* Password: Test123

* The server has three users USER1, USER2, USER3 with a password that matches the name.
USER1 and USER3 - disabled.

* To run this image with a local docker use `--privileged` parameter. 
For example: `docker run -d --privileged -p 389:389 cp-ldap`
