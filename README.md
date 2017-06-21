# SoapUI Mock Runner for Commons Daemon

A simple wrapper class that can be used to run a SoapUI mock service with commons daemon. Why? Because the shell scripts that ship with soapui do not work properly under a system daemon (and yes I know about `-b`).

## Installation

To configure this bootstrap utility:

1. build and install the commons daemon native binary as documented [here](https://commons.apache.org/proper/commons-daemon/jsvc.html)
2. copy the commons daemon control wrapper to `${SOAPUI_HOME}/bin/commons-daemon.jar`
3. copy the `soapui-mock-service-bootstrap.jar` to `$SOAPUI_HOME/bin/bootstrap.jar`
4. install a system service (see below example)


Below an example systemd service configuration to start a soapui service. Copy to `/lib/systemd/system/soapui.service` and configure service with systemctl.

```
#
#  SoapUI Mockrunner Daemon File
#
[Unit]
Description=SoapUI Mockrunner
After=network.target

[Service]
Type=forking
PIDFile=/var/run/soapui.pid
Environment=JAVA_HOME=/usr/lib/jvm/default-java
Environment=SOAPUI_VERSION=5.3.0
Environment=SOAPUI_HOME=/opt/SoapUI-${SOAPUI_VERSION}
Environment=MOCK_PROJECT=/home/soapui/Fancy-soapui-project.xml
Environment=MOCK_PATH=/mock/fancy
Environment=MOCK_PORT=8989

ExecStart=/bin/jsvc \
          -wait 20 \
          -cwd /home/soapui \
          -Xms128m \
          -Xmx1024m \
          -server \
          -Djava.awt.headless=true \
          -Dsoapui.properties=soapui.properties \
          -Dsoapui.home="${SOAPUI_HOME}" \
          -Dsoapui.ext.libraries="${SOAPUI_HOME}/bin/ext" \
          -Dsoapui.ext.listeners="${SOAPUI_HOME}/bin/listeners" \
          -Dsoapui.ext.actions="${SOAPUI_HOME}/bin/actions" \
          -cp "${SOAPUI_HOME}/bin/commons-daemon.jar:${SOAPUI_HOME}/bin/bootstrap.jar:${SOAPUI_HOME}/bin/soapui-${SOAPUI_VERSION}.jar:${SOAPUI_HOME}/lib/*" \
          -user soapui \
          -java-home "${JAVA_HOME}" \
          -pidfile /var/run/soapui.pid \
          -errfile /var/log/soapui/daemon.err \
          -outfile /var/log/soapui/daemon.log \
           io.fares.soapui.mock.Bootstrap -a "${MOCK_PATH}" -p "${MOCK_PORT}" "${MOCK_PROJECT}"

ExecStop=/bin/jsvc \
         -wait 10 \
          -pidfile /var/run/soapui.pid \
          -stop \
          io.fares.soapui.mock.Bootstrap

[Install]
WantedBy=multi-user.target
```

## Notes

1. kernel version 4.4.0-81 is borked and causes `jsvc` to bomb out with segmentation fault [see this bug report](https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=865311) only fix is to stay on/downgrade to kernel 4.4.0-79
