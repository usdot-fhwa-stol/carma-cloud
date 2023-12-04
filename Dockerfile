FROM ubuntu:latest
# install prerequisite packages
RUN apt-get update -y
RUN apt-get install -y build-essential cmake git wget
RUN apt-get install -y pkg-config sqlite3 libcurl4-openssl-dev libsqlite3-dev libtiff5-dev
# download openjdk and install
WORKDIR /tmp
RUN wget -q https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_linux-x64_bin.tar.gz
RUN tar -xzf openjdk-21.0.1_linux-x64_bin.tar.gz
RUN rm openjdk-21.0.1_linux-x64_bin.tar.gz
RUN mv jdk-21.0.1 /opt/jdk
# download geodesy projection library source
RUN wget -q https://download.osgeo.org/proj/proj-9.3.0.tar.gz
RUN tar -xzf proj-9.3.0.tar.gz
RUN rm proj-9.3.0.tar.gz
RUN mv proj-9.3.0 proj
# compile geodesy projection library
RUN mkdir -p proj/build
WORKDIR /tmp/proj/build
RUN cmake ..
RUN cmake --build .
RUN cmake --build . --target install
WORKDIR /tmp
# download apache tomcat
RUN wget -q https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.83/bin/apache-tomcat-9.0.83.tar.gz
RUN tar -xzf apache-tomcat-9.0.83.tar.gz
RUN rm apache-tomcat-9.0.83.tar.gz
RUN mv apache-tomcat-9.0.83 tomcat
RUN sed -i 's/appBase="webapps"/appBase="webapps\/carmacloud"/g' tomcat/conf/server.xml
RUN rm -r tomcat/webapps/*
# download carma-cloud and compile projection library
RUN git clone https://github.com/usdot-fhwa-stol/carma-cloud.git cc
RUN rm cc/lib/libcs2cswrapper.so
RUN mkdir -p tomcat/webapps/carmacloud/ROOT
RUN mv cc/web/* tomcat/webapps/carmacloud/ROOT
WORKDIR /tmp/cc/src/cc/geosrv
RUN gcc -c -std=c11 -fPIC -Wall -I /opt/jdk/include/ -I /opt/jdk/include/linux/ -I /tmp/proj/src/ cs2cswrapper.c
RUN gcc -shared -lproj cs2cswrapper.o -o libcs2cswrapper.so
RUN mv *.so /usr/lib
# compile carma-cloud java source
WORKDIR /tmp
RUN find ./cc/src -name "*.java" > sources.txt
RUN mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes
RUN /opt/jdk/bin/javac -cp tomcat/lib/servlet-api.jar:cc/lib/commons-compress-1.18.jar:cc/lib/javax.json.jar:cc/lib/json-20210307.jar:cc/lib/keccakj.jar:cc/lib/log4j-api-2.16.0.jar:cc/lib/vector_tile.jar -d tomcat/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt
RUN rm sources.txt
RUN /opt/jdk/bin/java -cp tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:tomcat/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > tomcat/webapps/carmacloud/user.csv
RUN echo "JAVA_HOME=/opt/jdk" > tomcat/bin/setenv.sh
RUN mv tomcat /opt
# cleanup and start
RUN rm -r proj
RUN rm -r cc
RUN /opt/tomcat/bin/catalina.sh start
