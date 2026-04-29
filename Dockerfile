FROM ubuntu:22.04

ARG CC_USER="cc_admin" 
ARG CC_PSWD="admin_testpw"

# Copy optional perimeter certs (safe even if empty)
COPY perimeter-certs/ /tmp/perimeter-certs/

# Conditionally install perimeter certs
RUN if ls /tmp/perimeter-certs/*.crt >/dev/null 2>&1; then \
      echo "Installing perimeter certificates..." && \
      mkdir -p /usr/local/share/ca-certificates/extra && \
      cp /tmp/perimeter-certs/*.crt /usr/local/share/ca-certificates/extra/ 2>/dev/null || true; \
    else \
      echo "Skipping perimeter certificates"; \
    fi

# Install base packages (including cert support)
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates

# update package manager and install prerequisites
COPY scripts/install_dependencies.sh /home/carma-cloud/scripts/install_dependencies.sh
RUN /home/carma-cloud/scripts/install_dependencies.sh

# Copy application source
COPY src /home/carma-cloud/src
COPY lib /home/carma-cloud/lib
COPY web /home/carma-cloud/web
COPY osmbin /home/carma-cloud/osmbin
COPY *.sh /home/carma-cloud/
COPY scripts/build.sh /home/carma-cloud/scripts/build.sh

# download carma-cloud source
RUN /home/carma-cloud/scripts/build.sh
ENTRYPOINT [ "/opt/tomcat/bin/catalina.sh", "run" ]