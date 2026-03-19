package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSeriesPointDto {

    private LocalDate date;
    private Double value;
}
