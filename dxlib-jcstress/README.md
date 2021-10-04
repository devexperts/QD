
# dxLib JCStress concurrent tests 

The test suite is based on the open source framework JCStress (https://openjdk.java.net/projects/code-tools/jcstress/).

**Disclaimer** 

Test harness provided "as is" and designed to support development of various dxLib components. 
Failure of a test may not indicate an issue in the production code.

## Build

JCStress test package will be built along with other modules during Maven `package` phase.

A self-contained jar is provided for convenience: `dxlib-jcstress/target/dxlib-jcstress.jar`

## Running tests

To run all available tests just launch provided self-contained jar (use `-h` to discover available options):

```bash
java -jar dxlib-jcstress/target/dxlib-jcstress.jar [opts]
```

List of all available tests: `... dxlib-jcstress.jar -l`

Individual tests may be launched by providing options to `dxlib-jcstress.jar`
(`... dxlib-jcstress.jar <benchmark-name-regex>`) 

