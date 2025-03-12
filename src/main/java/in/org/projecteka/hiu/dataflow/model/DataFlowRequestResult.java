package in.org.projecteka.hiu.dataflow.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class DataFlowRequestResult {
    HIRequest hiRequest;
    RespError error;
    GatewayResponse response;

    public GatewayResponse getResponse() {
        return response == null ? GatewayResponse.builder().build() : response;
    }
}
