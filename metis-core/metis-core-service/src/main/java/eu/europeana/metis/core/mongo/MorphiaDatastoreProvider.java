package eu.europeana.metis.core.mongo;

import dev.morphia.Datastore;

/**
 * This interface represents an object that can make available a Morphia connection.
 */
public interface MorphiaDatastoreProvider {

  /**
   * @return the {@link Datastore} connection to Mongo
   */
  Datastore getDatastore();
}
