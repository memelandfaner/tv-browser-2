        /* 📺 Safeer TV Remote D-Pad Navigation & Cinema OSD Engine */
        (function() {
            if ((location.href || '').indexOf('youtube.com/tv') !== -1) return;
            var xploreReentry = !!(window._safeer_tv_remote_installed &&
                (location.hostname || '').indexOf('xploretv') !== -1 &&
                !window._safeer_xplore_helpers_ready);
            if (window._safeer_tv_remote_installed && !xploreReentry) return;
            if (!window._safeer_tv_remote_installed) window._safeer_tv_remote_installed = true;

            // #region agent log
            window._safeerDbg = function(h, loc, msg) {
                try { console.log(String(h || '') + ' ' + String(loc || '') + ' ' + String(msg || '')); } catch (_) {}
            };
            window._safeerDbgRun = 'lean';
            // #endregion

            function isXploreHost() {
                return (location.hostname || '').indexOf('xploretv') !== -1;
            }
            function is24urHost() {
                return (location.hostname || '').toLowerCase().indexOf('24ur') !== -1;
            }
            function isHydraHost() {
                return (location.hostname || '').toLowerCase().indexOf('hydrahd') !== -1;
            }
            function isHydraWatchPath() {
                var p = (location.pathname || '').toLowerCase();
                return isHydraHost() && (p.indexOf('/movie/') !== -1 || p.indexOf('/tv/') !== -1 || p.indexOf('/watch') !== -1);
            }
            function isHydraBrandEl(el) {
                if (!isHydraHost() || !el) return false;
                try {
                    var href = ((el.getAttribute && el.getAttribute('href')) || '') + '';
                    var t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    var cls = ((el.className || '') + '').toLowerCase();
                    if (t === 'hydrahd') return true;
                    if (cls.indexOf('logo') !== -1) return true;
                    if (href === '/' || href.indexOf('/login') !== -1 || href.indexOf('/register') !== -1) return true;
                    if (/hydrahd\.[a-z]+\/?$/i.test(href)) return true;
                    if (el.closest && el.closest('.mynav a[href="/"], .mynav .logo, header .logo')) return true;
                } catch (_) {}
                return false;
            }
            function isHydraNavArrow(el) {
                if (!el) return false;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('owl-prev') !== -1 || cls.indexOf('owl-next') !== -1) return true;
                if (cls.indexOf('swiper-button') !== -1 || cls.indexOf('slick-arrow') !== -1) return true;
                if (cls.indexOf('carousel-control') !== -1 || cls.indexOf('slider-arrow') !== -1) return true;
                try {
                    if (el.closest && el.closest('.owl-nav, .swiper-button-prev, .swiper-button-next, .slick-arrow')) return true;
                } catch (_) {}
                return false;
            }
            function isHydraChromeEl(el) {
                if (!isHydraHost() || !el) return false;
                if (isHydraBrandEl(el) || isHydraNavArrow(el)) return true;
                try {
                    var href = ((el.getAttribute && el.getAttribute('href')) || '') + '';
                    var cls = ((el.className || '') + '').toLowerCase();
                    if (href.indexOf('/login') !== -1 || href.indexOf('/register') !== -1) return true;
                    if (cls.indexOf('navbar-toggle') !== -1 || cls.indexOf('navbar-toggler') !== -1) return true;
                    if (cls.indexOf('hamburger') !== -1 || cls.indexOf('user-avatar') !== -1) return true;
                    if (el.closest && el.closest('.mynav, header, .navbar, .navbar-header, .top-header')) {
                        var tx = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (tx === 'movies' || tx === 'series' || tx === 'watch now') return false;
                        return true;
                    }
                } catch (_) {}
                return false;
            }
            function hydraLayoutRect(el) {
                var empty = { top: 0, left: 0, width: 0, height: 0, right: 0, bottom: 0 };
                if (!el || !el.getBoundingClientRect) return empty;
                try {
                    var r = el.getBoundingClientRect();
                    var w = r.width;
                    var h = r.height;
                    var cs = window.getComputedStyle(el);
                    var padT = parseFloat(cs.paddingTop) || 0;
                    var padB = parseFloat(cs.paddingBottom) || 0;
                    if (h < padT + padB) h = padT + padB;
                    return { top: r.top, left: r.left, width: w, height: h, right: r.left + w, bottom: r.top + h };
                } catch (_) {}
                return empty;
            }
            function hydraIsShown(el) {
                if (!el) return false;
                try {
                    var st = window.getComputedStyle(el);
                    if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
                    if (el.closest) {
                        var pop = el.closest('#popup_login, .small_modal, #backgroundPopup');
                        if (pop) {
                            var ps = window.getComputedStyle(pop);
                            if (ps.display === 'none' || ps.visibility === 'hidden') return false;
                        }
                    }
                } catch (_) {}
                return true;
            }
            function hydraRowHidden(el) {
                if (!el || !el.closest) return false;
                try {
                    var box = el.closest('.trendingmovz, .trendingshowz, .latestmovz, .latestshowz, .ratedmovz, .ratedshowz');
                    if (!box) return false;
                    var cls = ((box.className || '') + '').toLowerCase();
                    if (cls.indexOf('active') === -1) return true;
                    var st = window.getComputedStyle(box);
                    if (st.display === 'none' || st.visibility === 'hidden') return true;
                } catch (_) {}
                return false;
            }
            function hydraOnX(el) {
                if (!el || !hydraIsShown(el)) return false;
                try {
                    var r = hydraLayoutRect(el);
                    var winW = window.innerWidth || 1920;
                    if (r.width < 24) return false;
                    if (r.left < -48 || r.left > winW - 20) return false;
                    var visW = Math.min(r.right, winW) - Math.max(r.left, 0);
                    if (visW < Math.min(r.width, 90) * 0.35) return false;
                    return true;
                } catch (_) {}
                return false;
            }
            function hydraVisible(el) {
                if (!el || !hydraIsShown(el)) return false;
                try {
                    var r = hydraLayoutRect(el);
                    var winW = window.innerWidth || 1920;
                    var winH = window.innerHeight || 1080;
                    if (r.width < 24 || r.height < 14) return false;
                    if (r.right < 12 || r.left > winW - 12) return false;
                    if (r.left < -48) return false;
                    var visW = Math.min(r.right, winW) - Math.max(r.left, 0);
                    if (visW < Math.min(r.width, 90) * 0.45) return false;
                    if (r.bottom < 8 || r.top > winH - 8) return false;
                    return true;
                } catch (_) {}
                return false;
            }
            function hydraPlayControl() {
                if (!isHydraHost()) return null;
                var play = document.querySelector('.video-play-button');
                if (play && hydraVisible(play)) return play;
                var ifr = document.getElementById('iframePlayer');
                if (ifr) {
                    var ir = ifr.getBoundingClientRect();
                    if (ir.width >= 80 && ir.height >= 80) return ifr;
                }
                return null;
            }
            function getHydraCandidates() {
                var out = [];
                function add(el, looseY) {
                    if (!el || out.indexOf(el) !== -1) return;
                    if (isHydraChromeEl(el) || isHydraNavArrow(el)) return;
                    if (hydraRowHidden(el)) return;
                    if (!hydraIsShown(el)) return;
                    if (looseY) {
                        if (!hydraOnX(el)) return;
                    } else if (!hydraVisible(el)) return;
                    out.push(el);
                }
                var i, nodes, el, r, t, href;
                if (isHydraWatchPath()) {
                    add(hydraPlayControl());
                    nodes = document.querySelectorAll(
                        '.video-play-button, .watch-now-btn, .slidebtn,' +
                        ' .undervidbtns button, .undervidbtns a,' +
                        ' .seasonHeader, a.dynamic-ep-link, a[data-episode], a[data-season],' +
                        ' [id*="server"] a, [id*="server"] button, .server-list a, .server-list button,' +
                        ' .select-server a, .select-server button,' +
                        ' a.hthis, .swiper-slide-featured > a'
                    );
                    for (i = 0; i < nodes.length; i++) {
                        el = nodes[i];
                        if ((el.id || '') === 'shareButton') continue;
                        href = ((el.getAttribute && el.getAttribute('href')) || '') + '';
                        if (href.indexOf('javascript:') === 0 && ((el.getAttribute('onclick') || '') + '').indexOf('popup_login') !== -1) continue;
                        add(el, true);
                    }
                    return out;
                }
                nodes = document.querySelectorAll('.slidebtn, button.slidebtn, a.slidebtn');
                var bestBtn = null;
                var bestBtnL = 1e9;
                var winWHero = window.innerWidth || 1920;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    if (!hydraVisible(el)) continue;
                    r = hydraLayoutRect(el);
                    if (r.left < -8 || r.left > winWHero * 0.48) continue;
                    if (r.left < bestBtnL) {
                        bestBtnL = r.left;
                        bestBtn = el;
                    }
                }
                if (bestBtn) add(bestBtn);
                nodes = document.querySelectorAll('.tab, .trendingmovies, .trendingseries, .latestmovies, .latestseries, .ratedmovies, .ratedseries');
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    if (t !== 'movies' && t !== 'series') continue;
                    if (el.closest && el.closest('.mynav, header, .menu-links')) continue;
                    r = hydraLayoutRect(el);
                    if (r.width < 28 || r.width > 280 || r.height > 88) continue;
                    add(el, true);
                }
                nodes = document.querySelectorAll('a.hthis, .swiper-slide-featured > a');
                var winWPoster = window.innerWidth || 1920;
                var winHPoster = window.innerHeight || 1080;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    if (el.closest && el.closest('.slidebtn')) continue;
                    r = hydraLayoutRect(el);
                    if (r.width > winWPoster * 0.45) continue;
                    if (r.height > winHPoster * 0.55 && r.width > winWPoster * 0.34) continue;
                    if (r.width < 48) continue;
                    add(el, true);
                }
                return out;
            }
            function hydraSeedFocus() {
                var cands = getHydraCandidates();
                var best = null;
                var bestScore = 1e15;
                var i, el, r, cls, tx, score;
                for (i = 0; i < cands.length; i++) {
                    el = cands[i];
                    r = hydraLayoutRect(el);
                    cls = ((el.className || '') + '').toLowerCase();
                    tx = ((el.innerText || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    score = r.top * 2 + r.left;
                    if (cls.indexOf('slidebtn') !== -1 || tx.indexOf('watch now') !== -1) score -= 500;
                    if (isHydraWatchPath() && cls.indexOf('video-play-button') !== -1) score -= 600;
                    if (score < bestScore) {
                        bestScore = score;
                        best = el;
                    }
                }
                if (best) highlightElement(best);
                return best;
            }
            function hydraPosterOf(el) {
                if (!el) return null;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('hthis') !== -1) return el;
                try {
                    if (el.closest) return el.closest('a.hthis, .swiper-slide-featured > a');
                } catch (_) {}
                return null;
            }
            function hydraPostersInSameRow(el) {
                var out = [];
                if (!el) return out;
                var row = null;
                try { row = el.closest && el.closest('.swiper-container-featured'); } catch (_) {}
                var nodes = (row || document).querySelectorAll('a.hthis');
                var i, n;
                for (i = 0; i < nodes.length; i++) {
                    n = nodes[i];
                    if (!n || out.indexOf(n) !== -1) continue;
                    if (hydraRowHidden(n) || !hydraIsShown(n)) continue;
                    out.push(n);
                }
                return out;
            }
            function hydraMovePoster(direction, current) {
                var poster = hydraPosterOf(current);
                if (!poster) return null;
                var list = hydraPostersInSameRow(poster);
                var i, idx = -1;
                for (i = 0; i < list.length; i++) {
                    if (list[i] === poster) { idx = i; break; }
                }
                if (idx < 0) return null;
                var next = null;
                if (direction === 'RIGHT' && list[idx + 1]) next = list[idx + 1];
                if (direction === 'LEFT' && list[idx - 1]) next = list[idx - 1];
                if (!next) return null;
                try {
                    var nr = hydraLayoutRect(next);
                    var winW = window.innerWidth || 1920;
                    if (nr.left > winW - 80 || nr.right < 80) {
                        next.scrollIntoView({ behavior: 'auto', block: 'nearest', inline: 'nearest' });
                    }
                } catch (_) {}
                return next;
            }
            function hydraMoveTab(direction, current) {
                if (!current) return null;
                var cls = ((current.className || '') + '').toLowerCase();
                if (cls.indexOf('tab') === -1 && cls.indexOf('trendingmovies') === -1 &&
                    cls.indexOf('trendingseries') === -1 && cls.indexOf('latestmovies') === -1 &&
                    cls.indexOf('latestseries') === -1) return null;
                var wrap = current.parentElement;
                if (!wrap) return null;
                var tabs = wrap.querySelectorAll('.tab');
                var i, idx = -1;
                for (i = 0; i < tabs.length; i++) {
                    if (tabs[i] === current) { idx = i; break; }
                }
                if (idx < 0) return null;
                if (direction === 'RIGHT' && tabs[idx + 1]) return tabs[idx + 1];
                if (direction === 'LEFT' && tabs[idx - 1]) return tabs[idx - 1];
                return null;
            }
            function hydraFirstPosterBelow(fromEl) {
                var root = null;
                try { root = fromEl && fromEl.closest && fromEl.closest('.container2, .tabtrending, .tablatest, .tabrated'); } catch (_) {}
                var nodes = (root || document).querySelectorAll('a.hthis, .swiper-slide-featured > a');
                var best = null;
                var bestL = 1e9;
                var from = hydraLayoutRect(fromEl);
                var i, n, r;
                for (i = 0; i < nodes.length; i++) {
                    n = nodes[i];
                    if (hydraRowHidden(n) || !hydraIsShown(n)) continue;
                    r = hydraLayoutRect(n);
                    if (r.top <= from.bottom - 4) continue;
                    if (r.width < 48) continue;
                    if (r.left < -20) continue;
                    if (r.left < bestL) {
                        bestL = r.left;
                        best = n;
                    }
                }
                return best;
            }
            window._safeer_xplore_release_cdm = function () {
                var had = false;
                try {
                    var vids = document.querySelectorAll('video');
                    var i, m;
                    for (i = 0; i < vids.length; i++) {
                        m = vids[i];
                        try {
                            if (m.mediaKeys) {
                                had = true;
                                m.setMediaKeys(null);
                            }
                        } catch (_) {}
                        try { m.pause(); } catch (_) {}
                    }
                } catch (_) {}
                return had;
            };
            window._safeer_xplore_live_program_tiles = function () {
                var nodes = document.querySelectorAll('.item.item--event, .item--event');
                var tiles = [];
                var i, el, cls;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    cls = ((el.className || '') + '').toLowerCase();
                    if (cls.indexOf('channel-container') !== -1 && cls.indexOf('item--event') === -1) continue;
                    if (el.closest && el.closest('.menu, #csh__menu_bar, .menu-items-wrapper')) continue;
                    tiles.push(el);
                }
                tiles.sort(function (a, b) {
                    var ay = a.offsetTop, by = b.offsetTop;
                    try { ay = a.getBoundingClientRect().top + (window.scrollY || 0); } catch (_) {}
                    try { by = b.getBoundingClientRect().top + (window.scrollY || 0); } catch (_) {}
                    if (Math.abs(ay - by) > 48) return ay - by;
                    var ax = a.offsetLeft, bx = b.offsetLeft;
                    try { ax = a.getBoundingClientRect().left + (window.scrollX || 0); } catch (_) {}
                    try { bx = b.getBoundingClientRect().left + (window.scrollX || 0); } catch (_) {}
                    return ax - bx;
                });
                return tiles;
            };
            window._safeer_xplore_tile_label = function (tile) {
                var name = '';
                try {
                    var img = tile && tile.querySelector && tile.querySelector('img[alt]');
                    if (img && img.alt) name = (img.alt + '').replace(/\s+/g, ' ').trim();
                } catch (_) {}
                if (!name && tile) name = ((tile.innerText || tile.textContent || '') + '').replace(/\s+/g, ' ').trim();
                return (name || '').slice(0, 36);
            };
            window._safeer_xplore_click_live_tile = function (tile, idx) {
                if (!tile) return 0;
                window._safeer_xplore_last_tile = tile;
                window._safeer_xplore_zap_idx = idx;
                try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                window._safeer_xplore_want_play = true;
                try { if (typeof highlightElement === 'function') highlightElement(tile); } catch (_) {}
                try { tile.click(); } catch (_) {}
                return window._safeer_xplore_tile_label(tile) || 1;
            };
            window._safeer_xplore_tune_channel = function (num) {
                try {
                    var tiles = window._safeer_xplore_live_program_tiles();
                    var idx = (num | 0) - 1;
                    if (idx < 0 || idx >= tiles.length) {
                        try {
                            if (window._safeerDbg) window._safeerDbg('H340', 'tv_spatial.js:tune', 'no program', {
                                want: num,
                                n: tiles.length
                            });
                        } catch (_) {}
                        return '!' + tiles.length;
                    }
                    var name = window._safeer_xplore_click_live_tile(tiles[idx], idx);
                    try {
                        if (window._safeerDbg) window._safeerDbg('H340', 'tv_spatial.js:tune', 'tune click', {
                            i: idx,
                            n: tiles.length,
                            t: name,
                            want: num
                        });
                    } catch (_) {}
                    return name || 1;
                } catch (e) {
                    return 0;
                }
            };
            window._safeer_xplore_zap_channel = function (delta) {
                try {
                    var step = delta > 0 ? 1 : -1;
                    var tiles = window._safeer_xplore_live_program_tiles();
                    if (!tiles.length) return 0;
                    var cur = window._safeer_xplore_last_tile;
                    var idx = -1;
                    var i;
                    if (typeof window._safeer_xplore_zap_idx === 'number' &&
                        window._safeer_xplore_zap_idx >= 0 &&
                        window._safeer_xplore_zap_idx < tiles.length) {
                        idx = window._safeer_xplore_zap_idx;
                    }
                    if (idx < 0) {
                        for (i = 0; i < tiles.length; i++) {
                            if (tiles[i] === cur || (cur && tiles[i].contains && tiles[i].contains(cur))) {
                                idx = i;
                                break;
                            }
                        }
                    }
                    if (idx < 0) {
                        var hl = document.querySelector('.item.item--event.safeer-active-card, .item--event.safeer-active-card');
                        if (hl) {
                            for (i = 0; i < tiles.length; i++) {
                                if (tiles[i] === hl || tiles[i].contains(hl) || (hl.contains && hl.contains(tiles[i]))) {
                                    idx = i;
                                    break;
                                }
                            }
                        }
                    }
                    if (idx < 0) idx = 0;
                    var nextIdx = idx + step;
                    if (nextIdx < 0) nextIdx = tiles.length - 1;
                    if (nextIdx >= tiles.length) nextIdx = 0;
                    if (tiles[nextIdx] === cur && tiles.length > 1) {
                        nextIdx = (nextIdx + step + tiles.length) % tiles.length;
                    }
                    var tile = tiles[nextIdx];
                    if (!tile) return 0;
                    var name = window._safeer_xplore_click_live_tile(tile, nextIdx);
                    try {
                        if (window._safeerDbg) window._safeerDbg('H339', 'tv_spatial.js:zap', 'live zap click', {
                            i: nextIdx,
                            n: tiles.length,
                            t: name,
                            d: step
                        });
                    } catch (_) {}
                    return name || 1;
                } catch (e) {
                    return 0;
                }
            };
            if (!xploreReentry && isXploreHost()) {
                try {
                    var vp = document.querySelector('meta[name="viewport"]');
                    if (!vp) {
                        vp = document.createElement('meta');
                        vp.setAttribute('name', 'viewport');
                        (document.head || document.documentElement).appendChild(vp);
                    }
                    vp.setAttribute('content', 'width=1920, initial-scale=1');
                    try { document.documentElement.classList.add('safeer-xplore-dark'); } catch (_) {}
                    try {
                        var cw = document.querySelector('.content__wrapper');
                        if (cw) cw.style.setProperty('background-color', '#07090d', 'important');
                    } catch (_) {}
                } catch (_) {}
            }

            if (!xploreReentry) {
            // 1. Injektiraj kinematografske sloge za fokus in OSD HUD
            try {
                var style = document.createElement('style');
                style.id = 'tv-remote-cinema-style';
                style.textContent = `
                    :focus, :focus-visible, .tv-remote-focused {
                        outline: 4px solid #00e5ff !important;
                        outline-offset: 4px !important;
                        box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.85), 0 0 16px rgba(0, 229, 255, 0.7) !important;
                        border-radius: 10px !important;
                        background-color: rgba(0, 229, 255, 0.10) !important;
                        transition: outline-color 0.1s ease, box-shadow 0.1s ease !important;
                    }
                    .safeer-active-card {
                        outline: 4px solid #00e5ff !important;
                        outline-offset: 4px !important;
                        box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.85), 0 0 18px rgba(0, 229, 255, 0.85) !important;
                        border-radius: 12px !important;
                        background-color: rgba(0, 229, 255, 0.12) !important;
                        transform: scale(1.03) !important;
                        transition: transform 0.12s ease, outline-color 0.1s ease, box-shadow 0.1s ease !important;
                        z-index: 9999 !important;
                    }
                    #safeer-focus-target-ring {
                        position: fixed;
                        pointer-events: none;
                        border: 4px solid #00e5ff;
                        border-radius: 12px;
                        box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.8), 0 0 16px rgba(0, 229, 255, 0.75);
                        background: rgba(0, 229, 255, 0.08);
                        z-index: 50000;
                        transition: top 0.1s ease, left 0.1s ease, width 0.1s ease, height 0.1s ease, opacity 0.12s ease;
                        opacity: 0;
                    }
                    #safeer-focus-target-ring.active {
                        opacity: 1;
                    }
                    .safeer-focus-badge {
                        position: absolute;
                        top: -14px;
                        right: -12px;
                        background: #00e5ff;
                        color: #000000;
                        font-size: 13px;
                        font-weight: 900;
                        border-radius: 50%;
                        width: 24px;
                        height: 24px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        box-shadow: 0 0 12px #00e5ff;
                    }
                    /* 🛑 Skrij odvečne pasice na TV zaslonu */
                    #onetrust-banner-sdk, .cookie-banner, .gdpr-consent, .cookie-notice,
                    .open-in-app, .smartbanner, [class*="CookieConsent"], [id*="cookie-notice"],
                    ytm-open-app-button, ytm-app-promo-renderer {
                        display: none !important;
                    }
                    /* 🎬 Kinematografski OSD HUD */
                    #safeer-tv-hud {
                        position: fixed;
                        bottom: 60px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: rgba(8, 14, 26, 0.92);
                        backdrop-filter: blur(16px);
                        -webkit-backdrop-filter: blur(16px);
                        border: 2px solid #00e5ff;
                        border-radius: 16px;
                        padding: 14px 28px;
                        color: #ffffff;
                        font-family: 'Segoe UI', Roboto, sans-serif;
                        font-size: 22px;
                        font-weight: 700;
                        letter-spacing: 0.5px;
                        box-shadow: 0 10px 40px rgba(0, 229, 255, 0.45), 0 0 15px rgba(0, 0, 0, 0.8);
                        display: flex;
                        align-items: center;
                        gap: 14px;
                        z-index: 2147483647;
                        pointer-events: none;
                        opacity: 0;
                        transition: opacity 0.25s ease, transform 0.25s ease;
                    }
                    #safeer-tv-hud.visible {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
                if (isHydraHost()) {
                    var hydraFix = document.createElement('style');
                    hydraFix.id = 'tv-remote-hydra-focus-fix';
                    hydraFix.textContent = ':focus,:focus-visible{outline:none!important;box-shadow:none!important;background-color:transparent!important;transform:none!important;}' +
                        '.safeer-active-card{outline:none!important;box-shadow:none!important;background-color:transparent!important;transform:none!important;}' +
                        '#safeer-focus-target-ring{border:4px solid #00e5ff!important;box-shadow:0 0 0 2px #000,0 0 18px #00e5ff!important;background:transparent!important;}';
                    (document.head || document.documentElement).appendChild(hydraFix);
                }
                if (isXploreHost()) {
                    window._safeer_xplore_paint_menu = function () {
                        try {
                            if (document.documentElement.classList.contains('safeer-xplore-fs')) return;
                            document.documentElement.classList.add('safeer-xplore-dark');
                            var cssId = 'tv-remote-xplore-menubar';
                            var css = 'html.safeer-xplore-dark .menu,html.safeer-xplore-dark .menu.menu--opaque,html.safeer-xplore-dark .menu.menu--fixed,html.safeer-xplore-dark .menu.menu--black-text,html.safeer-xplore-dark .menu.gradient-bg-white,html.safeer-xplore-dark .menu.gradient-bg-white.menu--black-text,html.safeer-xplore-dark header,html.safeer-xplore-dark nav{background:#0b1220!important;background-color:#0b1220!important;background-image:none!important;color:#f8fafc!important;border-bottom:1px solid #2a3a52!important;box-shadow:0 10px 28px rgba(0,0,0,.45)!important;min-height:84px!important;height:84px!important;z-index:60!important;}' +
                                'html.safeer-xplore-dark .menu::before,html.safeer-xplore-dark .menu::after{display:none!important;background:none!important;content:none!important;}' +
                                'html.safeer-xplore-dark .menu-items-wrapper,html.safeer-xplore-dark #csh__menu_bar,html.safeer-xplore-dark .menu [class*="wrapper"],html.safeer-xplore-dark .menu [class*="container"],html.safeer-xplore-dark .menu [class*="inner"],html.safeer-xplore-dark .menu [class*="bar"]{background:transparent!important;background-image:none!important;}' +
                                'html.safeer-xplore-dark .menu a,html.safeer-xplore-dark .menu button,html.safeer-xplore-dark .menu [role="button"],html.safeer-xplore-dark .menu .dropdown-toggle-button,html.safeer-xplore-dark #csh__menu_bar a{display:inline-flex!important;flex-direction:row!important;align-items:center!important;gap:10px!important;min-height:56px!important;padding:10px 16px!important;border-radius:12px!important;font-size:22px!important;font-weight:700!important;color:#f8fafc!important;-webkit-text-fill-color:#f8fafc!important;opacity:1!important;visibility:visible!important;overflow:visible!important;white-space:nowrap!important;background:transparent!important;text-shadow:0 1px 2px rgba(0,0,0,.7)!important;}' +
                                'html.safeer-xplore-dark .menu a span,html.safeer-xplore-dark .menu button span,html.safeer-xplore-dark #csh__menu_bar a span,html.safeer-xplore-dark .dropdown-toggle-button span{display:inline!important;opacity:1!important;visibility:visible!important;position:static!important;width:auto!important;max-width:none!important;height:auto!important;font-size:22px!important;font-weight:700!important;color:#fff!important;-webkit-text-fill-color:#fff!important;clip:auto!important;clip-path:none!important;overflow:visible!important;text-indent:0!important;white-space:nowrap!important;}' +
                                'html.safeer-xplore-dark .menu a:not(.logo) svg,html.safeer-xplore-dark .menu a:not(.logo) svg *,html.safeer-xplore-dark .menu button svg,html.safeer-xplore-dark .menu button svg *,html.safeer-xplore-dark .menu .dropdown-toggle-button svg{fill:#f8fafc!important;color:#f8fafc!important;opacity:1!important;}' +
                                'html.safeer-xplore-dark .home-link.route--active,html.safeer-xplore-dark .livetv-link.route--active,html.safeer-xplore-dark .movies-link.route--active,html.safeer-xplore-dark .library-link.route--active,html.safeer-xplore-dark .guide-link.route--active{background:rgba(0,229,255,.16)!important;border-radius:12px!important;box-shadow:inset 0 -3px 0 #00e5ff!important;color:#fff!important;}' +
                                'html.safeer-xplore-dark .menu a.safeer-active-card,html.safeer-xplore-dark .menu .dropdown-toggle-button.safeer-active-card{outline:3px solid #00e5ff!important;outline-offset:3px!important;background:rgba(0,229,255,.14)!important;box-shadow:none!important;}' +
                                'html.safeer-xplore-dark .menu .logo,html.safeer-xplore-dark .menu a.logo{background:transparent!important;outline:none!important;box-shadow:none!important;}';
                            var st = document.getElementById(cssId);
                            if (!st) {
                                st = document.createElement('style');
                                st.id = cssId;
                                (document.head || document.documentElement).appendChild(st);
                            }
                            if (st.textContent !== css) st.textContent = css;
                            function nearWhite(c) {
                                c = ((c || '') + '').toLowerCase();
                                if (c.indexOf('255, 255, 255') !== -1 || c === 'white' || c === '#fff' || c === '#ffffff') return true;
                                var m = c.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
                                return !!(m && +m[1] > 210 && +m[2] > 210 && +m[3] > 210);
                            }
                            function paintBar(node) {
                                if (!node) return;
                                node.style.setProperty('background', '#0b1220', 'important');
                                node.style.setProperty('background-color', '#0b1220', 'important');
                                node.style.setProperty('background-image', 'none', 'important');
                                node.style.setProperty('color', '#f8fafc', 'important');
                            }
                            var menu = document.querySelector('.menu');
                            var roots = [];
                            if (menu) roots.push(menu);
                            var barEl = document.getElementById('csh__menu_bar');
                            if (barEl) roots.push(barEl);
                            var ri, node, par, depth, r, cs, kids, ki, texts, ti, txt, cls;
                            for (ri = 0; ri < roots.length; ri++) {
                                node = roots[ri];
                                paintBar(node);
                                par = node.parentElement;
                                depth = 0;
                                while (par && depth < 3) {
                                    r = par.getBoundingClientRect();
                                    if (r.top < 12 && r.height > 48 && r.height < 170 && r.width > 700) {
                                        cs = getComputedStyle(par);
                                        if (nearWhite(cs.backgroundColor) || r.top <= 2) paintBar(par);
                                    }
                                    par = par.parentElement;
                                    depth++;
                                }
                                kids = node.querySelectorAll('div, nav, ul, header, section');
                                for (ki = 0; ki < kids.length; ki++) {
                                    r = kids[ki].getBoundingClientRect();
                                    if (r.top > 110 || r.height > 150) continue;
                                    cs = getComputedStyle(kids[ki]);
                                    if (nearWhite(cs.backgroundColor)) {
                                        kids[ki].style.setProperty('background', 'transparent', 'important');
                                        kids[ki].style.setProperty('background-color', 'transparent', 'important');
                                        kids[ki].style.setProperty('background-image', 'none', 'important');
                                    }
                                }
                                texts = node.querySelectorAll('a, button, span, p, li, [role="button"], [role="tab"]');
                                for (ti = 0; ti < texts.length; ti++) {
                                    txt = texts[ti];
                                    cls = ((txt.className || '') + '').toLowerCase();
                                    if (cls.indexOf('logo') !== -1) continue;
                                    txt.style.setProperty('color', '#f8fafc', 'important');
                                    txt.style.setProperty('-webkit-text-fill-color', '#f8fafc', 'important');
                                    txt.style.setProperty('opacity', '1', 'important');
                                    txt.style.setProperty('visibility', 'visible', 'important');
                                }
                            }
                        } catch (_) {}
                    };
                    try { window._safeer_xplore_paint_menu(); } catch (_) {}
                    var xploreFix = document.createElement('style');
                    xploreFix.id = 'tv-remote-xplore-layout-fix';
                    xploreFix.textContent = ':focus,:focus-visible{outline:none!important;box-shadow:none!important;background-color:transparent!important;transform:none!important;}' +
                        '.tv-remote-focused{transform:none!important;background-color:transparent!important;outline:5px solid #00e5ff!important;outline-offset:6px!important;box-shadow:0 0 0 3px #000,0 0 28px #00e5ff!important;}' +
                        '.livetv-griditem,[class*="livetv-griditem"],.livetv-griditem .item.item--event,.item.item--event,.item--event,[class*="content-carousel__item"]{overflow:visible!important;}' +
                        '.item.item--event.safeer-active-card,.item--event.safeer-active-card,[class*="content-carousel__item"].safeer-active-card{outline:6px solid #00e5ff!important;outline-offset:7px!important;box-shadow:0 0 0 4px #000,0 0 36px #00e5ff!important;background-color:transparent!important;border-radius:12px!important;z-index:auto!important;}' +
                        '[class*="content-carousel__item"].safeer-active-card{transform:scale(1.06)!important;}' +
                        '.item.item--event.safeer-active-card,.item--event.safeer-active-card{transform:none!important;}' +
                        '.menu a.safeer-active-card,.livetv-link.safeer-active-card,[class*="action-list"].safeer-active-card,.promo-carousel__button.safeer-active-card,a.safeer-active-card{outline:6px solid #00e5ff!important;outline-offset:7px!important;box-shadow:0 0 0 3px #000,0 0 32px #00e5ff!important;background-color:transparent!important;transform:none!important;border-radius:12px!important;}' +
                        '.menu .logo:focus,.menu a.logo:focus,[class*="header-logo"]:focus,#csh__logo:focus{outline:none!important;box-shadow:none!important;}' +
                        '#safeer-focus-target-ring .safeer-focus-badge{display:none!important;}' +
                        '#safeer-focus-target-ring{border:6px solid #00e5ff!important;outline:3px solid #fff!important;outline-offset:-9px!important;border-radius:14px!important;box-shadow:0 0 0 3px #000,0 0 38px #00e5ff!important;background:transparent!important;z-index:50000!important;}' +
                        'html.safeer-xplore-fs #safeer-focus-target-ring{display:none!important;opacity:0!important;}' +
                        'html.safeer-xplore-fs .safeer-active-card{box-shadow:none!important;outline:none!important;transform:none!important;}' +
                        'input:focus,input:focus-visible,textarea:focus{outline:2px solid #3b82f6!important;box-shadow:0 0 0 1px #07090d,0 0 14px rgba(59,130,246,.35)!important;background:#121826!important;}' +
                        '.menu,.menu--opaque,.menu--fixed,.gradient-bg-black,.menu.menu--black-text,.menu.gradient-bg-white{background:#0b0e14!important;background-image:none!important;color:#f1f5f9!important;border-bottom:1px solid #1a2230!important;z-index:40!important;min-height:76px!important;height:auto!important;}' +
                        '.menu.menu--fixed,.menu--fixed{top:0!important;left:0!important;right:0!important;}' +
                        '.menu a,.menu [role="tab"],#csh__menu_bar a,.menu .dropdown-toggle-button{min-height:52px!important;display:inline-flex!important;flex-direction:row!important;align-items:center!important;gap:8px!important;padding:8px 14px!important;font-size:20px!important;font-weight:700!important;color:#f1f5f9!important;white-space:nowrap!important;opacity:1!important;visibility:visible!important;overflow:visible!important;}' +
                        '.menu-items-wrapper,#csh__menu_bar{display:flex!important;align-items:center!important;gap:4px!important;background:transparent!important;background-color:transparent!important;}' +
                        '#csh__menu_bar a span,.menu a span,.home-link span,.livetv-link span,.movies-link span,.library-link span,.guide-link span,.dropdown-toggle-button span{display:inline!important;opacity:1!important;visibility:visible!important;position:static!important;width:auto!important;max-width:none!important;height:auto!important;font-size:20px!important;font-weight:700!important;color:#f1f5f9!important;clip:auto!important;clip-path:none!important;overflow:visible!important;text-indent:0!important;}' +
                        '.home-link.route--active,.livetv-link.route--active,.movies-link.route--active,.library-link.route--active,.guide-link.route--active{background:#1e293b!important;border-radius:10px!important;box-shadow:inset 0 -3px 0 #e10600!important;}' +
                        '.zw-overlays-layer.player-fullwindow,.zw-overlays-layer.player-scaled,[class*="overlays-layer"].player-fullwindow,[class*="overlays-layer"].player-scaled{z-index:100000!important;visibility:visible!important;opacity:1!important;}' +
                        'html.safeer-xplore-waitplay #safeer-focus-target-ring,html.safeer-xplore-playing #safeer-focus-target-ring{display:none!important;opacity:0!important;}' +
                        '.content__wrapper,.content__wrapper.has-footer,html.safeer-xplore-dark,html.safeer-xplore-dark body,html.safeer-xplore-dark main,html.safeer-xplore-dark .gradient-bg-white,html.safeer-xplore-dark .options-wrapper,html.safeer-xplore-dark [class*="options-wrapper"],html.safeer-xplore-dark [class*="livetv-grid"]{background:#07090d!important;background-color:#07090d!important;color:#e8eef5!important;}' +
                        'html.safeer-xplore-dark .item__bg{background-color:#12161e!important;}' +
                        'html.safeer-xplore-fs,html.safeer-xplore-fs body{overflow:hidden!important;background:#000!important;}' +
                        'html.safeer-xplore-fs video{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483646!important;object-fit:contain!important;background:#000!important;opacity:1!important;visibility:visible!important;display:block!important;}';
                    (document.head || document.documentElement).appendChild(xploreFix);
                    try { document.documentElement.style.colorScheme = 'dark'; } catch (_) {}
                    try {
                        if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) {
                            window.SafeerBridge.setChromeHidden(true);
                        }
                    } catch (_) {}
                    // #region agent log
                    try { window._safeerDbg('H112', 'tv_remote_nav.js:init', 'xplore kiosk chrome hidden', { path: (location.pathname || '').slice(0, 60) }); } catch (_) {}
                    // #endregion
                }

                /* hydra smash lives in site_hydra.js */
            } catch(e) {}

            // #region agent log
            try {
                window._safeerDbg('H1', 'UserScriptManager.kt:TV_REMOTE_init', 'layout', {
                    host: location.hostname,
                    path: location.pathname,
                    w: window.innerWidth,
                    h: window.innerHeight,
                    sw: (document.documentElement && document.documentElement.scrollWidth) || 0,
                    sh: (document.documentElement && document.documentElement.scrollHeight) || 0,
                    cinema: !!document.getElementById('tv-remote-cinema-style'),
                    xplore: (location.hostname || '').indexOf('xploretv') !== -1
                });
                if ((location.hostname || '').indexOf('xploretv') !== -1) {
                    var bs = window.getComputedStyle(document.body || document.documentElement);
                    window._safeerDbg('H8', 'UserScriptManager.kt:TV_REMOTE_init', 'xplore dark', {
                        bodyBg: (bs.backgroundColor || ''),
                        htmlScheme: (document.documentElement.style.colorScheme || ''),
                        hasDark: !!document.getElementById('tv-remote-xplore-dark'),
                        menuBg: (function(){ try { var m=document.querySelector('.menu'); return m?getComputedStyle(m).backgroundColor:''; } catch(e){ return ''; } })(),
                        barBg: (function(){ try { var b=document.getElementById('csh__menu_bar'); return b?getComputedStyle(b).backgroundColor:''; } catch(e){ return ''; } })()
                    });
                }
            } catch (_) {}
            // #endregion

            // 2. Ustvari OSD HUD element
            var hudTimer = null;
            function getOrCreateHud() {
                var hud = document.getElementById('safeer-tv-hud');
                if (!hud) {
                    hud = document.createElement('div');
                    hud.id = 'safeer-tv-hud';
                    (document.body || document.documentElement).appendChild(hud);
                }
                return hud;
            }

            window._safeer_show_osd = function(htmlContent, durationMs) {
                try {
                    var hud = getOrCreateHud();
                    hud.innerHTML = htmlContent;
                    hud.classList.add('visible');
                    if (hudTimer) clearTimeout(hudTimer);
                    hudTimer = setTimeout(function() {
                        hud.classList.remove('visible');
                    }, durationMs || 1500);
                } catch(_) {}
            };

            function formatTime(seconds) {
                if (!isFinite(seconds) || isNaN(seconds)) return '00:00';
                var m = Math.floor(seconds / 60);
                var s = Math.floor(seconds % 60);
                var mm = m < 10 ? '0' + m : '' + m;
                var ss = s < 10 ? '0' + s : '' + s;
                return mm + ':' + ss;
            }

            // 3. Predvajalniške funkcije za daljinec
            window._safeer_is_video_active = function() {
                var v = document.querySelector('video');
                var isWatch = location.pathname.indexOf('/watch') !== -1 || location.pathname.indexOf('/shorts') !== -1;
                var hydraWatch = (location.hostname || '').toLowerCase().indexOf('hydrahd') !== -1 &&
                    ((location.pathname || '').indexOf('/movie/') !== -1 || (location.pathname || '').indexOf('/tv/') !== -1);
                if (isWatch || hydraWatch || document.documentElement.classList.contains('safeer-hydra-fs')) return true;
                if (!v) return false;
                var r = v.getBoundingClientRect();
                return r.width > 800 && r.height > 400 && (v.videoWidth || 0) > 0;
            };

            window._safeer_toggle_play_pause = function() {
                try {
                    var v = document.querySelector('video');
                    var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    
                    // 1. HTML5 Video
                    if (v) {
                        if (v.paused) {
                            v.play().catch(function() {});
                            window._safeer_show_osd('▶ Predvajaj &nbsp;<span style="color:#00e5ff; font-size:18px;">' + formatTime(v.currentTime) + ' / ' + formatTime(v.duration) + '</span>', 1400);
                        } else {
                            v.pause();
                            window._safeer_show_osd('⏸ Premor &nbsp;<span style="color:#ffd700; font-size:18px;">' + formatTime(v.currentTime) + ' / ' + formatTime(v.duration) + '</span>', 1800);
                        }
                        return true;
                    }

                    // 2. Iframe Multi-Protocol Message Broadcast (StreamNexus engine)
                    var iframes = document.querySelectorAll('iframe');
                    if (iframes.length > 0) {
                        var msgs = [
                            '{"event":"command","func":"togglePlay","args":""}',
                            '{"event":"command","func":"pauseVideo","args":""}',
                            '{"event":"command","func":"playVideo","args":""}',
                            JSON.stringify({ method: 'toggle' }),
                            JSON.stringify({ event: 'command', func: 'toggle' }),
                            JSON.stringify({ type: 'player:toggle' }),
                            'toggle', 'playPause', 'pause', 'play'
                        ];
                        for (var i = 0; i < iframes.length; i++) {
                            for (var m = 0; m < msgs.length; m++) {
                                try { iframes[i].contentWindow.postMessage(msgs[m], '*'); } catch(_) {}
                            }
                        }
                        window._safeer_show_osd('⏯ Predvajalnik preklopljen', 1400);
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            window._safeer_seek = function(deltaSeconds) {
                try {
                    var v = document.querySelector('video');
                    if (!v) return false;
                    var newTime = Math.max(0, Math.min(v.duration || 99999, v.currentTime + deltaSeconds));
                    v.currentTime = newTime;
                    var sign = deltaSeconds > 0 ? '+ ' + deltaSeconds + 's ⏩' : deltaSeconds + 's ⏪';
                    var color = deltaSeconds > 0 ? '#00e5ff' : '#38bdf8';
                    window._safeer_show_osd(
                        '<span style="color:' + color + ';">' + sign + '</span> &nbsp; <span style="font-size:18px;">' + formatTime(newTime) + ' / ' + formatTime(v.duration) + '</span>',
                        1400
                    );
                    return true;
                } catch(_) {}
                return false;
            };

            window._safeer_toggle_fullscreen = function() {
                try {
                    if (window._safeer_hydra_smash && (location.hostname || '').toLowerCase().indexOf('hydrahd') !== -1) {
                        if (document.documentElement.classList.contains('safeer-hydra-fs')) {
                            if (window._safeer_hydra_unsmash) window._safeer_hydra_unsmash();
                            window._safeer_show_osd('🗗 Običajen pogled', 1200);
                        } else {
                            window._safeer_hydra_smash();
                            window._safeer_show_osd('⛶ Celozaslonski način', 1200);
                        }
                        return true;
                    }
                    var fsBtn = document.querySelector('.ytp-fullscreen-button, button[aria-label*="celozaslon"], button[aria-label*="Fullscreen"], button[aria-label*="Full screen"], .fullscreen-button');
                    if (fsBtn) {
                        fsBtn.click();
                        window._safeer_show_osd('⛶ Celozaslonski način', 1200);
                        return true;
                    }
                    var v = document.querySelector('video');
                    if (v) {
                        if (document.fullscreenElement) {
                            document.exitFullscreen();
                            window._safeer_show_osd('🗗 Običajen pogled', 1200);
                        } else {
                            (v.parentElement || v).requestFullscreen().catch(function() {});
                            window._safeer_show_osd('⛶ Celozaslonski način', 1200);
                        }
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            window._safeer_next_video = function() {
                try {
                    var isShorts = location.pathname.indexOf('/shorts') !== -1;
                    if (isShorts) {
                        window.scrollBy({ top: window.innerHeight * 0.9, behavior: 'smooth' });
                        window._safeer_show_osd('⏭ Naslednji Short', 1000);
                        return true;
                    }
                    var nextBtn = document.querySelector('.ytp-next-button, button[aria-label*="Naslednji"], button[aria-label*="Next"], a[aria-label*="Next"]');
                    if (nextBtn) {
                        nextBtn.click();
                        window._safeer_show_osd('⏭ Naslednji video', 1200);
                        return true;
                    }
                    var nextRec = document.querySelector('ytm-compact-video-renderer a, ytd-compact-video-renderer a, ytm-video-with-context-renderer a, .media-item-thumbnail-container');
                    if (nextRec) {
                        nextRec.click();
                        window._safeer_show_osd('⏭ Predvajam naslednjega', 1200);
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            window._safeer_prev_video = function() {
                try {
                    var isShorts = location.pathname.indexOf('/shorts') !== -1;
                    if (isShorts) {
                        window.scrollBy({ top: -window.innerHeight * 0.9, behavior: 'smooth' });
                        window._safeer_show_osd('⏮ Prejšnji Short', 1000);
                        return true;
                    }
                    var prevBtn = document.querySelector('.ytp-prev-button, button[aria-label*="Prejšnji"], button[aria-label*="Previous"]');
                    if (prevBtn) {
                        prevBtn.click();
                        window._safeer_show_osd('⏮ Prejšnji video', 1200);
                        return true;
                    }
                    if (window.history.length > 1) {
                        window.history.back();
                        window._safeer_show_osd('⏮ Nazaj', 1000);
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            window._safeer_adjust_volume = function(delta) {
                try {
                    var v = document.querySelector('video');
                    if (v) {
                        var newVol = Math.max(0.0, Math.min(1.0, v.volume + delta));
                        v.volume = newVol;
                        v.muted = false;
                        var pct = Math.round(newVol * 100);
                        var icon = pct === 0 ? '🔇' : (pct > 60 ? '🔊' : '🔉');
                        window._safeer_show_osd(icon + ' Spletna glasnost: ' + pct + '%', 1200);
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            // 4. Inteligentna geometrijska prostorska navigacija po spletnih elementih (2D Spatial Navigation)
            function isXploreLivetvPath() {
                return ((location.pathname || '').toLowerCase()).indexOf('/livetv') !== -1;
            }

            function isXploreFooterEl(el) {
                if (!el) return false;
                try {
                    if (el.closest && el.closest('footer, .footer, [class*="cookie"], [class*="piškot"], [class*="piskot"]')) return true;
                } catch (_) {}
                var t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (t.indexOf('piškot') !== -1 || t.indexOf('piskot') !== -1 || t.indexOf('cookie') !== -1) return true;
                var href = ((el.getAttribute && el.getAttribute('href')) || '').toLowerCase();
                if (href.indexOf('cookie') !== -1) return true;
                var r;
                try { r = el.getBoundingClientRect(); } catch (_) { r = null; }
                if (r && r.top > ((window.innerHeight || 1080) - 70) && r.height < 48) return true;
                return false;
            }

            function isXploreContentTile(el) {
                if (!el) return false;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('item--event') !== -1) return true;
                if (cls.indexOf('content-carousel__item') !== -1) return true;
                if (cls.indexOf('editorial') !== -1 && cls.indexOf('item') !== -1) return true;
                if (!isXploreLivetvPath() && cls.indexOf('channel-container') !== -1) return true;
                try {
                    if (el.closest && el.closest('[class*="content-carousel__item"], .item.item--event, .item--event')) return true;
                    if (!isXploreLivetvPath() && el.closest && el.closest('.channel-container, [class*="channel-container"]')) return true;
                } catch (_) {}
                return false;
            }

            function isXploreActionField(el) {
                if (!el) return false;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('action-list') !== -1) return true;
                if (cls.indexOf('promo-carousel__button') !== -1) return true;
                var t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (!t || t.length > 56) return false;
                return t.indexOf('predvajaj') !== -1 || t.indexOf('glej zdaj') !== -1 ||
                    t.indexOf('glej oddajo') !== -1 || t.indexOf('na začetek') !== -1 ||
                    t.indexOf('na zacetek') !== -1 || t.indexOf('priljubljen') !== -1 ||
                    t.indexOf('posnemi') !== -1 || t.indexOf('dodaj med') !== -1;
            }

            function pickXploreContentTile() {
                var firstLive = isXploreLivetvPath() ? document.getElementById('csh__first_item') : null;
                if (firstLive) {
                    try {
                        var fr0 = firstLive.getBoundingClientRect();
                        if (fr0.width >= 80 && fr0.height >= 50 && fr0.bottom > 80) return firstLive;
                    } catch (_) {}
                }
                var nodes = document.querySelectorAll(
                    '.item.item--event, .item--event, [class*="content-carousel__item"], [class*="editorial-item"], .content-carousel a, [class*="content-carousel"] a, .channel-container, [class*="channel-container"]'
                );
                var best = null;
                var bestScore = 1e12;
                var i, el, r, score;
                var winH = window.innerHeight || 1080;
                var winW = window.innerWidth || 1920;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    if (isXploreFooterEl(el)) continue;
                    var ncls = ((el.className || '') + '').toLowerCase();
                    if (isXploreLivetvPath() && ncls.indexOf('channel-container') !== -1 && ncls.indexOf('item--event') === -1) continue;
                    r = el.getBoundingClientRect();
                    if (r.width < 120 || r.height < 70) continue;
                    if (r.width > winW * 0.72 || r.height > winH * 0.62) continue;
                    if (r.top < 52 || r.top > winH - 80) continue;
                    if (r.left > winW - 80 || r.right < 40) continue;
                    score = r.top * 8 + r.left;
                    if ((el.id || '') === 'csh__first_item') score -= 8000;
                    if (ncls.indexOf('item--event') !== -1) score -= 60;
                    if (score < bestScore) {
                        bestScore = score;
                        best = el;
                    }
                }
                if (best) {
                    var root = null;
                    try {
                        root = best.closest && best.closest('[class*="content-carousel__item"], .item.item--event, .item--event');
                        if (!root && !isXploreLivetvPath()) {
                            root = best.closest && best.closest('.channel-container, [class*="channel-container"]');
                        }
                    } catch (_) {}
                    return root || best;
                }
                return null;
            }

            function findCardContainer(el) {
                if (!el || el === document.body || el === document.documentElement) return null;
                var tag = el.tagName.toUpperCase();
                var cls = (el.className || '').toString().toLowerCase();
                var text = (el.textContent || '').trim().toLowerCase();

                // Gumbi za neposredno dejanje (npr. 'Glej Zdaj', 'Predvajaj', 'Prijava', 'Zapri') naj ostanejo samostojni cilji!
                if (tag === 'BUTTON' || tag === 'INPUT' || (tag === 'A' && (cls.indexOf('btn') !== -1 || text.indexOf('glej') !== -1 || text.indexOf('predvajaj') !== -1 || text.indexOf('prijava') !== -1))) {
                    return null;
                }
                if (isHydraHost() && (cls.indexOf('video-play-button') !== -1 || cls.indexOf('slidebtn') !== -1)) {
                    return null;
                }
                if (isHydraChromeEl(el) || isHydraNavArrow(el)) {
                    return null;
                }

                var curr = el;
                var highestCard = null;
                while (curr && curr !== document.body && curr !== document.documentElement) {
                    var cTag = curr.tagName.toUpperCase();
                    var cCls = (curr.className || '').toString().toLowerCase();
                    
                    // Hero pasica ni kartica programa ampak vsebovalnik z gumbi
                    if (cCls.indexOf('hero') !== -1 || cCls.indexOf('slick-slider') !== -1 || cCls.indexOf('promo-carousel') !== -1) {
                        break;
                    }

                    var isCard = cCls.indexOf('item--event') !== -1 ||
                                 cCls.indexOf('content-carousel__item') !== -1 ||
                                 cCls.indexOf('channel-card') !== -1 ||
                                 cCls.indexOf('program-card') !== -1 ||
                                 cCls.indexOf('video-card') !== -1 || cCls.indexOf('media-card') !== -1 ||
                                 (cCls.indexOf('tile') !== -1 && cTag !== 'BODY');
                    if (cCls.indexOf('slick-slide') !== -1 && cCls.indexOf('item--event') === -1 &&
                        cCls.indexOf('content-carousel__item') === -1) {
                        isCard = false;
                    }
                                 
                    if (isCard) {
                        var rect = curr.getBoundingClientRect();
                        if (rect.width >= 60 && rect.height >= 40 && rect.width < 1600) {
                            highestCard = curr;
                            if (cCls.indexOf('item--event') !== -1 || cCls.indexOf('content-carousel__item') !== -1) {
                                break;
                            }
                        }
                    }
                    curr = curr.parentElement;
                }
                return highestCard;
            }

            function isActionable(el) {
                if (!el || el.nodeType !== 1) return false;
                var tag = el.tagName.toUpperCase();
                if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'HTML' || tag === 'BODY' || tag === 'SVG' || tag === 'PATH') return false;
                var href = (el.getAttribute && el.getAttribute('href')) || '';
                if (href.charAt(0) === '#' || (el.className && ('' + el.className).indexOf('wUrY2b') !== -1)) return false;
                if (isHydraBrandEl(el) || isHydraChromeEl(el) || isHydraNavArrow(el)) return false;
                var hydraCls = (el.className || '').toString().toLowerCase();
                if (isHydraHost() && hydraCls.indexOf('video-play-button') !== -1) return true;
                if (isHydraHost() && hydraCls.indexOf('slidebtn') !== -1) return true;
                
                // Izloči zgolj tekstovne elemente in časovne oznake znotraj kartic (čas je del celotnega polja vsebine)
                if (tag === 'SPAN' || tag === 'TIME' || tag === 'P' || tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'H4' || tag === 'LABEL' || tag === 'SMALL' || tag === 'EM' || tag === 'STRONG') {
                    if (!el.hasAttribute('onclick') && el.getAttribute('role') !== 'button' && el.tagName !== 'A') {
                        return false;
                    }
                }
                
                var cls = (el.className || '').toString().toLowerCase();
                if (isXploreFooterEl(el)) return false;
                if (isXploreLivetvPath() && cls.indexOf('channel-container') !== -1 && cls.indexOf('item--event') === -1) return false;
                if (cls.indexOf('channellists') !== -1 || cls.indexOf('icon-p24_channellist') !== -1) return false;
                if (cls.indexOf('time') !== -1 || cls.indexOf('duration') !== -1 || cls.indexOf('badge') !== -1 || cls.indexOf('progress') !== -1 || cls.indexOf('subtitle') !== -1) {
                    if (!el.hasAttribute('onclick') && el.getAttribute('role') !== 'button') {
                        return false;
                    }
                }

                var rect = el.getBoundingClientRect();
                if (rect.width < 18 || rect.height < 14) return false;
                var winW = window.innerWidth || 1920;
                var winH = window.innerHeight || 1080;
                var clsCheck = (el.className || '').toString().toLowerCase();
                var playTxtEarly = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                var isPlayActEarly = clsCheck.indexOf('action-list') !== -1 ||
                    clsCheck.indexOf('promo-carousel__button') !== -1 ||
                    (playTxtEarly.length > 0 && playTxtEarly.length <= 64 && (
                        playTxtEarly.indexOf('predvajaj v živo') !== -1 || playTxtEarly.indexOf('predvajaj v zivo') !== -1 ||
                        playTxtEarly.indexOf('glej zdaj') !== -1 || playTxtEarly.indexOf('glej oddajo') !== -1
                    ));
                if (isPlayActEarly) return true;
                if (clsCheck.indexOf('menu-items-wrapper') !== -1) return false;
                if (clsCheck.indexOf('home-link') !== -1) {
                    var homeTxt = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    if (homeTxt.indexOf('za vas') === -1) return false;
                }
                if (clsCheck.indexOf('slick-slider') !== -1 || clsCheck.indexOf('promo-carousel') !== -1) return false;
                if (clsCheck.indexOf('icon-p24_search') !== -1) return true;
                if (tag === 'LI' && (' ' + clsCheck + ' ').indexOf(' search ') !== -1) return true;
                if (clsCheck.indexOf('search-card') !== -1 || clsCheck.indexOf('shield-card') !== -1 ||
                    clsCheck.indexOf('clock-widget') !== -1 || clsCheck.indexOf('engine-chips') !== -1 ||
                    clsCheck.indexOf('favorites-grid') !== -1) {
                    return false;
                }
                if (rect.width > winW * 0.48 || rect.height > winH * 0.42) {
                    var wideTxt = ((el.innerText || el.textContent || '') + '').toLowerCase();
                    var widePlay = clsCheck.indexOf('action-list') !== -1 || wideTxt.indexOf('predvajaj') !== -1 || wideTxt.indexOf('glej zdaj') !== -1;
                    if (isHydraHost() && clsCheck.indexOf('slidebtn') === -1 && clsCheck.indexOf('video-play-button') === -1) return false;
                    if (!widePlay && clsCheck.indexOf('item--event') === -1 && clsCheck.indexOf('content-carousel__item') === -1 && clsCheck.indexOf('channel-container') === -1) {
                        if (tag !== 'A' && tag !== 'BUTTON' && tag !== 'INPUT' && tag !== 'TEXTAREA') return false;
                    }
                }
                
                var style = window.getComputedStyle(el);
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;

                var playTxt = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                var isPlayAct = clsCheck.indexOf('action-list') !== -1 ||
                    (playTxt.length > 0 && playTxt.length <= 64 && (
                    playTxt.indexOf('predvajaj v živo') !== -1 || playTxt.indexOf('predvajaj v zivo') !== -1 ||
                    playTxt.indexOf('glej zdaj') !== -1 || playTxt.indexOf('glej oddajo') !== -1));
                if (isPlayAct) return true;

                if (cls.indexOf('content-carousel__item') !== -1) return true;
                if (tag === 'A' || tag === 'BUTTON' || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
                if (el.hasAttribute('onclick') || el.hasAttribute('data-ved') || el.hasAttribute('tabindex')) return true;
                if (el.getAttribute('role') === 'button' || el.getAttribute('role') === 'link' || el.getAttribute('role') === 'tab') return true;
                if (cls.indexOf('item--event') !== -1) return true;
                
                // TV Grid kartice, celotni programski bloki, filmi, epizode, EPG vrstice, drsniki
                if (cls.indexOf('card') !== -1 || cls.indexOf('tile') !== -1 || cls.indexOf('item') !== -1 || 
                    cls.indexOf('channel') !== -1 || cls.indexOf('poster') !== -1 || cls.indexOf('program') !== -1 || 
                    cls.indexOf('epg') !== -1 || cls.indexOf('slider') !== -1 || cls.indexOf('slick') !== -1 || 
                    cls.indexOf('media') !== -1 || cls.indexOf('thumb') !== -1 || cls.indexOf('movie') !== -1 || 
                    cls.indexOf('watch') !== -1 || cls.indexOf('show') !== -1) {
                    return true;
                }
                
                return false;
            }

            function getCandidates() {
                var nowMs = Date.now();
                if (window._safeerCandCache && (nowMs - (window._safeerCandAt || 0)) < 80) {
                    return window._safeerCandCache;
                }
                var found = getCandidatesFresh();
                window._safeerCandCache = found;
                window._safeerCandAt = nowMs;
                return found;
            }
            function getCandidatesFresh() {
                    if ((location.href || '').indexOf('brave_home') !== -1) {
                        var homeEls = document.querySelectorAll('.hero-card, .engine-chip, .favorite-tile, .edit-chip, .search-input, .search-btn');
                        var homeOut = [];
                        for (var hi = 0; hi < homeEls.length; hi++) homeOut.push(homeEls[hi]);
                        if (homeOut.length) return homeOut;
                    }
                    if (is24urHost()) {
                        var news = [];
                        if (typeof window._safeer_24ur_cards === 'function') {
                            try { news = window._safeer_24ur_cards() || []; } catch (_) { news = []; }
                        }
                        if (!news.length) {
                            var rawCards = document.querySelectorAll('a.card, .submenu a, .menu__item a');
                            for (var nc = 0; nc < rawCards.length; nc++) news.push(rawCards[nc]);
                        }
                        if (news.length) return news;
                    }
                    if (isHydraHost()) {
                        var hydraEls = getHydraCandidates();
                        if (hydraEls.length) return hydraEls;
                    }
                    if (isXploreHost()) {
                        var fast = [];
                        function addFast(el) {
                            if (!el || fast.indexOf(el) !== -1) return;
                            if (isXploreFooterEl(el)) return;
                            var acls = ((el.className || '') + '');
                            if (!isActionable(el) && acls.indexOf('item--event') === -1 &&
                                acls.indexOf('content-carousel__item') === -1 &&
                                acls.indexOf('action-list') === -1 && !isXploreActionField(el)) return;
                            var root = findCardContainer(el) || el;
                            if (isXploreActionField(el) && ((el.className || '') + '').indexOf('action-list') !== -1) {
                                root = el;
                            }
                            try {
                                var rw = root.getBoundingClientRect();
                                if (rw.width > 620 && !isXploreActionField(root) &&
                                    ((root.className || '') + '').indexOf('item--event') === -1 &&
                                    ((root.className || '') + '').indexOf('content-carousel__item') === -1) {
                                    var inner = root.querySelector && root.querySelector('.item.item--event, .item--event, [class*="content-carousel__item"]');
                                    if (inner) root = inner;
                                }
                            } catch (_) {}
                            if (isXploreLivetvPath()) {
                                var rc = ((root.className || '') + '').toLowerCase();
                                if (rc.indexOf('channel-container') !== -1 && rc.indexOf('item--event') === -1) {
                                    var prog = root.querySelector && root.querySelector('.item.item--event, .item--event');
                                    if (prog) root = prog;
                                    else return;
                                }
                            }
                            if (fast.indexOf(root) !== -1) return;
                            fast.push(root);
                        }
                        var menuFast = getXploreMenuLinks();
                        for (var mf = 0; mf < menuFast.length; mf++) addFast(menuFast[mf]);
                        var searchEl = document.querySelector('.icon-p24_search, [class*="icon-p24_search"], li.search a');
                        if (searchEl) addFast(searchEl.closest && (searchEl.closest('a, li, button') || searchEl) || searchEl);
                        var actNodes = document.querySelectorAll('[class*="action-list"], .promo-carousel__button');
                        for (var an = 0; an < actNodes.length; an++) {
                            var actEl = actNodes[an];
                            var ar = actEl.getBoundingClientRect();
                            if (ar.width < 28 || ar.height < 20) continue;
                            if (ar.top < 40 || ar.top > ((window.innerHeight || 1080) - 20)) continue;
                            addFast(actEl);
                        }
                        if (isXploreLivetvPath()) {
                            var liveTiles = document.querySelectorAll('.item.item--event, .item--event');
                            for (var lt = 0; lt < liveTiles.length; lt++) addFast(liveTiles[lt]);
                        } else {
                            var homeTiles = document.querySelectorAll('.item.item--event, .item--event, [class*="content-carousel__item"]');
                            for (var ht = 0; ht < homeTiles.length; ht++) addFast(homeTiles[ht]);
                        }
                        if (fast.length) return fast;
                    }
                var all = document.querySelectorAll('a, button, input, textarea, select, [role="button"], [role="link"], [role="tab"], [tabindex], [data-ved], [onclick], .video-play-button, [class*="card"], [class*="tile"], [class*="item"], [class*="channel"], [class*="poster"], [class*="program"], [class*="epg"], [class*="movie"], [class*="media"], [class*="slick-slide"], article, li.search, .icon-p24_search, [class*="icon-p24_search"]');
                var raw = [];
                for (var i = 0; i < all.length; i++) {
                    var el = all[i];
                    if (isActionable(el)) {
                        var cardRoot = findCardContainer(el);
                        var finalEl = cardRoot || el;
                        if (raw.indexOf(finalEl) === -1) {
                            raw.push(finalEl);
                        }
                    }
                }
                
                // Keep .item--event tiles; drop wrapping .livetv-griditem parents
                var candidates = [];
                for (var j = 0; j < raw.length; j++) {
                    var item = raw[j];
                    var itemCls = ((item.className || '') + '').toLowerCase();
                    var isNested = false;
                    var dropWrapper = false;
                    for (var k = 0; k < raw.length; k++) {
                        if (j === k) continue;
                        if (raw[k].contains(item)) {
                            var pcls = ((raw[k].className || '') + '').toLowerCase();
                            if (itemCls.indexOf('item--event') !== -1 && pcls.indexOf('livetv-griditem') !== -1) {
                                continue;
                            }
                            if (itemCls.indexOf('content-carousel__item') !== -1 && pcls.indexOf('slick') !== -1) {
                                continue;
                            }
                            isNested = true;
                            break;
                        }
                        if (item.contains(raw[k])) {
                            var cclsK = ((raw[k].className || '') + '').toLowerCase();
                            if (itemCls.indexOf('livetv-griditem') !== -1 && itemCls.indexOf('item--event') === -1 && cclsK.indexOf('item--event') !== -1) {
                                dropWrapper = true;
                                break;
                            }
                            if (itemCls.indexOf('slick') !== -1 && cclsK.indexOf('content-carousel__item') !== -1) {
                                dropWrapper = true;
                                break;
                            }
                        }
                    }
                    if (!isNested && !dropWrapper) {
                        if (isXploreFooterEl(item)) {
                            continue;
                        }
                        if (itemCls.indexOf('slick-slide') !== -1 && itemCls.indexOf('item--event') === -1 &&
                            itemCls.indexOf('content-carousel__item') === -1) {
                            continue;
                        }
                        if (isXploreLivetvPath() && itemCls.indexOf('channel-container') !== -1 && itemCls.indexOf('item--event') === -1) {
                            continue;
                        }
                        if (itemCls.indexOf('channellists') !== -1) {
                            continue;
                        }
                        candidates.push(item);
                    }
                }
                return candidates;
            }

            function getActiveElement() {
                var current = document.querySelector('.safeer-active-card');
                if (current && current.isConnected) {
                    try {
                        var hr = isHydraHost() ? hydraLayoutRect(current) : current.getBoundingClientRect();
                        if (hr.width >= 8 && hr.height >= 8) return current;
                        var hKeep = ((current.className || '') + '').toLowerCase();
                        if (isHydraHost() && (hKeep.indexOf('hthis') !== -1 || hKeep.indexOf('slidebtn') !== -1 ||
                            hKeep.indexOf('tab') !== -1 || hKeep.indexOf('seasonheader') !== -1 ||
                            hKeep.indexOf('video-play-button') !== -1 || hKeep.indexOf('dynamic-ep-link') !== -1)) {
                            return current;
                        }
                    } catch (_) {
                        return current;
                    }
                }
                var act = document.activeElement;
                if (act && act !== document.body && act !== document.documentElement && isActionable(act)) return act;
                return null;
            }

            window._safeer_xplore_ensure_focus = function() {
                try {
                    var cur = document.querySelector('.safeer-active-card') || getActiveElement();
                    function isXploreMenuEl(el) {
                        if (!el) return false;
                        var cls = ((el.className || '') + '').toLowerCase();
                        if (cls.indexOf('menu-items-wrapper') !== -1) return false;
                        if (cls.indexOf('logo') !== -1 && cls.indexOf('menu-item') === -1) return false;
                        if (cls.indexOf('livetv-link') !== -1 || cls.indexOf('menu-item') !== -1) return true;
                        try {
                            if (el.closest && el.closest('.menu, #csh__menu_bar, .menu-items-wrapper')) return true;
                        } catch (_) {}
                        var tx = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (tx === 'za vas' || tx === 'tv v živo' || tx === 'tv v zivo' || tx === 'filmi' ||
                            tx.indexOf('knjižnic') !== -1 || tx.indexOf('knjiznic') !== -1 || tx === 'več' || tx === 'vec') return true;
                        return false;
                    }
                    if (cur) {
                        var keepCls = ((cur.className || '') + '').toLowerCase();
                        if (keepCls.indexOf('item--event') !== -1 || keepCls.indexOf('content-carousel__item') !== -1 ||
                            keepCls.indexOf('action-list') !== -1 || isXploreActionField(cur) || isXploreMenuEl(cur)) {
                            highlightElement(cur);
                            return getActiveElement();
                        }
                    }
                    if (cur && isXploreMenuEl(cur) && window._safeer_xplore_did_focus) {
                        // #region agent log
                        try { window._safeerDbg('H120', 'tv_remote_nav.js:ensure_focus', 'keep menu', { cls: ((cur.className || '') + '').slice(0, 60) }); } catch (_) {}
                        // #endregion
                        highlightElement(cur);
                        return getActiveElement();
                    }
                    if (!window._safeer_xplore_did_focus) {
                        var pathEf = (location.pathname || '').toLowerCase();
                        if (pathEf.indexOf('/event') !== -1) {
                            var playFirst = pickXplorePlayAction();
                            if (playFirst) {
                                highlightElement(playFirst);
                                var playGot = getActiveElement();
                                // #region agent log
                                try {
                                    window._safeerDbg('H261', 'tv_remote_nav.js:ensure_focus', 'play first', {
                                        got: !!(playGot),
                                        cls: ((playFirst.className || '') + '').slice(0, 70),
                                        act: !!isActionable(playFirst)
                                    });
                                } catch (_) {}
                                // #endregion
                                return playGot;
                            }
                        } else if (pathEf.indexOf('/livetv') === -1) {
                            var homePlay = pickXplorePlayAction();
                            if (homePlay) {
                                highlightElement(homePlay);
                                return getActiveElement();
                            }
                        }
                    }
                    if (!window._safeer_xplore_did_focus && cur && isXploreMenuEl(cur)) {
                        // #region agent log
                        try { window._safeerDbg('H263', 'tv_remote_nav.js:ensure_focus', 'skip initial menu', { cls: ((cur.className || '') + '').slice(0, 60) }); } catch (_) {}
                        // #endregion
                        cur = null;
                    }
                    if (cur && isXploreFooterEl(cur)) {
                        clearActive();
                        cur = null;
                    }
                    if (cur) {
                        var cclsMap = ((cur.className || '') + '').toLowerCase();
                        if (cclsMap.indexOf('livetv-griditem') !== -1 && cclsMap.indexOf('item--event') === -1) {
                            var childEv = cur.querySelector('.item.item--event, .item--event');
                            if (childEv) {
                                // #region agent log
                                try {
                                    window._safeerDbg('H95', 'tv_remote_nav.js:ensure_focus', 'remap griditem', {
                                        from: cclsMap.slice(0, 60),
                                        to: ((childEv.className || '') + '').slice(0, 70)
                                    });
                                } catch (_) {}
                                // #endregion
                                cur = childEv;
                            }
                        }
                        if (isXploreLivetvPath() && cclsMap.indexOf('channel-container') !== -1 && cclsMap.indexOf('item--event') === -1) {
                            var mappedProg = livetvProgramFromHeader(cur);
                            // #region agent log
                            try {
                                window._safeerDbg('H301', 'tv_remote_nav.js:ensure_focus', 'skip channel logo', {
                                    got: !!mappedProg,
                                    to: mappedProg ? ((mappedProg.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) : ''
                                });
                            } catch (_) {}
                            // #endregion
                            if (mappedProg) cur = mappedProg;
                            else {
                                clearActive();
                                cur = null;
                            }
                        }
                    }
                    if (cur) {
                        var cr0 = cur.getBoundingClientRect();
                        var ccls = ((cur.className || '') + '').toLowerCase();
                        var ctx = ((cur.innerText || cur.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        var isPlayAction = ccls.indexOf('action-list') !== -1 ||
                            ctx.indexOf('predvajaj v živo') !== -1 || ctx.indexOf('predvajaj v zivo') !== -1 ||
                            ctx.indexOf('glej zdaj') !== -1 || ctx.indexOf('glej oddajo') !== -1 ||
                            ctx.indexOf('predvajaj') === 0;
                        var isProg = ccls.indexOf('item--event') !== -1 || isXploreContentTile(cur);
                        var isLogoOnly = ccls.indexOf('logo') !== -1 && ctx.indexOf('za vas') === -1;
                        var tooBig = cr0.width > 700 && !isProg;
                        var off = !isXploreMenuEl(cur) && (cr0.top < 48 || cr0.bottom < 40 || cr0.top > ((window.innerHeight || 1080) - 40) || cr0.right < 40 || cr0.left > ((window.innerWidth || 1920) - 40));
                        if (isPlayAction && ctx.length <= 64) {
                            // #region agent log
                            try { window._safeerDbg('H221', 'tv_remote_nav.js:ensure_focus', 'keep play action', { cls: ccls.slice(0, 70) }); } catch (_) {}
                            // #endregion
                            highlightElement(cur);
                            return getActiveElement();
                        }
                        if (isLogoOnly || (!isProg && !isXploreMenuEl(cur)) || tooBig || off) {
                            clearActive();
                            cur = null;
                        }
                    }
                    if (cur) {
                        highlightElement(cur);
                        return getActiveElement();
                    }
                    var els = document.querySelectorAll('.item.item--event, .item--event, .livetv-griditem__item, [class*="livetv-griditem"]');
                    var i, el, r, pick = null;
                    for (i = 0; i < els.length; i++) {
                        el = els[i];
                        var pcls = ((el.className || '') + '').toLowerCase();
                        if (pcls.indexOf('livetv-griditem') !== -1 && pcls.indexOf('item--event') === -1) {
                            var childPick = el.querySelector('.item.item--event, .item--event, .livetv-griditem__item');
                            if (childPick) el = childPick;
                        }
                        r = el.getBoundingClientRect();
                        if (r.width < 80 || r.height < 50) continue;
                        if (r.top < 48 || r.bottom > ((window.innerHeight || 1080) - 8)) continue;
                        pick = el;
                        break;
                    }
                    if (!pick) pick = document.getElementById('csh__first_item') || (els[0] || null);
                    if (!pick) pick = pickXploreContentTile();
                    if (pick) highlightElement(pick);
                    return getActiveElement();
                } catch (_) {}
                return null;
            };

            function getOrCreateFocusRing() {
                var ring = document.getElementById('safeer-focus-target-ring');
                if (!ring) {
                    ring = document.createElement('div');
                    ring.id = 'safeer-focus-target-ring';
                    ring.innerHTML = '<span class="safeer-focus-badge">⚡</span>';
                    (document.body || document.documentElement).appendChild(ring);
                }
                return ring;
            }

            function updateFocusRing(el) {
                try {
                    var ring = getOrCreateFocusRing();
                    if (document.documentElement.classList.contains('safeer-xplore-fs')) {
                        ring.classList.remove('active');
                        ring.style.display = 'none';
                        return;
                    }
                    ring.style.display = '';
                    if (!el) {
                        ring.classList.remove('active');
                        return;
                    }
                    var r = isHydraHost() ? hydraLayoutRect(el) : el.getBoundingClientRect();
                    if (r.width === 0 && r.height === 0) {
                        ring.classList.remove('active');
                        return;
                    }
                    var pad = isXploreHost() ? 10 : 4;
                    ring.style.top = (r.top - pad) + 'px';
                    ring.style.left = (r.left - pad) + 'px';
                    ring.style.width = (r.width + pad * 2) + 'px';
                    ring.style.height = (r.height + pad * 2) + 'px';
                    ring.classList.add('active');
                } catch(_) {}
            }

            function clearActive() {
                var prevs = document.querySelectorAll('.safeer-active-card');
                for (var i = 0; i < prevs.length; i++) {
                    prevs[i].classList.remove('safeer-active-card');
                }
                updateFocusRing(null);
            }

            function highlightElement(el) {
                window._safeerCandCache = null;
                clearActive();
                if (!el) return;
                window._safeer_xplore_did_focus = true;
                el.classList.add('safeer-active-card');
                try { if (((el.className || '') + '').indexOf('item--event') !== -1) el.tabIndex = 0; } catch (_) {}
                var tag = (el.tagName || '').toUpperCase();
                // Never auto-focus INPUT/TEXTAREA — that opens the TV IME and traps the remote.
                if (tag !== 'INPUT' && tag !== 'TEXTAREA') {
                    try { el.focus({ preventScroll: true }); } catch(_) {}
                } else {
                    try { el.blur(); } catch(_) {}
                }
                var hr = isHydraHost() ? hydraLayoutRect(el) : el.getBoundingClientRect();
                var winHRing = window.innerHeight || 1080;
                if (isHydraHost()) {
                    if (hr.bottom < 64 || hr.top > winHRing - 64) {
                        try { el.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' }); } catch (_) {}
                    }
                } else if (hr.width < 800 && hr.height < 500) {
                    try { el.scrollIntoView({ behavior: 'auto', block: 'nearest', inline: 'nearest' }); } catch (_) {}
                }
                if (isXploreHost() && document.documentElement.classList.contains('safeer-xplore-fs')) updateFocusRing(null);
                else updateFocusRing(el);
                // #region agent log
                try {
                    window._safeerDbgHlN = (window._safeerDbgHlN || 0) + 1;
                    if (window._safeerDbgHlN <= 8) {
                        window._safeerDbg('H50', 'tv_remote_nav.js:hl', 'highlight', {
                            tag: tag,
                            cls: ((el.className || '') + '').toString().slice(0, 70),
                            w: Math.round(hr.width),
                            h: Math.round(hr.height),
                            t: Math.round(hr.top),
                            l: Math.round(hr.left)
                        });
                    }
                } catch (_) {}
                // #endregion
            }

            window._safeer_highlight = highlightElement;

            window.addEventListener('scroll', function() {
                if (window._safeerRingRaf) return;
                window._safeerRingRaf = requestAnimationFrame(function () {
                    window._safeerRingRaf = 0;
                    var curr = getActiveElement();
                    if (curr) updateFocusRing(curr);
                });
            }, { passive: true });

            window.addEventListener('resize', function() {
                var curr = getActiveElement();
                if (curr) updateFocusRing(curr);
            }, { passive: true });

            function isXploreChannel(el) {
                if (!el) return false;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('item--event') !== -1) return false;
                if (cls.indexOf('channel-container') !== -1) return true;
                try {
                    if (el.closest && el.closest('.channel-container, [class*="channel-container"]')) return true;
                } catch (_) {}
                return false;
            }

            function livetvProgramFromHeader(el) {
                if (!el) return null;
                try {
                    var root = (el.closest && el.closest('.livetv-griditem, .zw_column, [class*="livetv-griditem"]')) || el.parentElement;
                    var tile = root ? root.querySelector('.item.item--event, .item--event') : null;
                    if (tile && tile !== el) return tile;
                } catch (_) {}
                try {
                    var sib = el.nextElementSibling;
                    while (sib) {
                        var sc = ((sib.className || '') + '').toLowerCase();
                        if (sc.indexOf('item--event') !== -1) return sib;
                        var inner = sib.querySelector && sib.querySelector('.item.item--event, .item--event');
                        if (inner) return inner;
                        sib = sib.nextElementSibling;
                    }
                } catch (_) {}
                try {
                    var hr = el.getBoundingClientRect();
                    var cx = hr.left + hr.width / 2;
                    var tiles = document.querySelectorAll('.item.item--event, .item--event');
                    var best = null;
                    var bestD = 1e9;
                    for (var i = 0; i < tiles.length; i++) {
                        var tr = tiles[i].getBoundingClientRect();
                        if (tr.width < 80 || tr.height < 50) continue;
                        if (tr.top + 8 < hr.bottom) continue;
                        var dx = Math.abs((tr.left + tr.width / 2) - cx);
                        var dy = tr.top - hr.bottom;
                        if (dx > 90 || dy > 120) continue;
                        var d = dx + dy;
                        if (d < bestD) {
                            bestD = d;
                            best = tiles[i];
                        }
                    }
                    return best;
                } catch (_) {}
                return null;
            }

            function pickXploreChannelHeader() {
                var nodes = document.querySelectorAll('.channel-container, [class*="channel-container"]');
                var i, a, r, pick = null, bestLeft = 1e9;
                for (i = 0; i < nodes.length; i++) {
                    a = nodes[i];
                    r = a.getBoundingClientRect();
                    if (r.width < 80 || r.height < 20) continue;
                    if (r.top < 48 || r.top > 420) continue;
                    if (r.left >= -20 && r.left < bestLeft) {
                        bestLeft = r.left;
                        pick = a;
                    }
                }
                return pick;
            }

            function isXploreMenuLabel(t) {
                t = (t || '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (!t || t.length > 28) return false;
                if (t === 'za vas' || t === 'tv v živo' || t === 'tv v zivo' || t === 'filmi' ||
                    t === 'več' || t === 'vec' || t === 'družina' || t === 'druzina' || t === 'tv vodič' || t === 'tv vodic') return true;
                if (t.indexOf('knjižnic') !== -1 || t.indexOf('knjiznic') !== -1) return true;
                if (t.indexOf('vodič') !== -1 || t.indexOf('vodic') !== -1) return true;
                return false;
            }

            function isXploreMenuLink(el) {
                if (!el) return false;
                var cls = ((el.className || '') + '').toLowerCase();
                if (cls.indexOf('menu-items-wrapper') !== -1) return false;
                if (cls.indexOf('logo') !== -1 && cls.indexOf('menu-item') === -1) return false;
                try {
                    var rr = el.getBoundingClientRect();
                    if (rr.top > 150 || rr.height < 8) return false;
                } catch (_) { return false; }
                var t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (t.length > 28) return false;
                if (isXploreMenuLabel(t)) return true;
                var href = ((el.getAttribute && el.getAttribute('href')) || '') + '';
                if (isXploreMenuLabel(t) && /\/livetv|\/movies|\/library|\/gridguide|\/home/.test(href)) return true;
                return false;
            }

            function getXploreMenuLinks() {
                var nodes = document.querySelectorAll('.menu a, #csh__menu_bar a, .menu-items-wrapper a, a.livetv-link, a.home-link, li.menu-item a');
                var out = [];
                var seen = {};
                for (var i = 0; i < nodes.length; i++) {
                    var a = nodes[i];
                    var r = a.getBoundingClientRect();
                    if (r.width < 24 || r.height < 12) continue;
                    if (r.top > 150) continue;
                    var t = ((a.innerText || a.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    var cls = ((a.className || '') + '').toLowerCase();
                    if (cls.indexOf('logo') !== -1 && t.indexOf('za vas') === -1) continue;
                    if (!isXploreMenuLabel(t)) continue;
                    var href = ((a.getAttribute('href') || '') + '').toLowerCase();
                    var key = href + '|' + t;
                    if (seen[key]) continue;
                    seen[key] = true;
                    out.push(a);
                }
                out.sort(function (a, b) {
                    return a.getBoundingClientRect().left - b.getBoundingClientRect().left;
                });
                return out;
            }

            function pickXplorePlayAction() {
                var needles = ['predvajaj v živo', 'predvajaj v zivo', 'glej zdaj', 'glej oddajo od začetka', 'glej oddajo'];
                var nodes = document.querySelectorAll('button, a, [role="button"], [class*="action-list"]');
                var best = null;
                var bestPri = 99;
                var i, el, t, r, n, pri;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    t = ((el.innerText || el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    if (!t || t.length > 64) continue;
                    pri = 99;
                    for (n = 0; n < needles.length; n++) {
                        if (t.indexOf(needles[n]) !== -1 && n < pri) pri = n;
                    }
                    if (pri === 99) continue;
                    r = el.getBoundingClientRect();
                    if (r.width < 24 || r.height < 12) continue;
                    if (r.top < 50 || r.top > 520) continue;
                    if (pri < bestPri) {
                        bestPri = pri;
                        best = el;
                    }
                }
                return best;
            }

            function pickXploreMenuLink(preferLive, fromEl) {
                var links = getXploreMenuLinks();
                if (!links.length) return null;
                var i, href, t;
                if (preferLive) {
                    for (i = 0; i < links.length; i++) {
                        href = ((links[i].getAttribute('href') || '') + '').toLowerCase();
                        t = ((links[i].innerText || links[i].textContent || '') + '').toLowerCase();
                        if (href.indexOf('/livetv') !== -1 || t.indexOf('živo') !== -1 || t.indexOf('zivo') !== -1) {
                            return links[i];
                        }
                    }
                }
                if (fromEl) {
                    var fr = fromEl.getBoundingClientRect();
                    var cx = fr.left + fr.width / 2;
                    var best = links[0];
                    var bestD = 1e9;
                    for (i = 0; i < links.length; i++) {
                        var rr = links[i].getBoundingClientRect();
                        var d = Math.abs((rr.left + rr.width / 2) - cx);
                        if (d < bestD) {
                            bestD = d;
                            best = links[i];
                        }
                    }
                    return best;
                }
                var path = (location.pathname || '').toLowerCase();
                for (i = 0; i < links.length; i++) {
                    href = ((links[i].getAttribute('href') || '') + '').toLowerCase();
                    if (path && href && href.indexOf(path) !== -1) return links[i];
                }
                return links[0];
            }

            function moveXploreMenu(direction) {
                var links = getXploreMenuLinks();
                if (links.length < 2) return false;
                var cur = getActiveElement();
                var idx = -1;
                var i, t, tk;
                for (i = 0; i < links.length; i++) {
                    if (cur && (links[i] === cur || links[i].contains(cur) || (cur.contains && cur.contains(links[i])))) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0 && cur) {
                    t = ((cur.innerText || cur.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    for (i = 0; i < links.length; i++) {
                        tk = ((links[i].innerText || links[i].textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (tk && t && (tk === t || t.indexOf(tk) !== -1 || tk.indexOf(t) !== -1)) {
                            idx = i;
                            break;
                        }
                    }
                }
                if (idx < 0) return false;
                var next = direction === 'LEFT' ? idx - 1 : idx + 1;
                if (next < 0 || next >= links.length) return true;
                highlightElement(links[next]);
                // #region agent log
                try {
                    window._safeerDbg('H231', 'tv_remote_nav.js:menu', 'move', {
                        dir: direction,
                        from: idx,
                        to: next,
                        t: ((links[next].innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40),
                        href: ((links[next].getAttribute('href') || '') + '').slice(0, 50),
                        n: links.length
                    });
                } catch (_) {}
                // #endregion
                return true;
            }

            window._safeer_navigate_spatial = function(direction) {
                try {
                    var scrollY = window.scrollY || document.documentElement.scrollTop || 0;
                    var winH = window.innerHeight || 1080;
                    var winW = window.innerWidth || 1920;
                    var scrollEase = 'auto';

                    var current = getActiveElement();
                    if (isXploreLivetvPath() && current && isXploreChannel(current)) {
                        var fromLogo = livetvProgramFromHeader(current);
                        // #region agent log
                        try {
                            window._safeerDbg('H301', 'tv_remote_nav.js:spatial', 'skip channel logo', {
                                dir: direction,
                                got: !!fromLogo,
                                to: fromLogo ? ((fromLogo.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) : ''
                            });
                        } catch (_) {}
                        // #endregion
                        if (fromLogo) {
                            highlightElement(fromLogo);
                            if (direction === 'DOWN') return 1;
                            current = fromLogo;
                        }
                    }
                    var candidates = getCandidates();

                    if (candidates.length === 0) {
                        if (direction === 'DOWN') { window.scrollBy({ top: 120, behavior: scrollEase }); return 1; }
                        if (direction === 'UP') {
                            if (scrollY <= 20) return -1;
                            window.scrollBy({ top: -120, behavior: scrollEase });
                            return 1;
                        }
                        if (direction === 'LEFT') { window.scrollBy({ left: -140, behavior: scrollEase }); return 1; }
                        if (direction === 'RIGHT') { window.scrollBy({ left: 140, behavior: scrollEase }); return 1; }
                        return -1;
                    }

                    if (!current) {
                        if (isHydraHost()) {
                            var hydraSeeded = hydraSeedFocus();
                            if (hydraSeeded) {
                                current = hydraSeeded;
                                if (!(direction === 'LEFT' || direction === 'RIGHT')) return 1;
                            }
                        }
                        if (isXploreHost()) {
                            var seeded = window._safeer_xplore_ensure_focus && window._safeer_xplore_ensure_focus();
                            if (!seeded) seeded = pickXploreContentTile();
                            if (seeded) {
                                // #region agent log
                                try {
                                    window._safeerDbg('H264', 'tv_remote_nav.js:spatial', 'seed', {
                                        dir: direction,
                                        cls: ((seeded.className || '') + '').slice(0, 60),
                                        t: ((seeded.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40)
                                    });
                                } catch (_) {}
                                // #endregion
                                highlightElement(seeded);
                                current = seeded;
                                // UP/DOWN land on the seeded tile. Livetv LEFT/RIGHT then keep
                                // moving so the first RIGHT changes channel instead of staying on SLO 1.
                                if (!(isXploreLivetvPath() && (direction === 'LEFT' || direction === 'RIGHT'))) {
                                    return 1;
                                }
                            }
                        }
                        if (!current) {
                        var best = null;
                        var bestDist = Infinity;
                        for (var i = 0; i < candidates.length; i++) {
                            var cand = candidates[i];
                            var candTag = (cand.tagName || '').toUpperCase();
                            if (candTag === 'INPUT' || candTag === 'TEXTAREA') continue;
                            var r = cand.getBoundingClientRect();
                            if (r.top >= -20 && r.top <= winH && r.left >= 0 && r.right <= winW) {
                                var dist = r.top * 2 + r.left;
                                var cls = (cand.className || '').toString().toLowerCase();
                                if (cls.indexOf('engine-chip') !== -1 || cls.indexOf('favorite-tile') !== -1 || cls.indexOf('tile') !== -1) {
                                    dist -= 80;
                                }
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = cand;
                                }
                            }
                        }
                        if (!best) {
                            for (var z = 0; z < candidates.length; z++) {
                                var zt = (candidates[z].tagName || '').toUpperCase();
                                if (zt !== 'INPUT' && zt !== 'TEXTAREA') { best = candidates[z]; break; }
                            }
                        }
                        if (best) {
                            highlightElement(best);
                            return 1;
                        }
                        return -1;
                        }
                    }

                    var cRect = isHydraHost() ? hydraLayoutRect(current) : current.getBoundingClientRect();
                    var cCenterX = cRect.left + cRect.width / 2;
                    var cCenterY = cRect.top + cRect.height / 2;

                    if (direction === 'DOWN' && current) {
                        var homeCls = ((current.className || '') + '').toLowerCase();
                        if (homeCls.indexOf('engine-chip') !== -1) {
                            var chips = document.querySelectorAll('.engine-chip');
                            var favs = document.querySelectorAll('.favorite-tile');
                            var ci = -1, fi;
                            for (fi = 0; fi < chips.length; fi++) {
                                if (chips[fi] === current || (chips[fi].contains && chips[fi].contains(current))) { ci = fi; break; }
                            }
                            var hitFav = (ci >= 0 && favs[ci]) ? favs[ci] : (favs[0] || null);
                            if (hitFav) {
                                highlightElement(hitFav);
                                return 1;
                            }
                        }
                    }

                    if ((direction === 'LEFT' || direction === 'RIGHT') && isXploreHost() && current && isXploreMenuLink(current)) {
                        if (moveXploreMenu(direction)) return 1;
                    }

                    if (direction === 'UP' && isXploreHost()) {
                        var curTxt = ((current.innerText || current.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        var curCls = ((current.className || '') + '').toLowerCase();
                        var onPlayBtn = curCls.indexOf('promo-carousel__button') !== -1 || curCls.indexOf('action-list') !== -1 ||
                            curTxt === 'glej zdaj' || curTxt.indexOf('glej zdaj') === 0 ||
                            curTxt.indexOf('predvajaj v živo') !== -1 || curTxt.indexOf('predvajaj v zivo') !== -1;
                        if (onPlayBtn) {
                            var menuFromPlay = pickXploreMenuLink(false, current);
                            if (menuFromPlay) {
                                highlightElement(menuFromPlay);
                                // #region agent log
                                try { window._safeerDbg('H40', 'tv_remote_nav.js:up', 'snap menu from play', { t: ((menuFromPlay.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) }); } catch (_) {}
                                // #endregion
                                return 1;
                            }
                        }
                        if (current && isXploreMenuLink(current)) {
                            /* stay in menu; spatial LEFT/RIGHT above */
                        } else {
                            var playHit = pickXplorePlayAction();
                            if (playHit && playHit !== current) {
                                var playTop = playHit.getBoundingClientRect().top;
                                if (playTop < cRect.top - 8) {
                                    highlightElement(playHit);
                                    // #region agent log
                                    try { window._safeerDbg('H253', 'tv_remote_nav.js:up', 'snap play', { t: ((playHit.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) }); } catch (_) {}
                                    // #endregion
                                    return 1;
                                }
                            }
                            var menuHit = pickXploreMenuLink(!!isXploreLivetvPath(), current);
                            if (menuHit && menuHit !== current) {
                                var onTopRow = cRect.top < 320;
                                if (onTopRow) {
                                    highlightElement(menuHit);
                                    // #region agent log
                                    try { window._safeerDbg('H40', 'tv_remote_nav.js:up', 'snap menu', { t: ((menuHit.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40), href: ((menuHit.getAttribute('href') || '') + '').slice(0, 40), n: getXploreMenuLinks().length }); } catch (_) {}
                                    // #endregion
                                    return 1;
                                }
                            }
                        }
                    }

                    if (direction === 'DOWN' && isXploreHost() && current && isXploreMenuLink(current)) {
                        var mHref = ((current.getAttribute && current.getAttribute('href')) || '').toLowerCase();
                        var mTxt = ((current.innerText || current.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        var pth = (location.pathname || '').toLowerCase();
                        if ((mHref.indexOf('/livetv') !== -1 || mTxt === 'tv v živo' || mTxt === 'tv v zivo') && pth.indexOf('/livetv') === -1) {
                            // #region agent log
                            try { window._safeerDbg('H252', 'tv_remote_nav.js:down', 'open livetv', { from: pth.slice(0, 40) }); } catch (_) {}
                            // #endregion
                            try { window._safeer_xplore_want_play = false; sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            location.href = 'https://www.xploretv.si/livetv';
                            return 1;
                        }
                        var tileDown = pickXploreContentTile();
                        if (tileDown) {
                            highlightElement(tileDown);
                            return 1;
                        }
                        if (pth.indexOf('/event') !== -1) {
                            var playDown = pickXplorePlayAction();
                            if (playDown) {
                                highlightElement(playDown);
                                // #region agent log
                                try { window._safeerDbg('H253', 'tv_remote_nav.js:down', 'snap play', { t: ((playDown.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 40) }); } catch (_) {}
                                // #endregion
                                return 1;
                            }
                        }
                    }

                    if ((direction === 'LEFT' || direction === 'RIGHT') && isHydraHost() && current) {
                        var hTabMove = hydraMoveTab(direction, current);
                        if (hTabMove) {
                            highlightElement(hTabMove);
                            return 1;
                        }
                        var hPosterMove = hydraMovePoster(direction, current);
                        if (hPosterMove) {
                            highlightElement(hPosterMove);
                            return 1;
                        }
                    }

                    if (direction === 'DOWN' && isHydraHost() && current) {
                        var hDownCls = ((current.className || '') + '').toLowerCase();
                        var hDownTx = ((current.innerText || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                        var fromTab = hDownCls.indexOf('tab') !== -1 || hDownTx === 'movies' || hDownTx === 'series';
                        var fromHero = hDownCls.indexOf('slidebtn') !== -1 || hDownTx.indexOf('watch now') !== -1 ||
                            hDownCls.indexOf('video-play-button') !== -1;
                        if (fromTab) {
                            var tabPoster = hydraFirstPosterBelow(current);
                            if (tabPoster) {
                                highlightElement(tabPoster);
                                return 1;
                            }
                        }
                        if (fromHero) {
                            function hydraNearestBelow(list) {
                                var pick = null;
                                var pickTop = 1e9;
                                var qi, qEl, qR, qCls;
                                for (qi = 0; qi < list.length; qi++) {
                                    qEl = list[qi];
                                    if (!qEl || qEl === current) continue;
                                    if (current.contains && current.contains(qEl)) continue;
                                    qCls = ((qEl.className || '') + '').toLowerCase();
                                    if (qCls.indexOf('slidebtn') !== -1) continue;
                                    if (hydraRowHidden(qEl) || !hydraIsShown(qEl)) continue;
                                    qR = hydraLayoutRect(qEl);
                                    if (qR.top <= cRect.bottom - 4) continue;
                                    if (qR.width < 28) continue;
                                    if (qR.top < pickTop) {
                                        pickTop = qR.top;
                                        pick = qEl;
                                    }
                                }
                                return pick;
                            }
                            var hPick = hydraNearestBelow(getHydraCandidates());
                            if (!hPick) {
                                hPick = hydraNearestBelow(document.querySelectorAll('.tab, a.hthis, .swiper-slide-featured > a, .seasonHeader, a.dynamic-ep-link'));
                            }
                            if (hPick) {
                                highlightElement(hPick);
                                return 1;
                            }
                        }
                    }

                    if (direction === 'UP' && cRect.top <= 80 && scrollY <= 20) {
                        if (isXploreHost()) {
                            var stay = pickXploreMenuLink(false);
                            if (stay) {
                                highlightElement(stay);
                                return 1;
                            }
                            return 1;
                        }
                        clearActive();
                        return -1; // Izstop v iskalno vrstico (omnibox)
                    }

                    var bestTarget = null;
                    var bestScore = Infinity;
                    var bestAxis = null;
                    var bestAxisScore = Infinity;
                    var wrapTarget = null;
                    var wrapY = 1e9;
                    var wrapX = 1e9;

                    for (var j = 0; j < candidates.length; j++) {
                        var el = candidates[j];
                        if (el === current || current.contains(el)) continue;

                        var r = isHydraHost() ? hydraLayoutRect(el) : el.getBoundingClientRect();
                        if (r.width < 12 || r.height < 12) {
                            if (!(isHydraHost() && r.width >= 48)) continue;
                        }
                        var eCls = ((el.className || '') + '').toLowerCase();
                        if (isXploreHost() && isXploreFooterEl(el)) continue;
                        if (isXploreLivetvPath() && eCls.indexOf('channel-container') !== -1 && eCls.indexOf('item--event') === -1) continue;

                        var eCenterX = r.left + r.width / 2;
                        var eCenterY = r.top + r.height / 2;
                        var dx = eCenterX - cCenterX;
                        var dy = eCenterY - cCenterY;
                        var valid = false;
                        var mainDist = 0;
                        var crossDist = 0;

                        if (direction === 'DOWN') {
                            if (dy > 12) { valid = true; mainDist = dy; crossDist = Math.abs(dx); }
                        } else if (direction === 'UP') {
                            if (dy < -12) { valid = true; mainDist = -dy; crossDist = Math.abs(dx); }
                        } else if (direction === 'RIGHT') {
                            if (dx > 12) { valid = true; mainDist = dx; crossDist = Math.abs(dy); }
                            if (isXploreHost() && r.top > cRect.bottom - 10) {
                                var wy = r.top;
                                var wx = r.left;
                                if (wy < wrapY - 10 || (Math.abs(wy - wrapY) < 10 && wx < wrapX)) {
                                    wrapY = wy;
                                    wrapX = wx;
                                    wrapTarget = el;
                                }
                            }
                        } else if (direction === 'LEFT') {
                            if (dx < -12) { valid = true; mainDist = -dx; crossDist = Math.abs(dy); }
                            if (isXploreHost() && r.bottom < cRect.top + 10) {
                                var wy2 = -r.top;
                                var wx2 = -r.right;
                                if (wy2 < wrapY - 10 || (Math.abs(wy2 - wrapY) < 10 && wx2 < wrapX)) {
                                    wrapY = wy2;
                                    wrapX = wx2;
                                    wrapTarget = el;
                                }
                            }
                        }

                        if (valid) {
                            var rowTol = isXploreLivetvPath() ? 88 : 72;
                            var colTol = isXploreLivetvPath() ? 150 : 130;
                            var sameAxis = (direction === 'LEFT' || direction === 'RIGHT') ? (crossDist < rowTol) : (crossDist < colTol);
                            var score = mainDist + crossDist * (sameAxis ? 0.12 : 1.45);
                            if (isXploreHost() && isXploreContentTile(el) && isXploreContentTile(current) && sameAxis) score -= 80;
                            if (isXploreHost() && isXploreActionField(el) && isXploreActionField(current) && sameAxis) score -= 60;
                            if (sameAxis && score < bestAxisScore) {
                                bestAxisScore = score;
                                bestAxis = el;
                            }
                            if (score < bestScore) {
                                bestScore = score;
                                bestTarget = el;
                            }
                        }
                    }
                    if (bestAxis) bestTarget = bestAxis;
                    else if (!bestTarget && wrapTarget) bestTarget = wrapTarget;

                    if (bestTarget) {
                        highlightElement(bestTarget);
                        return 1;
                    } else {
                        if (direction === 'DOWN') window.scrollBy({ top: 120, behavior: scrollEase });
                        else if (direction === 'UP') {
                            if (isXploreHost()) {
                                var menuMiss = pickXploreMenuLink(false, current);
                                if (menuMiss) {
                                    highlightElement(menuMiss);
                                    return 1;
                                }
                                return 1;
                            }
                            if (scrollY <= 20) {
                                clearActive();
                                return -1;
                            }
                            window.scrollBy({ top: -120, behavior: scrollEase });
                        }
                        else if (direction === 'LEFT') window.scrollBy({ left: -140, behavior: scrollEase });
                        else if (direction === 'RIGHT') window.scrollBy({ left: 140, behavior: scrollEase });
                        return 0;
                    }
                } catch(_) {}
                return -1;
            };

            function nativeTapElement(el) {
                if (!el) return;
                try {
                    var host = (location.hostname || '').toLowerCase();
                    var clsTap = ((el.className || '') + '').toLowerCase();
                    var xploreTile = host.indexOf('xploretv') !== -1 && (
                        clsTap.indexOf('item--event') !== -1 ||
                        !!(el.closest && el.closest('.item--event, .item.item--event'))
                    );
                    if (xploreTile) {
                        if (window._safeerSiteAgent && !window._safeerSiteAgent.acceptOk()) return;
                        var tile = (el.closest && el.closest('.item--event, .item.item--event')) || el;
                        var ovTap = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                        var ocTap = ovTap ? ((ovTap.className || '') + '').toLowerCase() : '';
                        var overlayOnTap = ocTap.indexOf('player-fullwindow') !== -1 || ocTap.indexOf('player-scaled') !== -1;
                        var vTap = document.querySelector('video');
                        var framedTap = !!(vTap && (vTap.videoWidth || 0) >= 320 && vTap.readyState >= 2);
                        if (overlayOnTap && framedTap) return;
                        try { if (window._safeerSiteAgent) window._safeerSiteAgent.markWantPlay(); } catch (_) {}
                        try { window._safeer_xplore_last_tile = tile; } catch (_) {}
                        window._safeer_xplore_player_el = null;
                        window._safeer_xplore_video_boosted = false;
                        window._safeer_xplore_fs_clicked = false;
                        window._safeer_xplore_playing = false;
                        try { if (overlayOnTap && !framedTap && window._safeer_xplore_unsmash) window._safeer_xplore_unsmash(); } catch (_) {}
                        function fireTileClick() {
                            var rectT = tile.getBoundingClientRect();
                            var clickN = 0;
                            var clickTg = '';
                            function onCapClick(ev) {
                                clickN++;
                                var t = ev && ev.target;
                                clickTg = ((t && t.tagName) || '') + ':' + (((t && t.className) || '') + '').toString().slice(0, 40);
                            }
                            try { document.addEventListener('click', onCapClick, true); } catch (_) {}
                            try { tile.click(); } catch (_) {}
                            try { document.removeEventListener('click', onCapClick, true); } catch (_) {}
                            try {
                                window._safeerDbg('H97', 'tv_remote_nav.js:click', 'xplore tile pointer', {
                                    id: (tile.id || '').slice(0, 40),
                                    first: (tile.id || '') === 'csh__first_item',
                                    t: ((tile.innerText || '') + '').replace(/\s+/g, ' ').trim().slice(0, 50),
                                    l: Math.round(rectT.left || 0),
                                    tY: Math.round(rectT.top || 0),
                                    path: (location.pathname || '').slice(0, 50)
                                });
                            } catch (_) {}
                            try {
                                window._safeerDbg('H294', 'tv_remote_nav.js:click', 'tile click count', {
                                    n: clickN,
                                    tg: clickTg,
                                    first: (tile.id || '') === 'csh__first_item',
                                    method: 'tile.click'
                                });
                            } catch (_) {}
                        }
                        if (overlayOnTap && ovTap && !framedTap) {
                            try {
                                ovTap.classList.remove('player-fullwindow', 'player-scaled');
                                ovTap.classList.add('player-closed');
                            } catch (_) {}
                            setTimeout(fireTileClick, 280);
                            return;
                        }
                        fireTileClick();
                        return;
                    }
                    var rect = isHydraHost() ? hydraLayoutRect(el) : el.getBoundingClientRect();
                    var cx = rect.left + rect.width / 2;
                    var cy = rect.top + rect.height / 2;
                    var mouseOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, screenX: cx, screenY: cy };
                    try { el.dispatchEvent(new PointerEvent('pointerdown', mouseOpts)); } catch(_) {}
                    try { el.dispatchEvent(new MouseEvent('mousedown', mouseOpts)); } catch(_) {}
                    try { el.dispatchEvent(new PointerEvent('pointerup', mouseOpts)); } catch(_) {}
                    try { el.dispatchEvent(new MouseEvent('mouseup', mouseOpts)); } catch(_) {}
                    try { el.dispatchEvent(new MouseEvent('click', mouseOpts)); } catch(_) {}
                    try { el.click(); } catch(_) {}
                    try {
                        if (window.SafeerBridge && window.SafeerBridge.triggerNativeTap) {
                            window.SafeerBridge.triggerNativeTap(cx, cy);
                        }
                    } catch(_) {}
                } catch(_) {}
            }

            window._safeer_click_focused_card = function() {
                try {
                    var path = (location.pathname || '').toLowerCase();
                    var onEvent = path.indexOf('/events/') !== -1 || path.indexOf('/event/') !== -1;
                    var target = getActiveElement();
                    var goingEvent = onEvent;
                    try {
                        if (target && !goingEvent) {
                            var a = (target.closest && target.closest('a[href*="event"]')) || target.querySelector('a[href*="event"]');
                            var ah = a ? ((a.getAttribute('href') || a.href || '') + '') : '';
                            goingEvent = ah.indexOf('event') !== -1;
                        }
                    } catch (_) {}
                    if (isHydraWatchPath()) {
                        var hydraPlay = hydraPlayControl();
                        if (hydraPlay && (!target || isHydraBrandEl(target))) {
                            highlightElement(hydraPlay);
                            nativeTapElement(hydraPlay);
                            return true;
                        }
                    }
                    if (location.hostname.indexOf('xploretv') !== -1) {
                        var menuTxt = ((target && (target.innerText || target.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
                        var menuCls = ((target && target.className) || '').toString().toLowerCase();
                        var hrefMenu = '';
                        try { hrefMenu = ((target && target.getAttribute && (target.getAttribute('href') || '')) + '').toLowerCase(); } catch (_) {}
                        var isHomeNav = menuCls.indexOf('home-link') !== -1 || menuTxt === 'za vas' ||
                            (hrefMenu.indexOf('/home') !== -1 && menuTxt.indexOf('za vas') !== -1);
                        var isLiveNav = menuCls.indexOf('livetv-link') !== -1 || ((menuTxt === 'tv v živo' || menuTxt === 'tv v zivo') && menuCls.indexOf('menu-items-wrapper') === -1);
                        var inMenu = false;
                        try { inMenu = !!(target && target.closest && target.closest('.menu, #csh__menu_bar, .menu-items-wrapper')); } catch (_) {}
                        if (isHomeNav) {
                            window._safeer_xplore_want_play = false;
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            try { window._safeerDbg('H342', 'tv_remote_nav.js:click', 'za vas no play', { path: path.slice(0, 40) }); } catch (_) {}
                            if (path.indexOf('/home') !== -1 || path === '/' || path === '') return true;
                            location.href = 'https://www.xploretv.si/home';
                            return true;
                        }
                        if (isLiveNav) {
                            window._safeer_xplore_want_play = false;
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            if (path.indexOf('/livetv') !== -1) {
                                var stayLive = pickXploreContentTile();
                                if (stayLive) highlightElement(stayLive);
                                return true;
                            }
                            location.href = 'https://www.xploretv.si/livetv';
                            return true;
                        }
                        if (inMenu) {
                            window._safeer_xplore_want_play = false;
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            nativeTapElement(target);
                            return true;
                        }
                        try {
                            if (target && target.closest && target.closest('.item--event, .item.item--event')) goingEvent = true;
                        } catch (_) {}
                        if (path.indexOf('/livetv') !== -1) {
                            goingEvent = false;
                            window._safeer_xplore_want_play = true;
                            window._safeer_xplore_play_n = (window._safeer_xplore_play_n || 0) + 1;
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            // #region agent log
                            try { window._safeerDbg('H272', 'tv_remote_nav.js:click', 'livetv tile play', { n: window._safeer_xplore_play_n, cls: ((target && target.className) || '').toString().slice(0, 70) }); } catch (_) {}
                            // #endregion
                        }
                        var clsHit = ((target && target.className) || '').toString().toLowerCase();
                        var isSearchHit = clsHit.indexOf('search') !== -1 || (target && target.querySelector && target.querySelector('.icon-p24_search'));
                        if (!isSearchHit && goingEvent) {
                            try { sessionStorage.setItem('safeer_xplore_autoplay', '1'); } catch (_) {}
                            window._safeer_xplore_want_play = true;
                            window._safeer_xplore_video_boosted = false;
                            window._safeer_xplore_playing = false;
                            var eventRoot = (target && target.closest) ? target.closest('.item--event, .item.item--event') : null;
                            if (eventRoot) target = eventRoot;
                        }
                        if (!isSearchHit && !goingEvent) {
                            window._safeer_xplore_want_play = true;
                            window._safeer_xplore_video_boosted = false;
                            window._safeer_xplore_playing = false;
                        }
                        if (onEvent && typeof window._safeer_xplore_play_from_start === 'function') {
                            var actTxt = ((target && (target.innerText || target.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
                            var actCls = ((target && target.className) || '').toString().toLowerCase();
                            var isPlayAction = actCls.indexOf('action-list') !== -1 ||
                                actTxt.indexOf('glej oddajo') !== -1 ||
                                actTxt.indexOf('predvajaj') !== -1 ||
                                actTxt.indexOf('glej zdaj') !== -1 ||
                                actTxt.indexOf('na začetek') !== -1 ||
                                actTxt.indexOf('na zacetek') !== -1 ||
                                actTxt.indexOf('priljubljen') !== -1 ||
                                actTxt.indexOf('posnemi') !== -1 ||
                                actTxt.indexOf('dodaj med') !== -1;
                            var relatedTile = null;
                            try { relatedTile = target && target.closest ? target.closest('.item--event, .item.item--event') : null; } catch (_) {}
                            if (relatedTile && !isPlayAction) {
                                try { sessionStorage.setItem('safeer_xplore_autoplay', '1'); } catch (_) {}
                                nativeTapElement(relatedTile);
                                return true;
                            }
                            if (isPlayAction) {
                                var wantLive = actTxt.indexOf('predvajaj') !== -1 || actTxt.indexOf('glej zdaj') !== -1 || actTxt.indexOf('glej oddajo') !== -1;
                                if (wantLive) {
                                    window._safeer_xplore_want_play = true;
                                    window._safeer_xplore_video_boosted = false;
                                    window._safeer_xplore_playing = false;
                                }
                                try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                                nativeTapElement(target);
                                return true;
                            }
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (_) {}
                            return window._safeer_xplore_play_from_start();
                        }
                    }
                    // #region agent log
                    try {
                        window._safeerDbg('H60', 'tv_remote_nav.js:click', 'ok click target', {
                            path: path.slice(0, 80),
                            cls: ((target && target.className) || '').toString().slice(0, 90),
                            goingEvent: !!goingEvent,
                            onEvent: !!onEvent,
                            want: !!window._safeer_xplore_want_play,
                            auto: (function(){ try { return sessionStorage.getItem('safeer_xplore_autoplay') || ''; } catch(e){ return ''; } })()
                        });
                    } catch (_) {}
                    // #endregion
                    if (target) {
                        var clickable = target;
                        if (location.hostname.indexOf('xploretv') === -1) {
                            clickable = target.querySelector('a[href], button, [class*="play"], [class*="watch"], [class*="thumb"], [class*="image"], img, [role="button"]') || target;
                        }
                        nativeTapElement(clickable);
                        if (location.hostname.indexOf('xploretv') === -1 && clickable !== target) nativeTapElement(target);
                        if (location.hostname.indexOf('xploretv') !== -1 && !goingEvent && path.indexOf('/livetv') === -1) {
                            try { if (window._safeerSiteAgent && window._safeerSiteAgent.allowBoost()) window._safeer_xplore_boost(); } catch (_) {}
                        }
                        // #region agent log
                        if (location.hostname.indexOf('xploretv') !== -1) {
                            setTimeout(function() {
                                try {
                                    var v = document.querySelector('video');
                                    var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
                                    var ply = document.querySelector('[class*="overlays-layer"], [class*="clpp"]');
                                    window._safeerDbg('H63', 'tv_remote_nav.js:click', 'player 4s after ok', {
                                        path: (location.pathname || '').slice(0, 80),
                                        hasV: !!v,
                                        w: Math.round(r.width || 0),
                                        h: Math.round(r.height || 0),
                                        vw: v ? (v.videoWidth || 0) : 0,
                                        rs: v ? v.readyState : -1,
                                        paused: v ? !!v.paused : true,
                                        playing: !!window._safeer_xplore_playing,
                                        boosted: !!window._safeer_xplore_video_boosted,
                                        fs: document.documentElement.classList.contains('safeer-xplore-fs'),
                                        ply: ply ? ((ply.className || '') + '').slice(0, 70) : '',
                                        ph: ply ? Math.round(ply.getBoundingClientRect().height || 0) : 0
                                    });
                                } catch (_) {}
                            }, 4000);
                        }
                        // #endregion

                        setTimeout(function() {
                            try {
                                if (location.pathname.indexOf('/watch') === -1 && location.pathname.indexOf('/shorts') === -1) return;
                                var v = document.querySelector('video');
                                if (v) {
                                    v.muted = false;
                                    v.volume = 1.0;
                                    var playPromise = v.play();
                                    if (playPromise !== undefined) {
                                        playPromise.catch(function() {});
                                    }
                                }
                            } catch(_) {}
                        }, 700);
                        return true;
                    }
                } catch(_) {}
                return false;
            };

            // 5. Samodejno dodajanje tabindex in odstranjevanje GDPR pasic
            function autoDismissGdpr() {
                try {
                    var didomi = document.getElementById('didomi-notice-agree-button');
                    if (didomi) {
                        didomi.click();
                        return;
                    }
                    var btns = document.querySelectorAll('button, a, [role="button"]');
                    for (var i = 0; i < btns.length; i++) {
                        var t = (btns[i].textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
                        if (t === 'sprejmi in zapri' || t.indexOf('sprejmi in zapri') === 0 ||
                            t === 'sprejmi vse' || t === 'strinjam se' || t === 'v redu' ||
                            t === 'sprejmem' || t === 'dovolim' || t === 'strinjam se s piškotki' || t === 'sprejmi piškotke' ||
                            t === 'accept all' || t === 'i agree' || t === 'accept' || t === 'allow all') {
                            btns[i].click();
                            break;
                        }
                    }
                } catch(_) {}
            }

            function makeElementsFocusable() {
                try {
                    autoDismissGdpr();
                    if (isXploreHost()) {
                        var ovPlay = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                        var ovCls = ovPlay ? ((ovPlay.className || '') + '').toLowerCase() : '';
                        if (ovCls.indexOf('player-fullwindow') !== -1 || ovCls.indexOf('player-scaled') !== -1) {
                            return;
                        }
                        var vWant = document.querySelector('video');
                        var framedWant = !!(vWant && (vWant.videoWidth || 0) >= 320 && vWant.readyState >= 2);
                        if (window._safeer_xplore_want_play && !framedWant) {
                            return;
                        }
                    }
                    var focusables = document.querySelectorAll('a, button, input, select, textarea, [onclick], [role="button"], .card, .media-card, .item--event, .item.item--event, .tab, .seasonHeader, .video-play-button');
                    for (var i = 0; i < focusables.length; i++) {
                        var el = focusables[i];
                        if (isHydraChromeEl(el) || isHydraNavArrow(el)) continue;
                        if (!el.hasAttribute('tabindex')) {
                            el.setAttribute('tabindex', '0');
                        }
                    }
                    if (isXploreHost()) {
                        var pnow = (location.pathname || '');
                        if (pnow !== window._safeer_xplore_last_path) {
                            window._safeer_xplore_last_path = pnow;
                            window._safeer_xplore_did_focus = false;
                        }
                        var held = document.querySelector('.safeer-active-card');
                        var heldOk = false;
                        try { heldOk = !!(held && held.getBoundingClientRect().width >= 8); } catch (_) {}
                        if (heldOk) window._safeer_xplore_did_focus = true;
                        if (!window._safeer_xplore_did_focus) {
                            var got = window._safeer_xplore_ensure_focus && window._safeer_xplore_ensure_focus();
                            if (got) window._safeer_xplore_did_focus = true;
                        }
                    }
                    if (is24urHost() && !window._safeer_24ur_seeded && !document.querySelector('.safeer-active-card')) {
                        var ncard = document.querySelector('a.card');
                        if (ncard) {
                            highlightElement(ncard);
                            window._safeer_24ur_seeded = true;
                        }
                    }
                    if (isHydraHost()) {
                        var hHeld = document.querySelector('.safeer-active-card');
                        var hHeldOk = false;
                        try {
                            if (hHeld) {
                                var hb = hydraLayoutRect(hHeld);
                                var hClsHeld = ((hHeld.className || '') + '').toLowerCase();
                                hHeldOk = (hb.width >= 8 && hb.height >= 8) ||
                                    hClsHeld.indexOf('hthis') !== -1 || hClsHeld.indexOf('slidebtn') !== -1 ||
                                    hClsHeld.indexOf('tab') !== -1 || hClsHeld.indexOf('seasonheader') !== -1 ||
                                    hClsHeld.indexOf('video-play-button') !== -1;
                            }
                        } catch (_) {}
                        if (!hHeld || isHydraChromeEl(hHeld) || isHydraBrandEl(hHeld) || !hHeldOk) {
                            hydraSeedFocus();
                        }
                    }
                } catch(_) {}
            }

            document.addEventListener('DOMContentLoaded', makeElementsFocusable);
            setInterval(makeElementsFocusable, isXploreHost() ? 4000 : 4500);
            if (!isXploreHost()) {
                var focusMoTimer = 0;
                try {
                    var focusMo = new MutationObserver(function () {
                        if (focusMoTimer) return;
                        focusMoTimer = setTimeout(function () {
                            focusMoTimer = 0;
                            makeElementsFocusable();
                        }, 480);
                    });
                    focusMo.observe(document.documentElement, { childList: true, subtree: true });
                } catch (_) {}
            }
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
                            'html.safeer-xplore-waitplay #safeer-focus-target-ring,html.safeer-xplore-playing #safeer-focus-target-ring{display:none!important;opacity:0!important;}';
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
                    // Auth asset is injected in a separate evaluateJavascript; wait until it lands.
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
                        setReactInputValue(uInput, user);
                        setReactInputValue(pInput, pass);
                        window._safeerXploreLoginAttempted = true;
                        if (submitBtn) {
                            setTimeout(function() {
                                try { submitBtn.click(); } catch (_) {}
                            }, 500);
                        }
                        return;
                    }
                    window._safeerXploreLoginAttempted = true;
                    try {
                        var seed = submitBtn || uInput;
                        if (seed && !document.querySelector('.safeer-active-card')) highlightElement(seed);
                    } catch (_) {}
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
                            try { if (window._safeer_xplore_paint_menu) window._safeer_xplore_paint_menu(); } catch (_) {}
                            if (changed || key !== window._safeer_xplore_watch_key) {
                                window._safeer_xplore_watch_key = key;
                                window._safeer_xplore_report(changed ? 'path' : 'state');
                            } else if (window._safeer_xplore_watch_n <= 20 && window._safeer_xplore_watch_n % 5 === 0) {
                                window._safeer_xplore_report('tick');
                            }
                        } catch (_) {}
                    }, 700);
                }
                if (!window._safeer_xplore_auth_watch) {
                    window._safeer_xplore_auth_watch = true;
                    setInterval(runXploreAuth, 1200);
                }
                // #region agent log
                try {
                    window._safeerDbg('H4', 'UserScriptManager.kt:xplore_block', 'helpers ready', {
                        hasPlay: typeof window._safeer_xplore_play_from_start === 'function',
                        path: location.pathname
                    });
                } catch (_) {}
                // #endregion
            }
                        // 7. Real-time scroll listener za samodejno skrivanje/prikaz orodne vrstice
            var lastScrollPos = window.scrollY || 0;
            window.addEventListener('scroll', function() {
                try {
                    var currPos = window.scrollY || document.documentElement.scrollTop || 0;
                    var diff = currPos - lastScrollPos;
                    if (Math.abs(diff) > 24) {
                        if (window.SafeerBridge && window.SafeerBridge.onScrollChanged) {
                            window.SafeerBridge.onScrollChanged(diff > 0 ? 1 : -1, currPos);
                        }
                        lastScrollPos = currPos;
                    }
                } catch(_) {}
            }, { passive: true });

            try {
                setTimeout(function() {
                    var ae = document.activeElement;
                    if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA')) {
                        ae.blur();
                    }
                    if (location.href.indexOf('brave_home') !== -1 && !getActiveElement()) {
                        var start = document.querySelector('.hero-card') || document.querySelector('.favorite-tile') || document.querySelector('.engine-chip');
                        if (start) highlightElement(start);
                    }
                    if (is24urHost() && !document.querySelector('.safeer-active-card')) {
                        var newsStart = document.querySelector('a.card');
                        if (newsStart) highlightElement(newsStart);
                    }
                    if (isHydraHost()) {
                        hydraSeedFocus();
                    }
                }, 80);
            } catch(_) {}
        })();
