// ==UserScript==
// @name         SilientClient Music Bridge
// @namespace    http://shizavisuals.local
// @version      1.0
// @description  Sends the real <video>/<audio> playback position to the
//               SilientClient local bridge so in-browser music lyrics stay
//               synced (SMTC can't see seeks for browser players).
// @match        *://*.youtube.com/*
// @match        *://*.youtu.be/*
// @match        *://music.youtube.com/*
// @match        *://open.spotify.com/*
// @match        *://*.soundcloud.com/*
// @match        *://*.twitch.tv/*
// @match        *://vimeo.com/*
// @match        *://*.bandcamp.com/*
// @grant        none
// ==/UserScript==

(function () {
    'use strict';

    const ENDPOINT = 'http://127.0.0.1:8731/media';

    function post(state) {
        try {
            fetch(ENDPOINT, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(state),
                keepalive: true
            }).catch(function () {});
        } catch (e) {}
    }

    function cleanTitle(raw) {
        if (!raw) return '';
        return raw
            .replace(/\s*-\s*YouTube\s*Music$/i, '')
            .replace(/\s*-\s*YouTube$/i, '')
            .replace(/\s*-\s*Spotify$/i, '')
            .replace(/\s*on\s+SoundCloud$/i, '')
            .replace(/\s*-\s*Twitch$/i, '')
            .replace(/\s*-\s*Vimeo$/i, '')
            .trim();
    }

    // Try to pull a usable artist/title. Falls back to the page title.
    function metadata() {
        var title = '';
        var artist = '';
        var art = '';

        // YouTube / YouTube Music: player data is on the page, but a robust
        // fallback is the document title and the channel name.
        var ytTitle = document.querySelector('h1.title, .title.style-scope.yt-music-player-header, #above-the-fold ytmusic-player-bar');
        var docTitle = cleanTitle(document.title);
        if (docTitle) title = docTitle;

        var channel = document.querySelector('ytd-channel-name a, .subtitle.ytmusic-player-bar, #channel-name a');
        if (channel && channel.textContent) artist = channel.textContent.trim();

        // Spotify: dedicated elements.
        var spTitle = document.querySelector('[data-testid="now-playing-widget"] [data-testid="track-title-link"], .track-info__name');
        var spArtist = document.querySelector('[data-testid="now-playing-widget"] [data-testid="artist-link"], .track-info__artists');
        if (spTitle && spTitle.textContent) title = spTitle.textContent.trim();
        if (spArtist && spArtist.textContent) artist = spArtist.textContent.trim();

        // Album art / thumbnail extraction
        var ogImage = document.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
        if (ogImage && ogImage.content) {
            art = ogImage.content;
        } else {
            var thumb = document.querySelector('link[rel="image_src"], link[rel="apple-touch-icon"]');
            if (thumb && thumb.href) art = thumb.href;
        }

        if (!title) title = docTitle;
        return { title: title, artist: artist, art: art };
    }

    function tick() {
        var media = document.querySelector('video, audio');
        if (!media || !isFinite(media.currentTime) || media.currentTime <= 0) {
            return;
        }
        var meta = metadata();
        if (!meta.title) return;
        post({
            title: meta.title,
            artist: meta.artist || '',
            art: meta.art || '',
            pos: Math.round(media.currentTime * 1000),
            dur: isFinite(media.duration) ? Math.round(media.duration * 1000) : 0,
            playing: !media.paused && !media.ended
        });
    }

    setInterval(tick, 500);
    // also fire on play/pause/seek
    document.addEventListener('play', tick, true);
    document.addEventListener('seeked', tick, true);
    document.addEventListener('pause', tick, true);
})();
