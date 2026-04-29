# Adding Optional Trusted Organizational Perimeter Certificates

*(Skip if not required)*

If your organization uses private Certificate Authorities (CA), TLS inspection, or a corporate VPN that intercepts HTTPS traffic, Docker builds may fail when downloading dependencies.

Without these certificates installed, tools such as `wget`, `curl`, `nvm`, `git`, or package managers may fail with TLS certificate verification errors.

## Example Build Error
```
111.8 + wget -q https://download.osgeo.org/proj/proj-9.3.0.tar.gz
------
Dockerfile:25
--------------------
  23 |     # update package manager and install prerequisites
  24 |     COPY scripts/install_dependencies.sh /home/carma-cloud/scripts/install_dependencies.sh
  25 | >>> RUN /home/carma-cloud/scripts/install_dependencies.sh
  26 |
  27 |     # Copy application source
--------------------
ERROR: failed to build: failed to solve: process "/bin/sh -c /home/carma-cloud/scripts/install_dependencies.sh" did not complete successfully: exit code: 5
```

This occurs because the container does not trust your organization’s internal CA by default.

## Install Instructions

### 1. Obtain Required Certificates

Request from your IT/security team:

* The organization’s **Root CA certificate**
* Any **Intermediate CA certificates** used for perimeter or TLS inspection

> You do **not** need the VPN server certificate or any private keys — only public CA certificates.

### 2. Save Certificates to the Local Project Directory

Place the certificate files in:

```
./perimeter-certs/
```

#### Requirements:

* Files must be in **PEM format**
* Use the `.crt` file extension
* Only public CA certificates should be used
* Multiple certificates may be added if needed
* Do-Not commit certificates to respository. By default, repository is configured to ignore certificate files.

#### Example:

```
./perimeter-certs/
├── Corp-Root-CA.crt
├── Corp-Perimeter-CA.crt
```

### 3. Rebuild the Docker Image

```bash
docker build -t usdotfhwastol/carma-cloud:develop .
```

## How It Works

During the Docker build:
* The perimeter-certs/ folder is copied into the container
* If .crt files are present, they are installed into:
  `/usr/local/share/ca-certificates/`
* The system trust store is updated via update-ca-certificates
* If no .crt files are present, this step is skipped automatically.

## Troubleshooting

If certificate errors persist:
* Ensure certificate files use the `.crt` extension
* Verify certificates are valid PEM format
* Confirm both root and intermediate certificates are included (if required)
* Rebuild Docker image using `--no-cache`
* Check that files are actually present in `perimeter-certs/`

### Security Note

* Only public CA certificates should be used
* Never commit private keys (.key, .pem)
* Organizational certificates may contain sensitive infrastructure details—handle appropriately