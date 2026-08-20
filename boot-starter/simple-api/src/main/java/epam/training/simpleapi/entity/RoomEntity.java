package epam.training.simpleapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ROOMS")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ROOM_ID")
    private UUID roomId;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "NUMBER", nullable = false, unique = true, length = 2)
    private String number;

    @Column(name = "BED_INFO", nullable = false, length = 2)
    private String bedInfo;

}
