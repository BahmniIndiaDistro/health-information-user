package in.org.projecteka.hiu.patient.model;

import in.org.projecteka.hiu.clients.Patient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class AbhaAddressSearchResponse {
    private String healthIdNumber;
    private String abhaAddress;
    private String fullName;
    private String mobile;

    public Patient toPatient(){
        return new Patient(this.abhaAddress, this.fullName, "");
    }
}
