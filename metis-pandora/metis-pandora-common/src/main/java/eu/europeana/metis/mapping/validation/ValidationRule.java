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
package eu.europeana.metis.mapping.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The representation of a validation rule
 * Created by ymamakis on 6/15/16.
 */
@Entity
@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationRule {

    @Id
    private ObjectId id;


    @Embedded
    @ApiModelProperty(hidden = true)
    private ValidationFunction function;

    private FlagType flagType;

    private String message;

    /**
     * The id of the rule
     * @return The id of the rule
     */
    @XmlElement
    public ObjectId getId() {
        return id;
    }

    /**
     * The id of the rule
     * @param id The id of the rule
     */
    public void setId(ObjectId id) {
        this.id = id;
    }

    /**
     * The function implementing the rule
     * @return The function implementing the rule
     */
    @XmlElement
    public ValidationFunction getFunction() {
        return function;
    }

    /**
     * The function implementing the rule
     * @param function The function implementing the rule
     */
    public void setFunction(ValidationFunction function) {
        this.function = function;
    }

    /**
     * The flagType the rule will generate
     * @return The flagType the rule will generate
     */
    @XmlElement
    public FlagType getFlagType() {
        return flagType;
    }

    /**
     * The flagType the rule will generate
     * @param flagType The flagType the rule will generate
     */
    public void setFlagType(FlagType flagType) {
        this.flagType = flagType;
    }

    /**
     * The message to appear when the field does not pass the validation function
     * @return The message to appear when the field does not pass the validation function
     */
    @XmlElement
    public String getMessage() {
        return message;
    }

    /**
     * The message to appear when the field does not pass the validation function
     * @param message The message to appear when the field does not pass the validation function
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
