# CARMA Cloud Local TLS Certificate Generation

This directory contains utilities and configuration for generating local development TLS certificates for CARMA Cloud.

The `generate-local-cert.sh` script creates:

- A local development Root Certificate Authority (CA)
- An Intermediate CA
- A server certificate for CARMA Cloud
- A PKCS12 keystore for Tomcat HTTPS configuration

The generated certificates are intended for local development and testing only.

---

# Directory Structure

```text
carma-cloud-config/
├── generate-local-cert.sh
└── ssl/
    ├── carma-cloud.com.crt
    ├── carma-cloud.com.csr
    ├── carma-cloud.com.key
    ├── carma-cloud.com.p12
    ├── carma-cloud-rootCA.key
    ├── carma-cloud-rootCA.pem
    ├── fullchain.crt
    ├── intermediateCA.crt
    ├── intermediateCA.csr
    ├── intermediateCA.key
    ├── intermediate_ext.cnf
    ├── server.cnf
    └──server_ext.cnf
````

---

# Prerequisites

## Required Software
* OpenSSL

```bash
sudo apt-get install openssl
```
---

# Usage

## Generate Certificates

From the `carma-cloud-config` directory:

```bash
./generate-local-cert.sh
```

Certificates and keys will be generated under:

```text
carma-cloud-config/ssl/
```

---

# Generated Files

| File                     | Purpose                              |
| ------------------------ | ------------------------------------ |
| `carma-cloud-rootCA.pem` | Root CA certificate to trust locally |
| `intermediateCA.crt`     | Intermediate CA certificate          |
| `carma-cloud.com.crt`    | Server TLS certificate               |
| `carma-cloud.com.key`    | Server private key                   |
| `fullchain.crt`          | Full certificate chain               |
| `carma-cloud.com.p12`    | PKCS12 keystore for Tomcat           |

---

# Default Password

The generated PKCS12 keystore uses the following password:

```text
ChangeMe123!
```
Override using:

```shell
PASSWORD=my_secure_password ./generate-local-cert.sh
```
---

# Supported Hostnames

The generated certificate includes the following Subject Alternative Names (SANs):

* `carma-cloud.com`
* `host.docker.internal`
* `localhost`
* `127.0.0.1`

This supports:

* Local browser testing
* Localhost HTTPS development
* Docker container access - `host.docker.internal` enables secure communication between Docker containers and the host machine.
* CARMA Platform plugin integration

---

# Configure Tomcat HTTPS

Update the Tomcat HTTPS connector in `server.xml` and set `certificateKeystorePassword` to the password used when generating the PKCS12 TLS certificate.

Example:

```xml
<Connector
    port="8443"
    protocol="org.apache.coyote.http11.Http11NioProtocol"
    maxThreads="150"
    SSLEnabled="true">

    <SSLHostConfig>
        <Certificate
            certificateKeystoreFile="/opt/carma-cloud-config/ssl/carma-cloud.com.p12"
            certificateKeystorePassword="ChangeMe123!"
            certificateKeystoreType="PKCS12" />
    </SSLHostConfig>

</Connector>
```

---

# Trust the Root CA

To avoid TLS verification issues in V2X-Hub when using locally generated CARMA Cloud certificates, configure the CARMA-Cloud plugin to trust the generated Root CA certificate (`ssl/carma-cloud-rootCA.pem`) using the `carma_cloud_ca_cert_path` configuration parameter.

---

# Security Notes

These certificates are intended for:

* Local development
* Integration testing
* Docker-based deployments
* Prototype environments

Do NOT use these certificates in production environments.

Never commit generated private keys or PKCS12 files to source control.