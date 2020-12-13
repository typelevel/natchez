# Natchez Trace

[![Join the chat at https://gitter.im/tpolecat/natchez](https://badges.gitter.im/tpolecat/natchez.svg)](https://gitter.im/tpolecat/natchez?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

> **Note on Versioning
> - Versions 0.0.x are built with Cats-Effect 2 from branch `master`. This is a terminal series and will end sometime in 2021. 
> - Versions 0.1.x are built with Cats-Effect 3 from branch `series/0.1`. This will be the continuing series moving forward.
> - **Contibutions should target `master`**. Maintainers will merge these into `series/0.1` as needed.

This is a minimal distributed tracing effect for Cats, inspired by earlier work done on [puretracing](https://github.com/tabdulradi/puretracing). There is very little documentation (sorry) but nevertheless people are using this at scale and it seems to work.

Natchez currently has integration with:

- [Datadog](https://www.datadoghq.com)
- [Honeycomb](https://www.honeycomb.io/)
- [Jaeger](https://www.jaegertracing.io/)
- [LightStep](https://lightstep.com)
- [OpenCensus](https://www.opencensus.io/)

It supports

- spans
- numeric, string, and boolean fields
- swizzling to and from http headers

It does not support

- logs
- baggage

If you can make a case for either of these ideas I will consider supporting them, but they seem unnecessary to me.

The `Trace` effect looks like this:

```scala
def doStuff[F[_]: Trace]: F[Whatevs] =
  Trace[F].span("span here") {
    // Do stuff in F here. This is the extent of the span.
    // You can use the instance to:
    //  - create child spans
    //  - set data fields
    //  - extract a set of headers to pass to a remote
    //    service, which can then continue the trace
  }
```

To try it out start up [Jaeger](https://www.jaegertracing.io/) as your trace collector.

```
docker run -d --name jaeger \
  -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 \
  -p 5775:5775/udp \
  -p 6831:6831/udp \
  -p 6832:6832/udp \
  -p 5778:5778 \
  -p 16686:16686 \
  -p 14268:14268 \
  -p 9411:9411 \
  jaegertracing/all-in-one:1.8
```

Now, finally, if you run the example in `modules/examples` (with `sbt examples/run`) and go to [localhost:16686](http://localhost:16686) you can then select `natchez-example` and search for traces.

To use it in your own projects (not recommended yet) you can do

```scala
// or whichever module you need
libraryDependencies += "org.tpolecat"  %% "natchez-jaeger" % <version>
```

