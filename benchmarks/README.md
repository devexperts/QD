
# QD benchmarks 

The benchmark suite is based on the open source framework JMH (https://openjdk.java.net/projects/code-tools/jmh/).

**Disclaimer** 

Benchmarks are provided "as is" and designed to support development of various components and not 
intended for "official" measurement of the project components performance.

## Build

Benchmarks package will be built along with other modules during Maven `package` phase.

A self-contained jar is provided for convenience: `benchmarks/target/benchmarks.jar`

## Running benchmarks

To run all available benchmarks jast launch provided self-contained jar (use `-h` to discover available options):

```bash
java -jar benchmarks/target/benchmarks.jar [opts]
```

List of all available benchmarks: `... benchmarks.jar -l`

Individual benchmarks may be launched either by providing options to bookmarks.jar 
(`... benchmarks.jar <benchmark-name-regex>`) or by launching starter method of individual benchmark class 
(`java -cp benchmarks.jar <benchmark-class>`). The latter way is less flexible and may be useful to run a
benchmark set from an IDE.

