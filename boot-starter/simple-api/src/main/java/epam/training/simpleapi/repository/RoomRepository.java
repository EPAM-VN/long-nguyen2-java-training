package epam.training.simpleapi.repository;

import epam.training.simpleapi.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
}
