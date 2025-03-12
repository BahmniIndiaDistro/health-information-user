package in.org.projecteka.hiu.dataflow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.common.Constants.TIMESTAMP_PATTERN;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KeyStructure {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIMESTAMP_PATTERN)
    private LocalDateTime expiry;
    private String parameters;
    private String keyValue;
}
