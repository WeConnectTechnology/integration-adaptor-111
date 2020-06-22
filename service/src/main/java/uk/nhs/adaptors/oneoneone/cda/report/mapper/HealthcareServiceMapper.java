package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.dstu3.model.HealthcareService;
import org.hl7.fhir.dstu3.model.Reference;
import org.springframework.stereotype.Component;
import uk.nhs.adaptors.oneoneone.cda.report.service.OrganizationService;
import uk.nhs.adaptors.oneoneone.cda.report.util.NodeUtil;
import uk.nhs.connect.iucds.cda.ucr.*;


@Component
@RequiredArgsConstructor
public class HealthcareServiceMapper {
    private final LocationMapper locationMapper;
    private final OrganizationService organizationService;
    private final ContactPointMapper contactPointMapper;

    public HealthcareService transformRecipient(
            POCDMT000002UK01InformationRecipient informationRecipient) {

        POCDMT000002UK01IntendedRecipient intendedRecipient =
                informationRecipient.getIntendedRecipient();

        HealthcareService healthcareService = new HealthcareService()
                .setActive(true);

        Reference locationRef = new Reference(locationMapper.mapRecipientToLocation(intendedRecipient));
        healthcareService.addLocation(locationRef);


        if (intendedRecipient.sizeOfTelecomArray() > 0) {
            for (TEL tel : intendedRecipient.getTelecomArray()) {
                contactPointMapper.mapContactPoint(tel);
            }
        }

        if (intendedRecipient.isSetReceivedOrganization()) {
            POCDMT000002UK01Organization receivedOrganization =
                    intendedRecipient.getReceivedOrganization();

            healthcareService.setProvidedBy(organizationService.createOrganization(receivedOrganization));
            if (receivedOrganization.sizeOfNameArray() > 0) {
                ON name = receivedOrganization.getNameArray(0);
                healthcareService.setName(NodeUtil.getAllText(name.getDomNode()));
            }
        }

        return healthcareService;
    }
}
