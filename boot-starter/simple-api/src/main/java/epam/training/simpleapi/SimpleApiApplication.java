package epam.training.simpleapi;

import epam.training.simpleapi.repository.EmployeeRepository;
import epam.training.simpleapi.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.TimeZone;

@SpringBootApplication
public class SimpleApiApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(SimpleApiApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(RoomRepository roomRepository, EmployeeRepository employeeRepository) {
        return args -> {
            employeeRepository.findAll().forEach(employee -> {
                System.out.println("Employee ID: " + employee.getEmployeeId());
                System.out.println("First Name: " + employee.getFirstName());
                System.out.println("Last Name: " + employee.getLastName());
                System.out.println("Position: " + employee.getPosition());
                System.out.println("---------------------------");
            });

            roomRepository.findAll().forEach(room -> {
                System.out.println("Room ID: " + room.getRoomId());
                System.out.println("Room Number: " + room.getNumber());
                System.out.println("Bed Info: " + room.getBedInfo());
                System.out.println("---------------------------");
            });
        };
    }

}
