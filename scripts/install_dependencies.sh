#!/bin/bash

# exit on errors
set -e
apt-get update -y 
apt-get install -y build-essential cmake git wget pkg-config sqlite3 libcurl4-openssl-dev libsqlite3-dev libtiff5-dev openjdk-21-jdk
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
sed -i 's/appBase="webapps"/appBase="webapps\/carmacloud"/g' tomcat/conf/server.xml
rm -r tomcat/webapps/*

