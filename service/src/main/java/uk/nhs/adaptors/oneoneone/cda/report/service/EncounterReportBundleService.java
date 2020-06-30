package uk.nhs.adaptors.oneoneone.cda.report.service;

import lombok.AllArgsConstructor;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Group;
import org.hl7.fhir.dstu3.model.HealthcareService;
import org.hl7.fhir.dstu3.model.ListResource;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ReferralRequest;
import org.hl7.fhir.dstu3.model.Resource;
import org.springframework.stereotype.Component;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.CompositionMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.EncounterMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ListMapper;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01ClinicalDocument1;

import java.util.List;
import java.util.stream.Collectors;

import static org.hl7.fhir.dstu3.model.Bundle.BundleType.TRANSACTION;

@Component
@AllArgsConstructor
public class EncounterReportBundleService {

    private EncounterMapper encounterMapper;
    private CompositionMapper compositionMapper;
    private ListMapper listMapper;

    public Bundle createEncounterBundle(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        Bundle bundle = new Bundle();
        bundle.setType(TRANSACTION);

        Encounter encounter = encounterMapper.mapEncounter(clinicalDocument);
        Composition composition = compositionMapper.mapComposition(clinicalDocument, encounter);

        addEncounter(bundle, encounter);
        addServiceProvider(bundle, encounter);
        addParticipants(bundle, encounter);
        addLocation(bundle, encounter);
        addSubject(bundle, encounter);
        addIncomingReferral(bundle, encounter);
        addAppointment(bundle, encounter);
        addEpisodeOfCare(bundle, encounter);
        addComposition(bundle, composition);

        List<Resource> resourcesCreated = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).collect(Collectors.toList());
        ListResource listResource = listMapper.mapList(clinicalDocument, encounter, resourcesCreated);

        addList(bundle, listResource);

        return bundle;
    }

    private void addEncounter(Bundle bundle, Encounter encounter) {
        addEntry(bundle, encounter);
    }

    private void addEpisodeOfCare(Bundle bundle, Encounter encounter) {
        if (encounter.hasEpisodeOfCare()) {
            EpisodeOfCare episodeOfCare = (EpisodeOfCare) encounter.getEpisodeOfCareFirstRep().getResource();
            addEntry(bundle, episodeOfCare);

            if (episodeOfCare.hasCareManager()) {
                addEntry(bundle, episodeOfCare.getCareManagerTarget());
            }

            if (episodeOfCare.hasManagingOrganization()) {
                addEntry(bundle, episodeOfCare.getManagingOrganizationTarget());
            }
        }
    }

    private void addServiceProvider(Bundle bundle, Encounter encounter) {
        addEntry(bundle, encounter.getServiceProviderTarget());
    }

    private void addParticipants(Bundle bundle, Encounter encounter) {
        List<Encounter.EncounterParticipantComponent> participantComponents = encounter.getParticipant();
        participantComponents.stream().forEach(item -> addEntry(bundle, item.getIndividualTarget()));
    }

    private void addAppointment(Bundle bundle, Encounter encounter) {
        if (encounter.hasAppointment()) {
            Appointment appointment = encounter.getAppointmentTarget();
            addEntry(bundle, appointment);
            if (appointment.hasParticipant()) {
                for (Appointment.AppointmentParticipantComponent participant : appointment.getParticipant()) {
                    if (participant.hasActor()) {
                        addEntry(bundle, participant.getActorTarget());
                    }
                }
            }
        }
    }

    private void addLocation(Bundle bundle, Encounter encounter) {
        List<Encounter.EncounterLocationComponent> locationComponents = encounter.getLocation();
        for (Encounter.EncounterLocationComponent component : locationComponents) {
            if (component.hasLocation()) {
                Location location = component.getLocationTarget();
                addEntry(bundle, location);
                if (location.hasManagingOrganization()) {
                    addOrganization(bundle, location.getManagingOrganizationTarget());
                }
            }
        }
    }

    private void addOrganization(Bundle bundle, Organization organization) {
        addEntry(bundle, organization);
        if (organization.hasPartOf()) {
            addOrganization(bundle, organization.getPartOfTarget());
        }
    }

    private void addSubject(Bundle bundle, Encounter encounter) {
        if (encounter.getSubjectTarget() instanceof Patient) {
            Patient patient = (Patient) encounter.getSubjectTarget();
            addEntry(bundle, patient);

            if (patient.hasGeneralPractitioner()) {
                for (Reference gp : patient.getGeneralPractitioner()) {
                    Organization organization = (Organization) gp.getResource();
                    addEntry(bundle, organization);
                }
            }
        }
        if (encounter.getSubjectTarget() instanceof Group) {
            Group group = (Group) encounter.getSubjectTarget();
            addEntry(bundle, group);
            for (Group.GroupMemberComponent groupMemberComponent : group.getMember()) {
                bundle.addEntry()
                        .setFullUrl(groupMemberComponent.getIdElement().getValue())
                        .setResource(groupMemberComponent.getEntityTarget());
            }
        }
    }

    private void addIncomingReferral(Bundle bundle, Encounter encounter) {
        ReferralRequest referralRequest = (ReferralRequest) encounter.getIncomingReferralFirstRep().getResource();
        addEntry(bundle, referralRequest);
        if (referralRequest.hasSubject()) {
            addEntry(bundle, referralRequest.getSubjectTarget());
        }
        if (referralRequest.hasRecipient()) {
            for (Reference recipient :
                    referralRequest.getRecipient()) {
                addEntry(bundle, (Resource) recipient.getResource());
                HealthcareService healthcareService = (HealthcareService) recipient.getResource();
                if (healthcareService.hasLocation()) {
                    addEntry(bundle, (Location) healthcareService.getLocationFirstRep().getResource());
                }
                if (healthcareService.hasProvidedBy()) {
                    addEntry(bundle, healthcareService.getProvidedByTarget());
                }
            }
        }
        if (referralRequest.hasRequester()) {
            addEntry(bundle, referralRequest.getRequester().getOnBehalfOfTarget());
        }
    }

    private void addComposition(Bundle bundle, Composition composition) {
        addEntry(bundle, composition);
        if (composition.hasAuthor()) {
            addEntry(bundle, (Resource) composition.getAuthorFirstRep().getResource());
        }
    }

    private void addList(Bundle bundle, ListResource listResource) {
        addEntry(bundle, listResource);
    }

    private static void addEntry(Bundle bundle, Resource resource) {
        bundle.addEntry()
                .setFullUrl(resource.getIdElement().getValue())
                .setResource(resource);
    }
}
