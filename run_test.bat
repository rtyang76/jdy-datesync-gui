@echo off
setlocal

set CP=target/classes
set CP=%CP%;target/test-classes
set CP=%CP%;C:/Users/Administrator/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.12.3/jackson-databind-2.12.3.jar
set CP=%CP%;C:/Users/Administrator/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.12.3/jackson-core-2.12.3.jar
set CP=%CP%;C:/Users/Administrator/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.12.3/jackson-annotations-2.12.3.jar

java -cp "%CP%" org.example.StandaloneS60Test

endlocal
