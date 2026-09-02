/* Xplore livetv seed helpers, boost/nudge, BACK CSS, kiosk — after tv_spatial.js */
(function() {
    if ((location.hostname || '').indexOf('xploretv') === -1) return;
    if (!window._safeer_xplore_release_cdm) {
        window._safeer_xplore_release_cdm = function() {
            var released = false;
            try {
                document.querySelectorAll('video,audio').forEach(function(m) {
                    try { m.pause(); m.muted = true; m.volume = 0; } catch (e0) {}
                    try {
                        if (m.mediaKeys) {
                            m.setMediaKeys(null);
                            released = true;
                        }
                    } catch (e1) {}
                    try {
                        m.removeAttribute('src');
                        while (m.firstChild) m.removeChild(m.firstChild);
                        m.load();
                    } catch (e2) {}
                });
            } catch (e3) {}
            return released;
        };
    }
    window._safeer_xplore_report_drm_cfg = function() {
        function isLic(s) {
            if (typeof s !== 'string' || s.indexOf('http') !== 0) return false;
            var u = s.toLowerCase();
            return u.indexOf('drmtoday') !== -1 || u.indexOf('license-proxy') !== -1 ||
                u.indexOf('widevine') !== -1 || u.indexOf('/license') !== -1 ||
                u.indexOf('expressplay') !== -1 || u.indexOf('licensing') !== -1;
        }
        function isHdrKey(k) {
            var n = String(k || '').toLowerCase();
            return n.indexOf('dt-custom') !== -1 || n.indexOf('x-dt') !== -1 ||
                n === 'authorization' || n.indexOf('custom-data') !== -1;
        }
        var headers = {};
        var url = '';
        function walk(o, d) {
            if (!o || d > 6) return;
            if (isLic(o) && !url) url = o;
            if (typeof o !== 'object') return;
            try {
                var keys = Object.keys(o);
                var i, k;
                for (i = 0; i < keys.length && i < 64; i++) {
                    k = keys[i];
                    if (isHdrKey(k) && typeof o[k] === 'string' && o[k] && !headers[k]) headers[k] = o[k];
                    if (isLic(o[k]) && !url) url = o[k];
                    if (/license|drm|widevine|laurl|cenc|presto|licensing|custom.?data/i.test(k)) {
                        walk(o[k], d + 1);
                    }
                }
            } catch (_) {}
        }
        try { walk(window.clpp, 0); } catch (_) {}
        try { walk(window.prestoplay, 0); } catch (_) {}
        try { walk(window.clppConfig, 0); } catch (_) {}
        try { if (window.player) walk(window.player, 0); } catch (_) {}
        try {
            if ((url || Object.keys(headers).length) && window.SafeerBridge && window.SafeerBridge.onXploreMedia) {
                window.SafeerBridge.onXploreMedia(url || '', 'GET', JSON.stringify(headers), 'cfg');
            }
        } catch (_) {}
        return url ? 1 : 0;
    };
    if (!window._safeer_xplore_capture_hook) {
        window._safeer_xplore_capture_hook = true;
        function safeerReportMedia(url, method, headers, kind) {
            try {
                if (window.SafeerBridge && window.SafeerBridge.onXploreMedia) {
                    window.SafeerBridge.onXploreMedia(String(url || ''), String(method || 'GET'), JSON.stringify(headers || {}), String(kind || ''));
                }
            } catch (_) {}
        }
        function safeerIsDash(url) {
            var u = (url || '').toLowerCase();
            return u.indexOf('.mpd') !== -1;
        }
        function safeerIsLicense(url, method) {
            var u = (url || '').toLowerCase();
            var m = String(method || 'GET').toUpperCase();
            if (u.indexOf('.mpd') !== -1 || u.indexOf('.m4s') !== -1 || u.indexOf('.mp4') !== -1 || u.indexOf('.js') !== -1) return false;
            var post = m === 'POST';
            if (u.indexOf('drmtoday') !== -1 || u.indexOf('license-proxy') !== -1 || u.indexOf('expressplay') !== -1 || u.indexOf('widevine') !== -1) {
                return post || u.indexOf('license') !== -1 || u.indexOf('/drm/') !== -1 || u.indexOf('/widevine') !== -1;
            }
            return post && (u.indexOf('license') !== -1 || u.indexOf('/drm/') !== -1 || u.indexOf('cenc') !== -1);
        }
        function safeerHasDt(headers) {
            try {
                for (var k in (headers || {})) {
                    var n = String(k).toLowerCase();
                    if (n.indexOf('dt-') !== -1 || n.indexOf('custom-data') !== -1 || n === 'authorization') return true;
                }
            } catch (_) {}
            return false;
        }
        function safeerIsOctetLicense(method, headers) {
            if (String(method || 'GET').toUpperCase() !== 'POST') return false;
            try {
                for (var k in (headers || {})) {
                    if (String(k).toLowerCase() === 'content-type') {
                        return String(headers[k] || '').toLowerCase().indexOf('octet-stream') !== -1;
                    }
                }
            } catch (_) {}
            return false;
        }
        function headerObj(h) {
            var o = {};
            try {
                if (!h) return o;
                if (typeof h.forEach === 'function') { h.forEach(function(v, k){ o[k] = v; }); return o; }
                for (var k in h) if (Object.prototype.hasOwnProperty.call(h, k)) o[k] = h[k];
            } catch (_) {}
            return o;
        }
        function kindFromBody(body) {
            if (!body || typeof body !== 'string') return '';
            var t = String(body).replace(/^\s+/, '');
            if (t.charAt(0) !== '{') return 'raw';
            try {
                var p = JSON.parse(t);
                var keys = Object.keys(p || {});
                return 'json:' + keys.slice(0, 6).join(',');
            } catch (_) { return 'json'; }
        }
        try {
            var ofetch = window.fetch;
            if (typeof ofetch === 'function') {
                window.fetch = function(input, init) {
                    try {
                        var url = typeof input === 'string' ? input : (input && input.url) || '';
                        var method = (init && init.method) || (input && input.method) || 'GET';
                        var headers = headerObj((init && init.headers) || (input && input.headers));
                        if (safeerIsDash(url) || safeerIsLicense(url, method) || safeerIsOctetLicense(method, headers) || safeerHasDt(headers)) {
                            var body = init && init.body;
                            safeerReportMedia(url, method, headers, kindFromBody(typeof body === 'string' ? body : ''));
                        }
                    } catch (_) {}
                    return ofetch.apply(this, arguments);
                };
            }
        } catch (_) {}
        try {
            var XO = window.XMLHttpRequest;
            if (XO && XO.prototype) {
                var open0 = XO.prototype.open;
                var send0 = XO.prototype.send;
                var setH0 = XO.prototype.setRequestHeader;
                XO.prototype.open = function(method, url) {
                    this._safeer_m = method; this._safeer_u = url; this._safeer_h = {};
                    return open0.apply(this, arguments);
                };
                XO.prototype.setRequestHeader = function(k, v) {
                    try { if (!this._safeer_h) this._safeer_h = {}; this._safeer_h[k] = v; } catch (_) {}
                    return setH0.apply(this, arguments);
                };
                XO.prototype.send = function(body) {
                    try {
                        var url = this._safeer_u || '';
                        if (safeerIsDash(url) || safeerIsLicense(url, this._safeer_m || 'GET') || safeerIsOctetLicense(this._safeer_m || 'GET', this._safeer_h || {}) || safeerHasDt(this._safeer_h || {})) {
                            safeerReportMedia(url, this._safeer_m || 'GET', this._safeer_h || {}, kindFromBody(typeof body === 'string' ? body : ''));
                        }
                    } catch (_) {}
                    return send0.apply(this, arguments);
                };
            }
        } catch (_) {}
    }
    try { window._safeer_xplore_report_drm_cfg(); } catch (_) {}
    function clearActive() {
        try {
            var prevs = document.querySelectorAll('.safeer-active-card');
            for (var i = 0; i < prevs.length; i++) prevs[i].classList.remove('safeer-active-card');
            var ring = document.getElementById('safeer-focus-target-ring');
            if (ring) { ring.classList.remove('active'); ring.style.display = 'none'; }
        } catch (_) {}
    }
            // 6. 📡 A1 Xplore TV: prijava, gumb Glej oddajo od začetka, celozaslonski predvajalnik
            if ((location.hostname || '').indexOf('xploretv') !== -1 && !window._safeer_xplore_helpers_ready) {
                window._safeer_xplore_helpers_ready = true;
                try {
                    document.documentElement.classList.add('safeer-xplore-dark');
                    var vpH = document.querySelector('meta[name="viewport"]');
                    if (!vpH) {
                        vpH = document.createElement('meta');
                        vpH.setAttribute('name', 'viewport');
                        (document.head || document.documentElement).appendChild(vpH);
                    }
                    vpH.setAttribute('content', 'width=1920, initial-scale=1');
                    if (!document.getElementById('tv-remote-xplore-layout-fix')) {
                        var xploreFixH = document.createElement('style');
                        xploreFixH.id = 'tv-remote-xplore-layout-fix';
                        xploreFixH.textContent = 'html.safeer-xplore-fs,html.safeer-xplore-fs body{overflow:hidden!important;background:#000!important;}' +
                            'html.safeer-xplore-fs video{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483646!important;object-fit:contain!important;background:#000!important;opacity:1!important;visibility:visible!important;display:block!important;}' +
                            '.livetv-griditem,[class*="livetv-griditem"],.livetv-griditem .item.item--event,.item.item--event,.item--event,[class*="content-carousel__item"]{overflow:visible!important;}' +
                            '.item.item--event.safeer-active-card,.item--event.safeer-active-card,[class*="content-carousel__item"].safeer-active-card{outline:6px solid #00e5ff!important;outline-offset:7px!important;box-shadow:0 0 0 4px #000,0 0 36px #00e5ff!important;border-radius:12px!important;z-index:auto!important;}' +
                            '[class*="content-carousel__item"].safeer-active-card{transform:scale(1.06)!important;}' +
                            '.item.item--event.safeer-active-card,.item--event.safeer-active-card{transform:none!important;}' +
                            'html.safeer-xplore-fs #safeer-focus-target-ring{display:none!important;opacity:0!important;}' +
                            'html.safeer-xplore-fs .safeer-active-card{box-shadow:none!important;outline:none!important;transform:none!important;}' +
                            '.action-list.safeer-active-card,[class*="action-list"].safeer-active-card,.promo-carousel__button.safeer-active-card{outline:6px solid #00e5ff!important;outline-offset:7px!important;box-shadow:0 0 0 3px #000,0 0 32px #00e5ff!important;}' +
                            '#safeer-focus-target-ring .safeer-focus-badge{display:none!important;}' +
                            '#safeer-focus-target-ring{border:6px solid #00e5ff!important;outline:3px solid #fff!important;outline-offset:-9px!important;border-radius:14px!important;box-shadow:0 0 0 3px #000,0 0 38px #00e5ff!important;background:transparent!important;z-index:50000!important;}' +
                            '.zw-overlays-layer.player-fullwindow,.zw-overlays-layer.player-scaled,[class*="overlays-layer"].player-fullwindow,[class*="overlays-layer"].player-scaled{z-index:100000!important;visibility:visible!important;opacity:1!important;}' +
                            'html.safeer-xplore-waitplay #safeer-focus-target-ring,html.safeer-xplore-playing #safeer-focus-target-ring{display:none!important;opacity:0!important;}' +
                            '.menu,.menu--opaque,.menu--fixed,.menu.gradient-bg-white{background:#0b0e14!important;background-image:none!important;color:#f1f5f9!important;min-height:76px!important;}' +
                            '.menu a,#csh__menu_bar a,.menu .dropdown-toggle-button{display:inline-flex!important;align-items:center!important;gap:8px!important;min-height:52px!important;padding:8px 14px!important;font-size:20px!important;font-weight:700!important;color:#f1f5f9!important;white-space:nowrap!important;opacity:1!important;visibility:visible!important;}' +
                            '#csh__menu_bar a span,.menu a span{display:inline!important;opacity:1!important;visibility:visible!important;position:static!important;width:auto!important;font-size:20px!important;font-weight:700!important;color:#f1f5f9!important;clip:auto!important;overflow:visible!important;}' +
                            '.home-link.route--active,.livetv-link.route--active,.movies-link.route--active,.library-link.route--active,.guide-link.route--active{background:#1e293b!important;border-radius:10px!important;box-shadow:inset 0 -3px 0 #e10600!important;}';
                        (document.head || document.documentElement).appendChild(xploreFixH);
                    }
                    if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) window.SafeerBridge.setChromeHidden(true);
                } catch (_) {}
                function dismissXploreTutorials() {
                    try {
                        var joyrideClose = document.querySelector('.joyride-tooltip__close, [aria-label*="close"], [class*="close-button"], button[aria-label*="Zapri"], button[aria-label*="Close"]');
                        if (joyrideClose) joyrideClose.click();
                    } catch(_) {}
                }

                function setReactInputValue(input, val) {
                    if (!input) return;
                    try {
                        input.focus();
                        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        nativeInputValueSetter.call(input, val);
                    } catch(e) {
                        input.value = val;
                    }
                    try {
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'a' }));
                        input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'a' }));
                    } catch(_) {}
                }

                var xploreLoginAttempted = false;
                function findUserInput() {
                    return document.querySelector(
                        'input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], ' +
                        'input[id*="user"], input[id*="login"], input[placeholder*="ime"], ' +
                        'input[placeholder*="Ime"], input[autocomplete="username"]'
                    );
                }
                function findPassInput() {
                    return document.querySelector(
                        'input[type="password"], input[name*="pass"], input[id*="pass"], ' +
                        'input[placeholder*="geslo"], input[placeholder*="Geslo"], input[autocomplete="current-password"]'
                    );
                }
                function runXploreAuth() {
                    dismissXploreTutorials();
                    var path = (location.pathname || '').toLowerCase();
                    var onLogin = path.indexOf('login') !== -1 || path.indexOf('prijava') !== -1 || !!findPassInput();
                    if (!onLogin) {
                        window._safeerXploreLoginAttempted = false;
                        return;
                    }
                    if (window._safeerXploreLoginAttempted) return;
                    if (typeof window._safeerXploreAuth === 'undefined') return;

                    var uInput = findUserInput();
                    var pInput = findPassInput();
                    if (!uInput && !pInput) return;
                    var submitBtn = document.querySelector('button[type="submit"], input[type="submit"], .btn-primary');
                    if (!submitBtn) {
                        var btns = document.querySelectorAll('button, a.btn, [role="button"]');
                        for (var j = 0; j < btns.length; j++) {
                            var btxt = (btns[j].textContent || btns[j].value || '').trim().toLowerCase();
                            if (btxt.indexOf('prijava') !== -1 || btxt.indexOf('prijavi') !== -1 ||
                                btxt.indexOf('login') !== -1 || btxt.indexOf('vstop') !== -1 || btxt.indexOf('sign in') !== -1) {
                                submitBtn = btns[j];
                                break;
                            }
                        }
                    }
                    var auth = window._safeerXploreAuth;
                    var user = auth && (auth.user || auth.username || '');
                    var pass = auth && (auth.pass || auth.password || '');
                    if (user && pass && uInput && pInput) {
                        try {
                            var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                            nativeSetter.call(uInput, user);
                            nativeSetter.call(pInput, pass);
                            uInput.dispatchEvent(new Event('input', { bubbles: true }));
                            pInput.dispatchEvent(new Event('input', { bubbles: true }));
                        } catch (_) {}
                        window._safeerXploreLoginAttempted = true;
                        if (submitBtn) {
                            setTimeout(function() {
                                try { submitBtn.click(); } catch (_) {}
                            }, 500);
                        }
                        return;
                    }
                    window._safeerXploreLoginAttempted = true;
                }

                function findXplorePlayFromStart() {
                    var needles = [
                        { k: 'predvajaj v živo', p: 0 },
                        { k: 'predvajaj v zivo', p: 0 },
                        { k: 'glej zdaj', p: 0 },
                        { k: 'na začetek', p: 2 },
                        { k: 'na zacetek', p: 2 },
                        { k: 'od začetka', p: 2 },
                        { k: 'od zacetka', p: 2 },
                        { k: 'glej oddajo od začetka', p: 2 },
                        { k: 'glej oddajo od zacetka', p: 2 },
                        { k: 'glej oddajo', p: 1 },
                        { k: 'watch now', p: 0 },
                        { k: 'predvajaj', p: 3 }
                    ];
                    var nodes = document.querySelectorAll('button, a, [role="button"], [class*="action-list"], li.action-list, .action-list span');
                    var best = null;
                    var bestPri = 99;
                    var bestArea = 999999999;
                    var i, el, t, r, area, n, pri;
                    for (i = 0; i < nodes.length; i++) {
                        el = nodes[i];
                        t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (!t || t.length < 3 || t.length > 64) continue;
                        if (t.indexOf('posnemi') !== -1 || t.indexOf('priljubljene') !== -1) continue;
                        pri = 99;
                        for (n = 0; n < needles.length; n++) {
                            if (t.indexOf(needles[n].k) !== -1 && needles[n].p < pri) pri = needles[n].p;
                        }
                        if (pri === 99) continue;
                        r = el.getBoundingClientRect();
                        if (r.width < 10 || r.height < 10) continue;
                        area = r.width * r.height;
                        if (pri < bestPri || (pri === bestPri && area < bestArea)) {
                            bestPri = pri;
                            bestArea = area;
                            best = el;
                        }
                    }
                    return best;
                }

                function xploreAppBg() {
                    try {
                        return !!(window._safeer_app_bg || document.hidden || sessionStorage.getItem('safeer_app_bg') === '1');
                    } catch (_) {
                        return !!(window._safeer_app_bg || document.hidden);
                    }
                }

                function keepXploreAudio() {
                    if (xploreAppBg()) return;
                    try {
                        var vids = document.querySelectorAll('video');
                        for (var i = 0; i < vids.length; i++) {
                            vids[i].muted = false;
                            vids[i].volume = 1.0;
                        }
                    } catch(_) {}
                }

                function collectXploreVideos() {
                    var out = [];
                    function grab(root, src) {
                        if (!root) return;
                        var vs = root.querySelectorAll('video');
                        var i, v, r;
                        for (i = 0; i < vs.length; i++) {
                            v = vs[i];
                            r = v.getBoundingClientRect();
                            out.push({
                                src: src,
                                rs: v.readyState,
                                dur: v.duration || 0,
                                vw: v.videoWidth || 0,
                                vh: v.videoHeight || 0,
                                paused: !!v.paused,
                                w: Math.round(r.width),
                                h: Math.round(r.height),
                                t: Math.round(r.top),
                                l: Math.round(r.left)
                            });
                        }
                    }
                    grab(document, 'main');
                    var frames = document.querySelectorAll('iframe');
                    var fi;
                    for (fi = 0; fi < frames.length; fi++) {
                        try {
                            if (frames[fi].contentDocument) grab(frames[fi].contentDocument, 'iframe' + fi);
                        } catch (_) {}
                    }
                    return { iframes: frames.length, videos: out };
                }

                function xploreLivetvPath() {
                    return (location.pathname || '').toLowerCase().indexOf('/livetv') !== -1;
                }

                function xploreOverlayOn() {
                    try {
                        var ov = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                        var oc = ov ? ((ov.className || '') + '').toLowerCase() : '';
                        return oc.indexOf('player-fullwindow') !== -1 || oc.indexOf('player-scaled') !== -1;
                    } catch (_) {}
                    return false;
                }

                function xploreVideoEl() {
                    var vids = document.querySelectorAll('video');
                    var i, v, framed = null, last = null;
                    for (i = 0; i < vids.length; i++) {
                        v = vids[i];
                        last = v;
                        if ((v.videoWidth || 0) >= 320 && v.readyState >= 2) framed = v;
                    }
                    return framed || last || window._safeer_xplore_player_el || null;
                }

                function resumeXploreIfPaused() {
                    try {
                        if (xploreAppBg()) return false;
                        if (!window._safeer_xplore_want_play) return false;
                        var v = xploreVideoEl();
                        if (!v || !v.paused) return false;
                        if ((v.videoWidth || 0) < 320 || v.readyState < 2) return false;
                        if (xploreLivetvPath() && !xploreOverlayOn()) return false;
                        var r = v.getBoundingClientRect();
                        try { v.muted = false; v.volume = 1.0; } catch (_) {}
                        try { v.play(); } catch (_) {}
                        window._safeer_xplore_player_el = v;
                        if (!xploreLivetvPath() && (r.width < 800 || r.height < 450)) {
                            try { applyXploreVideoFullscreen(v); } catch (_) {}
                        }
                        // #region agent log
                        try {
                            window._safeerDbg('H94', 'tv_remote_nav.js:resume', 'unpause', {
                                rs: v.readyState,
                                vw: v.videoWidth || 0,
                                w: Math.round(r.width || 0),
                                ct: v.currentTime,
                                stillPaused: !!v.paused
                            });
                        } catch (_) {}
                        // #endregion
                        return true;
                    } catch (_) {}
                    return false;
                }

                function hideXplorePlayerChrome() {
                    window._safeer_xplore_playing = true;
                    try { clearActive(); } catch(_) {}
                    var ring = document.getElementById('safeer-focus-target-ring');
                    if (ring) {
                        ring.classList.remove('active');
                        ring.style.display = 'none';
                    }
                    try {
                        if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) {
                            window.SafeerBridge.setChromeHidden(true);
                        }
                    } catch(_) {}
                    try {
                        if (!document.getElementById('safeer-xplore-player-hide-ui')) {
                            var st = document.createElement('style');
                            st.id = 'safeer-xplore-player-hide-ui';
                            st.textContent = '.clpp-control-bar,.clpp-controls,.clpp-ui .clpp-button,.clpp-fullscreen{opacity:0!important;visibility:hidden!important;pointer-events:none!important;}';
                            (document.documentElement || document.head).appendChild(st);
                        }
                    } catch(_) {}
                        try {
                            var pv = window._safeer_xplore_player_el || document.querySelector('video');
                            if (pv && pv.paused && (pv.videoWidth || 0) >= 320 && pv.readyState >= 2) pv.play();
                        } catch(_) {}
                    // #region agent log
                    try { window._safeerDbg('H6', 'UserScriptManager.kt:boost', 'overlays hidden', { playing: true }); } catch (_) {}
                    // #endregion
                }

                function markSmashEl(el) {
                    try { if (el && el.setAttribute) el.setAttribute('data-safeer-smash', '1'); } catch (_) {}
                }
                function unsmashXplorePlayer() {
                    try { document.documentElement.classList.remove('safeer-xplore-fs', 'safeer-xplore-playing', 'safeer-xplore-hold', 'safeer-xplore-waitplay'); } catch (_) {}
                    try {
                        var smashed = document.querySelectorAll('[data-safeer-smash]');
                        var props = ['position', 'left', 'top', 'width', 'height', 'max-width', 'max-height', 'z-index', 'object-fit', 'background', 'opacity', 'visibility', 'display', 'transform', 'filter', 'overflow'];
                        for (var i = 0; i < smashed.length; i++) {
                            var el = smashed[i];
                            try { el.removeAttribute('data-safeer-smash'); } catch (_) {}
                            for (var p = 0; p < props.length; p++) {
                                try { el.style.removeProperty(props[p]); } catch (_) {}
                            }
                            try {
                                if (el.tagName === 'VIDEO') {
                                    el.removeAttribute('width');
                                    el.removeAttribute('height');
                                }
                            } catch (_) {}
                        }
                    } catch (_) {}
                    try {
                        var hid = document.getElementById('safeer-xplore-player-hide-ui');
                        if (hid && hid.parentNode) hid.parentNode.removeChild(hid);
                    } catch (_) {}
                    try {
                        if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) {
                            var onXplore = (location.hostname || '').indexOf('xploretv') !== -1;
                            window.SafeerBridge.setChromeHidden(!!onXplore);
                            // #region agent log
                            try { window._safeerDbg('H293', 'tv_remote_nav.js:unsmash', 'chrome kiosk', { onXplore: !!onXplore, hidden: !!onXplore }); } catch (_) {}
                            // #endregion
                        }
                    } catch (_) {}
                }
                window._safeer_xplore_unsmash = unsmashXplorePlayer;

                function applyXploreVideoFullscreen(v) {
                    if (!v) return;
                    if (window._safeer_xplore_native_player) return;
                    // Livetv: Castlabs already goes fullscreen. Smash/FS CSS on a
                    // still-opening overlay tears the player (BUFFERING -> PAUSED).
                    if (xploreLivetvPath()) return;
                    try { document.documentElement.classList.add('safeer-xplore-fs'); } catch (_) {}
                    try {
                        v.width = Math.max(v.videoWidth || 0, 1920);
                        v.height = Math.max(v.videoHeight || 0, 1080);
                    } catch (_) {}
                    markSmashEl(v);
                    try {
                        v.style.setProperty('position', 'fixed', 'important');
                        v.style.setProperty('left', '0px', 'important');
                        v.style.setProperty('top', '0px', 'important');
                        v.style.setProperty('width', '100vw', 'important');
                        v.style.setProperty('height', '100vh', 'important');
                        v.style.setProperty('max-width', 'none', 'important');
                        v.style.setProperty('max-height', 'none', 'important');
                        v.style.setProperty('z-index', '2147483646', 'important');
                        v.style.setProperty('object-fit', 'contain', 'important');
                        v.style.setProperty('background', '#000', 'important');
                        v.style.setProperty('opacity', '1', 'important');
                        v.style.setProperty('visibility', 'visible', 'important');
                        v.style.setProperty('display', 'block', 'important');
                        v.style.setProperty('transform', 'none', 'important');
                    } catch (_) {}
                    try {
                        var el = v.parentElement;
                        var n = 0;
                        while (el && n < 10 && el !== document.body && el !== document.documentElement) {
                            markSmashEl(el);
                            el.style.setProperty('transform', 'none', 'important');
                            el.style.setProperty('filter', 'none', 'important');
                            el.style.setProperty('opacity', '1', 'important');
                            el.style.setProperty('visibility', 'visible', 'important');
                            el.style.setProperty('display', 'block', 'important');
                            el.style.setProperty('overflow', 'visible', 'important');
                            el.style.setProperty('width', '100vw', 'important');
                            el.style.setProperty('height', '100vh', 'important');
                            el.style.setProperty('max-width', 'none', 'important');
                            el.style.setProperty('max-height', 'none', 'important');
                            el.style.setProperty('position', 'fixed', 'important');
                            el.style.setProperty('left', '0px', 'important');
                            el.style.setProperty('top', '0px', 'important');
                            el.style.setProperty('z-index', String(2147483645 - n), 'important');
                            el = el.parentElement;
                            n++;
                        }
                    } catch (_) {}
                }

                function maybeHideWhenReady(v) {
                    try {
                        if (!v || !window._safeer_xplore_want_play) return;
                        var hasFrames = (v.videoWidth || 0) >= 320 && v.readyState >= 2;
                        if (!hasFrames) return;
                        var r = v.getBoundingClientRect();
                        if (xploreLivetvPath()) {
                            if (v.paused) {
                                try { v.muted = false; v.volume = 1.0; } catch (_) {}
                                try { v.play(); } catch (_) {}
                            }
                            window._safeer_xplore_player_el = v;
                            if (r.width >= 800 && r.height >= 450) {
                                window._safeer_xplore_video_boosted = true;
                                hideXplorePlayerChrome();
                            }
                            return;
                        }
                        if (r.width < 800 || r.height < 450) {
                            applyXploreVideoFullscreen(v);
                            if (!window._safeer_xplore_fs_clicked) {
                                var fsBtn = document.querySelector('.clpp-fullscreen, [class*="clpp"] [class*="fullscreen"], button[class*="fullscreen"]');
                                if (fsBtn) {
                                    window._safeer_xplore_fs_clicked = true;
                                    try { fsBtn.click(); } catch (_) {}
                                    // #region agent log
                                    try { window._safeerDbg('H28', 'tv_remote_nav.js:maybeHide', 'fs click after 0x0', { found: true }); } catch (_) {}
                                    // #endregion
                                }
                            }
                            window._safeer_xplore_player_el = v;
                            // #region agent log
                            try { window._safeerDbg('H71', 'tv_remote_nav.js:boost', 'recover 0x0', { w0: Math.round(r.width || 0), h0: Math.round(r.height || 0), vw: v.videoWidth || 0, rs: v.readyState }); } catch (_) {}
                            // #endregion
                        }
                        var r2 = v.getBoundingClientRect();
                        var large = r2.width >= 800 && r2.height >= 450;
                        if (large) {
                            window._safeer_xplore_player_el = v;
                            window._safeer_xplore_video_boosted = true;
                            hideXplorePlayerChrome();
                        }
                        // #region agent log
                        try {
                            window._safeerDbg('H16', 'UserScriptManager.kt:boost', 'maybe hide', {
                                w: Math.round(r.width || 0),
                                w1: Math.round(r2.width || 0),
                                vw: v.videoWidth || 0,
                                rs: v.readyState,
                                hid: !!large
                            });
                            window._safeerDbg('H32', 'UserScriptManager.kt:boost', 'inline fs applied', {
                                w0: Math.round(r.width || 0),
                                w1: Math.round(r2.width || 0),
                                h1: Math.round(r2.height || 0),
                                vw: v.videoWidth || 0,
                                hasFs: document.documentElement.classList.contains('safeer-xplore-fs')
                            });
                        } catch (_) {}
                        // #endregion
                    } catch (_) {}
                }

                function nudgeXplorePlayer() {
                    try {
                        if (xploreAppBg()) return;
                        if (!window._safeer_xplore_want_play) return;
                        if (window._safeer_xplore_playing) {
                            resumeXploreIfPaused();
                            return;
                        }
                        var v = window._safeer_xplore_player_el;
                        if (!v || !v.parentNode) {
                            var vids = document.querySelectorAll('video');
                            var i, cand = null, cr;
                            for (i = 0; i < vids.length; i++) {
                                cr = vids[i].getBoundingClientRect();
                                if ((cr.width >= 800 && cr.height >= 450) || ((vids[i].videoWidth || 0) >= 320 && vids[i].readyState >= 2)) {
                                    cand = vids[i];
                                    break;
                                }
                            }
                            if (cand) {
                                v = cand;
                                window._safeer_xplore_player_el = v;
                            }
                        }
                        if (!v) return;
                        v.muted = false;
                        v.volume = 1.0;
                        if (v.paused && (v.videoWidth || 0) >= 320 && v.readyState >= 2) {
                            try { v.play(); } catch(_) {}
                        }
                        var vr = v.getBoundingClientRect();
                        // #region agent log
                        try {
                            window._safeerDbgNudgeN = (window._safeerDbgNudgeN || 0) + 1;
                            if (window._safeerDbgNudgeN <= 6) {
                                window._safeerDbg('H25', 'UserScriptManager.kt:nudge', 'catchup nudge', {
                                    rs: v.readyState,
                                    vw: v.videoWidth || 0,
                                    w: Math.round(vr.width),
                                    paused: !!v.paused
                                });
                            }
                        } catch (_) {}
                        // #endregion
                        maybeHideWhenReady(v);
                    } catch (_) {}
                }

                function boostXploreVideoOnce() {
                    if (xploreAppBg()) return;
                    if (!window._safeer_xplore_want_play) return;
                    if (window._safeer_xplore_video_boosted) return;
                    try {
                        var snap = collectXploreVideos();
                        // #region agent log
                        try {
                            window._safeerDbgBoostN = (window._safeerDbgBoostN || 0) + 1;
                            if (window._safeerDbgBoostN <= 4) {
                                window._safeerDbg('H3', 'UserScriptManager.kt:boost', 'video inventory', snap);
                            }
                        } catch (_) {}
                        // #endregion
                        var vids = document.querySelectorAll('video');
                        var i, v, best = null, bestScore = -1, r, traps = 0, framed = false, bestFramed = false;
                        for (i = 0; i < vids.length; i++) {
                            v = vids[i];
                            r = v.getBoundingClientRect();
                            var area = (r.width || 0) * (r.height || 0);
                            framed = (v.videoWidth || 0) >= 320 && v.readyState >= 2;
                            var score = framed ? Math.max(area, 1920 * 1080) : area;
                            if (score >= bestScore) {
                                bestScore = score;
                                best = v;
                                bestFramed = framed;
                            }
                        }
                        if (!best || !bestFramed) return;
                        v = best;
                        v.muted = false;
                        v.volume = 1.0;
                        // #region agent log
                        try {
                            var br0 = v.getBoundingClientRect();
                            window._safeerDbg('H61', 'tv_remote_nav.js:boost', 'boost before play', {
                                paused: !!v.paused,
                                rs: v.readyState,
                                vw: v.videoWidth || 0,
                                w: Math.round(br0.width),
                                h: Math.round(br0.height),
                                want: !!window._safeer_xplore_want_play,
                                framed: !!bestFramed
                            });
                        } catch (_) {}
                        // #endregion
                        if (v.paused && bestFramed && (!xploreLivetvPath() || xploreOverlayOn())) {
                            try { v.play(); } catch(_) {}
                        }
                        var after = v.getBoundingClientRect();
                        var large = after.width >= 800 && after.height >= 450;
                        if (bestFramed && !large && !xploreLivetvPath()) {
                            applyXploreVideoFullscreen(v);
                            after = v.getBoundingClientRect();
                            large = after.width >= 800 && after.height >= 450;
                            // #region agent log
                            try { window._safeerDbg('H71', 'tv_remote_nav.js:boost', 'boost recover 0x0', { w: Math.round(after.width), h: Math.round(after.height), vw: v.videoWidth || 0 }); } catch (_) {}
                            // #endregion
                        }
                        var fsBtn = document.querySelector('.clpp-fullscreen, [class*="clpp"] [class*="fullscreen"], button[class*="fullscreen"]');
                        if (fsBtn && !window._safeer_xplore_fs_clicked && bestFramed && !large && !xploreLivetvPath()) {
                            window._safeer_xplore_fs_clicked = true;
                            try { fsBtn.click(); } catch(_) {}
                            after = v.getBoundingClientRect();
                            large = after.width >= 800 && after.height >= 450;
                            // #region agent log
                            try { window._safeerDbg('H28', 'UserScriptManager.kt:boost', 'fs click pip', { w: Math.round(after.width), large: !!large }); } catch (_) {}
                            // #endregion
                        } else if (fsBtn && !window._safeer_xplore_fs_clicked && bestFramed && large) {
                            window._safeer_xplore_fs_clicked = true;
                            // #region agent log
                            try { window._safeerDbg('H28', 'UserScriptManager.kt:boost', 'fs skip already large', { w: Math.round(after.width) }); } catch (_) {}
                            // #endregion
                        }
                        if (large) {
                            window._safeer_xplore_player_el = v;
                            try {
                                var ring = document.getElementById('safeer-focus-target-ring');
                                if (ring) {
                                    ring.classList.remove('active');
                                    ring.style.display = 'none';
                                }
                            } catch(_) {}
                            if ((v.videoWidth || 0) >= 320 && v.readyState >= 2) {
                                window._safeer_xplore_video_boosted = true;
                                hideXplorePlayerChrome();
                            }
                        }
                        // #region agent log
                        try {
                            window._safeerDbg('H3', 'UserScriptManager.kt:boost', 'video boosted', {
                                w: Math.round(after.width),
                                h: Math.round(after.height),
                                paused: !!v.paused,
                                rs: v.readyState,
                                vw: v.videoWidth || 0,
                                large: large,
                                traps: 0
                            });
                        } catch (_) {}
                        try {
                            window._safeerDbg('H7', 'UserScriptManager.kt:boost', 'containing blocks', { traps: 0, hasFsClass: false, skipped: true });
                        } catch (_) {}
                        try {
                            window._safeerDbg('H16', 'UserScriptManager.kt:boost', 'chrome hide decision', {
                                large: !!large,
                                vw: v.videoWidth || 0,
                                rs: v.readyState,
                                paused: !!v.paused,
                                boosted: !!window._safeer_xplore_video_boosted,
                                hid: !!window._safeer_xplore_playing
                            });
                        } catch (_) {}
                        // #endregion
                        return;
                    } catch(_) {}
                }

                window._safeer_xplore_play_from_start = function() {
                    try {
                        console.log('SafeerXplore: play from start');
                        var btn = findXplorePlayFromStart();
                        // #region agent log
                        try {
                            var br = btn ? btn.getBoundingClientRect() : { width: 0, height: 0 };
                            var txt = btn ? ((btn.innerText || btn.textContent || '') + '').replace(/\s+/g, ' ').trim() : '';
                            window._safeerDbg('H2', 'UserScriptManager.kt:play_from_start', 'play target', {
                                found: !!btn,
                                tag: btn ? btn.tagName : '',
                                w: Math.round(br.width),
                                h: Math.round(br.height),
                                t: txt.slice(0, 80)
                            });
                        } catch (_) {}
                        // #endregion
                        if (!btn) return false;
                        try {
                            var ov = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                            var oc = ov ? ((ov.className || '') + '').toLowerCase() : '';
                            if (oc.indexOf('player-fullwindow') !== -1 || oc.indexOf('player-scaled') !== -1) {
                                // #region agent log
                                try { window._safeerDbg('H222', 'tv_remote_nav.js:play', 'skip dup click overlay open', { ply: oc.slice(0, 70) }); } catch (_) {}
                                // #endregion
                                return true;
                            }
                        } catch (_) {}
                        window._safeer_xplore_want_play = true;
                        window._safeer_xplore_video_boosted = false;
                        window._safeer_xplore_fs_clicked = false;
                        window._safeer_xplore_replay_clicked = false;
                        window._safeer_xplore_player_el = null;
                        try { btn.click(); } catch(_) {}
                        boostXploreVideoOnce();
                        return true;
                    } catch(_) {}
                    return false;
                };

                window._safeer_xplore_boost = boostXploreVideoOnce;

                document.addEventListener('loadedmetadata', function(ev) {
                    try {
                        if (ev.target && ev.target.tagName === 'VIDEO') {
                            if (window._safeer_xplore_video_boosted) {
                                maybeHideWhenReady(ev.target);
                            } else {
                                var framed = (ev.target.videoWidth || 0) >= 320 && ev.target.readyState >= 2;
                                var allow = framed;
                                try { if (window._safeerSiteAgent) allow = !!window._safeerSiteAgent.allowBoost(); } catch (_) {}
                                if (allow) boostXploreVideoOnce();
                            }
                        }
                    } catch (_) {}
                }, true);
                document.addEventListener('playing', function(ev) {
                    try {
                        if (ev.target && ev.target.tagName === 'VIDEO') maybeHideWhenReady(ev.target);
                    } catch (_) {}
                }, true);
                document.addEventListener('pause', function(ev) {
                    try {
                        if (ev.target && ev.target.tagName === 'VIDEO') {
                            // #region agent log
                            try {
                                window._safeerDbg('H94', 'tv_remote_nav.js:pause', 'video paused', {
                                    vw: ev.target.videoWidth || 0,
                                    rs: ev.target.readyState,
                                    playing: !!window._safeer_xplore_playing,
                                    want: !!window._safeer_xplore_want_play
                                });
                            } catch (_) {}
                            // #endregion
                            resumeXploreIfPaused();
                        }
                    } catch (_) {}
                }, true);
                document.addEventListener('loadeddata', function(ev) {
                    try {
                        if (ev.target && ev.target.tagName === 'VIDEO') maybeHideWhenReady(ev.target);
                    } catch (_) {}
                }, true);
                document.addEventListener('canplay', function(ev) {
                    try {
                        if (ev.target && ev.target.tagName === 'VIDEO') {
                            if ((ev.target.videoWidth || 0) >= 320 && ev.target.readyState >= 2) {
                                window._safeer_xplore_player_el = ev.target;
                            }
                            maybeHideWhenReady(ev.target);
                        }
                    } catch (_) {}
                }, true);
                if (!window._safeer_xplore_mo) {
                    var lastMoAt = 0;
                    window._safeer_xplore_mo = new MutationObserver(function() {
                        var nowMo = Date.now();
                        if (nowMo - lastMoAt < 280) return;
                        lastMoAt = nowMo;
                        if (window._safeer_xplore_playing && window._safeer_xplore_video_boosted) {
                            var vKeep = window._safeer_xplore_player_el || document.querySelector('video');
                            if (vKeep) {
                                var rk = vKeep.getBoundingClientRect();
                                if (rk.width >= 800 && rk.height >= 450 && !vKeep.paused) return;
                                maybeHideWhenReady(vKeep);
                                if (vKeep.paused) resumeXploreIfPaused();
                            }
                            return;
                        }
                        if (window._safeer_xplore_want_play && !window._safeer_xplore_video_boosted) {
                            var allowBoost = true;
                            try {
                                if (window._safeerSiteAgent && !window._safeerSiteAgent.allowBoost()) allowBoost = false;
                            } catch (_) {}
                            if (allowBoost) boostXploreVideoOnce();
                        } else if (window._safeer_xplore_want_play && window._safeer_xplore_playing) {
                            resumeXploreIfPaused();
                        } else if (window._safeer_xplore_want_play && window._safeer_xplore_video_boosted && !window._safeer_xplore_playing) {
                            nudgeXplorePlayer();
                        }
                        try {
                            var pnow = (location.pathname || '').toLowerCase();
                            if (pnow.indexOf('/event') !== -1 && sessionStorage.getItem('safeer_xplore_autoplay') === '1') {
                                if (window._safeer_xplore_play_from_start()) {
                                    window._safeer_xplore_want_play = true;
                                    sessionStorage.removeItem('safeer_xplore_autoplay');
                                    try { if (window._safeerDbg) window._safeerDbg('H13', 'UserScriptManager.kt:autoplay_mo', 'event autoplay ok', { path: pnow.slice(0, 80) }); } catch (_) {}
                                }
                            } else if (window._safeer_xplore_want_play && !window._safeer_xplore_playing && (window._safeer_xplore_playbtn_n || 0) < 2) {
                                var pEvent = pnow.indexOf('/event') !== -1;
                                var allowBtn = pEvent;
                                try { if (window._safeerSiteAgent && !window._safeerSiteAgent.allowPlayButtonClick()) allowBtn = false; } catch (_) {}
                                if (!allowBtn) {
                                    // #region agent log
                                    try {
                                        if (!window._safeer_xplore_skipbtn_logged) {
                                            window._safeer_xplore_skipbtn_logged = true;
                                            window._safeerDbg('H265', 'tv_remote_nav.js:mo', 'skip playbtn', { path: pnow.slice(0, 40), want: true });
                                        }
                                    } catch (_) {}
                                    // #endregion
                                }
                                if (allowBtn) {
                                var vs2 = document.querySelectorAll('video');
                                var framed2 = false;
                                var vi;
                                for (vi = 0; vi < vs2.length; vi++) {
                                    if ((vs2[vi].videoWidth || 0) >= 320 && vs2[vi].readyState >= 2) { framed2 = true; break; }
                                }
                                if (!framed2) {
                                    var liveBtn = findXplorePlayFromStart();
                                    var lbr = liveBtn ? liveBtn.getBoundingClientRect() : { width: 0, height: 0 };
                                    if (liveBtn && lbr.width >= 40 && lbr.height >= 20) {
                                        window._safeer_xplore_playbtn_n = (window._safeer_xplore_playbtn_n || 0) + 1;
                                        try { if (window._safeerDbg) window._safeerDbg('H72', 'tv_remote_nav.js:mo', 'livetv playbtn', { n: window._safeer_xplore_playbtn_n, t: ((liveBtn.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) }); } catch (_) {}
                                        window._safeer_xplore_play_from_start();
                                    }
                                }
                                }
                            }
                        } catch (_) {}
                    });
                    try {
                        window._safeer_xplore_mo.observe(document.documentElement, { childList: true, subtree: true });
                    } catch (_) {}
                }
                if (!window._safeer_xplore_play_watch) {
                    window._safeer_xplore_play_watch = true;
                    setInterval(function () {
                        try {
                            if (!window._safeer_xplore_want_play) return;
                            if (window._safeer_app_bg) return;
                            var vw = xploreVideoEl();
                            if (!vw) return;
                            if ((vw.videoWidth || 0) >= 320 && vw.readyState >= 2) window._safeer_xplore_player_el = vw;
                            if (vw.paused) resumeXploreIfPaused();
                            if ((vw.videoWidth || 0) < 320 || vw.readyState < 2) return;
                            maybeHideWhenReady(vw);
                            if (!window._safeer_xplore_video_boosted) boostXploreVideoOnce();
                        } catch (_) {}
                    }, 900);
                }
                try {
                    if (sessionStorage.getItem('safeer_xplore_autoplay') === '1') {
                        var p0 = (location.pathname || '').toLowerCase();
                        if (p0.indexOf('/event') !== -1) {
                            if (window._safeer_xplore_play_from_start()) {
                                sessionStorage.removeItem('safeer_xplore_autoplay');
                                try { if (window._safeerDbg) window._safeerDbg('H13', 'UserScriptManager.kt:autoplay_init', 'event autoplay init ok', { path: p0.slice(0, 80) }); } catch (_) {}
                            }
                        }
                    }
                } catch (_) {}
                window._safeer_xplore_last_path = location.pathname || '';
                window._safeer_xplore_report = function (why) {
                    try {
                        var el = document.querySelector('.safeer-active-card');
                        var v = document.querySelector('video');
                        var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
                        var ov = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                        var play = pickXplorePlayAction();
                        var links = getXploreMenuLinks();
                        var titles = [];
                        var li;
                        for (li = 0; li < links.length && li < 8; li++) {
                            titles.push(((links[li].innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 20));
                        }
                        window._safeerDbg('H250', 'tv_remote_nav.js:watch', why || 'snap', {
                            path: (location.pathname || '').slice(0, 60),
                            focus: el ? ((el.className || '') + '').slice(0, 50) : '',
                            ft: el ? ((el.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) : '',
                            play: play ? ((play.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) : '',
                            ply: ov ? ((ov.className || '') + '').slice(0, 70) : '',
                            w: Math.round(r.width || 0),
                            vw: v ? (v.videoWidth || 0) : 0,
                            rs: v ? v.readyState : -1,
                            paused: v ? !!v.paused : true,
                            want: !!window._safeer_xplore_want_play,
                            auto: (function () { try { return sessionStorage.getItem('safeer_xplore_autoplay') || ''; } catch (eA) { return ''; } })(),
                            menu: links.length,
                            mt: titles.join('|')
                        });
                    } catch (_) {}
                };
                if (!window._safeer_xplore_pathwatch) {
                    window._safeer_xplore_pathwatch = true;
                    window._safeer_xplore_last_watch_path = (location.pathname || '').toLowerCase();
                    window._safeer_xplore_watch_n = 0;
                    window._safeer_xplore_watch_key = '';
                    setInterval(function () {
                        try {
                            var p = (location.pathname || '').toLowerCase();
                            var changed = p !== (window._safeer_xplore_last_watch_path || '');
                            if (changed) {
                                window._safeer_xplore_last_watch_path = p;
                                window._safeer_xplore_playbtn_n = 0;
                                window._safeer_xplore_playing = false;
                                window._safeer_xplore_video_boosted = false;
                                window._safeer_xplore_player_el = null;
                                window._safeer_xplore_did_focus = false;
                                try { if (window._safeer_xplore_unsmash) window._safeer_xplore_unsmash(); } catch (eU2) {}
                            }
                            if (p.indexOf('/event') !== -1) {
                                var auto = '';
                                try { auto = sessionStorage.getItem('safeer_xplore_autoplay') || ''; } catch (eS) {}
                                if (auto === '1' && window._safeer_xplore_play_from_start) {
                                    if (window._safeer_xplore_play_from_start()) {
                                        window._safeer_xplore_want_play = true;
                                        try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eR) {}
                                        // #region agent log
                                        try { window._safeerDbg('H251', 'tv_remote_nav.js:watch', 'autoplay path', { path: p.slice(0, 80) }); } catch (_) {}
                                        // #endregion
                                    }
                                }
                            }
                            var ov2 = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                            var oc2 = ov2 ? ((ov2.className || '') + '') : '';
                            var el2 = document.querySelector('.safeer-active-card');
                            var key = p + '|' + oc2.slice(0, 40) + '|' + (el2 ? ((el2.className || '') + '').slice(0, 30) : '');
                            window._safeer_xplore_watch_n = (window._safeer_xplore_watch_n || 0) + 1;
                            if (changed || key !== window._safeer_xplore_watch_key) {
                                window._safeer_xplore_watch_key = key;
                                window._safeer_xplore_report(changed ? 'path' : 'state');
                            } else if (window._safeer_xplore_watch_n <= 20 && window._safeer_xplore_watch_n % 5 === 0) {
                                window._safeer_xplore_report('tick');
                            }
                        } catch (_) {}
                    }, 700);
                }
                // Prijava: tv_spatial.js runXploreAuth (en interval).
                // #region agent log
                try {
                    window._safeerDbg('H4', 'UserScriptManager.kt:xplore_block', 'helpers ready', {
                        hasPlay: typeof window._safeer_xplore_play_from_start === 'function',
                        path: location.pathname
                    });
                } catch (_) {}
                // #endregion
            }


})();
