> **Warning**
>
> VMware has ended active development of this project. this repository will no longer be updated.

# Sample Trace Generator

This trace generator tool sends synthetic spans to a [Wavefront Proxy](https://docs.wavefront.com/proxies.html) to
create interesting app topologies for demonstrating Distributed Tracing. It enables thorough demos of tracing without
using real apps or customer data, without laboriously creating each scenario.

Using the proxy’s `customTracingListenerPorts`
, [RED metrics](https://docs.wavefront.com/trace_data_details.html#red-metrics) and trace edges are computed
automatically, so from the Wavefront system’s perspective these are real traces.

### Features:

- Creates synthetic traces containing multiple spans
- Can generate random errors, latencies, durations
    - Errors may contain SpanLogs and proper tags
    - latencies and durations line up for realistic timing statistics
- Define apps and services via YAML
    - Minimal configuration required - entire topology can be generated randomly
    - [word lists](src/main/resources/wordlists.yaml) may be defined for random service, application and operation names
- Define call graph between services, applications and external endpoint

## Configuration

All fields are optional - omitted fields are filled by the trace generator as a best-effort.

### Data Format

A config file may contain `entrypoints` and `applications` at the top level.

Key | Definition
----|-----
`entrypoints`  | list of strings, dot-delimited app.service.operation starting points
`applications` | map of name to `application definition`

Each `application definition` contains a map of services

Key | Definition
----|-----
`services` | map of name to `service definition`

Each `service definition` contains a map of operations plus configuration for the service.

Key | Definition
----|-----
`operations` | map of name to `operation definition`
`tags` | map of string -> string tags to be added to every operation in the service
`baseLatency` | in milliseconds, the minimum duration for calls in the service

An `operation definition` is the basic unit for creating traces - it represents a single call and corresponds to a span
in the trace.

Key | Definition
----|-----
`tags` | map of string -> string tags to be added to the generated span
`calls` | list of operations to be called in parallel from this operation
`errorChance` | percent likelihood of generating an error for this span
`source` | string indicating the source of the span

### Annotated Example Configuration

Also see [exampleConfig.yaml](src/main/resources/exampleConfig.yaml) for a more complete example.

```yaml
entrypoints: # every request in this example starts as a simpleOrder
  - barista.order.simpleOrder

applications: # map of name to application definition
  barista:
    services:
      order:
        operations: # each operation is represented by a single span in the trace
          simpleOrder:
            tags: # freeform tags, included on the generated span
              _outboundExternalService: Redis # calling an external service
            calls: # the simpleOrder operation includes a dripCoffee and a muffin
              - service: brew # application defaults to the same, barista
                name: dripCoffee
              - application: bakery # calls may cross applications too
                service: order
                name: muffin
            errorChance: 0 # simpleOrder never fails
  bakery:
    services:
      order:
        operations:
          muffin:
            calls: [ { application: barista, service: fulfillment, name: delivery } ]
          scone:
            calls:
              - application: bakery
                service: oven # this service doesn't need to be defined elsewhere since it is simple
                name: warm
                calls: [ { application: barista, service: fulfillment, name: delivery } ]

```
