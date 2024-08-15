FROM ubuntu:22.04
# update package manager and install prerequisites
COPY scripts/install_dependencies.sh /home/carma-cloud/scripts/install_dependencies.sh
RUN /home/carma-cloud/scripts/install_dependencies.sh
COPY src /home/carma-cloud/src
COPY lib /home/carma-cloud/lib
COPY web /home/carma-cloud/web
COPY osmbin /home/carma-cloud/osmbin
COPY *.sh /home/carma-cloud/
COPY scripts/build.sh /home/carma-cloud/scripts/build.sh

# download carma-cloud source
RUN /home/carma-cloud/scripts/build.sh
ENTRYPOINT [ "/opt/tomcat/bin/catalina.sh", "run" ]

