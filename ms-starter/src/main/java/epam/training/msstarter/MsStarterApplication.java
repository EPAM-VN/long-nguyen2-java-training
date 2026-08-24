package epam.training.msstarter;

import epam.training.msstarter.entity.Difficulty;
import epam.training.msstarter.entity.Region;
import epam.training.msstarter.service.TourPackageService;
import epam.training.msstarter.service.TourService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootApplication
public class MsStarterApplication {
    private static final String TOUR_IMPORT_FILE = "ExploreCalifornia.json";

    public static void main(String[] args) {
        SpringApplication.run(MsStarterApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(TourPackageService tourPackageService, TourService tourService) {
        return args -> {
            createTourAllPackages(tourPackageService);
            System.out.println("Persisted Packages = " + tourPackageService.total());
            createToursFromFile(tourService);
            System.out.println("Persisted Tours = " + tourService.total());

            System.out.println("\n\nEasy Tours");
            tourService.lookupByDifficulty(Difficulty.Easy).forEach(System.out::println);

            System.out.println("\n\nBackpack Cali Tours");
            tourService.lookupByPackage("BC").forEach(System.out::println);
        };
    }

    /**
     * Iterate through all the tour packages, print the tour package name and
     * for each tour package lookup all tours and print the name and
     * description of the tour.
     *
     */
    private void printToursChallenge(TourPackageService tourPackageService) {
//        tourPackageService.lookupAll().forEach(tourPackage -> {
//            System.out.println("Tour Package: " + tourPackage.getName());
//            tourPackage.getTours().forEach(tour -> {
//                System.out.println("\tTour: " + tour.getTitle() + " - " + tour.getDescription());
//            });
//        });
    }

    /**
     * Initialize all the known tour packages
     */
    private void createTourAllPackages(TourPackageService tourPackageService) {
        tourPackageService.createTourPackage("BC", "Backpack Cal");
        tourPackageService.createTourPackage("CC", "California Calm");
        tourPackageService.createTourPackage("CH", "California Hot springs");
        tourPackageService.createTourPackage("CY", "Cycle California");
        tourPackageService.createTourPackage("DS", "From Desert to Sea");
        tourPackageService.createTourPackage("KC", "Kids California");
        tourPackageService.createTourPackage("NW", "Nature Watch");
        tourPackageService.createTourPackage("SC", "Snowboard Cali");
        tourPackageService.createTourPackage("TC", "Taste of California");
    }

    /**
     * Create tour entities from an external file
     */
    private void createToursFromFile(TourService tourService) throws IOException {
        TourFromFile.read().forEach(t -> tourService.createTour(
                t.packageName(),
                t.title(),
                t.description(),
                t.blurb(),
                t.price(),
                t.length(),
                t.bullets(),
                t.keywords(),
                Difficulty.valueOf(t.difficulty()),
                Region.findByLabel(t.region())));
    }

    /*
     * Helper to import ExploreCali.json
     */
    record TourFromFile(String packageName, String title, String description,
                        String blurb, Integer price, String length, String bullets,
                        String keywords, String difficulty, String region) {
        static List<TourFromFile> read() throws IOException {
            return new ObjectMapper().readValue(new File(TOUR_IMPORT_FILE),
                    new TypeReference<List<TourFromFile>>() {
                    });
        }
    }
}
