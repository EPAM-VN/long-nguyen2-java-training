package epam.training.simpleapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "EMPLOYEES")
@Getter
@Setter
public class EmployeeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "EMPLOYEE_ID")
    private UUID employeeId;

    @Column(name = "FIRST_NAME", nullable = false)
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false)
    private String lastName;

    @Column(name = "POSITION", nullable = false)
    @Enumerated(EnumType.STRING)
    private Position position;
}

