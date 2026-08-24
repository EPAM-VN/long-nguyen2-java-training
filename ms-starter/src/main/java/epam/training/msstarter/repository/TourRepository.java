package epam.training.msstarter.repository;

import epam.training.msstarter.entity.Difficulty;
import epam.training.msstarter.entity.Tour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourRepository extends JpaRepository<Tour, Integer> {
    List<Tour> findByDifficulty(Difficulty diff);
    List<Tour> findByTourPackageCode(String code);
}
