package epam.training.simpleapi.controller;

import epam.training.simpleapi.model.Room;
import epam.training.simpleapi.service.RoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<Room> getAllRooms() {
        return roomService.getAllRooms();
    }

    @GetMapping("/{roomId}")
    public Room getRoomById(@PathVariable UUID roomId) {
        return roomService.getRoomById(roomId);
    }

    @PostMapping
    public Room createRoom(@RequestBody Room room) {
        return roomService.AddRoom(room);
    }

}
