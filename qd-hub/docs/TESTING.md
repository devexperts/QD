# Brief instructions for qd-hub deployment and testing

## How to deploy and launch qd-hub
          
hub is packaged as a module for the new `launcher` tool, which is delivered as part of the `qds-tools` package.

In case hub itself is not yet packaged as a part of `dxfeed-bin` delivery package, the corresponding jar should be
placed as `qd-hub.jar` near the qds-tools.jar (normally in the `lib` folder).

'launcher' accepts a configuration file path/URL as a parameter, so the simplest way to launch hub is:

```bash
java -Ddxfeed.experimental.hub.enable -jar qds-tools.jar launcher hub.conf
```
                             
## Hub configuration model

The `qd-hub` configuration uses [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format 
(see detailed example in `./hub` subfolder) structured around following main building blocks:

```hocon
{
  launcherConfig {
    configCheckPeriod = 10s
    configReadPeriod = 1h
  }

  endpointProperties {
    # common properties for the endpoint configuration (mostly monitoring related for now)
  }

  modules = [{
      type = Hub
      name = <hub-instance-name>
      #... common properties
      universeConfig {
        include required("universe.conf")
        spaces {} # Space definitions
        products {} # Product definitions 
        # space overrides (mask-based control over individual definitions)
      }
     uplinks {} # Uplink definitions 
      downlinks {} # Downlink definitions
  }]
}
```

### Launcher & Environment
The top-level configuration defines the runtime container and environment endpoints:
* `launcherConfig`: Container behavior such as configuration check/reload intervals.
* `endpointProperties`: Global infrastructure endpoints (MARS configuration/address, JMX ports, monitoring).
* `modules`: Array of services managed by the launcher, declaring `type = Hub` for Hub instances.

### Hub Module 
The Hub module represents the qd-hub runtime instance:
* `universeConfig`: the domain configuration 
* `uplinks` / `downlinks`: declarations of "in" and "out" data connectors.

### Universe (`universeConfig`)
The central data domain model defining distributed data partitioning and channel abstractions:
* **Spaces (`spaces`)**: Independent, isolated data partitions. Each space manages a subset of symbols with defined 
  QD contracts (`ticker`, `stream`, `history`), and related parameters.
* **Products (`products`)**: Virtual multi-channel data abstractions that aggregate or slice data from underlying 
  spaces (or other products) into ordered list of channels with specific contracts and QOS parameters.
* **Space Overrides**: Global override lists applying common operational flags (such as activation, wildcard support, 
  or full event buffering) across multiple spaces.

It's supposed that typical universe configuration is split to a master-config containing specification of spaces and 
products shared over the whole organization's deployment and instance-specific adjustments. 

### Up/Down-Links
Connectors handling data flow into and out of the Hub:
* **Uplinks (`uplinks.<name>`)**: defines connections feeding data from upstream sources into designated target spaces.
  * *Key attributes*: address, filter, and target space.
* **Downlinks (`downlinks.<name>`)**: defines output connections to client applications or down-flow distributors in 
  one of two distribution modes:
  * *Space-based mode* ("space-by-port"): Routes data directly from specified spaces.
  * *Product-based mode*: Serves a composite multi-channel product to subscribers.
  * *Key attributes*: address, top-level filter, and target space(s) or product(s).
                                                                                    

## Main features to be tested

- Launcher itself and qd-hub module expected to support on-the-fly configuration updates and be resilient to 
  configuration errors. Particularly, erroneous configurations should be detected and rejected before making any
  changes in the current runtime.  
- QD-hub tries to apply configuration updates in a maximally unobtrusive way - elements of runtime that was not 
  affected by the configuration changes should work without any interruptions, secondary parameter changes
  will be applied without interrupting service of established connections if possible.    
- Heavy use of dynamic IPF filters in spaces, products and other places definitions is expected and fully supported. 
  Changes in filters should not significantly affect untouched subscription elements of established connections.
  Particularly, gradual moving of subscription elements between products/spaces through coordinated ipf filters change
  should work smooth. In case of temporary clash or "holes" in definitions due to asynchronous filter updates shall 
  not inflict double-source delivery or a permanent loss of subscription.
- In case of clash in space/product filter definitions (temporary or permanent), the preceding (leftmost) matching 
  component always "wins" a "double-matched" subscription element, regardless of having actual data.

## Known issues and limitations

- Some recent QOS features related to QD internal management and connections (namely striping, sticky subscription, 
  advanced aggregation period management, etc.) are not fully supported yet. 
  Global configuration through shared endpoint/system properties expected to work where they are originally supported, 
  but no individual config support for space endpoints.  
- Support for more sophisticated space/product list management in configuration is expected.
- Current connection infrastructure supports only a single space for "space-dedicated" downlinks, so each space 
  downlink need a separate connection port. The "multi-space" downlink connector sharing the same port is planned.
- Various minor configuration-related tweaks marked as TODO in reference config are yet to come.


