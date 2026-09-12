package dev.redstones.mediaplayerinfo;

import dev.redstones.mediaplayerinfo.impl.DummyMediaPlayerInfo;
import dev.redstones.mediaplayerinfo.impl.win.WindowsMediaPlayerInfo;
import java.util.List;

public interface MediaPlayerInfo {
    public static final MediaPlayerInfo INSTANCE = MediaState.getInstance();

    public List<IMediaSession> getMediaSessions();

    public static class MediaState {
        private static final MediaPlayerInfo instance = System.getProperty("os.name").toLowerCase().startsWith("windows") ? new WindowsMediaPlayerInfo() : new DummyMediaPlayerInfo();

        public static MediaPlayerInfo getInstance() {
            return instance;
        }
    }
}
