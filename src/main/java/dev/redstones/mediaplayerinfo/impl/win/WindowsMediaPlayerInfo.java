package dev.redstones.mediaplayerinfo.impl.win;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WindowsMediaPlayerInfo implements MediaPlayerInfo {
    @Override
    public native List<IMediaSession> getMediaSessions();

    static {
        try {
            Path path = Files.createTempDirectory("mediaplayerinfo-");
            Path path2 = path.resolve("MediaPlayerInfo.dll");
            try (InputStream inputStream = WindowsMediaPlayerInfo.class.getResourceAsStream("/mediaplayerinfo/natives/win/MediaPlayerInfo.dll");) {
                if (inputStream == null) {
                    throw new IOException("Resource not found: /mediaplayerinfo/natives/win/MediaPlayerInfo.dll");
                }
                Files.write(path2, inputStream.readAllBytes());
            }
            System.load(path2.toAbsolutePath().toString());
            try {
                Files.deleteIfExists(path2);
                Files.deleteIfExists(path);
            } catch (IOException iOException) {
                path2.toFile().deleteOnExit();
                path.toFile().deleteOnExit();
            }
        } catch (IOException iOException) {
            throw new RuntimeException("Failed to load MediaPlayerInfo.dll", iOException);
        }
    }
}
