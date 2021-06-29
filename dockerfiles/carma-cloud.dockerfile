FROM ubuntu:bionic

ENV TOMCAT_MAJOR_VERSION=9
ENV TOMCAT_VERSION=${TOMCAT_MAJOR_VERSION}.0.48
ENV TOMCAT_PREFIX=apache-tomcat-${TOMCAT_VERSION}
ENV TOMCAT_URL=https://mirrors.ocf.berkeley.edu/apache/tomcat/tomcat-${TOMCAT_MAJOR_VERSION}/v${TOMCAT_VERSION}/bin/${TOMCAT_PREFIX}.tar.gz

WORKDIR /build
# Install packages
COPY applications/carma-cloud/depends/os_packages os_packages
RUN apt-get update && \
    cat os_packages | xargs apt-get install -y

# Copy in config templates
COPY applications/carma-cloud/config/ /configs/

# Get CARMA code
COPY applications/carma-cloud/source carma-cloud

# Get and configure tomcat
RUN wget ${TOMCAT_URL} && \
    tar -xzf ${TOMCAT_PREFIX}.tar.gz && \
    mv ${TOMCAT_PREFIX} tomcat && \
    rm -rf ${TOMCAT_PREFIX}.tar.gz

# Build proj
RUN wget https://download.osgeo.org/proj/proj-6.1.1.tar.gz && tar -xzf proj-6.1.1.tar.gz && mv proj-6.1.1 proj
RUN cd proj && \
    ./configure && \
    make && \
    make install

# Setup carmacloud stuff
RUN mkdir -p tomcat/webapps/carmacloud/ROOT && \
    mv carma-cloud/web/* tomcat/webapps/carmacloud/ROOT/ && \
    mv /configs/logging.properties tomcat/conf/

RUN mkdir -p tomcat/work/carmacloud/xodr && \
    mkdir -p tomcat/work/carmacloud/validate/xodr && \
    find ./carma-cloud/src -name "*.java" > sources.txt && \
    mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes

RUN  javac -cp tomcat/lib/servlet-api.jar:carma-cloud/lib/* -d tomcat/webapps/carmacloud/ROOT/WEB-INF/classes --release 8 @sources.txt
COPY applications/carma-cloud/config/server.xml tomcat/conf/

# Place wrapper
RUN mv carma-cloud/lib/libcs2cswrapper.so /usr/lib/
RUN mv carma-cloud/lib tomcat/webapps/carmacloud/ROOT/WEB-INF/
RUN touch tomcat/webapps/carmacloud/event.csv

# Place OSM bin
RUN mv carma-cloud/osmbin/rop.csv tomcat/webapps/carmacloud/ && \
    mv carma-cloud/osmbin/storm.csv tomcat/webapps/carmacloud/

RUN gunzip carma-cloud/osmbin/*.gz
RUN mv carma-cloud/osmbin tomcat/webapps/carmacloud/
RUN rm -f sources.txt && rm -rf carma-cloud

# Put tomcat under opt
RUN mv tomcat /opt/
RUN groupadd v2xhub
RUN groupadd tomcat
RUN useradd -g v2xhub -m v2xhub
RUN useradd -g tomcat -m tomcat



WORKDIR /application
COPY applications/carma-cloud/scripts/application.sh ./
RUN chmod 775 ./application.sh
CMD ./application.sh
