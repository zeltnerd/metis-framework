package eu.europeana.enrichment.rest.client;

public abstract class AbstractConnectionBuilder {

  /**
   * The default value of the maximum amount of time, in milliseconds, we wait for a connection to a
   * resource before timing out. It's currently set to {@value AbstractConnectionBuilder#DEFAULT_CONNECT_TIMEOUT}
   * milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10_000;

  /**
   * The default value of the maximum amount of time, in milliseconds, we wait for the response
   * before timing out. It's currently set to {@value AbstractConnectionBuilder#DEFAULT_RESPONSE_TIMEOUT}
   * milliseconds.
   */
  public static final int DEFAULT_RESPONSE_TIMEOUT = 60_000;

  /**
   * The default value of the batch size with which we query the enrichment service. It's currently
   * set to {@value AbstractConnectionBuilder#DEFAULT_BATCH_SIZE_ENRICHMENT} values.
   */
  public static final int DEFAULT_BATCH_SIZE_ENRICHMENT = 20;

  protected int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  protected int responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
  protected int batchSizeEnrichment = DEFAULT_BATCH_SIZE_ENRICHMENT;

  /**
   * Set the maximum amount of time, in milliseconds, we wait for a connection before timing out.
   * The default (when not calling this method) is {@value AbstractConnectionBuilder#DEFAULT_CONNECT_TIMEOUT}
   * milliseconds.
   *
   * @param connectTimeout The maximum amount of time, in milliseconds, we wait for a connection
   * before timing out. If not positive, this signifies that the connection does not time out.
   * @return This instance, for convenience.
   */
  public AbstractConnectionBuilder setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  /**
   * Set the maximum amount of time, in milliseconds, we wait for the response. The default (when
   * not calling this method is {@value AbstractConnectionBuilder#DEFAULT_RESPONSE_TIMEOUT}
   * milliseconds.
   *
   * @param responseTimeout The maximum amount of time, in milliseconds, we wait for the response
   * before timing out. If not positive, this signifies that the response does not time out.
   * @return This instance, for convenience.
   */
  public AbstractConnectionBuilder setResponseTimeout(int responseTimeout) {
    this.responseTimeout = responseTimeout;
    return this;
  }

  /**
   * Set the batch size with which we query the enrichment service. The default (when not calling
   * this method) is {@value AbstractConnectionBuilder#DEFAULT_BATCH_SIZE_ENRICHMENT} values.
   *
   * @param batchSizeEnrichment The batch size. Must be strictly positive.
   * @return This instance, for convenience.
   */
  public AbstractConnectionBuilder setBatchSizeEnrichment(int batchSizeEnrichment) {
    if (batchSizeEnrichment < 1) {
      throw new IllegalArgumentException("Batch size cannot be 0 or negative.");
    }
    this.batchSizeEnrichment = batchSizeEnrichment;
    return this;
  }

}
