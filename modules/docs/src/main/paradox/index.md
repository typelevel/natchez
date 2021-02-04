# Natchez


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

**Natchez** is a minimal distributed tracing library for Cats, inspired by earlier work done on [puretracing](https://github.com/tabdulradi/puretracing).

Natchez is published for **Scala $scala-versions$**, with limited support for Scala-JS.

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

    - The [Gitter Channel](https://gitter.im/tpolecat/natchez).
    - The [API Documentation](https://javadoc.io/doc/org.tpolecat/natchez-core_$scala.binary.version$/$version$/index.html).


3. Let us know how it goes!

## How to Contribute

- Test it out and let us know how it goes!
- If you see a typo in the doc, click the link at the bottom and fix it!
- If you find a bug please open an issue (or fix it and open a PR) at our [GitHub Repository](https://github.com/tpolecat/natchez).
- If you want to make a larger contribution, please open an issue first so we can discuss.

Note that there are two active version series right now:

- Versions **0.0.x** are built with Cats-Effect 2 from branch `master`. This is a terminal series and will end sometime in 2021.
- Versions **0.1.x** are built with Cats-Effect 3 from branch `series/0.1`. This will be the continuing series moving forward.
- Contibutions should target the `master` branch. Maintainers will merge these into `series/0.1` as needed.

