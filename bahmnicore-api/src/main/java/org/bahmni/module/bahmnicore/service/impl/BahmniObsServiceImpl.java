package org.bahmni.module.bahmnicore.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.bahmni.module.bahmnicore.dao.ObsDao;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.bahmniemrapi.encountertransaction.mapper.OMRSObsToBahmniObsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BahmniObsServiceImpl implements BahmniObsService {

    private ObsDao obsDao;
    private OMRSObsToBahmniObsMapper omrsObsToBahmniObsMapper;
    private static final String[] NOT_STANDARD_OBS_CLASSES = {"Diagnosis", "LabSet", "LabTest", "Finding"};
    private VisitService visitService;
    private ObsService obsService;
    private ConceptService conceptService;

    @Autowired
    public BahmniObsServiceImpl(ObsDao obsDao, OMRSObsToBahmniObsMapper omrsObsToBahmniObsMapper, VisitService visitService, ObsService obsService, ConceptService conceptService) {
        this.obsDao = obsDao;
        this.omrsObsToBahmniObsMapper = omrsObsToBahmniObsMapper;
        this.visitService = visitService;
        this.obsService = obsService;
        this.conceptService = conceptService;
    }

    @Override
    public List<Obs> getObsForPerson(String identifier) {
        return obsDao.getNumericObsByPerson(identifier);
    }

    @Override
    public Collection<BahmniObservation> observationsFor(String patientUuid, Collection<Concept> concepts, Integer numberOfVisits, List<String> obsIgnoreList, Boolean filterObsWithOrders) {
        if(CollectionUtils.isNotEmpty(concepts)){
            List<String> conceptNames = new ArrayList<>();
            for (Concept concept : concepts) {
                conceptNames.add(concept.getName().getName());
            }

            List<Obs> observations = obsDao.getObsFor(patientUuid, conceptNames, numberOfVisits, obsIgnoreList, filterObsWithOrders);
            return omrsObsToBahmniObsMapper.map(observations,concepts);
        }
        return Collections.EMPTY_LIST;
    }

    @Override
    public Collection<BahmniObservation> getLatest(String patientUuid, Collection<Concept> concepts, Integer numberOfVisits, List<String> obsIgnoreList, Boolean filterObsWithOrders) {
        List<Obs> latestObs = new ArrayList<>();
        for (Concept concept : concepts) {
            if(null != concept) {
                latestObs.addAll(obsDao.getLatestObsFor(patientUuid, concept.getName().getName(), numberOfVisits, 1, obsIgnoreList, filterObsWithOrders));
            }
        }

        return omrsObsToBahmniObsMapper.map(latestObs, concepts);
    }

    @Override
    public Collection<BahmniObservation> getInitial(String patientUuid, Collection<Concept> conceptNames, Integer numberOfVisits) {
        List<Obs> latestObs = new ArrayList<>();
        for (Concept concept : conceptNames) {
            latestObs.addAll(obsDao.getInitialObsFor(patientUuid, concept.getName().getName(), numberOfVisits, 1));
        }

        return omrsObsToBahmniObsMapper.map(latestObs, conceptNames);
    }

    @Override
    public Collection<BahmniObservation> getLatestObsByVisit(Visit visit, Collection<Concept> concepts, List<String> obsIgnoreList, Boolean filterObsWithOrders){
        List<Obs> latestObs = new ArrayList<>();
        for (Concept concept : concepts) {
            latestObs.addAll(obsDao.getLatestObsByVisit(visit, concept.getName().getName(), 1, obsIgnoreList, filterObsWithOrders));
        }

        return omrsObsToBahmniObsMapper.map(latestObs, concepts);
    }

    @Override
    public Collection<BahmniObservation> getInitialObsByVisit(Visit visit, List<Concept> concepts) {
        List<Obs> latestObs = new ArrayList<>();
        for (Concept concept : concepts) {
            latestObs.addAll(obsDao.getInitialObsByVisit(visit, concept.getName().getName(), 1));
        }

        Collection<BahmniObservation> map = omrsObsToBahmniObsMapper.map(latestObs, concepts);
        return map;
    }

    @Override
    public List<Concept> getNumericConceptsForPerson(String personUUID) {
        return obsDao.getNumericConceptsForPerson(personUUID);
    }

    @Override
    public Collection<BahmniObservation> getLatestObsForConceptSetByVisit(String patientUuid, String conceptName, Integer visitId) {
        List<Obs> obs = obsDao.getLatestObsForConceptSetByVisit(patientUuid, conceptName, visitId);
        return omrsObsToBahmniObsMapper.map(obs, Arrays.asList(getConceptByName(conceptName)));
    }

    @Override
    public Collection<BahmniObservation> getObservationForVisit(String visitUuid, List<String> conceptNames) {
        Visit visit = visitService.getVisitByUuid(visitUuid);
        List<Person> persons = new ArrayList<>();
        persons.add(visit.getPatient());
        List<Obs> observations = obsService.getObservations(persons, new ArrayList<>(visit.getEncounters()), getConceptsForNames(conceptNames), null, null, null, null, null, null, null, null, false);
        observations = new ArrayList<>(getObsAtTopLevel(observations, conceptNames));
        return omrsObsToBahmniObsMapper.map(observations, null);
    }

    @Override
    public Collection<BahmniObservation> getObservationsForOrder(String orderUuid){
        List<Obs> observations = obsDao.getObsForOrder(orderUuid);
        return omrsObsToBahmniObsMapper.map(observations, null);
    }

    private List<Concept> getConceptsForNames(Collection<String> conceptNames) {
        List<Concept> concepts = new ArrayList<>();
        if (conceptNames != null) {
            for (String conceptName : conceptNames) {
                concepts.add(getConceptByName(conceptName));
            }
        }
        return concepts;
    }

    private Concept getConceptByName(String conceptName) {
        return conceptService.getConceptByName(conceptName);
    }

    private List<Obs> getObsAtTopLevel(List<Obs> observations, List<String> topLevelConceptNames) {
        List<Obs> topLevelObservations = new ArrayList<>();
        if(topLevelConceptNames == null) topLevelConceptNames = new ArrayList<>();

        Set<String> topLevelConceptNamesWithoutCase = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        topLevelConceptNamesWithoutCase.addAll(topLevelConceptNames);
        for (Obs o : observations) {
            if (o.getObsGroup() == null || topLevelConceptNamesWithoutCase.contains(o.getConcept().getName().getName().toLowerCase())) {
                if (!topLevelObservations.contains(o)) {
                    topLevelObservations.add(o);
                }
            }
        }

        return topLevelObservations;
    }
}
