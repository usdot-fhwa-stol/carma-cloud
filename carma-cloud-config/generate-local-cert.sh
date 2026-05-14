#!/usr/bin/env bash
set -e

SCRIPT_DIR=
WORKDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ssl"
PASSWORD="${PASSWORD:-ChangeMe123!}"

mkdir -p "$WORKDIR"
cd "$WORKDIR"

###########################################################
# Root CA
###########################################################

openssl genrsa -out carma-cloud-rootCA.key 4096

openssl req -x509 -new -nodes \
  -key carma-cloud-rootCA.key \
  -sha256 \
  -days 3650 \
  -out carma-cloud-rootCA.pem \
  -subj "/CN=CARMA Cloud Dev Root CA"

###########################################################
# Intermediate CA
###########################################################

openssl genrsa -out intermediateCA.key 4096

openssl req -new \
  -key intermediateCA.key \
  -out intermediateCA.csr \
  -subj "/CN=CARMA Cloud Dev Intermediate CA"

cat > intermediate_ext.cnf <<EOF
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF

openssl x509 -req \
  -in intermediateCA.csr \
  -CA carma-cloud-rootCA.pem \
  -CAkey carma-cloud-rootCA.key \
  -CAcreateserial \
  -out intermediateCA.crt \
  -days 1825 \
  -sha256 \
  -extfile intermediate_ext.cnf

###########################################################
# Server cert config
###########################################################

cat > server.cnf <<EOF
[ req ]
distinguished_name = dn
prompt = no
req_extensions = req_ext

[ dn ]
CN = carma-cloud.com

[ req_ext ]
subjectAltName = @alt_names
extendedKeyUsage = serverAuth

[ alt_names ]
DNS.1 = carma-cloud.com
DNS.2 = host.docker.internal
DNS.3 = localhost
IP.1 = 127.0.0.1
EOF

###########################################################
# Server key + CSR
###########################################################

openssl genrsa -out carma-cloud.com.key 2048

openssl req -new \
  -key carma-cloud.com.key \
  -out carma-cloud.com.csr \
  -config server.cnf

###########################################################
# Sign server cert with intermediate CA
###########################################################

cat > server_ext.cnf <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=@alt_names

[alt_names]
DNS.1=carma-cloud.com
DNS.2=host.docker.internal
DNS.3=localhost
IP.1=127.0.0.1
EOF

openssl x509 -req \
  -in carma-cloud.com.csr \
  -CA intermediateCA.crt \
  -CAkey intermediateCA.key \
  -CAcreateserial \
  -out carma-cloud.com.crt \
  -days 365 \
  -sha256 \
  -extfile server_ext.cnf

###########################################################
# Full chain for Tomcat
###########################################################

cat carma-cloud.com.crt intermediateCA.crt > fullchain.crt

###########################################################
# PKCS12 for Tomcat
###########################################################

openssl pkcs12 -export \
  -inkey carma-cloud.com.key \
  -in fullchain.crt \
  -name carma-cloud \
  -out carma-cloud.com.p12 \
  -passout pass:${PASSWORD}

###########################################################
# Output
###########################################################

# sudo chmod ugo+r carma-cloud-rootCA.pem  carma-cloud.com.p12 

echo
echo "Generated:"
echo "  carma-cloud-rootCA.pem               <-- trust this in plugin/client"
echo "  intermediateCA.crt"
echo "  carma-cloud.com.p12      <-- install into Tomcat"
echo 