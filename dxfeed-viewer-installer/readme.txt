This module creates Windows, Unix, and OSX installers for dxFeedViewer.
It requires install4j and therefore is not enabled by default.

To build installers run:
    mvn clean install -Dinstall4j.home=<Path to install4j installation>
For example for Windows (assuming default install4j location):
    mvn clean install -Dinstall4j.home="C:\Program Files\install4j6"
By default, current project version will be built, you can specify version if needed:
    mvn clean install -Dinstall4j.home="C:\Program Files\install4j6" -Dviewer.version=3.295

Install bundles are created into target/media folder.