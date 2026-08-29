package com.yuan.live2d.client.live2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Live2DModelManagerCheck {
    private Live2DModelManagerCheck() {}

    public static void check() throws Exception {
        Path temp = Files.createTempDirectory("yuan-live2d-manager-");
        try {
            Path models = temp.resolve("models");
            Path firstSource = validModel(temp.resolve("first-source"), "first");
            Live2DModelManifest existing = Live2DModelImporter.importSource(firstSource, models).get(0);
            Live2DModelRepository repository = new Live2DModelRepository(models);
            Live2DModelManager manager = new Live2DModelManager(models, repository);
            assert manager.secureDescentRoot().equals(models.toAbsolutePath().getRoot())
                    : "secure deletion must anchor at the configured filesystem root";

            String selected = "persisted-selection";
            Path secondSource = validModel(temp.resolve("second-source"), "second");
            Live2DModelManager.ImportResult imported = manager.importSources(List.of(secondSource));
            assert selected.equals("persisted-selection") : "import must not change external selection state";
            assert imported.imported().size() == 1;
            assert imported.errors().isEmpty();
            assert manager.list().size() == 2;

            Path packageSource = validModel(temp.resolve("package-source"), "package-first");
            validModel(packageSource.resolve("nested"), "package-second");
            List<Live2DModelManifest> packageModels = manager.importSources(List.of(packageSource)).imported();
            assert packageModels.size() == 2;
            Live2DModelManager.PackageInfo packageInfo = manager.packageInfo(packageModels.get(0).id());
            assert packageInfo.models().size() == 2 && packageInfo.modelIds().contains(packageModels.get(1).id());
            assertRejected(() -> manager.deletePackage(packageModels.get(0).id(),
                    () -> Set.of(packageModels.get(1).id())), "protected");
            assert Files.exists(packageInfo.root()) : "a protected sibling must preserve the whole package";

            assertRejected(() -> manager.deleteInactive(existing.id(), existing.id()), "active");
            assertRejected(() -> manager.deleteInactive(existing.id(), Set.of("other", existing.id())), "protected");
            Live2DModelManifest second = imported.imported().get(0);
            Path secondRoot = manager.managedRoot(second);
            manager.deleteInactive(second.id(), existing.id());
            assert !Files.exists(secondRoot);
            assert manager.list().size() == 3 : "deleting one package must preserve unrelated and protected packages";

            Path nestedSource = validModel(temp.resolve("nested-source/top/deeper"), "nested");
            Live2DModelManifest nested = manager.importSources(List.of(temp.resolve("nested-source"))).imported().get(0);
            assert manager.managedRoot(nested).getParent().toRealPath().equals(models.toRealPath());
            Live2DModelManifest escaped = new Live2DModelManifest("escaped", "escaped", 3, temp,
                    temp.resolve("escaped.model3.json"), temp.resolve("escaped.moc3"), List.of(), null, List.of(), List.of());
            assertRejected(() -> manager.managedRoot(escaped), "unique managed root");

            Path unsupported = temp.resolve("not-a-model.txt");
            Files.writeString(unsupported, "nope");
            Live2DModelManager.ImportResult mixed = manager.importSources(List.of(unsupported, validModel(temp.resolve("third-source"), "third")));
            assert mixed.imported().size() == 1 : "valid sources must proceed after a rejected source";
            assert mixed.errors().size() == 1;
            assert mixed.errors().get(0).message().length() <= 160;

            if (supportsSymlinks(temp)) {
                Path linked = manager.managedRoot(nested).resolve("linked");
                Files.createSymbolicLink(linked, temp);
                assertRejected(() -> manager.deleteInactive(nested.id(), existing.id()), "Symbolic");
                assert Files.exists(manager.managedRoot(nested)) : "prevalidation failure must preserve the tree";
                Files.delete(linked);
            }

            Live2DModelManifest third = mixed.imported().get(0);
            Path thirdRoot = manager.managedRoot(third);
            AtomicReference<Set<String>> changingProtection = new AtomicReference<>(Set.of());
            changingProtection.set(Set.of(third.id()));
            assertRejected(() -> manager.deletePackage(third.id(), changingProtection::get), "protected");
            int[] attempts = {0};
            Live2DModelManager unchanged = new Live2DModelManager(models, repository, path -> {
                if (attempts[0]++ == 0) throw new IOException("forced delete failure");
                Files.delete(path);
            });
            assertRejected(() -> unchanged.deleteInactive(third.id(), existing.id()), "Deletion failed");
            assert Files.exists(third.modelJson()) && Files.exists(third.moc())
                    : "failure before the first delete must preserve every file";
            Live2DModelManager failing = new Live2DModelManager(models, repository, path -> {
                if (path.equals(thirdRoot)) throw new IOException("forced delete failure");
                Files.delete(path);
            });
            assertRejected(() -> failing.deleteInactive(third.id(), existing.id()), "Partial deletion");
            assert Files.exists(thirdRoot) : "failed root deletion must preserve the entry";

            ManualExecutor executor = new ManualExecutor();
            Live2DModelManager queued = new Live2DModelManager(models, repository, Files::delete, executor);
            List<String> order = new ArrayList<>();
            var firstQueued = queued.submit(() -> { order.add("first"); return 1; });
            var secondQueued = queued.submit(() -> { order.add("second"); return 2; });
            assert !firstQueued.isDone() && !secondQueued.isDone();
            executor.runNext();
            assert firstQueued.join() == 1 && !secondQueued.isDone();
            executor.runNext();
            assert secondQueued.join() == 2 && order.equals(List.of("first", "second"));

            Path duplicateDeleteSource = validModel(temp.resolve("duplicate-delete-source"), "duplicate-delete");
            Live2DModelManifest duplicateDelete = queued.importSources(List.of(duplicateDeleteSource)).imported().get(0);
            var deleteOne = queued.deletePackageAsync(duplicateDelete.id(), Set::of);
            var deleteTwo = queued.deletePackageAsync(duplicateDelete.id(), Set::of);
            executor.runNext();
            assert deleteOne.join().deleted().modelIds().contains(duplicateDelete.id());
            executor.runNext();
            assert deleteTwo.isCompletedExceptionally() : "the queued sibling delete must run after the first deletion";

            Path queuedSource = validModel(temp.resolve("queued-source"), "queued");
            var queuedImport = queued.importSourcesAsync(List.of(queuedSource));
            executor.runNext();
            assert queuedImport.join().snapshot().models().stream().anyMatch(model -> model.displayName().equals("queued"))
                    : "every mutation must publish a refreshed snapshot";

            CountDownLatch dialogStarted = new CountDownLatch(1);
            CountDownLatch releaseDialog = new CountDownLatch(1);
            Executor dialogExecutor = command -> new Thread(command, "manager-dialog-check").start();
            Live2DModelManager dialogManager = new Live2DModelManager(models, repository, Files::delete, dialogExecutor);
            var dialog = dialogManager.submitDialog(() -> {
                dialogStarted.countDown();
                releaseDialog.await();
                return "closed";
            });
            assert dialogStarted.await(2, TimeUnit.SECONDS);
            repository.invalidate();
            assert !repository.scan().isEmpty() : "repository scan must not wait for a blocking dialog";
            releaseDialog.countDown();
            assert dialog.get(2, TimeUnit.SECONDS).equals("closed");

            Path lateProtectedSource = validModel(temp.resolve("late-protected-source"), "late-protected");
            Live2DModelManifest lateProtected = manager.importSources(List.of(lateProtectedSource)).imported().get(0);
            AtomicReference<Set<String>> lateProtectedIds = new AtomicReference<>(Set.of());
            Live2DModelManager lateProtectionManager = new Live2DModelManager(models, repository, Files::delete,
                    Runnable::run, () -> lateProtectedIds.set(Set.of(lateProtected.id())));
            assertRejected(() -> lateProtectionManager.deletePackage(lateProtected.id(), lateProtectedIds::get), "protected");
            assert Files.exists(lateProtected.modelJson());

            Path runtimeFailureSource = validModel(temp.resolve("runtime-failure-source"), "runtime-failure");
            Live2DModelManifest runtimeFailure = manager.importSources(List.of(runtimeFailureSource)).imported().get(0);
            Path runtimeRoot = manager.managedRoot(runtimeFailure);
            Live2DModelManager runtimeFailing = new Live2DModelManager(models, repository, path -> {
                if (path.equals(runtimeRoot)) throw new IllegalStateException("runtime delete failure");
                Files.delete(path);
            });
            assertRejected(() -> runtimeFailing.deletePackage(runtimeFailure.id(), Set::of), "Partial deletion");

            Path rootSwapSource = validModel(temp.resolve("root-swap-source"), "root-swap");
            Live2DModelManifest rootSwap = manager.importSources(List.of(rootSwapSource)).imported().get(0);
            if (supportsSymlinks(temp)) {
                Path originalModels = temp.resolve("models-original");
                Files.move(models, originalModels);
                Files.createSymbolicLink(models, originalModels);
                assertRejected(() -> manager.deletePackage(rootSwap.id(), Set::of), "root identity");
                Files.delete(models);
                Files.move(originalModels, models);
            }

            Path ancestorBase = temp.resolve("ancestor-base");
            Path ancestorModels = ancestorBase.resolve("trusted-parent/models");
            Path ancestorSource = validModel(temp.resolve("ancestor-source"), "ancestor");
            Live2DModelManifest ancestorModel = Live2DModelImporter.importSource(ancestorSource, ancestorModels).get(0);
            Path trustedParent = ancestorModels.getParent();
            Path movedParent = ancestorBase.resolve("trusted-parent-original");
            if (supportsSymlinks(temp)) {
                Live2DModelManager ancestorManager = new Live2DModelManager(ancestorModels,
                        new Live2DModelRepository(ancestorModels), Files::delete, Runnable::run, () -> {
                    try {
                        Files.move(trustedParent, movedParent);
                        Files.createSymbolicLink(trustedParent, movedParent);
                    } catch (IOException error) {
                        throw new RuntimeException(error);
                    }
                });
                assertRejected(() -> ancestorManager.deletePackage(ancestorModel.id(), Set::of), "root identity");
                Files.deleteIfExists(trustedParent);
                if (Files.exists(movedParent)) Files.move(movedParent, trustedParent);
            }

            Path identitySource = validModel(temp.resolve("identity-source"), "identity");
            Live2DModelManifest identity = manager.importSources(List.of(identitySource)).imported().get(0);
            Object originalKey = Files.readAttributes(identity.moc(), java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();
            if (originalKey != null) {
                int[] protectionReads = {0};
                assertRejected(() -> manager.deletePackage(identity.id(), () -> {
                    if (++protectionReads[0] == 2) {
                        try {
                            Files.delete(identity.moc());
                            Files.write(identity.moc(), new byte[] {2});
                        } catch (IOException error) {
                            throw new RuntimeException(error);
                        }
                    }
                    return Set.of();
                }), "identity changed");
            }
        } finally {
            deleteTree(temp);
        }
    }

    private static Path validModel(Path root, String name) throws Exception {
        Files.createDirectories(root);
        Files.write(root.resolve(name + ".moc3"), new byte[] {1});
        Files.write(root.resolve(name + ".png"), new byte[] {1});
        Files.writeString(root.resolve(name + ".model3.json"), """
                {"Version":3,"FileReferences":{"Moc":"%s.moc3","Textures":["%s.png"]}}
                """.formatted(name, name));
        return root;
    }

    private static boolean supportsSymlinks(Path temp) {
        Path link = temp.resolve("symlink-check");
        try {
            Files.createSymbolicLink(link, temp.resolve("missing"));
            Files.delete(link);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void assertRejected(ThrowingRunnable action, String message) throws Exception {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException | IOException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().contains(message);
        }
        assert rejected : "expected rejection containing: " + message;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        private void runNext() { tasks.remove().run(); }
    }
}
