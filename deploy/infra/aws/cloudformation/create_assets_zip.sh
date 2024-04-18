#!/bin/bash
 
_SSL_CERT=$1
_SSL_KEY=$2
_SSO_METADATA=$3
_SSO_CERT=$4
 
_TMP_ASSETS_LOCATION="/tmp/cp-assets-$RANDOM"

# Create folder structure for certificates using commands
mkdir -p $_TMP_ASSETS_LOCATION/common/pki $_TMP_ASSETS_LOCATION/api/pki $_TMP_ASSETS_LOCATION/api/sso
 
# Move your certificates to common/pki directory, for example using command:
if [ -f $_SSL_CERT ] && [ -f $_SSL_KEY ]; then
   cp $_SSL_CERT $_TMP_ASSETS_LOCATION/common/pki/ca-public-cert.pem
   cp $_SSL_KEY $_TMP_ASSETS_LOCATION/common/pki/ca-private-key.pem
   else
   echo "Certificate and key not provided nothing will be copied to common/pki"
fi

if [ -z $_SSO_METADATA ] && [ -z $_SSO_CERT ]; then
   echo "Metadata and sso certificate for Identity provider settings not set, nothing will be copied to api directory"
   else
   cp $_SSO_METADATA $_TMP_ASSETS_LOCATION/api/sso/cp-api-srv-fed-meta.xml
   cp $_SSO_CERT $_TMP_ASSETS_LOCATION/api/pki/idp-public-cert.pem
   
fi 
cd  $_TMP_ASSETS_LOCATION
zip -r cp-assets.zip common api
cd - &>/dev/null
echo $(realpath $_TMP_ASSETS_LOCATION/cp-assets.zip)
