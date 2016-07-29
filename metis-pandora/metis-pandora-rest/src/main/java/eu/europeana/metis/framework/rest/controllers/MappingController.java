/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.metis.framework.rest.controllers;

import eu.europeana.metis.RestEndpoints;
import eu.europeana.metis.mapping.exceptions.MappingNotFoundException;
import eu.europeana.metis.mapping.exceptions.SaveMappingFailedException;
import eu.europeana.metis.mapping.model.Mapping;
import eu.europeana.metis.service.MongoMappingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static eu.europeana.metis.RestEndpoints.*;

/**
 * A REST Controller exposing a REST API for CRUD operations on Mapping and other functionality
 * Created by ymamakis on 6/13/16.
 */
@Controller
@Api(value = "/", description = "Mapping REST API")
public class MappingController {

    @Autowired
    private MongoMappingService mappingService;

    /**
     * Persist a mapping
     * @param mapping The mapping to persist
     * @return The id of the mapping
     * @throws SaveMappingFailedException
     */
    @RequestMapping(value = RestEndpoints.MAPPING, method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE})
    @ApiOperation(value = "Persist a mapping")
    @ResponseBody
    public String saveMapping(@ApiParam @RequestBody Mapping mapping) throws SaveMappingFailedException {
        try {
            return mappingService.saveMapping(mapping);
        } catch (Exception e) {
            Logger.getGlobal().severe(e.getMessage());
            throw new SaveMappingFailedException(e.getMessage());
        }
    }

    /**
     * Update a mapping
     * @param mapping The mapping to udpate
     * @return The id of the mapping
     * @throws SaveMappingFailedException
     */
    @RequestMapping(value = RestEndpoints.MAPPING, method = RequestMethod.PUT)
    @ApiOperation(value = "Update a mapping")
    @ResponseBody
    public String updateMapping(@ApiParam @RequestBody Mapping mapping) throws SaveMappingFailedException {
        try {
            return mappingService.updateMapping(mapping);
        } catch (Exception e) {
            Logger.getGlobal().severe(e.getMessage());
            throw new SaveMappingFailedException(e.getMessage());
        }
    }

    /**
     * Delete a mapping
     * @param id The id of the mapping to delete
     * @throws SaveMappingFailedException
     */
    @RequestMapping(value = RestEndpoints.MAPPING_BYID, method = RequestMethod.DELETE)
    @ResponseStatus (HttpStatus.OK)
    @ApiOperation(value = "Delete a mapping")
    public void deleteMapping(@ApiParam("mappingId")@PathVariable(value = "mappingId") String id) throws SaveMappingFailedException {
        try {
            mappingService.deleteMapping(id);
        } catch (Exception e) {
            Logger.getGlobal().severe(e.getMessage());
            throw new SaveMappingFailedException(e.getMessage());
        }
    }

    /**
     * Get a mapping by id
     * @param id The id of the mapping to retrieve
     * @return The mapping with the given id
     * @throws MappingNotFoundException
     */
    @ApiOperation(value = "Get a mapping by id")
    @RequestMapping(value = RestEndpoints.MAPPING_BYID, method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public Mapping getMappingById(@ApiParam("mappingId")@PathVariable(value = "mappingId") String id) throws MappingNotFoundException {
        Mapping mapping = mappingService.getByid(id);
        if (mapping != null) {
            return mapping;
        } else {
            throw new MappingNotFoundException(id);
        }

    }

    /**
     * Retrieve a mapping by name
     * @param name The name of the mapping to retrieve
     * @return The Mapping with the given name
     * @throws MappingNotFoundException
     */
    @ApiOperation(value = "Get a mapping for dataset")
    @RequestMapping(value = RestEndpoints.MAPPING_DATASETNAME, method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public Mapping getMappingByName(@ApiParam("name") @PathVariable(value = "name") String name) throws MappingNotFoundException{
        Mapping mapping =mappingService.getByName(name);
        if (mapping != null) {
            return mapping;
        } else {
            throw new MappingNotFoundException(name);
        }
    }

    /**
     * Get the mappings of an organization
     * @param organizationId The id of the organization to search for
     * @return The List of Mappings for this organization
     */
    @ApiOperation(value = "Get all the mappings by organization id")
    @RequestMapping(value = RestEndpoints.MAPPINGS_BYORGANIZATIONID, method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public List<Mapping> getMappingsByOrganization(@ApiParam("orgId") @PathVariable(value = "orgId") String organizationId) {
        return mappingService.getMappingByOrganization(organizationId);
    }

    /**
     * Get the names of the mappings for an organization
     * @param organizationId The id of the organization to search for
     * @return The list of names of the mappings for an organization
     */
    @RequestMapping(value = RestEndpoints.MAPPINGS_NAMES_BYORGANIZATIONID, method = RequestMethod.GET, produces = "application/json")
    @ApiOperation(value = "Get all the mapping names by organization id")
    @ResponseBody
    public List<String> getMappingNamesByOrganization(@ApiParam("orgId") @PathVariable(value = "orgId") String organizationId) {
        return mappingService.getMappingNamesByOrganization(organizationId);
    }

    /**
     * Get all the mapping templates (empty mappings)
     * @return The names of the mappings
     */
    @ApiOperation(value = "Get all the mapping templates")
    @RequestMapping(value = MAPPING_TEMPLATES, method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public List<String> getTemplates() {
        return mappingService.getTemplates();
    }

    /**
     * Clear the validation statistics and flags for a mapping with a given name
     * The flags will be reloaded if they still apply after the mapping is completed
     * @param name The name of the mapping to clear from statistics and flags
     */
    @ApiOperation(value = "Clear the stastistics for a mapping")
    @RequestMapping(value = MAPPING_STATISTICS_BYNAME, method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void clearValidationsStatistics(@ApiParam("name") @PathVariable(value = "name") String name) {
        mappingService.clearValidationStatistics(name);
    }

    @RequestMapping(value = MAPPING_SCHEMATRON, method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ApiOperation(value = "Set the schematron rules for a mapping")
    public void setSchematronRulesForMapping(@ApiParam("mapping") @RequestBody Mapping mapping,
                                             @ApiParam("rules") @RequestParam(value = "rules")Set<String> rules){
        mappingService.setSchematronRulesForMapping(mapping,rules);
    }

    @RequestMapping(value = MAPPING_NAMESPACES, method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ApiOperation(value = "Set the namespaces for a mapping")
    public void setNamespacesForMapping(@ApiParam("mapping") @RequestBody Mapping mapping,
                                        @ApiParam("namespaces") @RequestParam(value = "namespaces")Map<String,String> namespaces){
        mappingService.setNamespacesForMapping(mapping,namespaces);
    }
}
