# Kernels

Each `Span` has a `Kernel`, which is a back-end-specific hunk of data that can be passed to another computer, allowing that computer to continue the current span. This is how tracing becomes **distributed**.

## Kernel Data

`Kernel` wraps a single value `toHeaders: Map[String, String]`, allowing for straightforward serialization. These values are typically turned into HTTP headers (and parsed from headers on the other end). See the `EntryPoint` @ref:[reference](entrypoints.md) for an example.

