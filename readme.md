start ELK
--------------
D:\tools\ELK\elasticsearch-6.2.1\bin> .\elasticsearch
D:\tools\ELK\kibana-6.2.1\bin> .\kibana.bat
D:\tools\ELK\grafana-4.6.3> ./bin/grafana-server web


start coherence (with prometheus)
-----------------------------------
D:\tools\prometheus> java -javaagent:./jmx_prometheus_javaagent-0.2.0.jar=9080:config.yml -jar D:\tools\Oracle\coherence\lib\coherence.jar
D:\tools\Oracle\coherence\bin> .\cache-server.cmd -jmx
D:\tools\prometheus\prometheus-2.1.0.windows-amd64> ./prometheus --config.file=prometheus.yml



notes
-----------------------------------

docker run -d store/oracle/coherence:12.2.1.1

#use docker compose
https://finestructure.co/blog/2016/5/16/monitoring-with-prometheus-grafana-docker-part-1

1) start coherence (with jmx expose via rmi)
D:\tools\Oracle\coherence\bin> .\cache-server.cmd -jmx

2) start jmx_exporter
D:\tools\prometheus> java -javaagent:./jmx_prometheus_javaagent-0.2.0.jar=9080:config.yml -jar D:\tools\Oracle\coherence\lib\coherence.jar

3) start prometheus server
D:\tools\prometheus\prometheus-2.1.0.windows-amd64> ./prometheus --config.file=prometheus.yml

cd D:\tools\prometheus\prometheus-2.1.0.windows-amd64 && d: && start .\prometheus --config.file=prometheus.yml


4) start grafana
D:\tools\grafana-4.6.3> ./bin/grafana-server web


NB
cache-server.cmd/jmxremote.port=9010 -> jmx_prometheus_javaagent/config/scrape-port -> jmx_prometheus_javaagent/service-port -> prometheus/targets-port

prometheus server-> localhost:9090/metrics
grafana/datasource/http://localhost:9090
grafana server->http://localhost:3000




appendix

1) cache-server.cmd
------------------------------------
@echo off
@
@rem This will start a cache server
@
setlocal

:config
@rem specify the Coherence installation directory
set coherence_home=%~dp0\..

@rem specify the JVM heap size
set memory=1g

:start
if not exist "%coherence_home%\lib\coherence.jar" goto instructions

set java_home=%java_home:"=%

if "%java_home%"=="" (set java_exec=java) else (set "java_exec=%java_home%\bin\java")


:launch

if "%1"=="-jmx" (
	set jmxproperties=-Dcoherence.management=all -Dcoherence.management.remote=true
	shift
)

set java_opts=-Xms%memory% -Xmx%memory% %jmxproperties%

set java_opts=%java_opts% -Dcom.sun.management.jmxremote
set java_opts=%java_opts% -Dcom.sun.management.jmxremote.port=9010
set java_opts=%java_opts% -Dcom.sun.management.jmxremote.local.only=false
set java_opts=%java_opts% -Dcom.sun.management.jmxremote.authenticate=false
set java_opts=%java_opts% -Dcom.sun.management.jmxremote.ssl=false

"%java_exec%" -server -showversion %java_opts% -cp "%coherence_home%\lib\coherence.jar" com.tangosol.net.DefaultCacheServer %*

goto exit

:instructions

echo Usage:
echo   ^<coherence_home^>\bin\cache-server.cmd
goto exit

:exit
endlocal
@echo on

2) config.yml
----------------------------------------
---
startDelaySeconds: 0
hostPort: 127.0.0.1:9010
ssl: false


3) prometheus.yml
-----------------------------------------
# my global config
global:
  scrape_interval:     15s # Set the scrape interval to every 15 seconds. Default is every 1 minute.
  evaluation_interval: 15s # Evaluate rules every 15 seconds. The default is every 1 minute.
  # scrape_timeout is set to the global default (10s).

# Alertmanager configuration
alerting:
  alertmanagers:
  - static_configs:
    - targets:
      # - alertmanager:9093

# Load rules once and periodically evaluate them according to the global 'evaluation_interval'.
rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

# A scrape configuration containing exactly one endpoint to scrape:
# Here it's Prometheus itself.
scrape_configs:
  # The job name is added as a label `job=<job_name>` to any timeseries scraped from this config.
  - job_name: 'prometheus'

    # metrics_path defaults to '/metrics'
    # scheme defaults to 'http'.

    static_configs:
      - targets: ['localhost:9080']



queries
----------------------------------
Docker and Container Stats -> https://grafana.com/dashboards/4170
"expr": "100 - ((node_memory_MemAvailable{instance=~\"$node:.*\"} * 100) / node_memory_MemTotal{instance=~\"$node:.*\"})",
"expr": "((node_memory_SwapTotal{instance=~\"$node:.*\"} - node_memory_SwapFree{instance=~\"$node:.*\"}) / (node_memory_SwapTotal{instance=~\"$node:.*\"} )) * 100",


https://www.elastic.co/guide/en/elasticsearch/guide/current/nested-mapping.html



