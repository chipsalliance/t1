# Chisel Interface

This project is a meta project used as a standard library defining the interfaces for chisel projects. It aims providing a interchangeability, it refers to specification from different upstreams, e.g. JEDEC, ARM, providing a standard chisel interface for them.

## Releasing for each project
The project versions is based on the [semver](https://semver.org/), to for a fine-control on different interface and maintaining interface as stable as possible, for each interface project, it has its own version file, before releasing 1.0.0, downstream users shouldn't anticipate its being stable enough for common use, but can use it experimentally(by accepting any incompatible changes.). Developer should use `mill $someProject.setVersion 0.0.x` to set version.

## How to contribute

### Add the interface readme.

Provide a readme to the interface, e.g: `dwbb/readme.md`. It should align to corresponding specification with the URL and filename. It should always follow a stable version of the specification, if specification is updated, the old specification should remain as what it is, e.g. upgrading AXI3 to AXI4, you shouldn't remove the AXI3, but keep both interface and refer them to different specification files.

### Config the build system

Adding project as a sub-directory, and refer to the `build.sc` and `common.sc`, make it as a standalone build target.

The `common.sc` is used to define the dependency, please don't depend on any project other than Chisel unless a specific reason. Thus it's simple to define the common trait.
```scala
trait SomeModule extends ChiselInterfaceModule
```

The `build.sc` is used to define the build flow at current project. It's simple to follow this pattern:
```scala
object somemodule
    extends common.DWBBModule
    with ScalafmtModule
    with ChiselInterfacePublishModule {
  m =>
  def millSourcePath = os.pwd / "somemodule"
  def scalaVersion = T(v.scala)
  def chiselIvy = Some(v.chisel)
  def chiselPluginIvy = Some(v.chiselPlugin)
}
```

### Adding Interfaces

The basic interface should be a parameterized `Bundle` or `Record` class, but user friendly and documented. If it's a standard blackbox, e.g. Synopsys Designwire Building Block, Cadence Chipware, a blackbox should be provided as well.

After finishing the code, you should run
```bash
mill --meta-level 1 mill.scalalib.scalafmt.ScalafmtModule/reformatAll sources
mill __.reformat
```
to format your code.


CI will run
```
mill --meta-level 1 mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll sources
mill __.checkFormat
mill __.compile
```

## License

The license of specifications is vendor only, Chips Alliance or Jiuyang doesn't own their copyright. The license of chisel-interface is released under the [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html), and copyright all reserved to Jiuyang Liu <liu@jiuyang.me>.