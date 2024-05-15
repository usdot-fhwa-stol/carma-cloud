#!/bin/bash

# exit on errors
set -e
cd tmp/
wget --no-check-certificate -q https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_linux-x64_bin.tar.gz 
tar -xzf openjdk-21.0.1_linux-x64_bin.tar.gz 
rm openjdk-21.0.1_linux-x64_bin.tar.gz
mv jdk-21.0.1 /opt/jdk
export JAVA_HOME="/opt/jdk/"
echo "JAVA HOME is ${JAVA_HOME}"
ls ${JAVA_HOME}
cd /home/cc/src/cc/geosrv
gcc -c -std=c11 -fPIC -Wall -I ${JAVA_HOME}/opt/include/ -I ${JAVA_HOME}/opt/include/linux/ -I /tmp/proj/src/ cs2cswrapper.c
gcc -shared cs2cswrapper.o -lproj -o /usr/local/lib/libcs2cswrapper.so

cd /tmp
mkdir -p tomcat/webapps/carmacloud/ROOT 
mv cc/web/* tomcat/webapps/carmacloud/ROOT 
mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes 
find ./cc/src -name "*.java" > sources.txt 
/opt/jdk/bin/javac -cp tomcat/lib/servlet-api.jar:cc/lib/commons-compress-1.18.jar:cc/lib/javax.json.jar:cc/lib/json-20210307.jar:cc/lib/keccakj.jar:cc/lib/log4j-api-2.16.0.jar:cc/lib/vector_tile.jar -d tomcat/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt 
rm sources.txt 
gunzip cc/osmbin/*.gz 
mv cc/lib tomcat/webapps/carmacloud/ROOT/WEB-INF 
mv cc/osmbin/rop.csv tomcat/webapps/carmacloud 
mv cc/osmbin/storm.csv tomcat/webapps/carmacloud 
mv cc/osmbin/units.csv tomcat/webapps/carmacloud 
mv cc/osmbin tomcat/webapps/carmacloud 
mv tomcat/webapps/carmacloud/ROOT/WEB-INF/log4j2.properties tomcat/webapps/carmacloud/ROOT/WEB-INF/classes 
touch tomcat/webapps/carmacloud/event.csv 
mkdir -p tomcat/work/carmacloud/xodr 
mkdir -p tomcat/work/carmacloud/validate/xodr 
/opt/jdk/bin/java -cp tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:tomcat/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > tomcat/webapps/carmacloud/user.csv 
echo 'JAVA_HOME=/opt/jdk' > tomcat/bin/setenv.sh 
sed -i 's/<param-value>ambassador-address<\/param-value>/<param-value>127.0.0.1<\/param-value>/g' tomcat/webapps/carmacloud/ROOT/WEB-INF/web.xml 
sed -i 's/<param-value>simulation-url<\/param-value>/<param-value>http:\/\/127.0.0.1:8080\/carmacloud\/simulation<\/param-value>/g' tomcat/webapps/carmacloud/ROOT/WEB-INF/web.xml 
mv tomcat /opt 
rm -r proj 
rm -r cc