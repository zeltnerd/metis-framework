package eu.europeana.indexing.mongo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import eu.europeana.corelib.definitions.edm.entity.AbstractEdmEntity;
import eu.europeana.corelib.definitions.edm.entity.Proxy;
import eu.europeana.corelib.definitions.edm.entity.WebResource;
import eu.europeana.corelib.definitions.solr.DocType;
import eu.europeana.corelib.edm.exceptions.MongoUpdateException;
import eu.europeana.corelib.mongo.server.EdmMongoServer;
import eu.europeana.corelib.solr.bean.impl.FullBeanImpl;
import eu.europeana.corelib.solr.entity.AgentImpl;
import eu.europeana.corelib.solr.entity.ConceptImpl;
import eu.europeana.corelib.solr.entity.PlaceImpl;
import eu.europeana.corelib.solr.entity.ProxyImpl;
import eu.europeana.corelib.solr.entity.TimespanImpl;
import eu.europeana.corelib.solr.entity.WebResourceImpl;
import eu.europeana.indexing.exception.IndexingException;
import eu.europeana.indexing.mongo.property.PropertyMongoUpdater;
import eu.europeana.indexing.mongo.property.WebResourceUpdater;

/**
 * DAO object for saving and updating Full Beans (instances of {@link FullBeanImpl}) and all its
 * child objects.
 * 
 * @author jochen
 *
 */
public class FullBeanDao {

  private static final String ABOUT_FIELD = "about";

  private final EdmMongoServer mongoServer;

  /**
   * Constructor.
   * 
   * @param mongoServer The persistence.
   */
  public FullBeanDao(EdmMongoServer mongoServer) {
    this.mongoServer = mongoServer;
  }

  /**
   * Searches an object by the value of its about field. This method shouldn't be called directly:
   * please use one of the public methods in this class.
   * 
   * @param clazz The object type to find.
   * @param about The value of the about field to find.
   * @return The object, or null if no such object could be found.
   */
  <T> T get(Class<T> clazz, String about) {
    return mongoServer.getDatastore().find(clazz).field(ABOUT_FIELD).equal(about).get();
  }

  /**
   * Determines whether a given full bean exists (i.e. is persisted).
   * 
   * @param fullBean The full bean that we wish to check.
   * @return The 'about' attribute value of the full bean, or null in case the bean does not exist.
   * @throws IndexingException In case the full bean does not have an 'about' value.
   */
  public String getPersistedAbout(FullBeanImpl fullBean) throws IndexingException {

    // Get the base value (without the prefix).
    if (StringUtils.isBlank(fullBean.getAbout())) {
      throw new IndexingException("Full bean does not have an 'about' value.");
    }
    final String aboutValue = fullBean.getAbout().trim();

    // Check with or without the prefix.
    final String result;
    if (null != get(FullBeanImpl.class, aboutValue)) {
      result = aboutValue;
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Saves the object (for new objects only).
   * 
   * @param data The object to save.
   * @return The key under which the object was saved.
   */
  public <T> Key<T> save(T data) {
    return mongoServer.getDatastore().save(data);
  }

  /**
   * Saves multiple objects (for new objects only).
   * 
   * @param data The objects to save.
   */
  public void save(Iterable<?> data) {
    data.forEach(this::save);
  }

  /**
   * Updatres an object in the mongo database. Checks whether the given object is already known. If
   * it is, updates the object. If it isn't, it saves the object as a new object.
   * 
   * @param data The object to save.
   * @param clazz The type of the object.
   * @param updater The helper class to update the object.
   * @return The persisted object.
   * @throws MongoUpdateException In case an exception occurred in the supplied updater.
   */
  public <T extends AbstractEdmEntity> T update(T data, Class<T> clazz, PropertyMongoUpdater<T> updater)
      throws MongoUpdateException {
    if (data == null) {
      return null;
    }
    final T existingData = get(clazz, data.getAbout());
    final T newData;
    if (existingData != null) {
      newData = updater.update(existingData, data, mongoServer);
    } else {
      final Key<T> key = save(data);
      newData = mongoServer.getDatastore().getByKey(clazz, key);
    }
    return newData;
  }

  /**
   * Convenience method for {@link #update(AbstractEdmEntity, Class, PropertyMongoUpdater)} that accepts
   * multiple objects.
   * 
   * @param dataToAdd The object to save.
   * @param clazz The type of the object.
   * @param updater The helper class to update the object.
   * @return The persisted objects.
   * @throws MongoUpdateException In case an exception occurred in the supplied updater.
   */
  public <T extends AbstractEdmEntity> List<T> update(List<T> dataToAdd, Class<T> clazz,
      PropertyMongoUpdater<T> updater) throws MongoUpdateException {
    final List<T> result = new ArrayList<>();
    if (dataToAdd == null) {
      return result;
    }
    for (T data : dataToAdd) {
      final T newData = update(data, clazz, updater);
      if (newData != null) {
        result.add(newData);
      }
    }
    return result;
  }
  
  /**
   * Updates a web resource in the mongo database. Checks whether the given web resource is already
   * known. If it is, updates the web resource. If it isn't, it saves the web resource as a new web
   * resource.
   * 
   * @param dataToAdd The web resource to save.
   * @return The persisted web resource.
   * @throws MongoUpdateException In case an exception occurred while saving.
   */
  public List<WebResourceImpl> updateWebResources(List<? extends WebResource> dataToAdd)
      throws MongoUpdateException {
    if (dataToAdd == null) {
      return Collections.emptyList();
    }
    final WebResourceUpdater webResourceCreator = new WebResourceUpdater();
    for (WebResource data : dataToAdd) {
      webResourceCreator.saveWebResource(data, mongoServer);
    }
    return dataToAdd.stream().map(data -> get(WebResourceImpl.class, data.getAbout()))
        .collect(Collectors.toList());
  }

  /**
   * This method updates a Full Bean. If the bean does not yet exist (i.e. is persisted), this
   * method does nothing.
   * 
   * @param fullBean The Full Bean to update.
   * @return The persisted version of the Full Bean. Or null if the bean does not exist.
   * @throws IndexingException In case the full bean does not have an 'about' value.
   */
  public FullBeanImpl updateFullBean(FullBeanImpl fullBean) throws IndexingException {

    final String persistedAboutValue = getPersistedAbout(fullBean);
    if (persistedAboutValue == null) {
      return null;
    }

    Query<FullBeanImpl> updateQuery = mongoServer.getDatastore().createQuery(FullBeanImpl.class)
        .field(ABOUT_FIELD).equal(persistedAboutValue);
    UpdateOperations<FullBeanImpl> ops =
        mongoServer.getDatastore().createUpdateOperations(FullBeanImpl.class);

    // To avoid potential index out of bounds
    if (fullBean.getProxies().isEmpty()) {
      ArrayList<Proxy> proxyList = new ArrayList<>();
      ProxyImpl proxy = new ProxyImpl();
      proxyList.add(proxy);
      fullBean.setProxies(proxyList);
    }

    ops.set("title", fullBean.getTitle() != null ? fullBean.getTitle() : new String[] {});
    ops.set("year", fullBean.getYear() != null ? fullBean.getYear() : new String[] {});
    ops.set("provider", fullBean.getProvider() != null ? fullBean.getProvider() : new String[] {});
    ops.set("language", fullBean.getLanguage() != null ? fullBean.getLanguage() : new String[] {});
    ops.set("type", fullBean.getType() != null ? fullBean.getType() : DocType.IMAGE);
    ops.set("europeanaCompleteness", fullBean.getEuropeanaCompleteness());
    ops.set("places",
        fullBean.getPlaces() != null ? fullBean.getPlaces() : new ArrayList<PlaceImpl>());
    ops.set("agents",
        fullBean.getAgents() != null ? fullBean.getAgents() : new ArrayList<AgentImpl>());
    ops.set("timespans",
        fullBean.getTimespans() != null ? fullBean.getTimespans() : new ArrayList<TimespanImpl>());
    ops.set("concepts",
        fullBean.getConcepts() != null ? fullBean.getConcepts() : new ArrayList<ConceptImpl>());
    ops.set("aggregations", fullBean.getAggregations());
    ops.set("providedCHOs", fullBean.getProvidedCHOs());
    ops.set("europeanaAggregation", fullBean.getEuropeanaAggregation());
    ops.set("proxies", fullBean.getProxies());
    ops.set("country", fullBean.getCountry() != null ? fullBean.getCountry() : new String[] {});
    ops.set("services", fullBean.getServices());
    ops.set("europeanaCollectionName", fullBean.getEuropeanaCollectionName());

    mongoServer.getDatastore().update(updateQuery, ops);

    return get(FullBeanImpl.class, persistedAboutValue);
  }
}
