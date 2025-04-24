package de.ait.restaurantapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {
    private String to;
    private String reservationCode;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int guestCount;
    private boolean isCancel;
    private String subject;
}
