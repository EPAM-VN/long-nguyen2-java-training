package epam.training.simpleapi.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Room {
    private UUID roomId;
    private String name;
    private String number;
    private String bedInfo;
}
