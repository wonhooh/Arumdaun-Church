@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -d out src\main\java\com\arumdaun\church\Main.java
java -cp out com.arumdaun.church.Main
