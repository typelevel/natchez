# Natchez

[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.gg/CSh8u9yPMe)
[![Join the chat at https://gitter.im/skunk-pg/Lobby](https://badges.gitter.im/skunk-pg/Lobby.svg)](https://gitter.im/tpolecat/natchez?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/org.tpolecat/natchez-core_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/org.tpolecat/natchez-core_2.13)
[![Javadocs](https://javadoc.io/badge/org.tpolecat/natchez-core_2.13.svg)](https://javadoc.io/doc/org.tpolecat/skunk-core_2.12)

![](natchez.jpg)

_The Natchez Trace, also known as the "Old Natchez Trace", is a historic forest trail within the United States which extends roughly 440 miles (710 km) from Nashville, Tennessee, to Natchez, Mississippi, linking the Cumberland, Tennessee, and Mississippi rivers._

_Credits: [Wikipedia](https://en.wikipedia.org/wiki/Natchez_Trace) and the [National Park Service](https://www.nps.gov/natr/index.htm)._

@@@index

* [Overview](overview.md)
* [Reference](reference/index.md)
* [Back Ends](backends/index.md)
* [Examples](examples/index.md)

@@@

## Welcome!

**Natchez** is a minimal distributed tracing library for Cats, inspired by earlier work done on [puretracing](https://github.com/tabdulradi/puretracing). Natchez is published for **Scala $scala-versions$**, with limited support for Scala-JS.

Below are the available version series (see [releases](https://github.com/tpolecat/natchez/releases) for exact version numbers). You are strongly encouraged to use the **Active** series. Older series will receive bug fixes when necessary but are not actively maintained.

| Series    | Status     | 2.12 | 2.13 | 3.0 | Cats-Effect |
|:---------:|------------|:----:|:----:|:---:|:-----------:|
| **0.1.x** | **Active** | ✅   | ✅   | ✅   | **3.x**   |
| 0.0.x     | EOL        | ✅   | ✅   | ✅   | 2.x |

## Quick Start

1. Choose your dependency:

    - If you wish to write **application code** then you should use the dependency specific to your tracing @ref[back end](backends/index.md).

    - If you wish to write **library code** that supports any tracing back-end, then you should use the core dependency below (also available for Scala-JS).

    @@dependency[sbt,Maven,Gradle] {
      group="$org$"
      artifact="$core-dep$"
      version="$version$"
    }


2. Read the @ref:[Overview](overview.md) and explore from there.


    Natchez is written for [cats](http://typelevel.org/cats/) and [cats-effect](https://typelevel.org/cats-effect/). This documentation assumes you are familiar with pure-functional programming with effects, including use of `Resource` and tagless-final coding style. If you run into trouble be sure to check out:

    - The Natchez channel on [Typelevel Discord](https://sca.la/typeleveldiscord).
    - The [API Documentation](https://javadoc.io/doc/org.tpolecat/natchez-core_$scala.binary.version$/$version$/index.html).


3. Let us know how it goes!

## How to Contribute

This is a community-supported project and contributions are welcome!

- If you see a typo in the doc, click the link at the bottom and fix it!
- If you find a bug please open an issue (or fix it and open a PR) at our [GitHub Repository](https://github.com/tpolecat/natchez).
- If you want to make a larger contribution, please open an issue first so we can discuss.

Contibutions should target the **newest active branch** (see above). Maintainers will back-port to older branches when appropriate.
