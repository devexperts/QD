DXFEED/QDS API BINARIES AND SOURCES
===================================
Copyright (C) 2002-2020 Devexperts LLC

This package contains binaries and sources for dxFeed/QDS API.
Binaries are in "lib" directory, the corresponding sources in "src" directory.

  * dxlib.jar          - Collection of reusable utility classes from Devexperts.
  * dxfeed-api.jar     - dxFeed API classes.
  * qds.jar            - Client-side QDS API, implementation, and dxFeed implementation classes.
                         Use this library along with dxlib.jar and dxfeed-api.jar on the client-side to
                         receive data from dxFeed.
  * mars.jar           - Monitoring and Reporting System agent-side implementation classes.
  * jmxtools.jar       - JMX HTML console
  * qds-file.jar       - dxFeed/QDS plugin that enables file reading/writing.
  * qds-monitoring.jar - dxFeed/QDS plugin that enables JMX and MARS monitoring.
  * qds-tools.jar      - Server-side QDS tools and additional features.

Dependency Diagram
------------------

    +------------+     +-------+     +------+
    | dxfeed-api | --> | dxlib | <-- | mars |
    +------------+     +-------+     +------+
               ^         ^               ^
               |         |               |
             +-------------+     +----------------+     +----------+
             |     qds     | <-- | qds-monitoring | --> | jmxtools |
             +-------------+     +----------------+     +----------+
               ^         ^               ^
               |         |               |
      +----------+     +--------------------+
      | qds-file | <-- |      qds-tools     |
      +----------+     +--------------------+


All dependencies are included into jar file manifests via Class-Path attribute.

