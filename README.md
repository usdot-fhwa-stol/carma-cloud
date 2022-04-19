# CARMAcloud

## Documentation
CARMAcloud provides some of the infrastructure components for CARMA. It enables users to define geofences, rules of practice, replay storms to test weather-related rules of practice, as well as monitor CARMA-enabled vehicles and the messages and controls exchanged with them.

## Deployment
CARMAcloud can be deployed on a Linux server. Ensure you have a properly configured git client and Java Development Kit before executing the following commands (some paths may have to be updated depending on the version and installation of the JDK):
```
cd /tmp
git clone https://github.com/usdot-fhwa-stol/carma-cloud.git carma-cloud
sudo -u root apt-get update && sudo -u root apt-get install pkg-config sqlite3 libsqlite3-dev iptables
sudo chmod 755 carma-cloud/*.sh
sudo carma-cloud/iptables.sh
wget https://download.osgeo.org/proj/proj-6.1.1.tar.gz && tar -xzf proj-6.1.1.tar.gz && mv proj-6.1.1 proj
cd /tmp/proj && ./configure && make && sudo -u root make install
wget http://archive.apache.org/dist/tomcat/tomcat-9/v9.0.34/bin/apache-tomcat-9.0.34.tar.gz && tar -xzf apache-tomcat-9.0.34.tar.gz && mv apache-tomcat-9.0.34 tomcat && rm -rf apache-tomcat-9.0.34.tar.gz
mkdir -p tomcat/webapps/carmacloud/ROOT && mv carma-cloud/web/* tomcat/webapps/carmacloud/ROOT/
mv carma-cloud/end_cc.sh tomcat
mv carma-cloud/start_cc.sh tomcat
mkdir -p tomcat/work/carmacloud/xodr
mkdir -p tomcat/work/carmacloud/validate/xodr
find ./carma-cloud/src -name "*.java" > sources.txt && mkdir -p tomcat/webapps/carmacloud/ROOT/WEB-INF/classes
javac -cp "tomcat/lib/servlet-api.jar:cc/lib/*" -d tomcat/webapps/carmacloud/ROOT/WEB-INF/classes @sources.txt
sed -i '/<\/Engine>/ i \ \ \ \ \  <Host name="carmacloud" appBase="webapps/carmacloud" unpackWARs="false" autoDeploy="false">\n      </Host>' tomcat/conf/server.xml
echo -e '127.0.0.1\tcarmacloud' | sudo -u root tee -a /etc/hosts
mv carma-cloud/lib/libcs2cswrapper.so /usr/lib/
mv carma-cloud/lib tomcat/webapps/carmacloud/ROOT/WEB-INF/
touch tomcat/webapps/carmacloud/event.csv
mv carma-cloud/osmbin/rop.csv tomcat/webapps/carmacloud/
mv carma-cloud/osmbin/storm.csv tomcat/webapps/carmacloud/
java -cp tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:tomcat/lib/servlet-api.jar cc.ws.UserMgr ccadmin admin_testpw > tomcat/webapps/carmacloud/user.csv
gunzip carma-cloud/osmbin/*.gz
mv carma-cloud/osmbin tomcat/webapps/carmacloud/
rm -f sources.txt && rm -rf carma-cloud
sudo -u root mv tomcat /opt/
sudo groupadd v2xhub
sudo groupadd tomcat
useradd -g v2xhub -m v2xhub
useradd -g tomcat -m tomcat
```
These commands will download the CARMAcloud source code from github, necessary dependencies, and the tomcat webserver. Changes to the tomcat version might be necessary if version 9.0.34 is no longer available on the Apache mirror. You can also download tomcat directly from the tomcat website. Tomcat cannot bind the port 80 when ran as the tomcat user, so iptables is used to redirect port 80 to 8080. Next the java code will be compiled and the .class files will be placed in the correct directory. Tomcat's server.xml file will have the carmacloud host entry inserted in the correct location. Carmacloud will be added to the /etc/hosts file. The java command that runs cc.ws.UserMgr will create the ccadmin user for the system. It is suggested to change to password to something more secure by replacing "admin_testpw" with the desired password in the command. Groups and users for tomcat and v2xhub will be created.
## Configuration
The Tomcat webserver must be configured to run on the deployment server. Click [here](https://tomcat.apache.org/tomcat-9.0-doc/index.html) to find the documentation for Tomcat. There are many configuration items to considered but two that must be addressed for any deployment of the system are adding the domain name and SSL Certificate to the server.xml file. Proper security measures dealing with file permissions should be taken as well. According to Tomcat's [documentation](https://tomcat.apache.org/tomcat-9.0-doc/security-howto.html#System_Properties) a tomcat user and group should be created in the operating system. The standard configuration for file permissions is to have all Tomcat files owned by root with group tomcat. Owner should have read/write permissions, group should have read permission, and world has no permissions. The exceptions are the logs, temp and work directory that are owned by the tomcat user rather than root. Additional users can be added to CARMAcloud by running the following command and replacing <username> and <password> with the desired user name and password respectively:
```
java -cp /opt/tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:/opt/tomcat/lib/servlet-api.jar cc.ws.UserMgr <username> <password> >> /opt/tomcat/webapps/carmacloud/user.csv
```
Additionally, you will need to generate an [access token](https://account.mapbox.com/access-tokens/) from Mapbox, and replace the text \<your access token goes here\> with your access token in the /opt/tomcat/webapps/carmacloud/ROOT/script/map.js file. The domain names in /opt/tomcat/webapps/ROOT/mapbox/sourcelayers.json and /opt/tomcat/webapps/ROOT/mapbox/validatexodr_sourcelayers.json files must be updated to match the domain name the Mapbox access token is associated with. File paths may need to be updated in web.xml for the servlets which describe where to find and save data files for CARMACloud. Once everything is configured for the deployment, convenience scripts have been included to start and stop Tomcat which can be ran with the following commands:
```
/opt/tomcat/start_cc.sh
/opt/tomcat/end_cc.sh
```

### Bounds File for IHP2 Speed Harmonization
This file allows the configuration of subsegments to be used for the IHP2 Speed Harmonization algorithm. The file consists of sections separated by a blank line. Each section has n number of lines. Each line describes a rectangular polygon that represents one subsegment for the algorithm. The first polygon of each section is the furthest downstream subsegment of a path along a road. Polygons must be defined by four points such that all lanes in a single direction of travel are encompassed by the polygon. The first point needs to be at the beginning of the subsegment, in regards to direction of travel, closest to the left edge of the lanes. The second point then follows the direction of travel to the end of the subsegment’s left edge. The third point is at the end of the subsegment’s right edge. The fourth point is at the beginning of the subsegment’s left edge. Arranging the points in this order should result in an open polygon with a clockwise order of points. The maxspeed traffic controls generated by the algorithm will have the geometries of these polygons.  
Example:
  
> x<sub>0</sub><sub>0</sub>,y<sub>0</sub><sub>0</sub>,x<sub>0</sub><sub>1</sub>,y<sub>0</sub><sub>1</sub>,x<sub>0</sub><sub>2</sub>,y<sub>0</sub><sub>2</sub>,x<sub>0</sub><sub>3</sub>,y<sub>0</sub><sub>3</sub> // furthest downstream subsegment of path 0  
> x<sub>1</sub><sub>0</sub>,y<sub>1</sub><sub>0</sub>,x<sub>1</sub><sub>1</sub>,y<sub>1</sub><sub>1</sub>,x<sub>1</sub><sub>2</sub>,y<sub>1</sub><sub>2</sub>,x<sub>1</sub><sub>3</sub>,y<sub>1</sub><sub>3</sub>  
> ...  
> x<sub>(n-2)</sub><sub>0</sub>,y<sub>(n-2)</sub><sub>0</sub>,x<sub>(n-2)</sub><sub>1</sub>,y<sub>(n-2)</sub><sub>1</sub>,x<sub>(n-2)</sub><sub>2</sub>,y<sub>(n-2)</sub><sub>2</sub>,x<sub>(n-2)</sub><sub>3</sub>,y<sub>(n-2)</sub><sub>3</sub>  
> x<sub>(n-1)</sub><sub>0</sub>,y<sub>(n-1)</sub><sub>0</sub>,x<sub>(n-1)</sub><sub>1</sub>,y<sub>(n-1)</sub><sub>1</sub>,x<sub>(n-1)</sub><sub>2</sub>,y<sub>(n-1)</sub><sub>2</sub>,x<sub>(n-1)</sub><sub>3</sub>,y<sub>(n-1)</sub><sub>3</sub>  
> <br>
> x<sub>0</sub><sub>0</sub>,y<sub>0</sub><sub>0</sub>,x<sub>0</sub><sub>1</sub>,y<sub>0</sub><sub>1</sub>,x<sub>0</sub><sub>2</sub>,y<sub>0</sub><sub>2</sub>,x<sub>0</sub><sub>3</sub>,y<sub>0</sub><sub>3</sub> // furthest downstream subsegment of path 1  
> x<sub>1</sub><sub>0</sub>,y<sub>1</sub><sub>0</sub>,x<sub>1</sub><sub>1</sub>,y<sub>1</sub><sub>1</sub>,x<sub>1</sub><sub>2</sub>,y<sub>1</sub><sub>2</sub>,x<sub>1</sub><sub>3</sub>,y<sub>1</sub><sub>3</sub>  
> ...  
> x<sub>(n-2)</sub><sub>0</sub>,y<sub>(n-2)</sub><sub>0</sub>,x<sub>(n-2)</sub><sub>1</sub>,y<sub>(n-2)</sub><sub>1</sub>,x<sub>(n-2)</sub><sub>2</sub>,y<sub>(n-2)</sub><sub>2</sub>,x<sub>(n-2)</sub><sub>3</sub>,y<sub>(n-2)</sub><sub>3</sub>  
> x<sub>(n-1)</sub><sub>0</sub>,y<sub>(n-1)</sub><sub>0</sub>,x<sub>(n-1)</sub><sub>1</sub>,y<sub>(n-1)</sub><sub>1</sub>,x<sub>(n-1)</sub><sub>2</sub>,y<sub>(n-1)</sub><sub>2</sub>,x<sub>(n-1)</sub><sub>3</sub>,y<sub>(n-1)</sub><sub>3</sub>  

## Contribution
Welcome to the CARMA contributing guide. Please read this guide to learn about our development process, how to propose pull requests and improvements, and how to build and test your changes to this project. [CARMA Contributing Guide](Contributing.md) 

## Code of Conduct 
Please read our [CARMA Code of Conduct](Code_of_Conduct.md) which outlines our expectations for participants within the CARMA community, as well as steps to reporting unacceptable behavior. We are committed to providing a welcoming and inspiring community for all and expect our code of conduct to be honored. Anyone who violates this code of conduct may be banned from the community.

## Attribution
The development team would like to acknowledge the people who have made direct contributions to the design and code in this repository. [CARMA Attribution](ATTRIBUTION.md) 

## License
By contributing to the Federal Highway Administration (FHWA) Connected Automated Research Mobility Applications (CARMA), you agree that your contributions will be licensed under its Apache License 2.0 license. [CARMA License](<docs/License.md>) 

## Contact
Please click on the CARMA logo below to visit the Federal Highway Adminstration(FHWA) CARMA website. For more information, contact CARMA@dot.gov.

[![CARMA Image](docs/image/CARMA_icon2.png)](https://highways.dot.gov/research/research-programs/operations/CARMA)
