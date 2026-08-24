package epam.training.msstarter.service;

import epam.training.msstarter.entity.Difficulty;
import epam.training.msstarter.entity.Region;
import epam.training.msstarter.entity.Tour;
import epam.training.msstarter.entity.TourPackage;
import epam.training.msstarter.repository.TourPackageRepository;
import epam.training.msstarter.repository.TourRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TourService {
    private final TourPackageRepository tourPackageRepository;
    private final TourRepository tourRepository;

    public TourService(TourPackageRepository tourPackageRepository, TourRepository tourRepository) {
        this.tourPackageRepository = tourPackageRepository;
        this.tourRepository = tourRepository;
    }

    public Tour createTour(String tourPackageName, String title,
                           String description, String blurb, Integer price, String duration,
                           String bullets, String keywords, Difficulty difficulty, Region region) {

        TourPackage tourPackage = tourPackageRepository.findByName(tourPackageName)
                .orElseThrow(() -> new RuntimeException("Tour Package not found for id:" + tourPackageName));
        return tourRepository.save(new Tour(title, description, blurb,
                price, duration, bullets, keywords, tourPackage, difficulty, region));
    }

    public List<Tour> lookupByDifficulty(Difficulty difficulty) {
        return tourRepository.findByDifficulty(difficulty);
    }

    public List<Tour> lookupByPackage(String tourPackageCode) {
        return tourRepository.findByTourPackageCode(tourPackageCode);
    }

    public long total() {
        return tourRepository.count();
    }
}

