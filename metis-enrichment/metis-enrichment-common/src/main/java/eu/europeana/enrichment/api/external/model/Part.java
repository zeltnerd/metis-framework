package eu.europeana.enrichment.api.external.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Part {
  @XmlAttribute(name = "resource", namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
  private String resource;

  public Part(String resource) {
    this.resource = resource;
  }

  public Part() {
  }

 public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }
}