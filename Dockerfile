FROM ubuntu:22.04
# update package manager and install prerequisites
RUN apt-get update -y && \
	apt-get install -y build-essential cmake git wget pkg-config sqlite3 libcurl4-openssl-dev libsqlite3-dev libtiff5-dev
# download geodesy projection library source
WORKDIR /tmp
RUN wget -q https://download.osgeo.org/proj/proj-9.3.0.tar.gz && \
	tar -xzf proj-9.3.0.tar.gz && \
	rm proj-9.3.0.tar.gz && \
	mv proj-9.3.0 proj && \
	mkdir -p proj/build
# download openjdk
RUN wget -q https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_linux-x64_bin.tar.gz && \
	tar -xzf openjdk-21.0.1_linux-x64_bin.tar.gz && \
	rm openjdk-21.0.1_linux-x64_bin.tar.gz && \
	mv jdk-21.0.1 /opt/jdk
# download apache tomcat
RUN wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.83/bin/apache-tomcat-9.0.83.tar.gz && \
	tar -xzf apache-tomcat-9.0.83.tar.gz && \
	rm apache-tomcat-9.0.83.tar.gz && \
	mv apache-tomcat-9.0.83 tomcat && \
	sed -i 's/appBase="webapps"/appBase="webapps\/carmacloud"/g' tomcat/conf/server.xml && \
	rm -r tomcat/webapps/*
# download carma-cloud source
RUN git clone https://github.com/usdot-fhwa-stol/carma-cloud.git cc && \
	rm cc/lib/libcs2cswrapper.so && \
	mkdir -p tomcat/webapps/carmacloud/ROOT && \
	mv cc/web/* tomcat/webapps/carmacloud/ROOT
# compile geodesy projection library
WORKDIR /tmp/proj/build
RUN cmake .. && \
	cmake --build . && \
	cmake --build . --target install
# compile jni projection library
WORKDIR /tmp/cc/src/cc/geosrv
RUN gcc -c -std=c11 -fPIC -Wall -I /opt/jdk/include/ -I /opt/jdk/include/linux/ -I /tmp/proj/src/ cs2cswrapper.c && \
	gcc -shared -lproj cs2cswrapper.o -o libcs2cswrapper.so && \
	mv *.so /usr/lib
# compile carma-cloud java source and cleanup
WORKDIR /tmp
RUN find ./cc/src -name "*.java" > sources.txt && \
	mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes && \
	/opt/jdk/bin/javac -cp tomcat/lib/servlet-api.jar:cc/lib/commons-compress-1.18.jar:cc/lib/javax.json.jar:cc/lib/json-20210307.jar:cc/lib/keccakj.jar:cc/lib/log4j-api-2.16.0.jar:cc/lib/vector_tile.jar -d tomcat/webapps/carmacloud$ && \
	rm sources.txt && \
	/opt/jdk/bin/java -cp tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:tomcat/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > tomcat/webapps/carmacloud/user.csv && \
	echo "JAVA_HOME=/opt/jdk" > tomcat/bin/setenv.sh && \
	mv tomcat /opt && \
	rm -r proj && \
	rm -r cc
# start carma-cloud
CMD /opt/tomcat/bin/catalina.sh start
