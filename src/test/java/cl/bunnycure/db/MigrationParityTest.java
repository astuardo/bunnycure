package cl.bunnycure.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationParityTest {

    private static final Path POSTGRES_MIGRATIONS = Paths.get("src", "main", "resources", "db", "migration");
    private static final Path H2_MIGRATIONS = Paths.get("src", "main", "resources", "db", "migration-h2");

    @Test
    void postgresAndH2MigrationDirectoriesExist() {
        assertTrue(Files.isDirectory(POSTGRES_MIGRATIONS), "Missing PostgreSQL migration directory: " + POSTGRES_MIGRATIONS.toAbsolutePath());
        assertTrue(Files.isDirectory(H2_MIGRATIONS), "Missing H2 migration directory: " + H2_MIGRATIONS.toAbsolutePath());
    }

    @Test
    void postgresAndH2MigrationSetsStayInSync() throws IOException {
        Set<String> postgresScripts = listMigrationScripts(POSTGRES_MIGRATIONS);
        Set<String> h2Scripts = listMigrationScripts(H2_MIGRATIONS);

        assertEquals(
                postgresScripts,
                h2Scripts,
                () -> "Flyway migration drift detected.\nOnly in PostgreSQL: " + difference(postgresScripts, h2Scripts)
                        + "\nOnly in H2: " + difference(h2Scripts, postgresScripts)
        );
    }

    @Test
    void migrationDirectoriesDoNotContainDuplicateVersionNumbers() throws IOException {
        assertNoDuplicateVersions(POSTGRES_MIGRATIONS, "PostgreSQL");
        assertNoDuplicateVersions(H2_MIGRATIONS, "H2");
    }

    private Set<String> listMigrationScripts(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.+\\.sql"))
                    .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        }
    }

    private void assertNoDuplicateVersions(Path directory, String label) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            List<String> versions = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.+\\.sql"))
                    .map(this::extractVersion)
                    .toList();

            Set<String> uniqueVersions = new TreeSet<>(versions);
            assertEquals(uniqueVersions.size(), versions.size(), label + " migration directory contains duplicate Flyway versions: " + versions);
        }
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> diff = new TreeSet<>(left);
        diff.removeAll(right);
        return diff;
    }

    private String extractVersion(String filename) {
        int separatorIndex = filename.indexOf("__");
        return separatorIndex > 0 ? filename.substring(0, separatorIndex) : filename;
    }
}
