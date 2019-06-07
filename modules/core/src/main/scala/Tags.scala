// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

object Tags {

  /** The software package, framework, library, or module that generated the associated Span. E.g., "grpc", "django", "JDBI". */
  def component(c: String): (String, TraceValue) = ("component", c)

  object db {
    private val prefix = "db"

    /**
     * Database instance name.
     * E.g., In java, if the jdbc.url="jdbc:mysql://127.0.0.1:3306/customers", the instance name is "customers".
     */
    def instance(i: String): (String, TraceValue) = (s"$prefix.instance", i)

    /**
     * A database statement for the given database type.
     * E.g., for db.type="sql", "SELECT * FROM wuser_table"; for db.type="redis", "SET mykey 'WuValue'".
     */
    def statement(s: String): (String, TraceValue) = (s"$prefix.statement", s)

    /**
     * Database type.
     * For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis".
     */
    def `type`(t: String): (String, TraceValue) = (s"$prefix.type", t)

    /**
     * Username for accessing database.
     * E.g., "readonly_user" or "reporting_user"
     */
    def user(u: String): (String, TraceValue) = (s"$prefix.user", u)
  }

  /** true if and only if the application considers the operation represented by the Span to have failed */
  def error(bool: Boolean): (String, TraceValue) = ("error", bool)

  object http {
    private val prefix = "http"

    /** HTTP method of the request for the associated Span. E.g., "GET", "POST" */
    def method(m: String): (String, TraceValue) = (s"$prefix.method", m)

    /** HTTP response status code for the associated Span. E.g., 200, 503, 404 */
    def status_code(s: String): (String, TraceValue) = (s"$prefix.status_code", s)

    /**
     * URL of the request being handled in this segment of the trace, in standard URI format.
     * E.g., "https://domain.net/path/to?resource=here"
     */
    def url(u: String): (String, TraceValue) = (s"$prefix.url", u)
  }

  object message_bus {
    /**
     * An address at which messages can be exchanged.
     * E.g. A Kafka record has an associated "topic name" that
     * can be extracted by the instrumented producer or consumer and stored using this tag.
     */
    def destination(d: String): (String, TraceValue) = (s"message_bus.destination", d)
  }

  object peer {
    private val prefix = "peer"

    /**
     * Remote "address", suitable for use in a networking client library.
     * This may be a "ip:port", a bare "hostname", a FQDN, or even a JDBC substring like "mysql://prod-db:3306"
     */
    def address(a: String): (String, TraceValue) = (s"$prefix.address", a)

    /** Remote hostname. E.g., "opentracing.io", "internal.dns.name" */
    def hostname(h: String): (String, TraceValue) = (s"$prefix.hostname", h)

    /** Remote IPv4 address as a .-separated tuple. E.g., "127.0.0.1" */
    def ipv4(i: String): (String, TraceValue) = (s"$prefix.ipv4", i)

    /**
     * Remote IPv6 address as a string of colon-separated 4-char hex tuples.
     * E.g., "2001:0db8:85a3:0000:0000:8a2e:0370:7334"s
     */
    def ipv6(i: String): (String, TraceValue) = (s"$prefix.ipv6", i)

    /** Remote port. E.g., 80 */
    def port(p: String): (String, TraceValue) = (s"$prefix.port", p)

    /**
     * Remote service name (for some unspecified definition of "service").
     * E.g., "elasticsearch", "a_custom_microservice", "memcache"
     */
    def service(s: String): (String, TraceValue) = (s"$prefix.service", s)
  }

  object sampling {
    /**
     * If greater than 0, a hint to the Tracer to do its best to capture the trace.
     * If 0, a hint to the trace to not-capture the trace.
     * If absent, the Tracer should use its default sampling mechanism.
     */
    def priority(p: Int): (String, TraceValue) = ("sampling.priority", p)
  }

  object span {
    /**
     * Either "client" or "server" for the appropriate roles in an RPC,
     * and "producer" or "consumer" for the appropriate roles in a messaging scenario.
     */
    def kind(k: String): (String, TraceValue) = ("span.kind", k)
  }

}
