JMX tools stub
==============

Module `jmxtools-stub` replicates a minimal fragment of API from jmxtools.jar from the JDMK package required for
compiling the initialization code in `qds-monitoring`.

JMX tools package is licensed under "Oracle Binary Code License Agreement for Java SE and JavaFX Technologies"
(see https://www.oracle.com/downloads/licenses/java-se-archive-license.html) and can be downloaded manually as
a part of the "Java Management Extensions 1.2 Reference Implementation" software from
https://www.oracle.com/java/technologies/java-archive-downloads-java-plat-downloads.html

If the original `jmxtools.jar` should be used for the build, the artifact obtained from Oracle should be placed into 
the local Maven repository as 'com.sun.jdmk:jmxtools:1.2.8'.

