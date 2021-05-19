# Trace Values

The fields in a `Span` consist of tagged values of type `TraceValue`. Natchez supports three data types, which seem to be commonly supported by tracing back ends. You can construct them explicitly, or via the provided implicit conversions (no import necessary).

- `Tracevalue.StringValue("foo")` or just `"foo"` via implicit conversion;
- `TraceValue.BooleanValue(true)` or just `true`; and
- `TraceValue.NumberValue(1.23)` or just `1.23`.

Note that `NumberValue` accepts any `java.lang.Number`.
