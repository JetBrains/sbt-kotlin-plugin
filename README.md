[![Version](https://index.scala-lang.org/jetbrains/sbt-kotlin-plugin/sbt-kotlin-plugin/latest.svg)](https://index.scala-lang.org/jetbrains/sbt-kotlin-plugin/sbt-kotlin-plugin)

# sbt-kotlin-plugin

Build kotlin code using sbt.

The plugin is cross-published to:

| sbt Version | Published          |
|-------------|--------------------|
| 1.x         | :white_check_mark: |
| 2.x         |                    |

## Attribution

`sbt-kotlin-plugin` started off as a fork of [kotlin-plugin](https://github.com/pfn/kotlin-plugin) but has been
completely revamped with time and now shares almost no code with the original repository.

## Usage

* Insert into project/plugins.sbt:
```sbt
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "<version>")
```
* Enable plugin for your project:
```sbt
lazy val myProject = project.in(file(".")).enablePlugins(KotlinPlugin)
```
* If necessary, add `kotlinLib("stdlib")`, it is not included by default.
  * Loading standard kotlin libraries and plugins: use `kotlinLib(NAME)` as
    above to load standard kotlin modules provided by JetBrains. For JetBrains
    kotlin compiler plugins, use `kotlinPlugin(NAME)` (e.g.
    `kotlinPlugin("android-extensions")`). The difference is that the latter
    marks the module as a `compile-internal` dependency and will be excluded
    from the final build product.
  * Any other libraries can be loaded using the normal `libraryDependencies`
    mechanism. Compiler plugins should be added as a normal `libraryDependency`
    but specified to be `% "compile-internal"`
* If a non-standard Classpath key needs to be added to the kotlin compile step,
  it can be added using the `kotlinClasspath(KEY)` function
  * For example, to compile with the android platform using `android-sdk-plugin`:
    `kotlinClasspath(Compile, bootClasspath in Android)`

## Options

* `kotlincPluginOptions`: specifies options to pass to kotlin compiler plugins.
  Use `val plugin = KotlinPluginOptions(PLUGINID)` and
  `plugin.option(KEY, VALUE)` to populate this setting
* `kotlinSource`: specifies kotlin source directory, defaults to
  `src/main/kotlin` and `src/test/kotlin`
* `kotlinVersion`: specifies versions of kotlin compiler and libraries to use,
   defaults to `1.3.50`
* `kotlincOptions`: options to pass to the kotlin compiler
* `kotlincJvmTarget`: specifies JVM target version for building, defaults to `1.6`
* `kotlinLib(LIB)`: load a standard kotlin library, for example
  `kotlinLib("stdlib")`; the library will utilize the version specified in
  `kotlinVersion`
  plugin

### Examples

* See the [test cases](src/sbt-test/kotlin) for this plugin
