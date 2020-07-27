# Cloud Pipeline installation prerequisites

## Cloud Provider specific configuration

A number of the resources shall be created/configured in the underlying Cloud Provider.

Please refer to the corresponding section for the details:

* [AWS](aws.md)
* [Azure](azure.md)
* [GCP](gcp.md)

## Domain names

The following DNS entries are considered to access the Cloud Pipeline GUI and route the requests to the other services.

* Cloud Pipeline GUI/API
  * Record type: `A`
  * Record value: `<Cloud Pipeline Core Instance IP>`
  * Example: cloud-pipeline.epam.com
* Embedded git access for the "well-established" pipelines
  * Record type: `CNAME`
  * Record value: `<Cloud Pipeline GUI/API>`
  * Example: git.cloud-pipeline.epam.com
* Access to the docker registry for pull/push operation
  * Record type: `CNAME`
  * Record value: `<Cloud Pipeline GUI/API>`
  * Example: docker.cloud-pipeline.epam.com
* Access to the "interactive" services, controlled by the Cloud Pipeline
  * Record type: `CNAME`
  * Record value: `<Cloud Pipeline GUI/API>`
  * Example: edge.cloud-pipeline.epam.com

## SSL/TLS certificates

SSL/TLS certificate shall be issued using an available CA (shall be trusted by the users' workstations) or purchased from the external CA.

## SAML/SSO configuration

The following IdP configuration is required:

* Cloud Pipeline GUI
  * Service Provider URL: https://`<Cloud Pipeline GUI/API>`/pipeline
  * Service Provider ACS URL: https://`<Cloud Pipeline GUI/API>`/pipeline/saml
  * SAML Binding: `HTTP Redirect`
  * Assertion information:
    * NameID
    * Email
    * FirstName
    * LastName
* Cloud Pipeline Git
  * Service Provider URL: https://`<Embedded git>`
  * Service Provider ACS URL: https://`<Embedded git>`/users/auth/saml/callback
  * SAML Binding: `HTTP POST`
  * Assertion information:
    * NameID
    * Email
    * FirstName
    * LastName
