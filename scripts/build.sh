#!/bin/bash

# exit on errors
set -e
cd tmp/
export JAVA_HOME="/opt/jdk"
export TOMCAT_HOME="/opt/tomcat"
echo "JAVA HOME is ${JAVA_HOME}"
ls ${JAVA_HOME}/include/
cd /home/cc/src/cc/geosrv
gcc -c -std=c11 -fPIC -Wall -I "${JAVA_HOME}/include/" -I "${JAVA_HOME}/include/linux/" -I /tmp/proj/src/ cs2cswrapper.c
gcc -shared cs2cswrapper.o -lproj -o /usr/local/lib/libcs2cswrapper.so

mkdir -p "${TOMCAT_HOME}/webapps/carmacloud/ROOT" 
cd /home
mv cc/web/* ${TOMCAT_HOME}/webapps/carmacloud/ROOT 
mkdir -p ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes 
find ./cc/src -name "*.java" > sources.txt 
/opt/jdk/bin/javac -cp ${TOMCAT_HOME}/lib/servlet-api.jar:cc/lib/commons-compress-1.18.jar:cc/lib/javax.json.jar:cc/lib/json-20210307.jar:cc/lib/keccakj.jar:cc/lib/log4j-api-2.16.0.jar:cc/lib/vector_tile.jar -d ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt 
rm sources.txt 
gunzip cc/osmbin/*.gz 
mv cc/lib ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF 
mv cc/osmbin/rop.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv cc/osmbin/storm.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv cc/osmbin/units.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv cc/osmbin ${TOMCAT_HOME}/webapps/carmacloud 
mv ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/log4j2.properties ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes 
touch ${TOMCAT_HOME}/webapps/carmacloud/event.csv 
mkdir -p ${TOMCAT_HOME}/work/carmacloud/xodr 
mkdir -p ${TOMCAT_HOME}/work/carmacloud/validate/xodr 
/opt/jdk/bin/java -cp ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes/:${TOMCAT_HOME}/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > ${TOMCAT_HOME}/webapps/carmacloud/user.csv 
echo 'JAVA_HOME=/opt/jdk' > ${TOMCAT_HOME}/bin/setenv.sh 
sed -i 's/<param-value>ambassador-address<\/param-value>/<param-value>127.0.0.1<\/param-value>/g' ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/web.xml 
sed -i 's/<param-value>simulation-url<\/param-value>/<param-value>http:\/\/127.0.0.1:8080\/carmacloud\/simulation<\/param-value>/g' ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/web.xml 
