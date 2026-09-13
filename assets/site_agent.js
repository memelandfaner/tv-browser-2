/* Safeer: keep YouTube and YouTube Music playing without the idle prompt
   ("Video paused. Continue watching?" / "Predvajanje videoposnetka je začasno zaustavljeno"). */
(function () {
    var host = (location.hostname || '').toLowerCase();
    if (!(host === 'youtube.com' || host.slice(-12) === '.youtube.com')) return;
    if (window.top !== window || window.__safeerKeepWatching) return;
    window.__safeerKeepWatching = true;

    // YouTube and YouTube Music show the idle prompt only when Date.now() - window._lact
    // (time of the last user activity) exceeds the limit sent by the server. A getter keeps the
    // value current without timers, which browsers delay for minutes in hidden or minimized windows.
    function markActive() {
        try { window._lact = Date.now(); } catch (e) {}
    }
    try {
        Object.defineProperty(window, '_lact', {
            configurable: true,
            get: function () { return Date.now(); },
            set: function () {}
        });
    } catch (e) {
        markActive();
        setInterval(markActive, 30000);
    }

    var lastUserInput = 0;
    var lastAutoPause = 0;
    var autoPausedVideo = null;
    ['pointerdown', 'mousedown', 'touchstart', 'keydown'].forEach(function (type) {
        window.addEventListener(type, function (ev) {
            if (ev.isTrusted) lastUserInput = Date.now();
        }, true);
    });
    // A pause without user input is how the idle prompt stops playback.
    document.addEventListener('pause', function (ev) {
        var v = ev.target;
        if (!v || v.tagName !== 'VIDEO' || v.ended) return;
        if (Date.now() - lastUserInput > 2000) {
            lastAutoPause = Date.now();
            autoPausedVideo = v;
            scanSoon();
        }
    }, true);
    // YouTube announces its dialogs; react in a microtask instead of waiting for a (throttled) timer.
    document.addEventListener('yt-popup-opened', function () { scanSoon(); }, true);

    var PROMPTS = 'ytmusic-you-there-renderer, ytd-you-there-renderer, ytm-you-there-renderer, yt-confirm-dialog-renderer';
    // In priority order: querySelector with a selector list would return the outer wrapper first.
    var BUTTONS = ['#confirm-button button', '#confirm-button tp-yt-paper-button', 'yt-button-renderer button',
        'button', 'tp-yt-paper-button', '[role="button"]', '#confirm-button'];

    function isShown(el) {
        if (!el || !el.isConnected) return false;
        var r = el.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) return false;
        var s = window.getComputedStyle(el);
        return s.visibility !== 'hidden' && s.display !== 'none';
    }

    function mainVideo() {
        if (autoPausedVideo && autoPausedVideo.isConnected) return autoPausedVideo;
        return document.querySelector('#movie_player video, ytmusic-player video, video.html5-main-video, video');
    }

    function isIdlePrompt(el) {
        if (/YOU-THERE|STILL-WATCHING/.test(el.tagName)) return true;
        // The generic confirm dialog is also used for real questions; accept it only when
        // YouTube paused the video by itself and the user has not just interacted.
        var v = mainVideo();
        return !!v && v.paused && !v.ended &&
            Date.now() - lastAutoPause < 10000 && Date.now() - lastUserInput > 5000;
    }

    function resume() {
        var v = mainVideo();
        if (v && v.paused && !v.ended) {
            try {
                var p = v.play();
                if (p && p.catch) p.catch(function () {});
            } catch (e) {}
        }
        markActive();
    }

    function findPrompts() {
        var found = Array.prototype.slice.call(document.querySelectorAll(PROMPTS));
        // YouTube for TV (youtube.com/tv) uses its own renderer names; look for them only
        // shortly after an unexplained pause so normal pages are not walked every second.
        if (!found.length && Date.now() - lastAutoPause < 15000 && document.body) {
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
            for (var n = walker.nextNode(), count = 0; n && count < 20000; n = walker.nextNode(), count++) {
                if (/YOU-THERE|STILL-WATCHING/.test(n.tagName)) found.push(n);
            }
        }
        return found;
    }

    function pressEnter(el) {
        var target = el.querySelector('[tabindex], [role="button"], button') || el;
        try { target.focus(); } catch (e) {}
        ['keydown', 'keyup'].forEach(function (type) {
            var ev = new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
            (document.activeElement || target).dispatchEvent(ev);
        });
    }

    function scan() {
        var prompts = findPrompts();
        for (var i = 0; i < prompts.length; i++) {
            var el = prompts[i];
            if (!isShown(el) || !isIdlePrompt(el)) continue;
            if (el.__safeerConfirmedAt && Date.now() - el.__safeerConfirmedAt < 3000) continue;
            el.__safeerConfirmedAt = Date.now();
            var clicked = false;
            for (var b = 0; b < BUTTONS.length; b++) {
                var button = el.querySelector(BUTTONS[b]);
                if (button) { button.click(); clicked = true; break; }
            }
            // Resume at once as well: the timer below can be delayed in a hidden window.
            if (clicked) scanSoonResume();
            (function (prompt, wasClicked) {
                setTimeout(function () {
                    // TV layouts react to the remote's Enter key rather than to click().
                    if (!wasClicked || isShown(prompt)) pressEnter(prompt);
                    resume();
                }, 300);
            })(el, clicked);
        }
    }

    function scanSoon() {
        try { Promise.resolve().then(scan); } catch (e) {}
    }

    function scanSoonResume() {
        try { Promise.resolve().then(resume); } catch (e) {}
    }

    var queued = false;
    function queueScan() {
        if (queued) return;
        queued = true;
        setTimeout(function () { queued = false; scan(); }, 250);
    }
    function start() {
        try {
            new MutationObserver(queueScan).observe(document.documentElement, { childList: true, subtree: true });
        } catch (e) {}
        setInterval(scan, 1000);
    }
    if (document.documentElement) start();
    else document.addEventListener('DOMContentLoaded', start);
})();
/* Safeer: remove empty ad inserts and the "Please disable adblock or subscribe" placeholders
   on Hookshot Media sites (Push Square, Nintendo Life, Pure Xbox, Time Extension). */
(function () {
    var host = (location.hostname || '').toLowerCase();
    var sites = ['pushsquare.com', 'nintendolife.com', 'purexbox.com', 'timeextension.com', 'digitalfoundry.net'];
    var match = false;
    for (var i = 0; i < sites.length; i++) {
        if (host === sites[i] || host.slice(-(sites[i].length + 1)) === '.' + sites[i]) match = true;
    }
    if (!match || window.__safeerHookshotInserts) return;
    window.__safeerHookshotInserts = true;

    var CSS = '.insert, .insert-label, .item-insert, .for-mobile.below-article, [style*="min-height:250px;"]' +
        '{display:none !important;height:0 !important;min-height:0 !important;margin:0 !important;padding:0 !important}';

    function addStyle() {
        if (document.getElementById('safeer-hookshot-inserts')) return;
        var style = document.createElement('style');
        style.id = 'safeer-hookshot-inserts';
        style.textContent = CSS;
        var parent = document.head || document.documentElement;
        if (parent) parent.appendChild(style);
    }

    var NOTICE = /disable\s+ad\s*block|or\s+subscribe/i;
    function hideNotices() {
        var labels = document.querySelectorAll('span, p, div, strong, small');
        for (var i = 0; i < labels.length; i++) {
            var el = labels[i];
            if (el.__safeerChecked || el.children.length > 2) continue;
            var text = el.textContent || '';
            if (text.length > 80 || !NOTICE.test(text)) continue;
            el.__safeerChecked = true;
            // Hide the whole insert (label plus the reserved subscription box), never the article.
            var box = el.closest('.insert, .item-insert, [class*="insert"], aside, figure') || el.parentElement;
            if (!box || box === document.body || box.tagName === 'ARTICLE' || box.tagName === 'MAIN') box = el;
            box.style.setProperty('display', 'none', 'important');
        }
    }

    function run() { addStyle(); hideNotices(); }
    var queued = false;
    var observing = false;
    function start() {
        run();
        if (observing || !document.documentElement) return;
        observing = true;
        new MutationObserver(function () {
            if (queued) return;
            queued = true;
            setTimeout(function () { queued = false; run(); }, 300);
        }).observe(document.documentElement, { childList: true, subtree: true });
    }
    if (document.documentElement) start();
    document.addEventListener('DOMContentLoaded', start);
})();
(function () {
    if (window._safeerSiteAgent) return;

    var lastOkAt = 0;
    var lastPly = '';
    var boostSkipLogged = false;
    var keepN = 0;
    var drmClickN = 0;

    function dbg(msg, data) {
        try {
            if (window._safeerDbg) {
                window._safeerDbg('H140', 'site_agent.js', msg, data || {});
            }
        } catch (_) {}
    }

    function videoEl() {
        return window._safeer_xplore_player_el || document.querySelector('video');
    }

    function overlayEl() {
        return document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
    }

    function overlayClass() {
        var el = overlayEl();
        return el ? ((el.className || '') + '').toString() : '';
    }

    function overlayOpen() {
        var c = overlayClass().toLowerCase();
        if (!c) return false;
        if (c.indexOf('player-closed') !== -1) return false;
        return c.indexOf('player-fullwindow') !== -1 ||
            c.indexOf('player-open') !== -1 ||
            c.indexOf('player-scaled') !== -1;
    }

    function isAppBg() {
        try {
            if (window._safeer_app_bg) return true;
            if (sessionStorage.getItem('safeer_app_bg') === '1') return true;
        } catch (_) {}
        return !!(document.hidden || document.visibilityState === 'hidden');
    }

    function reopenTile(reason) {
        // Second livetv channel: overlay briefly goes player-closed while Castlabs
        // swaps streams. Re-clicking the tile here kills the new session (H208 n=1..3).
        // #region agent log
        try {
            if (window._safeerDbg) {
                window._safeerDbg('H266', 'site_agent.js:reopen', 'skip', {
                    reason: reason || '',
                    keepN: keepN,
                    ply: overlayClass().slice(0, 50)
                });
            }
        } catch (_) {}
        // #endregion
        return false;
    }

    function isFramed(v) {
        return !!(v && (v.videoWidth || 0) >= 320 && v.readyState >= 2);
    }

    function wantPlayNow() {
        return !!(window._safeer_xplore_want_play || (agent && agent.wantPlay));
    }

    function ensureHoldStyle() {
        if (document.getElementById('safeer-xplore-hold-style')) return;
        var st = document.createElement('style');
        st.id = 'safeer-xplore-hold-style';
        st.textContent =
            'html.safeer-xplore-hold,html.safeer-xplore-hold body{background:#000!important;}' +
            'html.safeer-xplore-hold [class*="livetv-grid"],' +
            'html.safeer-xplore-hold .item--event,' +
            'html.safeer-xplore-hold .menu-items-wrapper,' +
            'html.safeer-xplore-hold #csh__menu_bar,' +
            'html.safeer-xplore-hold header,' +
            'html.safeer-xplore-hold #safeer-focus-target-ring' +
            '{visibility:hidden!important;opacity:0!important;pointer-events:none!important;}' +
            'html.safeer-xplore-hold .zw-overlays-layer,' +
            'html.safeer-xplore-hold video' +
            '{visibility:visible!important;opacity:1!important;}';
        try { (document.documentElement || document.head).appendChild(st); } catch (_) {}
    }

    function syncHoldCover(reason) {
        var want = wantPlayNow();
        var v = videoEl();
        var framed = isFramed(v);
        var path = (location.pathname || '').toLowerCase();
        var onLivetv = path.indexOf('/livetv') !== -1;
        // Do not hide the livetv grid: Castlabs tears down the overlay if the
        // catalog is visibility:hidden (home Glej Zdaj works because hold is off).
        var show = false;
        if (want && !framed && onLivetv && overlayOpen()) {
            // #region agent log
            try {
                if (window._safeerDbg) {
                    window._safeerDbg('H267', 'site_agent.js:hold', 'skip grid hide', {
                        ply: overlayClass().slice(0, 50),
                        reason: reason || ''
                    });
                }
            } catch (_) {}
            // #endregion
        }
        var dead = document.getElementById('safeer-xplore-hold-cover');
        if (dead && dead.parentNode) {
            try { dead.parentNode.removeChild(dead); } catch (_) {}
        }
        try { ensureHoldStyle(); } catch (_) {}
        var html = document.documentElement;
        var on = html.classList.contains('safeer-xplore-hold');
        if (show) {
            if (!on) {
                html.classList.add('safeer-xplore-hold');
                try {
                    var ring = document.getElementById('safeer-focus-target-ring');
                    if (ring) { ring.classList.remove('active'); ring.style.display = 'none'; }
                } catch (_) {}
                dbg('hold cover', { show: true, mode: 'css-grid', reason: reason, ply: overlayClass().slice(0, 70) });
            }
        } else if (on) {
            html.classList.remove('safeer-xplore-hold');
            dbg('hold cover', { show: false, mode: 'css-grid', reason: reason, framed: framed, vw: v ? (v.videoWidth || 0) : 0 });
        }
    }

    function bindVideoEme() {
        if (window._safeer_eme_bound) return;
        window._safeer_eme_bound = true;
        window._safeer_eme_enc_n = 0;
        try {
            document.addEventListener('encrypted', function (ev) {
                try {
                    window._safeer_eme_enc_n = (window._safeer_eme_enc_n || 0) + 1;
                    var t = ev && ev.target;
                    dbg('encrypted', {
                        n: window._safeer_eme_enc_n,
                        tag: t ? (t.tagName || '') : '',
                        init: (ev && ev.initDataType) ? String(ev.initDataType) : '',
                        vw: t ? (t.videoWidth || 0) : 0,
                        rs: t ? t.readyState : -1,
                        mk: !!(t && t.mediaKeys),
                        ply: overlayClass().slice(0, 50)
                    });
                } catch (_) {}
            }, true);
            document.addEventListener('error', function (ev) {
                try {
                    var t = ev && ev.target;
                    if (!t || (t.tagName || '') !== 'VIDEO') return;
                    var err = t.error;
                    dbg('video error', {
                        code: err ? err.code : -1,
                        msg: err && err.message ? String(err.message).slice(0, 80) : '',
                        vw: t.videoWidth || 0,
                        rs: t.readyState,
                        ply: overlayClass().slice(0, 50)
                    });
                } catch (_) {}
            }, true);
            document.addEventListener('pause', function (ev) {
                try {
                    var t = ev && ev.target;
                    if (!t || (t.tagName || '') !== 'VIDEO') return;
                    if (!wantPlayNow()) return;
                    dbg('video pause', {
                        vw: t.videoWidth || 0,
                        rs: t.readyState,
                        ply: overlayClass().slice(0, 50),
                        w: Math.round(t.getBoundingClientRect().width || 0)
                    });
                } catch (_) {}
            }, true);
        } catch (_) {}
    }

    function warmDrm() {
        var p = (location.pathname || '').toLowerCase();
        if (p.indexOf('xplore') === -1 && (location.hostname || '').indexOf('xploretv') === -1) return;
        if (window._safeer_drm_warm_started) return;
        window._safeer_drm_warm_started = true;
        // Android 11 WebView has a single CDM: createMediaKeys() here holds it
        // so Castlabs cannot attach MediaKeys before the overlay times out (H283).
        // #region agent log
        dbg('drm warm', { ok: false, why: 'skip createMediaKeys' });
        // #endregion
    }

    var agent = {
        wantPlay: false,

        acceptOk: function () {
            var n = Date.now();
            if (lastOkAt && (n - lastOkAt) < 750) {
                dbg('dup ok swallowed', { dt: n - lastOkAt, path: (location.pathname || '').slice(0, 50) });
                return false;
            }
            lastOkAt = n;
            return true;
        },

        markWantPlay: function () {
            this.wantPlay = true;
            this._reopened = false;
            lastPly = '';
            lastOkAt = 0;
            keepN = 0;
            drmClickN = 0;
            window._safeer_xplore_want_play = true;
            window._safeer_xplore_playbtn_n = 0;
            window._safeer_xplore_skipbtn_logged = false;
            window._safeer_xplore_drm_stream = false;
            boostSkipLogged = false;
            window._safeer_app_bg = false;
            try { sessionStorage.removeItem('safeer_app_bg'); } catch (_) {}
            try {
                var pWant = (location.pathname || '').toLowerCase();
                if (pWant.indexOf('/livetv') === -1 && window._safeer_xplore_unsmash) window._safeer_xplore_unsmash();
            } catch (_) {}
            try { watchOverlay(); } catch (_) {}
            try { syncHoldCover('want'); } catch (_) {}
            try { bindVideoEme(); } catch (_) {}
            // #region agent log
            try {
                var v0 = videoEl();
                var r0 = v0 ? v0.getBoundingClientRect() : { width: 0, height: 0 };
                var cs0 = (v0 && window.getComputedStyle) ? window.getComputedStyle(v0) : null;
                dbg('want play', {
                    warm: !!window._safeer_drm_warm_ok,
                    warmS: !!window._safeer_drm_warm_started,
                    nV: document.querySelectorAll('video').length,
                    disp: v0 ? (v0.style.display || '') : '',
                    cdisp: cs0 ? (cs0.display || '') : '',
                    mk: !!(v0 && v0.mediaKeys),
                    vis: document.visibilityState || '',
                    w: Math.round(r0.width || 0),
                    vw: v0 ? (v0.videoWidth || 0) : 0
                });
            } catch (_) {}
            // #endregion
        },

        clearWant: function () {
            this.wantPlay = false;
            this._reopened = false;
            window._safeer_xplore_want_play = false;
            window._safeer_xplore_playing = false;
            window._safeer_xplore_video_boosted = false;
            window._safeer_xplore_playbtn_n = 0;
            window._safeer_xplore_fs_clicked = false;
            window._safeer_xplore_player_el = null;
            lastPly = '';
            lastOkAt = 0;
            keepN = 0;
            drmClickN = 0;
            window._safeer_xplore_drm_stream = false;
            try { document.documentElement.classList.remove('safeer-xplore-fs', 'safeer-xplore-hold'); } catch (_) {}
            try { if (window._safeer_xplore_unsmash) window._safeer_xplore_unsmash(); } catch (_) {}
            try {
                var ovZ = overlayEl();
                if (ovZ && !isFramed(videoEl())) {
                    ovZ.classList.remove('player-fullwindow', 'player-scaled');
                    ovZ.classList.add('player-closed');
                    // #region agent log
                    try { if (window._safeerDbg) window._safeerDbg('H276', 'site_agent.js:clear', 'close zombie overlay', {}); } catch (_) {}
                    // #endregion
                }
            } catch (_) {}
            try {
                var hid = document.getElementById('safeer-xplore-player-hide-ui');
                if (hid && hid.parentNode) hid.parentNode.removeChild(hid);
            } catch (_) {}
            try {
                var v = document.querySelector('video');
                if (v) {
                    try { v.pause(); } catch (_) {}
                    try { v.removeAttribute('style'); } catch (_) {}
                }
            } catch (_) {}
            try { syncHoldCover('clear'); } catch (_) {}
        },

        allowBoost: function () {
            var v = videoEl();
            var ok = isFramed(v);
            if (!ok && this.wantPlay && !boostSkipLogged) {
                boostSkipLogged = true;
                var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
                dbg('boost skipped empty', {
                    hasV: !!v,
                    w: Math.round(r.width || 0),
                    vw: v ? (v.videoWidth || 0) : 0,
                    rs: v ? v.readyState : -1,
                    ply: overlayClass().slice(0, 80)
                });
            }
            return ok;
        },

        allowPlayButtonClick: function () {
            var p = (location.pathname || '').toLowerCase();
            return p.indexOf('/event') !== -1;
        },

        allowDrmPlay: function () {
            if (isAppBg()) return false;
            if (!(this.wantPlay || window._safeer_xplore_want_play)) return false;
            var v = videoEl();
            if (!v || !v.paused) return false;
            return isFramed(v);
        },

        onPageReady: function () {
            var path = (location.pathname || '').toLowerCase();
            try { bindVideoEme(); } catch (_) {}
            try { warmDrm(); } catch (_) {}
            var v = videoEl();
            if (!v) {
                dbg('page ready', { path: path.slice(0, 60), hasV: false, href: (location.href || '').slice(0, 90) });
                watchOverlay();
                return;
            }
            var r = v.getBoundingClientRect();
            var framed = isFramed(v);
            var want = this.wantPlay || !!window._safeer_xplore_want_play;
            dbg('page ready', {
                path: path.slice(0, 60),
                w: Math.round(r.width || 0),
                h: Math.round(r.height || 0),
                vw: v.videoWidth || 0,
                rs: v.readyState,
                framed: framed,
                want: want
            });
            try { v.muted = false; v.volume = 1.0; } catch (_) {}
            if (!want && r.width >= 800 && !framed && path.indexOf('/livetv') === -1) {
                try {
                    v.style.setProperty('display', 'none', 'important');
                    v.style.setProperty('width', '0px', 'important');
                    v.style.setProperty('height', '0px', 'important');
                } catch (_) {}
                dbg('hid empty cover', { path: path.slice(0, 40) });
            }
            watchOverlay();
        },

        holdOverlay: function (reason) {
            if (isAppBg()) return;
            var v = videoEl() || document.querySelector('video');
            var ply = overlayClass();
            var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
            var didPlay = false;
            var framed = isFramed(v);
            var want = this.wantPlay || window._safeer_xplore_want_play;
            var streamDrm = !!window._safeer_xplore_drm_stream;
            var large = (r.width || 0) >= 800;
            var path = (location.pathname || '').toLowerCase();
            var onLivetv = path.indexOf('/livetv') !== -1;
            var canStart = framed;
            if (v && v.paused && want && large && !framed && !canStart) {
                // #region agent log
                try { if (window._safeerDbg) window._safeerDbg('H167', 'site_agent.js:hold', 'skip play empty', { w: Math.round(r.width || 0), vw: v.videoWidth || 0, rs: v.readyState, ply: ply.slice(0, 70), stream: !!streamDrm }); } catch (_) {}
                // #endregion
            }
            if (v && v.paused && want && overlayOpen() && canStart) {
                try { v.muted = false; v.volume = 1.0; } catch (_) {}
                try { v.play(); didPlay = true; } catch (_) {}
                // #region agent log
                try { if (window._safeerDbg) window._safeerDbg('H168', 'site_agent.js:hold', 'play framed', { stream: !!streamDrm, drmOk: !!window._safeer_xplore_drm_ok, livetv: onLivetv, vw: v.videoWidth || 0, w: Math.round(r.width || 0), ply: ply.slice(0, 70) }); } catch (_) {}
                // #endregion
            }
            dbg('hold overlay', {
                reason: reason,
                play: didPlay,
                stream: streamDrm,
                paused: v ? !!v.paused : true,
                w: Math.round(r.width || 0),
                vw: v ? (v.videoWidth || 0) : 0,
                rs: v ? v.readyState : -1,
                ply: ply.slice(0, 80)
            });
            try { syncHoldCover(reason || 'hold'); } catch (_) {}
        },

        onDrm: function () {
            var want = this.wantPlay || !!window._safeer_xplore_want_play;
            window._safeer_xplore_drm_at = Date.now();
            if (want) window._safeer_xplore_drm_stream = true;
            var v = videoEl();
            var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
            var play = this.allowDrmPlay();
            if (play && v) {
                try { v.muted = false; v.volume = 1.0; } catch (_) {}
                try { v.play(); } catch (_) {}
            }
            dbg('drm play', {
                hasV: !!v,
                play: play,
                want: want,
                stream: !!window._safeer_xplore_drm_stream,
                paused: v ? !!v.paused : true,
                rs: v ? v.readyState : -1,
                vw: v ? (v.videoWidth || 0) : 0,
                w: Math.round(r.width || 0),
                ply: overlayClass().slice(0, 80)
            });
            try { syncHoldCover('drm'); } catch (_) {}
            // #region agent log
            try {
                if (window._safeerDbg) {
                    window._safeerDbg('H269', 'site_agent.js:drm', 'no auto reclick', {
                        framed: isFramed(v),
                        open: overlayOpen(),
                        ply: overlayClass().slice(0, 50)
                    });
                }
            } catch (_) {}
            // #endregion
        }
    };

    function watchOverlay() {
        if (window._safeer_agent_overlay_mo) return;
        var el = overlayEl();
        if (!el) return;
        try {
            window._safeer_agent_overlay_mo = new MutationObserver(function () {
                try {
                    var ply = overlayClass();
                    if (ply === lastPly) return;
                    lastPly = ply;
                    var v = videoEl();
                    var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
                    var low = ply.toLowerCase();
                    var cs = (v && window.getComputedStyle) ? window.getComputedStyle(v) : null;
                    var ae = document.activeElement;
                    var foc = document.querySelector('.safeer-active-card');
                    dbg('overlay class', {
                        ply: ply.slice(0, 90),
                        want: !!(agent.wantPlay || window._safeer_xplore_want_play),
                        w: Math.round(r.width || 0),
                        vw: v ? (v.videoWidth || 0) : 0,
                        paused: v ? !!v.paused : true,
                        disp: v ? (v.style.display || '') : '',
                        cdisp: cs ? (cs.display || '') : '',
                        mk: !!(v && v.mediaKeys),
                        warm: !!window._safeer_drm_warm_ok,
                        vis: document.visibilityState || '',
                        ae: ae ? ((ae.tagName || '') + '.' + ((ae.className || '') + '').toString().slice(0, 40)) : '',
                        focus: foc ? ((foc.className || '') + '').toString().slice(0, 50) : '',
                        encN: window._safeer_eme_enc_n || 0
                    });
                    if ((agent.wantPlay || window._safeer_xplore_want_play) &&
                        (low.indexOf('player-fullwindow') !== -1 || low.indexOf('player-scaled') !== -1)) {
                        try { document.documentElement.classList.add('safeer-xplore-waitplay'); } catch (_) {}
                    }
                    if (low.indexOf('player-closed') !== -1) {
                        try { document.documentElement.classList.remove('safeer-xplore-playing', 'safeer-xplore-waitplay'); } catch (_) {}
                        try {
                            if (!isFramed(v)) {
                                var lastT = window._safeer_xplore_last_tile;
                                if (lastT && lastT.isConnected) lastT.classList.add('safeer-active-card');
                            }
                        } catch (_) {}
                        reopenTile('class');
                    }
                    try { syncHoldCover('class'); } catch (_) {}
                } catch (_) {}
            });
            window._safeer_agent_overlay_mo.observe(el, {
                attributes: true,
                attributeFilter: ['class']
            });
        } catch (_) {}
    }

    window._safeerSiteAgent = agent;
    try { bindVideoEme(); } catch (_) {}
    try {
        document.addEventListener('visibilitychange', function () {
            if (document.hidden || document.visibilityState === 'hidden') {
                window._safeer_app_bg = true;
                try { sessionStorage.setItem('safeer_app_bg', '1'); } catch (_) {}
            try { agent.clearWant(); } catch (_) {}
            var n = 0;
            try {
                document.querySelectorAll('video,audio').forEach(function (m) {
                    try { m.pause(); m.muted = true; m.volume = 0; n++; } catch (_) {}
                });
            } catch (_) {}
            // #region agent log
            try {
                if (window._safeerDbg) {
                    window._safeerDbg('H212', 'site_agent.js', 'hidden', {
                        n: n,
                        vis: document.visibilityState,
                        path: (location.pathname || '').slice(0, 40)
                    });
                }
            } catch (_) {}
            // #endregion
            }
        }, true);
    } catch (_) {}
    try {
        document.addEventListener('DOMContentLoaded', function () { agent.onPageReady(); }, true);
    } catch (_) {}
    try {
        document.addEventListener('loadedmetadata', function () { syncHoldCover('meta'); }, true);
        document.addEventListener('canplay', function () { syncHoldCover('canplay'); }, true);
    } catch (_) {}
    try { watchOverlay(); } catch (_) {}
})();
