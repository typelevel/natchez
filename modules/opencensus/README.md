# OpenCensus

OpenCensus is capable of exporting to different collector types. 
  Exporters are added registered globally against a singleton registry.
  There is nothing stopping someone registering a new exporter outside
  of a side effect, so it is up to the user whether to do so inside the
  effects system or not.
  
The recommended approach for registering exporters is to use a resource.
  The snippet below shows how this may be done for the OpenCensus agent
  trace exporter implementation:
  
```scala
  def ocAgentEntryPoint[F[_]: Sync](system: String)(
      configure: OcAgentTraceExporterConfiguration.Builder => OcAgentTraceExporterConfiguration.Builder)
    : Resource[F, EntryPoint[F]] =
    Resource
      .make(
        Sync[F].delay(
          OcAgentTraceExporter.createAndRegister(configure(
            OcAgentTraceExporterConfiguration.builder().setServiceName(system))
            .build())))(_ =>
        Sync[F].delay(
          OcAgentTraceExporter.unregister()
      ))
      .flatMap(_ => Resource.liftF(entryPoint[F]))
```