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
package eu.europeana.redirects.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europeana.redirects.model.RedirectRequest;
import eu.europeana.redirects.model.RedirectRequestList;
import eu.europeana.redirects.model.RedirectResponse;
import eu.europeana.redirects.model.RedirectResponseList;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;

/**
 * Redirects REST client
 * Created by ymamakis on 1/15/16.
 */
public class RedirectsClient {

    private Client client =  ClientBuilder.newBuilder().build();
    private Config config = new Config();

    /**
     * Request for a redirect for a single record
     * @param request The request for a redirect
     * @return A response with the redirect generated if any
     * @throws JsonProcessingException
     */

    public RedirectResponse redirectSingle(RedirectRequest request) throws JsonProcessingException{
        WebTarget target  = client.target(config.getRedirectsPath()).path("redirect/single");
        ObjectMapper mapper = new ObjectMapper();
        Form form =new Form();
        form.param("record", mapper.writeValueAsString(request));
        return target.request().post(Entity.form(form)).readEntity(RedirectResponse.class);
    }

    /**
     * Request for batch redirects
     * @param requests The list of redirect requests
     * @return A list of responses for each redirect
     * @throws JsonProcessingException
     */
    public RedirectResponseList redirectBatch(RedirectRequestList requests) throws JsonProcessingException{
        WebTarget target  = client.target(config.getRedirectsPath()).path("redirect/batch");
        ObjectMapper mapper = new ObjectMapper();
        Form form =new Form();
        form.param("records", mapper.writeValueAsString(requests));
        return target.request().post(Entity.form(form)).readEntity(RedirectResponseList.class);
    }
}
