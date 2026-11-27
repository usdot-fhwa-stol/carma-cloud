#!/bin/bash

# exit on errors
set -ex
apt-get update -y 
apt-get install -y build-essential cmake git wget pkg-config sqlite3 libcurl4-openssl-dev libsqlite3-dev libtiff5-dev
# download geodesy projection library source and build
cd /tmp
wget -q https://download.osgeo.org/proj/proj-9.3.0.tar.gz
tar -xzf proj-9.3.0.tar.gz 
rm proj-9.3.0.tar.gz 
mv proj-9.3.0 proj
mkdir -p proj/build
cd /tmp/proj/build
cmake .. 
cmake --build . && \
cmake --build . --target install && \
ldconfig
# download apache tomcat
cd /tmp
wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.83/bin/apache-tomcat-9.0.83.tar.gz
tar -xzf apache-tomcat-9.0.83.tar.gz
rm apache-tomcat-9.0.83.tar.gz
mv apache-tomcat-9.0.83 tomcat
#sed -i 's/appBase="webapps"/appBase="webapps\/carmacloud"/g' tomcat/conf/server.xml
rm -r tomcat/webapps/*
mv tomcat /opt 

# download and install JDK 25 (Eclipse Temurin LTS), the only JDK used to build and run CARMA Cloud
JDK_VERSION="25.0.5.1+2"
JDK_SHA256="760f91b22c3efc9bd1b64ff950598231be82928cc807e30691a0e7a2995a62c0"
cd /tmp
wget -q -O jdk.tar.gz "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-${JDK_VERSION/+/%2B}/OpenJDK25U-jdk_x64_linux_hotspot_${JDK_VERSION/+/_}.tar.gz"
echo "${JDK_SHA256}  jdk.tar.gz" | sha256sum -c -
tar -xzf jdk.tar.gz
mv "jdk-${JDK_VERSION}" /opt/jdk
rm jdk.tar.gz
