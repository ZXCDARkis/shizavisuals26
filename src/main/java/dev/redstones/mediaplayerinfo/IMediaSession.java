package dev.redstones.mediaplayerinfo;

public interface IMediaSession {
    public String getOwner();

    public MediaInfo getMedia();

    public void play();

    public void pause();

    public void playPause();

    public void stop();

    public void next();

    public void previous();

    public void swapCycle();

    public int getCycleType();
}
