@echo off
rem 把 agent 注入 HMCL 的 Windows 启动器（HMCLauncher 会读取 HMCL_JAVA_OPTS）。
rem 放在 HMCL-*.exe 与 region-agent.jar 同一目录下运行。
set "HMCL_JAVA_OPTS=-javaagent:%~dp0region-agent.jar"
start "" "%~dp0HMCL-3.17.0.362.exe"
