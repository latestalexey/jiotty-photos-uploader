package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;

final class UploaderImpl implements Uploader {
    private static final Logger logger = LoggerFactory.getLogger(UploaderImpl.class);
    private final FilesystemManager filesystemManager;
    private final GooglePhotosUploader uploader;
    private final DirectoryStructureSupplier directoryStructureSupplier;
    private final AlbumManager albumManager;
    private final CloudAlbumsProvider cloudAlbumsProvider;
    private final ProgressStatusFactory progressStatusFactory;

    @Inject
    UploaderImpl(FilesystemManager filesystemManager,
                 GooglePhotosUploader uploader,
                 DirectoryStructureSupplier directoryStructureSupplier,
                 AlbumManager albumManager,
                 CloudAlbumsProvider cloudAlbumsProvider,
                 ProgressStatusFactory progressStatusFactory) {
        this.filesystemManager = checkNotNull(filesystemManager);
        this.uploader = checkNotNull(uploader);
        this.directoryStructureSupplier = checkNotNull(directoryStructureSupplier);
        this.albumManager = checkNotNull(albumManager);
        this.cloudAlbumsProvider = checkNotNull(cloudAlbumsProvider);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    @Override
    public CompletableFuture<Void> upload(Path rootDir) {
        CompletableFuture<List<AlbumDirectory>> albumDirectoriesFuture = directoryStructureSupplier.listAlbumDirectories(rootDir);
        CompletableFuture<Map<String, List<GooglePhotosAlbum>>> cloudAlbumsByTitleFuture = cloudAlbumsProvider.listCloudAlbums();
        return albumDirectoriesFuture
                .thenCompose(albumDirectories -> cloudAlbumsByTitleFuture
                        .thenCompose(cloudAlbumsByTitle -> albumManager.listAlbumsByTitle(albumDirectories, cloudAlbumsByTitle)
                                .thenCompose(albumsByTitle -> {
                                    ProgressStatus directoryProgressStatus =
                                            progressStatusFactory.create("Folders uploaded", Optional.of(albumDirectories.size() - 1));
                                    ProgressStatus fileProgressStatus =
                                            progressStatusFactory.create("Uploading files", Optional.empty());
                                    return albumDirectories.stream()
                                            .flatMap(albumDirectory -> {
                                                directoryProgressStatus.increment();
                                                return filesystemManager.listFiles(albumDirectory.path()).stream()
                                                        .map(path -> uploader.uploadFile(albumDirectory.albumTitle().map(albumsByTitle::get), path)
                                                                .whenComplete((aVoid, e) -> fileProgressStatus.increment()));
                                            })
                                            .collect(toFutureOfList())
                                            .thenAccept(list -> {
                                                logger.info("All done without errors, files uploaded: {}", list.size());
                                                directoryProgressStatus.close();
                                                fileProgressStatus.close();
                                            });
                                })));
    }
}
