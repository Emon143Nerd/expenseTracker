@echo off
cd /d %~dp0\client
set CP=../lib/*;./src;./resources
if not exist out mkdir out
for /r src %%f in (*.java) do (
  if not defined FILES (set FILES="%%f") else (set FILES=%FILES% "%%f")
)
javac -d out -cp "%CP%" %FILES%
java -cp "out;../lib/*;resources" com.expensedash.client.ClientMain
