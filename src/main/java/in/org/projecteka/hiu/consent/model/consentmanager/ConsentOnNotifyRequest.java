package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class ConsentOnNotifyRequest {
    private List<ConsentAcknowledgement> acknowledgement;
    private RespError error;
    private GatewayResponse response;
}
