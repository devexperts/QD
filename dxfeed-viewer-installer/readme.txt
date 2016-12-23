This module creates Windows, Unix, and OSX installers for dxFeedViewer.
It requires install4j and therefore is not enabled by default.

To build installers run:
    mvn clean install -Dinstall4j=<Path to install4j compiler executable>
For example for Windows (assuming default install4j location):
    mvn clean install -Dinstall4j="C:\Program Files\install4j6\bin\install4jc.exe"

Install bundles are created into target/install4j folder.
