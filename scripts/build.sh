#!/bin/bash

# exit on errors
set -ex
export JAVA_HOME="/opt/jdk"
export TOMCAT_HOME="/opt/tomcat"


cd /home/carma-cloud/src/cc/geosrv
gcc -c -std=c11 -fPIC -Wall -I "${JAVA_HOME}/include/" -I "${JAVA_HOME}/include/linux/" -I /tmp/proj/src/ cs2cswrapper.c
gcc -shared cs2cswrapper.o -lproj -o /usr/lib/libcs2cswrapper.so

mkdir -p "${TOMCAT_HOME}/webapps/carmacloud/ROOT" 
cd /home
mv carma-cloud/web/* ${TOMCAT_HOME}/webapps/carmacloud/ROOT 
mkdir -p ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes
cd /home/carma-cloud
find ./src -name "*.java" > sources.txt 
/opt/jdk/bin/javac -cp ${TOMCAT_HOME}/lib/servlet-api.jar:lib/commons-compress-1.18.jar:lib/javax.json.jar:lib/json-20210307.jar:lib/keccakj.jar:lib/log4j-api-2.16.0.jar:lib/vector_tile.jar -d ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt 
rm sources.txt 
gunzip osmbin/*.gz 
mv lib ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF 
mv osmbin/rop.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv osmbin/storm.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv osmbin/units.csv ${TOMCAT_HOME}/webapps/carmacloud 
mv osmbin ${TOMCAT_HOME}/webapps/carmacloud 
mv ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/log4j2.properties ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes 
touch ${TOMCAT_HOME}/webapps/carmacloud/event.csv 
mkdir -p ${TOMCAT_HOME}/work/carmacloud/xodr 
mkdir -p ${TOMCAT_HOME}/work/carmacloud/validate/xodr 
/opt/jdk/bin/java -cp ${TOMCAT_HOME}/webapps/carmacloud/ROOT/WEB-INF/classes/:${TOMCAT_HOME}/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > ${TOMCAT_HOME}/webapps/carmacloud/user.csv 
sed -i 's/appBase="webapps"/appBase="webapps\/carmacloud"/g' ${TOMCAT_HOME}/conf/server.xml
echo "JAVA_HOME=${JAVA_HOME}" > ${TOMCAT_HOME}/bin/setenv.sh 
