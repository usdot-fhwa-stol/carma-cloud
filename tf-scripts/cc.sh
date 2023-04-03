#!/bin/bash
sudo apt update
sudo apt install openjdk-11-jdk-headless
sudo source .bashrc 
sudo mkdir tmp && cd tmp
wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.34/bin/apache-tomcat-9.0.34.tar.gz
tar -xzf apache-tomcat-9.0.34.tar.gz
mv apache-tomcat-9.0.34 tomcat
rm -rf tomcat/webapps/ROOT
rm -rf tomcat/webapps/docs
rm -rf tomcat/webapps/examples
rm -rf tomcat/webapps/host-manager
rm -rf tomcat/webapps/manager
rm -f apache-tomcat-9.0.34.tar.gz
git clone https://github.com/usdot-fhwa-stol/carma-cloud.git
mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes
find ./carma-cloud/src -name "*.java" > sources.txt
javac -cp "tomcat/lib/servlet-api.jar:carma-cloud/lib/*" -d tomcat/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt
rm -f sources.txt
mv carma-cloud/end_cc.sh tomcat
mv carma-cloud/start_cc.sh tomcat
mkdir -p tomcat/work/carmacloud/xodr
mkdir -p tomcat/work/carmacloud/validate/xodr
mv carma-cloud/web/WEB-INF/web.xml tomcat/webapps/carmacloud/ROOT/WEB-INF/
mv carma-cloud/web/WEB-INF/log4j2.properties tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/
mv -n carma-cloud/web/* tomcat/webapps/carmacloud/ROOT/
sudo mv carma-cloud/lib/libcs2cswrapper.so /usr/lib/
mv carma-cloud/lib tomcat/webapps/carmacloud/ROOT/WEB-INF/
touch tomcat/webapps/carmacloud/event.csv
mv carma-cloud/osmbin/rop.csv tomcat/webapps/carmacloud/
mv carma-cloud/osmbin/storm.csv tomcat/webapps/carmacloud/
mv carma-cloud/osmbin/units.csv tomcat/webapps/carmacloud/
gunzip carma-cloud/osmbin/*.gz
mv carma-cloud/osmbin tomcat/webapps/carmacloud/
java -cp tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:tomcat/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > tomcat/webapps/carmacloud/user.csv
rm -rf carma-cloud
sed -i '/<\/Engine>/ i \ \ \ \ \  <Host name="carmacloud" appBase="webapps/carmacloud" unpackWARs="false" autoDeploy="false">\n      </Host>' tomcat/conf/server.xml 
echo -e '127.0.0.1\tcarmacloud' | sudo tee -a /etc/hosts
sudo groupadd tomcat
sudo useradd -g tomcat -m tomcat
chmod g+r tomcat/conf/*
chmod -R o-rwx tomcat/webapps/*
sudo chown -R root:tomcat tomcat
sudo chown -R tomcat:tomcat tomcat/logs
sudo chown -R tomcat:tomcat tomcat/temp
sudo chown -R tomcat:tomcat tomcat/work
sudo mv tomcat /home/ubuntu/ 
ls -la && pwd && cd .. && rm -rf tmp
sudo source /home/ubuntu/.bashrc && source /home/ubuntu/etc/profile
sudo /home/ubuntu/tomcat/bin/catalina.sh start
sleep 10
if [[ `sudo grep "startup in" /home/ubuntu/tomcat/logs/catalina.out | wc -l` -ne "1" ]]; then exit 1; fi
if [[ `wget -O - http://carmacloud:8080 | grep "CARMAcloud Login" | wc -l` -ne "1" ]]; then exit 2; fi
if [[ `wget -O - --post-data="uname=ccadmin&pword=admin_testpw" http://carmacloud:8080/api/auth/login | grep "token" | wc -l` -ne "1" ]]; then exit 3; fi 
