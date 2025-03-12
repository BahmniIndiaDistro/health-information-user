package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@Builder
public class ConsentStatusRequest {
    ConsentStatusDetail consentRequest;
    RespError error;
    @NotNull
    GatewayResponse response;
}
