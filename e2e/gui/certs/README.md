Optional extra CA certificates (`*.pem`/`*.crt`) to trust inside the GUI tests runner image.

They are needed when the image is built behind a TLS-intercepting proxy (dnf, Chrome and Gradle downloads)
and can also hold the platform CA. `run-e2e.sh` copies the CA of the platform (`/opt/common/pki/ca-public-cert.pem`)
here before building the image, unless `--ca-cert` points to another file. Certificates are not committed.
