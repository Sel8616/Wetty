#Wetty

###⿻ About
***
WebApp-Jetty Integration Demo.

This is a spring-mvc webapp project using gradle as build tool.

>The essentials:

1. Class 'Main'.
    
2. Overridden gradle task 'war'.


###♨ Features
* The output war file is executable.


###☞ Usage
1. Building

    1. Create a webapp project in IntelliJ IDEA.

    2. Copy class 'Main' into the src folder. 
    
    3. Copy task 'war' into build.gradle file.

    4. Refresh the gradle project.
    
    5. Use task 'war' to build the artifact.

2. Running

    >java -jar xxx.war


###☀ Notice
1. The following dependencies required:

    * javax.servlet:javax.servlet-api:${ver_servlet}
    
    * org.eclipse.jetty:jetty-webapp:${ver_jetty}

2. The version of gradle > 3.0.


###☺ Contact
***
✉  sel8616@gmail.com || philshang@163.com

Ⓠ  117764756