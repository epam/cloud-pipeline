# Process of changing certificates on kube cluster

## Kubernetes v1.15

```
# Check the expiration
kubeadm certs check-expiration

# It's also possible to renew specific certs only, instead of all
# Check `kubeadm certs renew --help`
kubeadm certs renew all

# Restart kube containers
systemctl stop docker
systemctl stop kubelet
systemctl start docker
systemctl start kubelet

# Renew auth tokens for the Cloud Pipeline API
\cp /etc/kubernetes/admin.conf /root/.kube/config
# Copy /root/.kube/config to all the API nodes
```

## Kubernetes v1.7.5

There is a script to backup old one and generate new one certificates for kube cluster
In order to change certificates:

 - Copy this script to some file on kube master node
 - Replace placeholders for `<KUBE_IP_HOST>`, `<KUBE_API_HOSTNAME>`
 - Increase DAYS_CERT if needed (365 by default)
 - `chmod +x` this file
 - Run this script, it will save old certificates in `./kubernetes-bk` and generates and print to the console new one
 - You will need manually replace `client-certificate-data` in several files in according to script's output
 - Reboot kube master node
 - Login again wait until all kube-system pod will be ready and cluster will recover to the working state
 - Now cluster should work properly 

```
set -e

KUBE_FOLDER=/etc/kubernetes
KUBE_IP_HOST=<Kube master IP>
KUBE_API_IP=<generally it is a 10.96.0.1>
KUBE_API_HOSTNAME=$(hostname -s)

KUBE_BACKUP_FOLDER=./kubernetes-bk
KEYS_FOLDER=./keys
TEMP_FOLDER=./temp
CERT_FOLDER=./certs
DAYS_CERT=365

function get-private-key {
    for f in $(ls $KUBE_FOLDER/*.conf)
    do
        cat $f | grep client-key-data | sed 's/client-key-data://' | sed -e 's/^[ \t]*//' | base64 -d > $KEYS_FOLDER/$(basename $f .conf).key
    done
    cp $KUBE_FOLDER/pki/apiserver.key $KEYS_FOLDER/apiserver.key
    cp $KUBE_FOLDER/pki/apiserver-kubelet-client.key $KEYS_FOLDER/apiserver-kubelet-client.key
    cp $KUBE_FOLDER/pki/front-proxy-client.key $KEYS_FOLDER/front-proxy-client.key
}

function create-backup {
   cp -r /etc/kubernetes $KUBE_BACKUP_FOLDER
}

function prepare {
    mkdir -p $KEYS_FOLDER $TEMP_FOLDER $CERT_FOLDER
}

function remove-all {
    rm -rf $KUBE_BACKUP_FOLDER $KEYS_FOLDER $TEMP_FOLDER $CERT_FOLDER
}

function create_cnf () {
FILENAME=$1
CN=$2
O=$3
echo " - $FILENAME.cnf"
if [ -z "$O" ]
then
cat <<EOF > $TEMP_FOLDER/$FILENAME
[req]
distinguished_name = req_distinguished_name
x509_extensions     = v3_req
prompt = no
[req_distinguished_name]
commonName = $CN
[v3_req]
keyUsage           = digitalSignature, keyEncipherment
extendedKeyUsage   = clientAuth
EOF
else
cat <<EOF > $TEMP_FOLDER/$FILENAME
[req]
distinguished_name = req_distinguished_name
x509_extensions     = v3_req
prompt = no
[req_distinguished_name]
commonName = $CN
O          = $O
[v3_req]
keyUsage           = digitalSignature, keyEncipherment
extendedKeyUsage   = clientAuth
EOF
fi
}

function create_api_cnf {
echo " - apiserver.cnf"
cat <<EOF > $TEMP_FOLDER/apiserver.cnf
[req]
distinguished_name = req_distinguished_name
x509_extensions     = v3_req
prompt = no
[req_distinguished_name]
commonName = kube-apiserver
[v3_req]
keyUsage           = digitalSignature, keyEncipherment
extendedKeyUsage   = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = $KUBE_API_HOSTNAME
DNS.2 = kubernetes
DNS.3 = kubernetes.default
DNS.4 = kubernetes.default.svc
DNS.5 = kubernetes.default.svc.cluster.local
IP.1 = $KUBE_API_IP
IP.2 = $KUBE_IP_HOST
EOF
}

function create_csr {
    for f in $(ls $TEMP_FOLDER/*.cnf)
    do
        FN=$(basename $f .cnf)
	echo " - $FN.csr"
        openssl req -new -key $KEYS_FOLDER/$FN.key -out $TEMP_FOLDER/$FN.csr -config $TEMP_FOLDER/$FN.cnf
    done
}

function create_cert {
    for f in $(ls $TEMP_FOLDER/*.csr)
    do
        FN=$(basename $f .csr)
	echo " - $FN.crt"
        if [ "$FN" == "front-proxy-client" ]
        then
            openssl x509 -req -days $DAYS_CERT -in $TEMP_FOLDER/$FN.csr -CA $KUBE_FOLDER/pki/front-proxy-ca.crt -CAkey $KUBE_FOLDER/pki/front-proxy-ca.key -CAcreateserial -out $CERT_FOLDER/$FN.crt -extensions v3_req -extfile $TEMP_FOLDER/$FN.cnf
        else
	    openssl x509 -req -days $DAYS_CERT -in $TEMP_FOLDER/$FN.csr -CA $KUBE_FOLDER/pki/ca.crt -CAkey $KUBE_FOLDER/pki/ca.key -CAcreateserial -out $CERT_FOLDER/$FN.crt -extensions v3_req -extfile $TEMP_FOLDER/$FN.cnf
        fi
    done

}

CUR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $CUR_DIR
remove-all

echo "Prepare"
prepare
echo "Create backup"
create-backup
echo "Get private key"
get-private-key
echo "Create cnf"
create_cnf scheduler.cnf system:kube-scheduler
create_cnf controller-manager.cnf system:kube-controller-manager
create_cnf front-proxy-client.cnf front-proxy-client
create_cnf apiserver-kubelet-client.cnf kube-apiserver-kubelet-client system:masters
create_cnf admin.cnf kubernetes-admin system:masters
create_cnf kubelet.cnf system:node:$KUBE_API_HOSTNAME system:nodes
create_api_cnf
echo "Create csr"
create_csr
echo "Create certificates"
create_cert

echo ""
echo "manually cp $CERT_FOLDER/apiserver.crt $KUBE_FOLDER/pki/apiserver.crt"
echo ""

echo "manually cp $CERT_FOLDER/apiserver-kubelet-client.crt $KUBE_FOLDER/pki/apiserver-kubelet-client.crt"
echo ""

echo "manually cp $CERT_FOLDER/front-proxy-client.crt $KUBE_FOLDER/pki/front-proxy-client.crt"
echo ""

admin_st=$(openssl base64 -in $CERT_FOLDER/admin.crt | tr -d '\n')
echo "manually replace client-certificate-data in $KUBE_FOLDER/admin.conf - $admin_st"
echo ""

kubelet_st=$(openssl base64 -in $CERT_FOLDER/kubelet.crt | tr -d '\n')
echo "manually replace client-certificate-data in $KUBE_FOLDER/kubelet.conf - $kubelet_st"
echo ""

scheduler_st=$(openssl base64 -in $CERT_FOLDER/scheduler.crt | tr -d '\n')
echo "manually replace client-certificate-data in $KUBE_FOLDER/scheduler.conf - $scheduler_st"
echo ""

controller_manager_st=$(openssl base64 -in $CERT_FOLDER/controller-manager.crt | tr -d '\n')
echo "manually replace client-certificate-data in $KUBE_FOLDER/controller-manager.conf - $controller_manager_st"
echo ""

echo "manually replace client-certificate-data in ~/.kube/config - $admin_st"
echo ""
```
