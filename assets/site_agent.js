(function () {
    if (window._safeerSiteAgent) return;
    var _host = ((location.hostname || '') + '').toLowerCase();
    if (_host.indexOf('xploretv') !== -1 || _host.indexOf('a1xploretv') !== -1) {
        window._safeerSiteAgent = {
            wantPlay: false,
            acceptOk: function () { return true; },
            markWantPlay: function () {},
            clearWant: function () {},
            allowBoost: function () { return false; },
            onPageReady: function () {}
        };
        return;
    }

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
            try { if (window._safeer_xplore_unsmash) window._safeer_xplore_unsmash(); } catch (_) {}
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
            if (!want && r.width >= 800 && !framed) {
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
                        try {
                            var foc2 = document.querySelector('.safeer-active-card');
                            if (foc2) foc2.classList.remove('safeer-active-card');
                            if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
                            var ring2 = document.getElementById('safeer-focus-target-ring');
                            if (ring2) { ring2.classList.remove('active'); ring2.style.display = 'none'; }
                            // #region agent log
                            dbg('blur tile overlay', { ply: ply.slice(0, 50), had: !!foc2 });
                            // #endregion
                        } catch (_) {}
                        agent.holdOverlay('class');
                    }
                    if (low.indexOf('player-closed') !== -1) {
                        try { document.documentElement.classList.remove('safeer-xplore-playing'); } catch (_) {}
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
