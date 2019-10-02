
# Log Handler

This is a simple Natchez back-end that prints traces to a logger, formatted as a JSON structure.

- Continued traces have correct parent span/trace ids but otherwise distributed tracing is unsupported (because there is no span collector).
- Spans are displayed as JSON objects where span fields are object properties. A special property called `children` contains a list of child spans.

