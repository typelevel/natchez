# Natchez Trace

This is an experimental [OpenTracing](https://opentracing.io/) effect for Cats, inspired by earlier work done on [puretracing](https://github.com/tabdulradi/puretracing).

```scala
def doStuff[F[_]: Trace]: F[Whatevs] =
  Trace[F].span("span here") {
    // do stuff in F here, will be associated with the span
    // can also use the instance to set baggage/tags and log things
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

Now, finally, if you run the example in `modules/jaeger` and go to [localhost:16686](http://localhost:16686) you can then select `natchez-example` and search for traces.

