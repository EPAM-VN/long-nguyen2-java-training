package epam.training.simpleapi.service;

import epam.training.simpleapi.entity.RoomEntity;
import epam.training.simpleapi.model.Room;
import epam.training.simpleapi.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<Room> getAllRooms() {
        List<RoomEntity> roomEntities = roomRepository.findAll();
        return roomEntities.stream()
                .map(entity -> new Room(entity.getRoomId(), entity.getName(), entity.getNumber(), entity.getBedInfo()))
                .toList();
    }

    public Room getRoomById(UUID roomId) {
        RoomEntity roomEntity = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));
        return new Room(roomEntity.getRoomId(), roomEntity.getName(), roomEntity.getNumber(), roomEntity.getBedInfo());
    }

    public Room AddRoom(Room room) {
        RoomEntity roomEntity = new RoomEntity();
        roomEntity.setRoomId(room.getRoomId());
        roomEntity.setName(room.getName());
        roomEntity.setNumber(room.getNumber());
        roomEntity.setBedInfo(room.getBedInfo());
        RoomEntity savedRoomEntity = roomRepository.save(roomEntity);
        return new Room(savedRoomEntity.getRoomId(), savedRoomEntity.getName(), savedRoomEntity.getNumber(), savedRoomEntity.getBedInfo());
    }
}
