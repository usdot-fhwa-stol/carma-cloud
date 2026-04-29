
# Adding Optional Trusted Organizational Perimeter Certificates

*(Skip if not required)*

If your organization uses private Certificate Authorities (CA), TLS inspection, or a corporate VPN that intercepts HTTPS traffic, the docker build must trust your organization’s root and/or intermediate CA certificates.

Without these certificates installed, tools such as `wget`, `curl`, `nvm`, `git`, or package managers may fail with TLS certificate verification errors.

---

## Install Instructions

### 1. Obtain Required Certificates

Request from your IT/security team:

* The organization’s **Root CA certificate**
* Any **Intermediate CA certificates** used for perimeter or TLS inspection

> You do **not** need the VPN server certificate or any private keys — only public CA certificates.

---

### 2. Save Certificates to the Repository

Place the certificate files (public certs only) in:

```
./perimeter-certs/
```

Requirements:

* Files must be in **PEM format**
* Use the `.crt` file extension (required for automatic installation)
* Multiple certificates may be added if needed

Example:

```
./perimeter-certs/
├── Corp-Root-CA.crt
├── Corp-Perimeter-CA.crt
```

---

### 3. Rebuild the Docker Container

In bash:

```bash
docker build -t usdotfhwastol/carma-cloud:develop .
```

During docker build, the Dockerfile will:

* Detect any `.crt` files in `./perimeter-certs/`
* Install them into `/usr/local/share/ca-certificates/`
* Update the container’s system trust store

No manual installation steps are required.

---

## Troubleshooting

If you still see certificate errors:

* Ensure certificate files use the `.crt` extension
* Confirm the files contain valid PEM-encoded certificates
* Verify you included both root and intermediate CA certificates (if required)
* Rebuild the container again after changes

---

### Security Note

Only public CA certificates should be committed to the repository.
Never commit private keys.