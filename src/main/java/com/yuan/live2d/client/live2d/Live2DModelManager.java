package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class Live2DModelManager {
    static final Object FILESYSTEM_LOCK = new Object();
    private static final ExecutorService OPERATIONS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "yuan-live2d-model-operations");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((ignored, failure) -> failure.printStackTrace());
        return thread;
    });

    @FunctionalInterface
    interface DeleteOperation { void delete(Path path) throws IOException; }

    public record Snapshot(List<Live2DModelManifest> models, Map<Path, String> errors) {
        public Snapshot {
            models = List.copyOf(models);
            errors = Map.copyOf(errors);
        }
    }

    public record ImportError(Path source, String message) {}
    public record ImportResult(List<Live2DModelManifest> imported, List<ImportError> errors, Snapshot snapshot) {
        public ImportResult {
            imported = List.copyOf(imported);
            errors = List.copyOf(errors);
        }

        public ImportResult(List<Live2DModelManifest> imported, List<ImportError> errors) {
            this(imported, errors, new Snapshot(List.of(), Map.of()));
        }
    }

    public record PackageInfo(Path root, List<Live2DModelManifest> models, Set<String> modelIds) {
        public PackageInfo {
            models = List.copyOf(models);
            modelIds = Set.copyOf(modelIds);
        }

        public String name() { return root.getFileName().toString(); }
    }

    public record DeleteResult(PackageInfo deleted, Snapshot snapshot) {}

    private record PathIdentity(Path path, Object fileKey, boolean directory) {}

    private final Path modelsRoot;
    private final Live2DModelRepository repository;
    private final DeleteOperation delete;
    private final Executor executor;
    private final boolean secureDelete;
    private final Runnable beforeMutation;
    private final Path configuredModelsRoot;
    private final Path realModelsRoot;
    private final Path realModelsParent;
    private final List<PathIdentity> rootChain;
    private final Path filesystemRoot;

    public Live2DModelManager() {
        this(Live2DPaths.models(), new Live2DModelRepository(Live2DPaths.models()));
    }

    public Live2DModelManager(Path modelsRoot, Live2DModelRepository repository) {
        this(modelsRoot, repository, Files::delete, OPERATIONS, true, () -> {});
    }

    Live2DModelManager(Path modelsRoot, Live2DModelRepository repository, DeleteOperation delete) {
        this(modelsRoot, repository, delete, OPERATIONS, false, () -> {});
    }

    Live2DModelManager(Path modelsRoot, Live2DModelRepository repository, DeleteOperation delete, Executor executor) {
        this(modelsRoot, repository, delete, executor, false, () -> {});
    }

    Live2DModelManager(Path modelsRoot, Live2DModelRepository repository, DeleteOperation delete, Executor executor,
                       Runnable beforeMutation) {
        this(modelsRoot, repository, delete, executor, false, beforeMutation);
    }

    private Live2DModelManager(Path modelsRoot, Live2DModelRepository repository, DeleteOperation delete,
                               Executor executor, boolean secureDelete, Runnable beforeMutation) {
        this.modelsRoot = modelsRoot.toAbsolutePath().normalize();
        this.configuredModelsRoot = this.modelsRoot;
        this.repository = Objects.requireNonNull(repository);
        this.delete = Objects.requireNonNull(delete);
        this.executor = Objects.requireNonNull(executor);
        this.secureDelete = secureDelete;
        this.beforeMutation = Objects.requireNonNull(beforeMutation);
        try {
            synchronized (FILESYSTEM_LOCK) {
                Files.createDirectories(this.modelsRoot);
                this.rootChain = inspectRootChain(this.modelsRoot);
                this.filesystemRoot = this.modelsRoot.getRoot();
                this.realModelsRoot = this.modelsRoot.toRealPath();
                this.realModelsParent = this.modelsRoot.getParent().toRealPath();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid models root: " + modelsRoot, error);
        }
    }

    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        return submit(operation, true);
    }

    Path secureDescentRoot() { return filesystemRoot; }

    public <T> CompletableFuture<T> submitDialog(Callable<T> operation) {
        return submit(operation, false);
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation, boolean filesystem) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                if (filesystem) synchronized (FILESYSTEM_LOCK) { future.complete(operation.call()); }
                else future.complete(operation.call());
            }
            catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        return future;
    }

    public CompletableFuture<Snapshot> refreshAsync() {
        return submit(this::refresh);
    }

    public CompletableFuture<ImportResult> importSourcesAsync(List<Path> sources) {
        return submit(() -> importSources(sources));
    }

    public CompletableFuture<PackageInfo> packageInfoAsync(String id) {
        return submit(() -> packageInfo(id));
    }

    public CompletableFuture<DeleteResult> deletePackageAsync(String id, Supplier<Set<String>> protectedIds) {
        return submit(() -> deletePackage(id, protectedIds));
    }

    public List<Live2DModelManifest> list() throws IOException {
        return refresh().models();
    }

    public Snapshot refresh() throws IOException {
        synchronized (FILESYSTEM_LOCK) {
            repository.invalidate();
            return new Snapshot(repository.scan(), repository.errors());
        }
    }

    public ImportResult importSources(List<Path> sources) throws IOException {
        synchronized (FILESYSTEM_LOCK) {
            List<Live2DModelManifest> imported = new ArrayList<>();
            List<ImportError> errors = new ArrayList<>();
            try {
                for (Path source : sources == null ? List.<Path>of() : sources) {
                    try { imported.addAll(Live2DModelImporter.importSource(source, modelsRoot)); }
                    catch (Exception error) { errors.add(new ImportError(source, bounded(message(error)))); }
                }
            } finally {
                repository.invalidate();
            }
            return new ImportResult(imported, errors, snapshot());
        }
    }

    public PackageInfo packageInfo(String id) throws IOException {
        synchronized (FILESYSTEM_LOCK) {
            Snapshot snapshot = refresh();
            Live2DModelManifest selected = snapshot.models().stream().filter(model -> model.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown model: " + id));
            Path managed = managedRoot(selected);
            List<Live2DModelManifest> packageModels = snapshot.models().stream()
                    .filter(model -> {
                        try { return managedRoot(model).equals(managed); }
                        catch (Exception ignored) { return false; }
                    }).toList();
            if (packageModels.isEmpty()) throw new IllegalArgumentException("Managed package has no models: " + managed);
            return new PackageInfo(managed, packageModels,
                    packageModels.stream().map(Live2DModelManifest::id)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    public Path managedRoot(Live2DModelManifest manifest) throws IOException {
        Path realRoot = modelsRoot.toRealPath();
        Path modelJson = manifest.modelJson().toAbsolutePath().normalize();
        Path manifestRoot = manifest.root().toAbsolutePath().normalize();
        List<Path> matches;
        try (var children = Files.list(realRoot)) {
            matches = children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> manifestRoot.startsWith(path) && modelJson.startsWith(path))
                    .toList();
        }
        if (matches.size() != 1) throw new IllegalArgumentException("Model has no unique managed root: " + manifest.id());
        Path managed = matches.get(0);
        BasicFileAttributes attributes = attributes(managed);
        if (!attributes.isDirectory() || attributes.isOther())
            throw new IllegalArgumentException("Managed root is not a regular directory: " + managed);
        if (!managed.getParent().toRealPath().equals(realRoot))
            throw new IllegalArgumentException("Model root escapes managed directory: " + managed);
        return managed;
    }

    public void deleteInactive(String id, String activeId) throws IOException {
        if (id != null && id.equals(activeId)) throw new IllegalArgumentException("Cannot delete active model: " + id);
        deletePackage(id, () -> activeId == null || activeId.isEmpty() ? Set.of() : Set.of(activeId));
    }

    public void deleteInactive(String id, Set<String> protectedIds) throws IOException {
        deletePackage(id, () -> protectedIds);
    }

    public DeleteResult deletePackage(String id, Supplier<Set<String>> protectedIds) throws IOException {
        synchronized (FILESYSTEM_LOCK) {
            verifyRootChain();
            PackageInfo target = packageInfo(id);
            rejectProtected(target, protectedIds.get());
            List<PathIdentity> tree = inspectTree(target.root());
            Map<Path, PathIdentity> identities = tree.stream().collect(java.util.stream.Collectors.toMap(
                    PathIdentity::path, value -> value));
            boolean[] changed = {false};
            try {
                beforeMutation.run();
                verifyRootChain();
                rejectProtected(target, protectedIds.get());
                if (!secureDelete || !deleteSecure(target.root(), identities, changed)) {
                    for (PathIdentity identity : tree) {
                        verifyRootChain();
                        revalidate(target.root(), identity, identities);
                        delete.delete(identity.path());
                        changed[0] = true;
                    }
                }
            } catch (IOException | RuntimeException error) {
                rethrowFatal(error);
                throw new IOException((changed[0] ? "Partial deletion: " : "Deletion failed: ") + message(error), error);
            } finally {
                repository.invalidate();
            }
            return new DeleteResult(target, snapshot());
        }
    }

    private Snapshot snapshot() throws IOException {
        return new Snapshot(repository.scan(), repository.errors());
    }

    private static void rejectProtected(PackageInfo target, Set<String> protectedIds) {
        Set<String> safe = protectedIds == null ? Set.of() : protectedIds;
        if (target.modelIds().stream().anyMatch(safe::contains))
            throw new IllegalArgumentException("Cannot delete package containing protected model");
    }

    private static List<PathIdentity> inspectTree(Path managed) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(managed)) {
            if (stream instanceof java.nio.file.SecureDirectoryStream<?>) {
                // The provider supports secure relative operations, but Java's API cannot recursively delete
                // a heterogeneous tree without reopening child streams. Identity rechecks below remain required.
            }
        }
        try (var paths = Files.walk(managed)) {
            return paths.map(path -> {
                        try {
                            BasicFileAttributes value = attributes(path);
                            if (Files.isSymbolicLink(path) || value.isOther())
                                throw new IllegalArgumentException("Symbolic links or reparse points are not allowed: " + path);
                            return new PathIdentity(path, value.fileKey(), value.isDirectory());
                        } catch (IOException error) {
                            throw new InspectionFailure(error);
                        }
                    })
                    .sorted(Comparator.comparing((PathIdentity value) -> value.path().getNameCount()).reversed())
                    .toList();
        } catch (InspectionFailure failure) {
            throw failure.error;
        }
    }

    private static List<PathIdentity> inspectRootChain(Path configuredRoot) throws IOException {
        List<PathIdentity> identities = new ArrayList<>();
        Path current = configuredRoot.getRoot();
        if (current != null) identities.add(identity(current));
        for (Path part : configuredRoot) {
            current = current == null ? part : current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) identities.add(identity(current));
        }
        return List.copyOf(identities);
    }

    private void verifyRootChain() throws IOException {
        try {
            for (PathIdentity expected : rootChain) {
                PathIdentity actual = identity(expected.path());
                if (actual.directory() != expected.directory()
                        || expected.fileKey() != null && !expected.fileKey().equals(actual.fileKey()))
                    throw new IllegalArgumentException("Models root identity changed: " + expected.path());
            }
            if (!configuredModelsRoot.toRealPath().equals(realModelsRoot)
                    || !configuredModelsRoot.getParent().toRealPath().equals(realModelsParent))
                throw new IllegalArgumentException("Models root identity changed: " + configuredModelsRoot);
        } catch (IOException | IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("Models root identity changed")) throw error;
            throw new IllegalArgumentException("Models root identity changed: " + configuredModelsRoot, error);
        }
    }

    private static void revalidate(Path managed, PathIdentity expected, Map<Path, PathIdentity> identities) throws IOException {
        Path normalized = expected.path().toAbsolutePath().normalize();
        if (!normalized.startsWith(managed)) throw new IllegalArgumentException("Deletion path escapes managed root: " + normalized);
        for (Path current = managed; current != null && normalized.startsWith(current); current = next(current, normalized)) {
            BasicFileAttributes actual = attributes(current);
            if (Files.isSymbolicLink(current) || actual.isOther())
                throw new IllegalArgumentException("Symbolic links or reparse points are not allowed: " + current);
            PathIdentity recorded = identities.get(current);
            if (recorded != null && recorded.fileKey() != null && !recorded.fileKey().equals(actual.fileKey()))
                throw new IllegalArgumentException("Deletion path identity changed: " + current);
            if (current.equals(normalized)) {
                if (actual.isDirectory() != expected.directory())
                    throw new IllegalArgumentException("Deletion path type changed: " + current);
                if (expected.fileKey() != null && !expected.fileKey().equals(actual.fileKey()))
                    throw new IllegalArgumentException("Deletion path identity changed: " + current);
                break;
            }
        }
    }

    private boolean deleteSecure(Path root, Map<Path, PathIdentity> identities, boolean[] changed) throws IOException {
        verifyRootChain();
        List<DirectoryStream<Path>> opened = new ArrayList<>();
        try {
            DirectoryStream<Path> rootStream = Files.newDirectoryStream(filesystemRoot);
            opened.add(rootStream);
            if (!(rootStream instanceof java.nio.file.SecureDirectoryStream<Path> current)) return false;
            Path currentPath = filesystemRoot;
            for (Path component : filesystemRoot.relativize(configuredModelsRoot)) {
                Path childPath = currentPath.resolve(component);
                PathIdentity expected = rootChain.stream().filter(value -> value.path().equals(childPath)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Models root identity changed: " + childPath));
                BasicFileAttributes actual = current.getFileAttributeView(component, BasicFileAttributeView.class,
                        LinkOption.NOFOLLOW_LINKS).readAttributes();
                if (actual.isSymbolicLink() || actual.isOther() || !actual.isDirectory()
                        || !expected.directory()
                        || expected.fileKey() != null && !expected.fileKey().equals(actual.fileKey()))
                    throw new IllegalArgumentException("Models root identity changed: " + childPath);
                java.nio.file.SecureDirectoryStream<Path> next =
                        current.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
                opened.add(next);
                current = next;
                currentPath = childPath;
            }
            deleteSecure(current, root.getFileName(), root, identities, changed);
            return true;
        } finally {
            IOException closeFailure = null;
            for (int i = opened.size() - 1; i >= 0; --i) {
                try { opened.get(i).close(); }
                catch (IOException error) { if (closeFailure == null) closeFailure = error; else closeFailure.addSuppressed(error); }
            }
            if (closeFailure != null) throw closeFailure;
        }
    }

    private static void deleteSecure(java.nio.file.SecureDirectoryStream<Path> parent, Path name, Path current,
                                     Map<Path, PathIdentity> identities, boolean[] changed) throws IOException {
        BasicFileAttributes attributes = parent.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
        if (attributes.isSymbolicLink() || attributes.isOther())
            throw new IllegalArgumentException("Symbolic links or reparse points are not allowed: " + name);
        PathIdentity expected = identities.get(current);
        if (expected == null || expected.directory() != attributes.isDirectory()
                || expected.fileKey() != null && !expected.fileKey().equals(attributes.fileKey()))
            throw new IllegalArgumentException("Deletion path identity changed: " + current);
        if (!attributes.isDirectory()) {
            parent.deleteFile(name);
            changed[0] = true;
            return;
        }
        try (java.nio.file.SecureDirectoryStream<Path> directory =
                     parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
            for (Path child : directory) {
                Path childName = child.getFileName();
                deleteSecure(directory, childName, current.resolve(childName), identities, changed);
            }
        }
        parent.deleteDirectory(name);
        changed[0] = true;
    }

    private static Path next(Path current, Path target) {
        int index = current.getNameCount();
        return index < target.getNameCount() ? current.resolve(target.getName(index)) : null;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static PathIdentity identity(Path path) throws IOException {
        BasicFileAttributes value = attributes(path);
        if (Files.isSymbolicLink(path) || value.isOther())
            throw new IllegalArgumentException("Symbolic links or reparse points are not allowed: " + path);
        return new PathIdentity(path, value.fileKey(), value.isDirectory());
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private static String bounded(String value) {
        return value.substring(0, Math.min(160, value.length()));
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
        if (failure instanceof VirtualMachineError virtualMachineError) throw virtualMachineError;
    }

    private static final class InspectionFailure extends RuntimeException {
        private final IOException error;
        private InspectionFailure(IOException error) { this.error = error; }
    }
}
