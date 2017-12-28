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
package eu.europeana.validation.service;

import eu.europeana.validation.model.ValidationResult;
import eu.europeana.validation.model.ValidationResultList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Schema service service
 * Created by gmamakis on 18-12-15.
 */
@Service
public class ValidationExecutionService {
    private final ClasspathResourceResolver lsResourceResolver;
    private final ValidationServiceConfig config;
    private final ExecutorService es;

    @Autowired
    private SchemaProvider schemaProvider;

    @Autowired
    public ValidationExecutionService(ValidationServiceConfig config, ClasspathResourceResolver lsResourceResolver) {
        this.config = config;
        this.lsResourceResolver = lsResourceResolver;
        this.es = Executors.newFixedThreadPool(10);
    }

    /**
     * Perform single service given a schema.
     *
     * @param schema   The schema to perform service against.
     * @param document The document to validate against
     * @return A service result
     */
    public ValidationResult singleValidation(final String schema, final String document) {
        return new Validator(schema, document,schemaProvider, lsResourceResolver).call();
    }

    /**
     * Batch service given a schema
     *
     * @param schema    The schema to validate against
     * @param documents The documents to validate
     * @return A list of service results
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public ValidationResultList batchValidation(final String schema, List<String> documents) throws InterruptedException, ExecutionException {

        ExecutorCompletionService cs = new ExecutorCompletionService(es);
        for (final String document : documents) {
            cs.submit(new Validator(schema, document,schemaProvider, lsResourceResolver));
        }

        List<ValidationResult> results = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Future<ValidationResult> future = cs.take();
            ValidationResult res = future.get();
            if (!res.isSuccess()) {
                results.add(res);
            }
        }

        ValidationResultList resultList = new ValidationResultList();
        resultList.setResultList(results);
        if (resultList.getResultList().size() == 0) {
            resultList.setSuccess(true);
        }
        return resultList;
    }

    @PreDestroy
    void cleanup() {
        if (es != null) {
            es.shutdown();
        }
    }

}
