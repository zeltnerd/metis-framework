package eu.europeana.metis.core.dataset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import eu.europeana.metis.json.ObjectIdSerializer;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-12-27
 */
@Entity
public class DatasetIdSequence {

  @Id
  @JsonSerialize(using = ObjectIdSerializer.class)
  private ObjectId id;

  private int sequence;

  public DatasetIdSequence() {
  }

  public DatasetIdSequence(int sequence) {
    this.sequence = sequence;
  }

  public ObjectId getId() {
    return id;
  }

  public void setId(ObjectId id) {
    this.id = id;
  }

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int sequence) {
    this.sequence = sequence;
  }
}