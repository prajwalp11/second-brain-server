package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TimeSeriesPointDto {
    private LocalDateTime date;
    private Double value;
}
