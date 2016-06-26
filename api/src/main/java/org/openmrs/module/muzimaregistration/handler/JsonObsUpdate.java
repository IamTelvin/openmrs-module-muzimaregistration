/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openmrs.module.muzimaregistration.handler;


/**
 *
 * @author Telvin
 */

import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.module.muzima.exception.QueueProcessorException;
import org.openmrs.module.muzima.model.QueueData;
import org.openmrs.module.muzima.model.handler.QueueDataHandler;
import org.openmrs.module.muzimaregistration.api.RegistrationDataService;
import org.openmrs.module.muzimaregistration.api.model.RegistrationData;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 */
@Component
@Handler(supports = QueueData.class, order = 50)
public class JsonObsUpdate  implements QueueDataHandler {

    private static final String DISCRIMINATOR_VALUE = "obsEdit";

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Log log = LogFactory.getLog(EncounterQueueDataHandler.class);

    @Override
    public void process(final QueueData queueData) throws QueueProcessorException {
        log.info("Processing observation form data: " + queueData.getUuid());
        String payload = queueData.getPayload();

         Encounter observation = new Encounter();

        RegistrationDataService service = Context.getService(RegistrationDataService.class);

        String personUuid = JsonPath.read(payload, "$['person']['person.uuid']");
        RegistrationData registrationData = service.getRegistrationDataByTemporaryUuid(personUuid);
        if (registrationData != null) {
            // we need to use the person uuid stored in the person table.
            personUuid = registrationData.getAssignedUuid();
        }

        Patient patient = Context.getPatientService().getPatientByUuid(personUuid);
        observation.setPatient(patient);

        String formUuid = JsonPath.read(payload, "$['observation']['form.uuid']");
        Form form = Context.getFormService().getFormByUuid(formUuid);
        observation.setForm(form);

        String encounterTypeUuid = JsonPath.read(payload, "$['observation']['observationType.uuid']");
        EncounterType encounterType = Context.getEncounterService().getEncounterTypeByUuid(encounterTypeUuid);
        observation.setEncounterType(encounterType);

        String providerUuid = JsonPath.read(payload, "$['observation']['provider.uuid']");
        User user = Context.getUserService().getUserByUuid(providerUuid);
        observation.setProvider(user);

        String locationUuid = JsonPath.read(payload, "$['observation']['location.uuid']");
        Location location = Context.getLocationService().getLocationByUuid(locationUuid);
        observation.setLocation(location);

        String encounterDatetime = JsonPath.read(payload, "$['observation']['datetime']");
        observation.setEncounterDatetime(parseDate(encounterDatetime));

        List<Object> obsObjects = JsonPath.read(payload, "$['obs']");
        for (Object obsObject : obsObjects) {
            Obs obs = new Obs();

            String conceptUuid = JsonPath.read(obsObject, "$['uuid']");
            Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
            obs.setConcept(concept);

            String value = JsonPath.read(obsObject, "$['value']").toString();
            if (concept.getDatatype().isNumeric()) {
                obs.setValueNumeric(Double.parseDouble(value));
            } else if (concept.getDatatype().isDate()
                    || concept.getDatatype().isTime()
                    || concept.getDatatype().isDateTime()) {
                obs.setValueDatetime(parseDate(value));
            } else if (concept.getDatatype().isCoded()) {
                Concept valueCoded = Context.getConceptService().getConceptByUuid(value);
                obs.setValueCoded(valueCoded);
            } else if (concept.getDatatype().isText()) {
                obs.setValueText(value);
            }
            observation.addObs(obs);
        }

        Context.getEncounterService().saveEncounter(observation);
    }

    private Date parseDate(final String dateValue) {
        Date date = null;
        try {
            date = dateFormat.parse(dateValue);
        } catch (ParseException e) {
            log.error("Unable to parse date data for observation!", e);
        }
        return date;
    }

    @Override
    public boolean accept(final QueueData queueData) {
        return StringUtils.equals(DISCRIMINATOR_VALUE, queueData.getDiscriminator());
    }
}

}