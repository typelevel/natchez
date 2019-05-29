# Natchez Trace

This is an experimental distributed tracing effect for Cats, inspired by earlier work done on [puretracing](https://github.com/tabdulradi/puretracing).

It currently has integration with:

- [OpenTracing](https://opentracing.io/)
- [Jaeger](https://www.jaegertracing.io/) (via OpenTracing)
- [Honeycomb](https://www.honeycomb.io/)

Anyway it looks like this:

```scala
def doStuff[F[_]: Trace]: F[Whatevs] =
  Trace[F].span("span here") {
    // Do stuff in F here, it will be associated with the span.
    // You can also use the instance to set baggage/tags and log things.
    // You can turn the context into a set of headers you can pass on to a
    // remote service, which can pick up and continue the trace.
  }
```

To try it out start up [Jaeger](https://www.jaegertracing.io/) as an OpenTracing server.

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

You'll also need to start up the Skunk test database. Sorry, the example does database stuff for no particularly good reason.

```
docker run -p5432:5432 -d tpolecat/skunk-world
```

Now, finally, if you run the example in `modules/examples` and go to [localhost:16686](http://localhost:16686) you can then select `natchez-example` and search for traces.

To use it in your own projects (not recommended yet) you can do

```scala
// search at sonatype or central for latest version, sorry
libraryDependencies += "org.tpolecat"  %% "natchez-jaeger" % <version>
```
