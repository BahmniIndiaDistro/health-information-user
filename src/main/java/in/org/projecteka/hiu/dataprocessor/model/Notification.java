package in.org.projecteka.hiu.dataprocessor.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.common.Constants.TIMESTAMP_PATTERN;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder
public class Notification {
    private String consentId;
    private String transactionId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIMESTAMP_PATTERN)
    private LocalDateTime doneAt;
    private Notifier notifier;
    private StatusNotification statusNotification;
}
