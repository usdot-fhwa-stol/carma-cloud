#!/bin/bash
set -e

if [[ -z "${CARMA_ADMIN_USER}" ]]; then
  echo "CARMA_ADMIN_USER env var required but not set!"
  sleep 5
  exit 1
fi

if [[ -z "${CARMA_ADMIN_PASSWORD}" ]]; then
  echo "CARMA_ADMIN_PASSWORD env var required but not set!"
  sleep 5
  exit 1
fi

if [[ -z "${MAPBOX_ACCESS_TOKEN}" ]]; then
  echo "MAPBOX_ACCESS_TOKEN env var required but not set!"
  sleep 5
  exit 1
fi

if [[ -z "${IMPLEMENTER_EMAIL}" ]]; then
  echo "IMPLEMENTER_EMAIL env var required but not set!"
  sleep 5
  exit 1
fi

if [[ -z "${DNS_NAME}" ]]; then
  echo "DNS_NAME env var required but not set!"
  sleep 5
  exit 1
fi

# Set the web user account
java -cp /opt/tomcat/webapps/carmacloud/ROOT/WEB-INF/classes/:/opt/tomcat/lib/servlet-api.jar cc.ws.UserMgr ${CARMA_ADMIN_USER} ${CARMA_ADMIN_PASSWORD} >> /opt/tomcat/webapps/carmacloud/user.csv

# Set mapbox config
cat /configs/map.js | envsubst '${MAPBOX_ACCESS_TOKEN}' > /opt/tomcat/webapps/carmacloud/ROOT/script/map.js
cat /configs/sourcelayers.json | envsubst '${DNS_NAME}' > /opt/tomcat/webapps/carmacloud/ROOT/mapbox/sourcelayers.json
cat /configs/validatexodr_sourcelayers.json | envsubst '${DNS_NAME}' > /opt/tomcat/webapps/carmacloud/ROOT/mapbox/validatexodr_sourcelayers.json

chown -R tomcat:tomcat /opt
chmod -R u+rw /opt
chmod -R g+rw /opt


# Start Tomcat
sudo -u tomcat /opt/tomcat/bin/catalina.sh run