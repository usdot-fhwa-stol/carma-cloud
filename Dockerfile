FROM ubuntu:22.04
# update package manager and install prerequisites
COPY scripts/install_dependencies.sh /home/cc/scripts/install_dependencies.sh
RUN /home/cc/scripts/install_dependencies.sh
COPY src /home/cc/src
COPY lib /home/cc/lib
COPY web /home/cc/web
COPY osmbin /home/cc/osmbin
COPY *.sh /home/cc/
COPY scripts/build.sh /home/cc/scripts/build.sh

# download carma-cloud source
RUN /home/cc/scripts/build.sh

