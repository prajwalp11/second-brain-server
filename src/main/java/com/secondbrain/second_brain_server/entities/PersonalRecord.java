package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "personal_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_log_id")
    private SessionLog sessionLog;

    private String metricKey;

    private Double value;

    private String unit;

    private LocalDate achievedAt;

    private Double previousValue;

    public PersonalRecordDto toDto() {
        return PersonalRecordDto.builder()
                .domainId(this.domain != null ? this.domain.getId() : null)
                .metricKey(this.metricKey)
                .label(null) // Populated by service layer
                .value(this.value)
                .unit(this.unit)
                .achievedAt(this.achievedAt)
                .previousValue(this.previousValue)
                .delta(this.previousValue != null ? this.value - this.previousValue : 0.0)
                .build();
    }
}
