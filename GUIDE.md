Mule Buffer Aggregator - User Guide
===================================

Module Features
---------------

These are the main features provided by the module:

- **Buffer** - Aggregate incoming messages in groups sending them as a collection when the number reaches the maximum buffer size
- **Flush Buffer** - Flushes all the expired groups, so that the buffered messages are sent out as a collection even if the maximum buffer size has not been reached

Configuration Reference
-----------------------

All the configuration parameters supported by the module and processor configuration elements are described in this section.

### Module Attributes

The module configuration defines the global behaviour of the processor.

<table class="confluenceTable">
  <tr>
    <th style="width:10%" class="confluenceTh">Name</th><th style="width:10%" class="confluenceTh">Type</th><th style="width:10%" class="confluenceTh">Required</th><th style="width:10%" class="confluenceTh">Default</th><th class="confluenceTh">Description</th>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">bufferSize</td><td style="text-align: center" class="confluenceTd">integer</td><td style="text-align: center" class="confluenceTd">yes</td><td style="text-align: center" class="confluenceTd"></td><td class="confluenceTd">
      <p>
          The maximum size that an aggregation group in the buffer can reach before being flushed. 
        </p>
    </td>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">bufferTimeToLive</td><td style="text-align: center" class="confluenceTd">integer</td><td style="text-align: center" class="confluenceTd">yes</td><td style="text-align: center" class="confluenceTd"></td><td class="confluenceTd">
      <p>
          The maximum amount of time in milliseconds to wait before marking an aggregation group as expired.
          All the expired groups will be flushed even if the `bufferSize` threshold has not been reached. 
        </p>
    </td>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">storePrefix</td><td style="text-align: center" class="confluenceTd">string</td><td style="text-align: center" class="confluenceTd">yes</td><td style="text-align: center" class="confluenceTd"></td><td class="confluenceTd">
      <p>
          The prefix to use for the Mule Object Store (this allows to configure multiple buffer aggregators in the same Mule Application).
        </p>
    </td>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">persistent</td><td style="text-align: center" class="confluenceTd">boolean</td><td style="text-align: center" class="confluenceTd">no</td><td style="text-align: center" class="confluenceTd">false</td><td class="confluenceTd">
      <p>
          Whether messages in the buffer should be persisted.
          In a Mule HA cluster the messages will be persisted in the memory grid regardless this attribute being true or false.
        </p>
    </td>
  </tr>
</table>

### Processors Attributes

The following attributes are only available in the buffer processor.

<table class="confluenceTable">
  <tr>
    <th style="width:10%" class="confluenceTh">Name</th><th style="width:10%" class="confluenceTh">Type</th><th style="width:10%" class="confluenceTh">Required</th><th style="width:10%" class="confluenceTh">Default</th><th class="confluenceTh">Description</th>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">group</td><td style="text-align: center" class="confluenceTd">string</td><td style="text-align: center" class="confluenceTd">yes</td><td style="text-align: center" class="confluenceTd"></td><td class="confluenceTd">
      <p>
          Used to specify an aggregation group (aggregation groups can be seen as separated buckets within the same buffer).
        </p>
    </td>
  </tr>
  <tr>
    <td rowspan="1" class="confluenceTd">key</td><td style="text-align: center" class="confluenceTd">string</td><td style="text-align: center" class="confluenceTd">no</td><td style="text-align: center" class="confluenceTd"></td><td class="confluenceTd">
      <p>
          Used to package messages together withing the same aggregation group.
        </p>
    </td>
  </tr>
</table>

Examples
--------

There are mainly two ways to use the Buffer Aggregator module. The following examples will demonstrate the common use cases.

### Synchronous Buffering

This shows how to configure the Buffer Aggregator to buffer incoming messages until the maximum buffer size is reached.
 
    <bufferaggregator:config name="buffer-aggregator-config"
                             bufferSize="5"
                             bufferTimeToLive="15000"
                             storePrefix="_buffer" />
                             
    <inbound-endpoint />
    
    <bufferaggregator:buffer config-ref="buffer-aggregator-config"
                             group="#[message.inboundProperties['group']]"
                             key="#[message.inboundProperties['key']]" />
                             
    <outbound-endpoint />
    
### Asynchronous Buffer Flushing

This shows how to asynchronously trigger a buffer flush.

    <bufferaggregator:config name="buffer-aggregator-config"
                                 bufferSize="5"
                                 bufferTimeToLive="15000"
                                 storePrefix="_buffer" />
                                 
    <poll>
        <fixed-frequency-scheduler frequency="5000" startDelay="2000"/>
        <bufferaggregator:flush-buffer config-ref="buffer-aggregator-config" />      
    </poll>
    
    <outbound-endpoint />


