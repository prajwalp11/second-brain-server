package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TimeSeriesPointResponse {
    private LocalDate date;
    private Double value;
}
