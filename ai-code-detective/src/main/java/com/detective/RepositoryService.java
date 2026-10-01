package com.detective;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class RepositoryService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".jsx", ".ts", ".tsx",
            ".html", ".css", ".scss",
            ".c", ".cpp", ".h", ".hpp", ".cs",
            ".go", ".rs", ".rb", ".php", ".kt", ".swift",
            ".sql", ".sh", ".yml", ".yaml", ".xml", ".json",
            ".md", ".txt", ".properties"
    );

    private static final Set<String> IGNORE_DIRS = Set.of(
            "node_modules", "target", "build", "dist", "out", ".git",
            ".idea", ".vscode", "venv", "__pycache__", ".next"
    );

    /**
     * Clone a GitHub repository into a unique temp folder.
     * Every analyze call gets a fresh folder — no fighting with locked files.
     */
    public File cloneRepository(String repoUrl) throws Exception {
        // Unique folder per analyze call
        String uniqueName = "ai-detective-repo-" + UUID.randomUUID().toString().substring(0, 8);
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), uniqueName);

        System.out.println("Cloning " + repoUrl + " into " + cloneDir.getAbsolutePath());

        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(cloneDir)
                .setDepth(1)
                .call()
                .close();

        return cloneDir;
    }

    public List<File> findSourceFiles(File repoDir) throws IOException {
        List<File> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isInAllowedDir)
                 .filter(p -> hasTextExtension(p.toString()))
                 .forEach(p -> files.add(p.toFile()));
        }

        return files;
    }

    private boolean hasTextExtension(String path) {
        String lower = path.toLowerCase();
        for (String ext : TEXT_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private boolean isInAllowedDir(Path path) {
        for (Path part : path) {
            if (IGNORE_DIRS.contains(part.toString())) return false;
        }
        return true;
    }
}