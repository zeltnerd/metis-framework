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

import eu.europeana.metis.mapping.exceptions.MappingNotFoundException;
import eu.europeana.metis.mapping.exceptions.MappingToXSLException;
import eu.europeana.metis.mapping.model.Mapping;
import eu.europeana.metis.service.MongoMappingService;
import eu.europeana.metis.service.XSLTGenerationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A Controller providing a method to convert a Mapping to an XSL
 * Created by ymamakis on 6/13/16.
 */
@Controller
@Api(value = "/",description = "REST API to convert a Mapping to an XSL")
public class XslController {

    @Autowired
    private XSLTGenerationService generationService;
    @Autowired
    private MongoMappingService mappingService;

    /**
     * Generate an XSL from a Mapping
     * @param mapping The mapping to generate the XSL from
     * @return The resulting XSL
     * @throws MappingToXSLException
     */
    @ApiOperation(value = "Create an XSL from mapping")
    @RequestMapping(value = "/xsl/generate",method = RequestMethod.POST)
    public String createXSLFromMapping(@ApiParam @RequestBody Mapping mapping) throws MappingToXSLException {
        try {
            return generationService.generateXslFromMapping(mapping);
        } catch(Exception e){
            throw new MappingToXSLException(mapping.getDataset(),mapping.getName(), e.getMessage());
        }
    }

    /**
     * Download the XSL for a mapping (it will be automatically generated if it has not been created in the past)
     * @param mappingId The mapping id
     * @param response The HttpServletResponse
     * @throws MappingNotFoundException
     * @throws IOException
     * @throws MappingToXSLException
     */
    @ApiOperation(value="Download the xsl for a mapping")
    @ResponseBody
    @RequestMapping(value="/xsl/{mappingId}", method = RequestMethod.GET)
    public void downloadXslForMapping(@PathVariable(value="mappingId") String mappingId, HttpServletResponse response)
            throws MappingNotFoundException, IOException, MappingToXSLException {
        Mapping mapping = mappingService.getByid(mappingId);
        if(mapping==null){
            throw new MappingNotFoundException(mappingId);
        }
        byte[] xslBytes = mapping.getXsl()==null?createXSLFromMapping(mapping).getBytes():mapping.getXsl().getBytes();
        ByteArrayInputStream bain = new ByteArrayInputStream(xslBytes);
        String mimeType = "application/octet-stream";
        response.setContentType(mimeType);
        response.setHeader("Content-Disposition", String.format("inline; filename=\"" + mapping.getName() + ".xsl\""));
        response.setContentLength(mapping.getXsl().length());
        InputStream inputStream = new BufferedInputStream(bain);
        FileCopyUtils.copy(inputStream, response.getOutputStream());
    }
}
