package eu.europeana.enrichment.service.wikidata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Properties;
import javax.xml.bind.JAXBException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.europeana.corelib.definitions.edm.entity.Organization;
import eu.europeana.corelib.solr.entity.OrganizationImpl;
import eu.europeana.enrichment.api.external.model.zoho.ZohoOrganization;
import eu.europeana.enrichment.service.EntityService;
import eu.europeana.enrichment.service.dao.WikidataAccessService;
import eu.europeana.enrichment.service.dao.WikidataOrganization;
import eu.europeana.enrichment.service.exception.EntityConverterException;
import eu.europeana.enrichment.service.exception.WikidataAccessException;
import eu.europeana.enrichment.service.exception.ZohoAccessException;

/**
 * Test class for Wikidata workflow.
 * 
 * The Zoho/Wikidata worklfow: 1. Get organization from Zoho 2. Retrieve wikidata for given
 * organization 3. Store retrieved wikidata in XML format (applying XSLT) 4. Parse wikidata to
 * OrganizationWikidata object 5. Merge both organization objects 6. Store merged data in
 * OrganizationImpl object in Metis
 * 
 * @author GrafR
 *
 */
public class WikidataWorkflowTest extends BaseWikidataAccessSetup {

  EntityService entityService;
  final Logger LOGGER = LoggerFactory.getLogger(getClass());


  @Before
  public void setUp() throws Exception {
    super.setUp();
    Properties props = loadProperties("/metis.properties");
    String mongoHost = props.getProperty("mongo.hosts");
    int mongoPort = Integer.valueOf(props.getProperty("mongo.port"));
    entityService = new EntityService(mongoHost, mongoPort);
    initWikidataAccessService();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void mergeOrganizationsFromFilesTest() throws WikidataAccessException, ZohoAccessException,
      ParseException, IOException, JAXBException, EntityConverterException, URISyntaxException {

    /** 1. Get organization from Zoho file */
    File zohoTestInputFile = getClasspathFile(ZOHO_TEST_INPUT_FILE);
    ZohoOrganization org = zohoAccessService.getOrganizationFromFile(zohoTestInputFile);
    assertNotNull(org);
    assertNotNull(org.getSameAs());
    assertNotNull(org.getSameAs().get(0));

    /** 2. Get organization from Wikidata file */
    File wikidataTestInputFile = getClasspathFile(WIKIDATA_TEST_INPUT_FILE);
    WikidataOrganization wikidataOrganization = wikidataAccessService.getWikidataAccessDao()
        .parseWikidataOrganization(wikidataTestInputFile);
    assertNotNull(wikidataOrganization);

    /** 3. Convert both organization objects to OrganizationImpl format */
    Organization zohoOrganizationImpl = zohoAccessService.toEdmOrganization(org);
    Organization wikidataOrganizationImpl =
        wikidataAccessService.toOrganizationImpl(wikidataOrganization);

    /** remove object IDs because they are always unique and are not needed for this test */
    zohoOrganizationImpl.setId(null);
    wikidataOrganizationImpl.setId(null);

    /** 4. Merge both organization objects */
    Organization mergedOrganization = wikidataAccessService.getWikidataAccessDao()
        .merge(zohoOrganizationImpl, wikidataOrganizationImpl);
    assertNotNull(mergedOrganization);

    /** 5. Store merged OrganizationImpl object in output file */
    String serializedOrganizationImpl = wikidataAccessService.getEntityConverterUtils()
        .serialize((OrganizationImpl) mergedOrganization);
    assertNotNull(serializedOrganizationImpl);
    File organizationImplOutputFile = getClasspathFile(ORGANIZATION_IMPL_TEST_OUTPUT_FILE);
    writeToFile(serializedOrganizationImpl, organizationImplOutputFile);

    /** 6. Compare output file with expected file */
    File organizationImplTestOutputFile = getClasspathFile(ORGANIZATION_IMPL_TEST_OUTPUT_FILE);
    File organizationImplTestExpectedFile = getClasspathFile(ORGANIZATION_IMPL_TEST_EXPECTED_FILE);
    String outputOrganizationImplStr =
        getEntityConverterUtils().readFile(organizationImplTestOutputFile);
    String expectedOrganizationImplStr =
        getEntityConverterUtils().readFile(organizationImplTestExpectedFile);
    assertEquals(outputOrganizationImplStr, expectedOrganizationImplStr);
  }

  @Test
  public void mergeZohoAndWikidataOrganizationsTest() throws WikidataAccessException, ZohoAccessException,
      ParseException, IOException, JAXBException, URISyntaxException {

    /** 1. Get Zoho organization */
    File zohoTestInputFile = getClasspathFile(ZOHO_TEST_INPUT_FILE);
    ZohoOrganization org = zohoAccessService.getOrganizationFromFile(zohoTestInputFile);
//    ZohoOrganization org = zohoAccessService.getOrganization(TEST_ORGANIZATION_ID);
    assertNotNull(org);
    assertTrue(org.getCreated().getTime() > 0);
    assertTrue(org.getModified().getTime() > 0);

    /** 2. Retrieve wikidata for given organization */
    /** 3. Store retrieved wikidata in XML format (applying XSLT) */
    String uri = org.getSameAs().get(0);
    wikidataAccessService.getWikidataAccessDao().dereference(uri);

    /** 4. Parse wikidata to OrganizationWikidata object */
    File wikidataTestOutputFile = getClasspathFile(WIKIDATA_TEST_OUTPUT_FILE);
    WikidataOrganization wikidataOrganization =
        wikidataAccessService.getWikidataAccessDao().parse(wikidataTestOutputFile);
    assertNotNull(wikidataOrganization);

    /** 5. Merge both organization objects */
    Organization zohoOrganizationImpl = zohoAccessService.toEdmOrganization(org);
    Organization wikidataOrganizationImpl =
        wikidataAccessService.toOrganizationImpl(wikidataOrganization);
    Organization mergedOrganization = wikidataAccessService.getWikidataAccessDao()
        .merge(zohoOrganizationImpl, wikidataOrganizationImpl);
    assertNotNull(mergedOrganization);

    /** 6. Store merged data in OrganizationImpl object in Metis */
    // Date now = new Date();
    // OrganizationTermList termList = entityService.storeOrganization(org);
    // assertNotNull(termList);
    // assertEquals(org.getAbout(), termList.getCodeUri());
    // assertEquals(termList.getCreated(), termList.getModified());
    // assertTrue(termList.getCreated().getTime() > now.getTime());
  }

  @Test
  public void retrieveWikidataOrganigationXmlTest()
      throws WikidataAccessException, ZohoAccessException, ParseException, IOException {

    String uri =
        wikidataAccessService.buildOrganizationUri(TEST_WIKIDATA_ORGANIZATION_ID).toString();
    String wikidataXml = wikidataAccessService.getWikidataAccessDao().dereference(uri).toString();
    assertNotNull(wikidataXml);
  }

  @Test
  public void parseWikidataFromXmlFileTest() throws WikidataAccessException,
      ZohoAccessException, ParseException, IOException, JAXBException, URISyntaxException {

    File wikidataTestOutputFile = getClasspathFile(WIKIDATA_TEST_OUTPUT_FILE);
    WikidataOrganization wikidataOrganization = wikidataAccessService.getWikidataAccessDao()
        .parseWikidataOrganization(wikidataTestOutputFile);
    assertNotNull(wikidataOrganization);
    LOGGER
        .info("wikidata organization about: " + wikidataOrganization.getOrganization().getAbout());
    LOGGER.info(
        "wikidata organization country: " + wikidataOrganization.getOrganization().getCountry());
    LOGGER.info("wikidata organization homepage: "
        + wikidataOrganization.getOrganization().getHomepage().getResource().toString());
    assertNotNull(wikidataOrganization.getOrganization().getCountry());
    assertNotNull(wikidataOrganization.getOrganization().getHomepage().getResource().toString());
  }

  @Test
  public void mergeZohoAndWikidataOrganizationFromFilesTest() throws WikidataAccessException,
      ZohoAccessException, ParseException, IOException, JAXBException, URISyntaxException {

//    ZohoOrganization zohoOrganization = zohoAccessService.getOrganization(TEST_ORGANIZATION_ID);
    File zohoTestInputFile = getClasspathFile(ZOHO_TEST_INPUT_FILE);
    ZohoOrganization zohoOrganization = zohoAccessService.getOrganizationFromFile(zohoTestInputFile);
    
    Organization zohoOrganizationImpl = zohoAccessService.toEdmOrganization(zohoOrganization);
    File wikidataTestOutputFile = getClasspathFile(WIKIDATA_TEST_OUTPUT_FILE);
    WikidataOrganization wikidataOrganization =
        wikidataAccessService.parseWikidataOrganization(wikidataTestOutputFile);
    Organization wikidataOrganizationImpl =
        wikidataAccessService.toOrganizationImpl(wikidataOrganization);
    Organization mergedOrganization = wikidataAccessService.getWikidataAccessDao()
        .merge(zohoOrganizationImpl, wikidataOrganizationImpl);
    assertNotNull(mergedOrganization);
  }

}
