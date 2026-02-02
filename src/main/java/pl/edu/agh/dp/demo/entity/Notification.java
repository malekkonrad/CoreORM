package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.time.LocalDateTime;

/**
 * Powiadomienie - klasa bazowa dla hierarchii dziedziczenia TABLE_PER_CLASS.
 * Notification <- EmailNotification, SmsNotification, PushNotification
 */
@Getter
@Setter
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class Notification {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @Column(defaultValue = "false")
    private Boolean isRead;

    @Column(defaultValue = "PENDING")
    private String status;
}
