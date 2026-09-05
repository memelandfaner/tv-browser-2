package com.example.safeerbrowser

import android.net.Uri
import android.webkit.WebView

object UserScriptManager {

    private const val DARK_MODE_AMOLED_CSS = """
        /* 🌌 Safeer Universal Smart OLED Dark Theme */
        :root {
            color-scheme: dark !important;
        }
        html, body, #root, #app, [id*="root"], [id*="app"],
        main, article, section, [class*="content"], [class*="container"], [class*="wrapper"],
        [class*="layout"], [class*="page"], [class*="view"], [class*="main"], [class*="body"],
        [class*="theme"], [class*="grid"], [class*="row"], [class*="section"], [class*="list"],
        [class*="guide"], [class*="epg"], [class*="channel"], [class*="vod"], [class*="home"] {
            background-color: #0b0e14 !important;
            color: #e2e8f0 !important;
        }
        header, nav, [class*="header"], [class*="topbar"], [class*="nav-bar"], [class*="navbar"], [class*="navigation"], .header {
            background-color: #0f131c !important;
            color: #f8fafc !important;
            border-bottom: 1px solid #1e293b !important;
        }
        [class*="modal"], [class*="dialog"], [class*="popup"], [class*="overlay"], [class*="drawer"], [class*="sheet"] {
            background-color: #141824 !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }
        [class*="card"], [class*="tile"], [class*="panel"], [class*="box"], [class*="item"] {
            background-color: #131722 !important;
            color: #e2e8f0 !important;
            border-color: #232a3b !important;
        }
        h1, h2, h3, h4, h5, h6, b, strong, th {
            color: #ffffff !important;
        }
        p, span, label, li, td, dt, dd {
            color: #cbd5e1 !important;
        }
        a, a * {
            color: #38bdf8 !important;
        }
        input, textarea, select {
            background-color: #1a1f2c !important;
            color: #ffffff !important;
            border: 1px solid #334155 !important;
        }
        /* Zaščiti video, slike, grafike, logotipe, hero pasice in ozadja pred brisanjem */
        img, video, canvas, svg, picture, [class*="poster"], [class*="thumb"], [class*="image"], [class*="photo"], [class*="avatar"], [class*="logo"], [class*="banner"] {
            filter: none !important;
            background-color: transparent !important;
        }
        /* YouTube / HTML5 predvajalnik ne sme dobiti črnega overlayja */
        .html5-video-player, .html5-video-container, ytd-player, ytm-player,
        #player, #movie_player, #player-container, #player-container-inner,
        [class*="html5-video"], video.html5-main-video, video.video-stream {
            background: transparent !important;
            background-color: transparent !important;
            opacity: 1 !important;
            visibility: visible !important;
        }
        video {
            background-color: transparent !important;
            opacity: 1 !important;
        }
    """

    private const val XPLORE_DARK_CSS = """
        .content-carousel, .content-carousel__container, .content-carousel__slider,
        .item, .item--event, .item__container, .item__metadata {
            background-color: transparent !important;
        }
        html, body, #root, #app, #__next, main {
            background: #07090d !important;
            color: #e8eef5 !important;
            color-scheme: dark !important;
        }
        html.safeer-xplore-dark, html.safeer-xplore-dark body {
            background: #07090d !important;
            background-color: #07090d !important;
            color: #e8eef5 !important;
            color-scheme: dark !important;
        }
        html.safeer-xplore-dark .gradient-bg-white,
        html.safeer-xplore-dark [class*="gradient-bg-white"],
        html.safeer-xplore-dark [class*="page-content"],
        html.safeer-xplore-dark [class*="PageContent"],
        html.safeer-xplore-dark [class*="livetv"],
        html.safeer-xplore-dark [class*="LiveTv"],
        html.safeer-xplore-dark [class*="all-program"],
        html.safeer-xplore-dark [class*="channel-list"],
        html.safeer-xplore-dark [class*="epg"],
        html.safeer-xplore-dark .options-wrapper,
        html.safeer-xplore-dark [class*="options-wrapper"],
        html.safeer-xplore-dark [class*="livetv-grid"] {
            background: #07090d !important;
            background-color: #07090d !important;
            background-image: none !important;
            color: #e8eef5 !important;
        }
                        html.safeer-xplore-dark .item__bg,
                        html.safeer-xplore-dark [class*="item__bg"] {
                            background-color: #12161e !important;
                        }
        html.safeer-xplore-dark .menu,
        html.safeer-xplore-dark .menu.menu--opaque,
        html.safeer-xplore-dark .menu.menu--fixed,
        html.safeer-xplore-dark .menu.menu--noscroll,
        html.safeer-xplore-dark .menu.menu--black-text,
        html.safeer-xplore-dark .menu.menu--white-text,
        html.safeer-xplore-dark .menu.gradient-bg-black,
        html.safeer-xplore-dark .menu.gradient-bg-white,
        html.safeer-xplore-dark .menu.gradient-bg-white.menu--black-text,
        html.safeer-xplore-dark header,
        html.safeer-xplore-dark nav {
            background: #0b1220 !important;
            background-color: #0b1220 !important;
            background-image: none !important;
            color: #f8fafc !important;
            border-bottom: 1px solid #2a3a52 !important;
            box-shadow: 0 10px 28px rgba(0, 0, 0, 0.45) !important;
            min-height: 84px !important;
            height: 84px !important;
            z-index: 60 !important;
        }
        html.safeer-xplore-dark .menu::before,
        html.safeer-xplore-dark .menu::after {
            display: none !important;
            background: none !important;
            content: none !important;
        }
        html.safeer-xplore-dark .menu-items-wrapper,
        html.safeer-xplore-dark #csh__menu_bar,
        html.safeer-xplore-dark .menu [class*="wrapper"],
        html.safeer-xplore-dark .menu [class*="container"],
        html.safeer-xplore-dark .menu [class*="inner"],
        html.safeer-xplore-dark .menu [class*="bar"] {
            display: flex !important;
            align-items: center !important;
            gap: 6px !important;
            background: transparent !important;
            background-color: transparent !important;
            background-image: none !important;
        }
        html.safeer-xplore-dark .menu a,
        html.safeer-xplore-dark .menu button,
        html.safeer-xplore-dark .menu [role="button"],
        html.safeer-xplore-dark .menu [role="tab"],
        html.safeer-xplore-dark .menu .dropdown-toggle-button,
        html.safeer-xplore-dark #csh__menu_bar a {
            display: inline-flex !important;
            flex-direction: row !important;
            align-items: center !important;
            gap: 10px !important;
            min-height: 56px !important;
            padding: 10px 16px !important;
            border-radius: 12px !important;
            font-size: 22px !important;
            font-weight: 700 !important;
            letter-spacing: 0.01em !important;
            color: #f8fafc !important;
            -webkit-text-fill-color: #f8fafc !important;
            opacity: 1 !important;
            visibility: visible !important;
            overflow: visible !important;
            white-space: nowrap !important;
            background: transparent !important;
            text-shadow: 0 1px 2px rgba(0, 0, 0, 0.7) !important;
        }
        html.safeer-xplore-dark .menu a span,
        html.safeer-xplore-dark .menu button span,
        html.safeer-xplore-dark #csh__menu_bar a span,
        html.safeer-xplore-dark .home-link span,
        html.safeer-xplore-dark .livetv-link span,
        html.safeer-xplore-dark .movies-link span,
        html.safeer-xplore-dark .library-link span,
        html.safeer-xplore-dark .guide-link span,
        html.safeer-xplore-dark .dropdown-toggle-button span {
            display: inline !important;
            opacity: 1 !important;
            visibility: visible !important;
            position: static !important;
            width: auto !important;
            max-width: none !important;
            height: auto !important;
            font-size: 22px !important;
            font-weight: 700 !important;
            color: #ffffff !important;
            -webkit-text-fill-color: #ffffff !important;
            clip: auto !important;
            clip-path: none !important;
            overflow: visible !important;
            text-indent: 0 !important;
            white-space: nowrap !important;
        }
        html.safeer-xplore-dark .menu a:not(.logo) svg,
        html.safeer-xplore-dark .menu a:not(.logo) svg *,
        html.safeer-xplore-dark .menu button svg,
        html.safeer-xplore-dark .menu button svg *,
        html.safeer-xplore-dark .menu .dropdown-toggle-button svg,
        html.safeer-xplore-dark .menu .dropdown-toggle-button svg * {
            fill: #f8fafc !important;
            color: #f8fafc !important;
            opacity: 1 !important;
        }
        html.safeer-xplore-dark .home-link.route--active,
        html.safeer-xplore-dark .livetv-link.route--active,
        html.safeer-xplore-dark .movies-link.route--active,
        html.safeer-xplore-dark .library-link.route--active,
        html.safeer-xplore-dark .guide-link.route--active {
            background: rgba(0, 229, 255, 0.16) !important;
            border-radius: 12px !important;
            box-shadow: inset 0 -3px 0 #00e5ff !important;
            color: #ffffff !important;
        }
        html.safeer-xplore-dark .menu a.safeer-active-card,
        html.safeer-xplore-dark .menu .dropdown-toggle-button.safeer-active-card,
        html.safeer-xplore-dark .livetv-link.safeer-active-card {
            outline: 3px solid #00e5ff !important;
            outline-offset: 3px !important;
            background: rgba(0, 229, 255, 0.14) !important;
            box-shadow: none !important;
        }
        html.safeer-xplore-dark .menu .logo,
        html.safeer-xplore-dark .menu a.logo {
            background: transparent !important;
            outline: none !important;
            box-shadow: none !important;
            -webkit-text-fill-color: unset !important;
        }
        .content__wrapper, .content__wrapper.has-footer,
        html.safeer-xplore-dark .content__wrapper,
        html.safeer-xplore-dark .content__wrapper.has-footer,
        html.safeer-xplore-dark body {
            background: #07090d !important;
            background-color: #07090d !important;
        }
        .menu--black-text, .menu--black-text a, .menu--black-text span,
        .menu--black-text li, .menu--white-text, .menu--white-text a, .menu--white-text span {
            color: #e8eef5 !important;
        }
        header, nav, footer {
            background-color: #0b0e14 !important;
            color: #f8fafc !important;
        }
        .search__query_wrapper, [class*="search__query"] {
            background: #07090d !important;
            background-image: none !important;
        }
        img, video, canvas, picture, svg,
        [class*="poster"], [class*="thumb"], [class*="Hero"], [class*="hero"],
        [class*="Banner"], [class*="clpp"], [class*="player"], [class*="Player"],
        [class*="logo"], [class*="image-header"] {
            background-color: transparent !important;
            filter: none !important;
        }
        input, textarea, select {
            background-color: #121826 !important;
            color: #fff !important;
            border: 1px solid #243044 !important;
            caret-color: #93c5fd !important;
        }
        input:focus, textarea:focus, input:focus-visible {
            outline: 2px solid #3b82f6 !important;
            outline-offset: 2px !important;
            box-shadow: 0 0 0 1px #0b0e14, 0 0 14px rgba(59, 130, 246, 0.35) !important;
            background-color: #121826 !important;
        }
    """

    private const val ANTI_POPUNDER_SHIELD_JS = """
        /* 🛡️ Safeer Anti-Popunder, Anti-Clickjacking & Streaming Shield Engine */
        (function() {
            if ((location.href || '').indexOf('youtube.com/tv') !== -1) return;
            if ((location.hostname || '').indexOf('xploretv.si') !== -1) return;
            if (window._safeer_popunder_shield_active) return;
            window._safeer_popunder_shield_active = true;

            // 🚫 1. Popolna nevtralizacija window.open popunderjev
            try {
                window.open = function(url, target, features) {
                    console.log('[Safeer AdBlock] Preprečen window.open:', url);
                    return null;
                };
            } catch(e) {}

            // 🚫 2. Zaščita pred ugrabitvijo top.location iz vdelanih okvirjev (iframes)
            try {
                if (window.top !== window.self) {
                    Object.defineProperty(window, 'top', {
                        get: function() { return window.self; },
                        set: function() {},
                        configurable: true
                    });
                    Object.defineProperty(window, 'parent', {
                        get: function() { return window.self; },
                        set: function() {},
                        configurable: true
                    });
                }
            } catch(e) {}

            // 🚫 3. Samodejno odstranjevanje lažnih opozoril, vsiljenih modalov in video oglasnih prekrivk
            function cleanAllAdOverlays() {
                try {
                    if ((location.hostname || '').indexOf('xploretv') !== -1) return;
                    var adSelectors = [
                        '.reward-zone', '#reward-zone', '.fc-ab-root', '.adblock-overlay', '#adblock-modal',
                        '[class*="dating-popup"]', '[id*="dating-popup"]', '[class*="fake-download"]',
                        '.download-button-ad', 'div[class*="download-arrow"]',
                        '.mgp_adOverlay', '.mgp_adSkip', '.mgp_adMarker', '.mgp_commercial',
                        '.adBlockContainer', 'div[class*="adSkip"]',
                        '.mgp_skipAdButton', 'a[class*="adLink"]', 'div[class*="adInformation"]', '.adInformation',
                        'div[class*="mgp_ad"]', '.removeAds', 'a[href*="casino"]', '.topAd', '.bottomAd',
                        '.wideBanner', '.underPlayerAd', '.commercial-unit', '.ad-zone',
                        '[class*="ad-banner"]', '[class*="player-advertisement"]', '[id*="player-advertisement"]',
                        '.ad-banner-overlay', '.jw-ad-container', '.plyr__ad', '.vjs-ad', '.video-ad-overlay'
                    ].join(', ');
                    
                    var adElements = document.querySelectorAll(adSelectors);
                    adElements.forEach(function(el) {
                        try { el.remove(); } catch(e) {}
                    });

                    // Odstrani lažna sistemska opozorila (baterija poškodovana, virus zaznan)
                    var dialogs = document.querySelectorAll('[role="dialog"], [role="alertdialog"], .modal, .popup');
                    for (var i = 0; i < dialogs.length; i++) {
                        var d = dialogs[i];
                        var txt = (d.innerText || d.textContent || '').trim().toLowerCase();
                        if (txt.includes('battery damaged') || txt.includes('virus detected') || 
                            txt.includes('vpn recommended') || txt.includes('whatsapp za seks') ||
                            (txt.includes('disable your ad blocker') && txt.includes('disable'))) {
                            try { d.remove(); } catch(e) {}
                        }
                    }

                    // Odstrani oglasne iframe okvirje (srcdoc ali lebdeče overlay iframe-e)
                    var iframes = document.querySelectorAll('iframe');
                    for (var k = 0; k < iframes.length; k++) {
                        var ifr = iframes[k];
                        var src = (ifr.getAttribute('src') || ifr.src || '').toLowerCase();
                        var srcdoc = ifr.getAttribute('srcdoc');
                        var isFixed = false;
                        try {
                            var ifStyle = window.getComputedStyle(ifr);
                            if (ifStyle.position === 'fixed' || (ifStyle.position === 'absolute' && parseInt(ifStyle.zIndex, 10) > 20)) {
                                isFixed = true;
                            }
                        } catch(e) {}

                        if (srcdoc != null || src.includes('srcdoc') || (isFixed && !src.includes('embed') && !src.includes('player') && !src.includes('streamex') && !src.includes('vidgod'))) {
                            ifr.remove();
                        }
                    }

                    // Odstrani nevidne prekrivne plasti (Invisible Click-Jacking Overlays)
                    var allFixed = document.querySelectorAll('div, a, span, button');
                    var winW = window.innerWidth || 1000;
                    var winH = window.innerHeight || 800;
                    for (var j = 0; j < allFixed.length; j++) {
                        var fx = allFixed[j];
                        try {
                            var style = window.getComputedStyle(fx);
                            if (style.position === 'fixed' || style.position === 'absolute') {
                                var z = parseInt(style.zIndex, 10) || 0;
                                var op = parseFloat(style.opacity);
                                var rect = fx.getBoundingClientRect();
                                if (z >= 99 && (op === 0 || style.visibility === 'hidden') && rect.width >= winW * 0.5 && rect.height >= winH * 0.5) {
                                    if (fx.querySelectorAll('video, iframe, form').length === 0) {
                                        fx.remove();
                                    }
                                }
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
            }

            // ⚡ 4. Samodejno preskakovanje video oglasov (Instant Video Ad Skipper)
            function autoSkipVideoAds() {
                try {
                    if ((location.hostname || '').indexOf('xploretv') !== -1) return;
                    // Klikni gumb za preskok oglasa takoj ko se pojavi
                    var skipButtons = document.querySelectorAll(
                        '.videoAdUiSkipButton, .mgp_skipAdButton, .mgp_adSkip, [class*="skipAd"], ' +
                        '[class*="SkipAd"], [class*="adSkip"], [class*="ad-skip"], .video-ad-skip, ' +
                        'button[class*="skip-ad"], .skip-button, .ad-skip-button'
                    );
                    skipButtons.forEach(function(btn) {
                        if (btn && (btn.offsetWidth > 0 || btn.offsetHeight > 0)) {
                            try { btn.click(); } catch(_) {}
                        }
                    });

                    // Če teče oglasni video posnetek na spletnih straneh (NE na YouTube, kjer deluje namenski YouTube Freedom)
                    if (location.hostname.indexOf('youtube.com') === -1) {
                        var isAdActive = document.querySelector('.mgp_adPlaying, [class*="adPlaying"]');
                        if (isAdActive) {
                            var adVideos = document.querySelectorAll('.mgp_adContainer video, .ad-container video, video.ad-video');
                            adVideos.forEach(function(v) {
                                if (v && !v.paused) {
                                    if (isFinite(v.duration) && v.duration > 0) {
                                        try { v.currentTime = v.duration; } catch(_) {}
                                    }
                                    try { v.playbackRate = 16.0; } catch(_) {}
                                    try { v.muted = true; } catch(_) {}
                                }
                            });
                        }
                    }
                } catch(e) {}
            }

            // 🚫 5. Blokada klikov na zunanje oglasne povezave
            document.addEventListener('click', function(e) {
                var target = e.target;
                var a = target.closest ? target.closest('a') : null;
                if (a && a.href) {
                    var h = a.href.toLowerCase();
                    if (h.includes('doubleclick') || h.includes('googleads') || h.includes('monetag') ||
                        h.includes('onclick') || h.includes('adsterra') || h.includes('popads') ||
                        h.includes('popcash') || h.includes('hilltop') || h.includes('propu.sh') ||
                        h.includes('highperformance') || h.includes('deloplen') || h.includes('20bet') ||
                        h.includes('1xbet') || h.includes('casino') || h.includes('pussing') ||
                        h.includes('effectivegate') || h.includes('dating') || h.includes('stripchat')) {
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        a.remove();
                    }
                }
            }, true);

            // Zagon čistilcev in samodejnega preskakovanja
            cleanAllAdOverlays();
            autoSkipVideoAds();
            setInterval(function() {
                cleanAllAdOverlays();
                autoSkipVideoAds();
            }, 200);

            var observer = new MutationObserver(function() {
                cleanAllAdOverlays();
                autoSkipVideoAds();
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    if (document.body) observer.observe(document.body, { childList: true, subtree: true });
                });
            }
        })();
    """

    private const val YOUTUBE_FREEDOM_MOBILE_JS = """
        (function initYouTubeFreedomAgent() {
            if ((location.href || '').indexOf('youtube.com/tv') !== -1) return;
            if ((location.hostname || '').indexOf('xploretv') !== -1) return;
            if (window._safeer_yt_agent_installed) return;
            window._safeer_yt_agent_installed = true;

            // 🧠 Safeer YouTube Instant Song Accelerator & Track Transition Agent
            var ytAgent = {
                lastHref: location.href,
                lastTriggerTime: 0,
                initialPlayDone: false,
                
                init: function() {
                    this.injectPerformanceHints();
                    this.startSupervision();
                },

                // Pospeši povezovanje z Googlovimi video strežniki (Preconnect & DNS-prefetch)
                injectPerformanceHints: function() {
                    try {
                        var preconnects = [
                            'https://googlevideo.com',
                            'https://i.ytimg.com',
                            'https://yt3.ggpht.com',
                            'https://m.youtube.com',
                            'https://www.youtube.com',
                            'https://youtubei.googleapis.com',
                            'https://jnn-pa.googleapis.com'
                        ];
                        preconnects.forEach(function(url) {
                            var link = document.createElement('link');
                            link.rel = 'preconnect';
                            link.href = url;
                            link.crossOrigin = 'anonymous';
                            document.head.appendChild(link);

                            var dnsLink = document.createElement('link');
                            dnsLink.rel = 'dns-prefetch';
                            dnsLink.href = url;
                            document.head.appendChild(dnsLink);
                        });
                    } catch(e) {}
                },

                // ⚡ Bliskovito pospeši predvajanje nove skladbe brez zakasnitev
                boostPlayback: function() {
                    try {
                        if ((location.hostname || '').indexOf('xploretv') !== -1) return;
                        var isWatchPage = location.pathname.indexOf('/watch') !== -1 || location.pathname.indexOf('/shorts') !== -1;
                        if (!isWatchPage) return;

                        // 🔄 Zaznaj zamenjavo pesmi (New Song Transition) in hipno ponastavi stanje
                        if (location.href !== this.lastHref) {
                            this.lastHref = location.href;
                            this.initialPlayDone = false;
                            this.lastTriggerTime = 0;
                            var v = document.querySelector('video');
                            if (v) {
                                v._safeer_user_paused = false;
                                v.preload = 'auto';
                                try { v.play().catch(function() {}); } catch(_) {}
                            }
                        }

                        var video = document.querySelector('video');
                        var moviePlayer = document.getElementById('movie_player') ||
                                          document.querySelector('.html5-video-player');

                        var now = Date.now();

                        // 🚀 Enkraten zagon predvajanja ob začetku nove skladbe (brez motenja predvajalnika)
                        if ((!video || (video.paused && !video._safeer_user_paused)) && !this.initialPlayDone) {
                            if (now - this.lastTriggerTime > 250) {
                                this.lastTriggerTime = now;
                                if (video) {
                                    try { video.play().catch(function() {}); } catch(_) {}
                                }
                                if (moviePlayer && typeof moviePlayer.playVideo === 'function') {
                                    try { moviePlayer.playVideo(); } catch(_) {}
                                }
                                var playTriggers = document.querySelectorAll(
                                    '.ytp-large-play-button, .ytp-cued-thumbnail-overlay, .ytp-cued-thumbnail-overlay-image, ' +
                                    'button.ytp-play-button[aria-label*="Predvajaj"], button.ytp-play-button[aria-label*="Play"], ' +
                                    'div.player-container, #player-control-container, ytm-player-microformat-renderer'
                                );
                                for (var t = 0; t < playTriggers.length; t++) {
                                    try {
                                        playTriggers[t].dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                                        playTriggers[t].click();
                                    } catch(_) {}
                                }
                            }
                        }

                        if (video && !video.paused && video.currentTime > 0.5) {
                            this.initialPlayDone = true;
                        }

                        if (!video) return;

                        video.preload = 'auto';
                        video.setAttribute('playsinline', 'true');
                        video.setAttribute('webkit-playsinline', 'true');

                        if (!video._safeer_instant_hooks) {
                            video._safeer_instant_hooks = true;
                            var onMediaReady = function() {
                                if (!video._safeer_user_paused && video.paused) {
                                    try { video.play().catch(function() {}); } catch(_) {}
                                }
                            };
                            video.addEventListener('loadstart', onMediaReady);
                            video.addEventListener('loadedmetadata', onMediaReady);
                            video.addEventListener('canplay', onMediaReady);
                            video.addEventListener('canplaythrough', onMediaReady);
                            video.addEventListener('pause', function() {
                                if (!video.ended && video.readyState >= 2 && location.href === ytAgent.lastHref) {
                                    video._safeer_user_paused = true;
                                }
                            });
                            video.addEventListener('play', function() {
                                video._safeer_user_paused = false;
                            });
                        }

                        var isAd = false;
                        if (moviePlayer && moviePlayer.classList) {
                            isAd = moviePlayer.classList.contains('ad-showing') ||
                                   moviePlayer.classList.contains('ad-interrupting');
                        }

                        // 🚫 Takojšen preskok oglasa v 0s
                        if (isAd) {
                            video.muted = true;
                            if (isFinite(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration;
                            }
                            video.playbackRate = 16.0;
                            if (moviePlayer && typeof moviePlayer.skipAd === 'function') {
                                try { moviePlayer.skipAd(); } catch(_) {}
                            }
                        } else {
                            // ✅ Normalna skladba: povrni hitrost in vklopi zvok
                            if (video.playbackRate > 2.0) {
                                video.playbackRate = 1.0;
                                video.muted = false;
                            }
                            if (video.muted) {
                                video.muted = false;
                            }
                            if (video.volume < 1.0) {
                                video.volume = 1.0;
                            }

                            // 🚀 Bliskovit vžig skladbe (samo ko je naložen medpomnilnik readyState >= 3 za preprečevanje zatikanja)
                            if (video.paused && !video.ended && !video._safeer_user_paused && video.readyState >= 3) {
                                var playPromise = video.play();
                                if (playPromise !== undefined) {
                                    playPromise.catch(function() {});
                                }
                            }
                        }

                        // 🎯 Preskok oglasnih gumbov
                        var skipBtn = document.querySelector(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                            '.ytp-ad-overlay-close-button, button.ytp-ad-skip-button-text, ' +
                            '.ytp-ad-skip-button-slot button, [id^="skip-button"], ' +
                            'button[aria-label*="Preskoči"], button[aria-label*="Skip"]'
                        );
                        if (skipBtn) skipBtn.click();

                        // 🔊 Vklop zvoka in takojšen izbris Mute gumba
                        var unmuteBtns = document.querySelectorAll(
                            '.ytp-unmute, .ytp-unmute-inner, .ytp-unmute-animated, .ytp-unmute-box, ' +
                            'button[aria-label*="Vklopite zvok"], button[aria-label*="Unmute"]'
                        );
                        for (var u = 0; u < unmuteBtns.length; u++) {
                            try { unmuteBtns[u].click(); unmuteBtns[u].remove(); } catch(_) {}
                        }

                    } catch(e) {}
                },

                // 🛡️ Samodejno zdravljenje napak
                healErrors: function() {
                    try {
                        var isWatchPage = location.pathname.indexOf('/watch') !== -1 || location.pathname.indexOf('/shorts') !== -1;
                        if (!isWatchPage) return;

                        var errorContainer = document.querySelector('.ytp-error, .yt-playability-error-supported-renderers, ytm-player-error-message-renderer');
                        var retryBtn = document.querySelector('button[aria-label*="znova"], button[aria-label*="retry"], .ytp-error-content button, ytm-player-error-message-renderer button');

                        if (errorContainer || retryBtn) {
                            if (retryBtn) {
                                retryBtn.click();
                            }
                        }

                        // Odstrani gumb "Odpri aplikacijo", promocije aplikacije in modalna okna
                        var appPromos = document.querySelectorAll(
                            'ytm-open-app-button, ytm-app-promo-renderer, ytm-mealbar-promo-renderer, ytm-upsell-dialog-renderer, ' +
                            '.topbar-action-buttons, button[aria-label*="Odpri aplikacijo"], button[aria-label*="Open app"], ' +
                            '[aria-label*="Odpri"], [aria-label*="Open in app"]'
                        );
                        for (var p = 0; p < appPromos.length; p++) {
                            try { appPromos[p].style.display = 'none'; appPromos[p].remove(); } catch(_) {}
                        }

                        // 🚫 Samodejno zapri vsiljena pojavna okna seznamov predvajanja / miksov
                        var playlistCloseBtns = document.querySelectorAll(
                            'ytm-engagement-panel-section-list-renderer button.header-close-button, ' +
                            'ytm-engagement-panel-section-list-renderer button[aria-label*="Zapri"], ' +
                            'ytm-engagement-panel-section-list-renderer button[aria-label*="Close"], ' +
                            'ytm-bottom-sheet-renderer button.bottom-sheet-layout-close-button, ' +
                            'button[aria-label*="Zapri ploščo"], button[aria-label*="Close panel"], ' +
                            '.bottom-sheet-layout-close-button, .header-close-button, .panel-header-close-button'
                        );
                        for (var pcb = 0; pcb < playlistCloseBtns.length; pcb++) {
                            try { playlistCloseBtns[pcb].click(); } catch(_) {}
                        }

                        // 🚫 Odstrani zatemnitev in zameglitev videa
                        var backdrops = document.querySelectorAll('.engagement-panel-backdrop, ytm-bottom-sheet-renderer.backdrop');
                        for (var bd = 0; bd < backdrops.length; bd++) {
                            try {
                                backdrops[bd].style.display = 'none';
                                backdrops[bd].style.opacity = '0';
                                backdrops[bd].style.pointerEvents = 'none';
                            } catch(_) {}
                        }
                    } catch(e) {}
                },

                // Stalni prilagodljivi nadzorni cikel agenta (250ms ob oglasih, 1500ms med nemotenim predvajanjem)
                startSupervision: function() {
                    var self = this;
                    var _supervisorTimer = null;

                    function runSupervisorCycle() {
                        self.boostPlayback();
                        self.healErrors();

                        var video = document.querySelector('video');
                        var isAd = playerHasAd();
                        var isSmoothPlaying = video && !video.paused && video.readyState >= 3 && !isAd;
                        var nextInterval = (isAd || !self.initialPlayDone) ? 250 : (isSmoothPlaying ? 1500 : 350);
                        scheduleNextCycle(nextInterval);
                    }

                    function scheduleNextCycle(intervalMs) {
                        if (_supervisorTimer) clearTimeout(_supervisorTimer);
                        _supervisorTimer = setTimeout(runSupervisorCycle, intervalMs);
                    }
                    self._scheduleNextCycle = scheduleNextCycle;

                    scheduleNextCycle(250);

                    window.addEventListener('yt-navigate-start', function() {
                        self.lastTriggerTime = 0;
                        self.initialPlayDone = false;
                        var v = document.querySelector('video');
                        if (v) {
                            v._safeer_user_paused = false;
                            v.preload = 'auto';
                        }
                        scheduleNextCycle(150);
                    });
                    window.addEventListener('yt-navigate-finish', function() { scheduleNextCycle(150); });
                    window.addEventListener('yt-page-data-updated', function() { scheduleNextCycle(200); });
                    window.addEventListener('popstate', function() { scheduleNextCycle(150); });
                    document.addEventListener('DOMContentLoaded', function() { scheduleNextCycle(200); });
                }
            };

            ytAgent.init();
        })();
    """

    private const val BACKGROUND_PLAYBACK_JS = """
        /* 🎵 Safeer Browser Background Audio & Lock-Screen Playback Engine */
        (function() {
            if ((location.href || '').indexOf('youtube.com/tv') !== -1) return;
            if ((location.hostname || '').indexOf('xploretv.si') !== -1) return;
            if (window._safeer_bg_playback_installed) return;
            window._safeer_bg_playback_installed = true;

            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'hasFocus', { value: function() { return true; }, configurable: true });
            } catch(e) {}

            var stopEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur', 'focusout'];
            for (var i = 0; i < stopEvents.length; i++) {
                (function(name) {
                    window.addEventListener(name, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                    document.addEventListener(name, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                })(stopEvents[i]);
            }

            var origPause = HTMLMediaElement.prototype.pause;
            var origPlay = HTMLMediaElement.prototype.play;

            var lastUserInteractionTime = Date.now();
            var userExplicitlyPaused = false;
            var lastBgHref = location.href;

            var userActionEvents = ['click', 'touchstart', 'touchend', 'pointerdown', 'pointerup', 'keydown'];
            for (var u = 0; u < userActionEvents.length; u++) {
                window.addEventListener(userActionEvents[u], function() {
                    lastUserInteractionTime = Date.now();
                }, true);
            }

            HTMLMediaElement.prototype.pause = function() {
                if ((location.hostname || '').indexOf('xploretv') !== -1) {
                    return origPause.apply(this, arguments);
                }
                if (window._safeer_app_bg) {
                    return origPause.apply(this, arguments);
                }
                var elapsed = Date.now() - lastUserInteractionTime;
                // Če se menja pesem ali je video končan, dovoli naravno pavzo za zagon nove skladbe
                if (location.href !== lastBgHref || this.ended || this.readyState < 2 || (isFinite(this.duration) && this.duration > 0 && Math.abs(this.currentTime - this.duration) < 1.0)) {
                    lastBgHref = location.href;
                    return origPause.apply(this, arguments);
                }
                // Če je pavza sprožena brez neposrednega klika uporabnika, jo ignoriraj za predvajanje v ozadju
                if (elapsed > 800) {
                    return;
                }
                userExplicitlyPaused = true;
                return origPause.apply(this, arguments);
            };

            HTMLMediaElement.prototype.play = function() {
                if ((location.hostname || '').indexOf('xploretv') !== -1) {
                    return origPlay.apply(this, arguments);
                }
                userExplicitlyPaused = false;
                lastBgHref = location.href;
                return origPlay.apply(this, arguments);
            };

            function hookPlayerObject() {
                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (player && !player._safeer_bg_hooked) {
                    player._safeer_bg_hooked = true;
                    var origPauseVideo = player.pauseVideo;
                    if (typeof origPauseVideo === 'function') {
                        player.pauseVideo = function() {
                            var elapsed = Date.now() - lastUserInteractionTime;
                            var v = document.querySelector('video');
                            if (location.href !== lastBgHref || (v && (v.ended || v.readyState < 2 || (isFinite(v.duration) && v.duration > 0 && Math.abs(v.currentTime - v.duration) < 1.0)))) {
                                lastBgHref = location.href;
                                return origPauseVideo.apply(this, arguments);
                            }
                            if (elapsed > 800) {
                                return;
                            }
                            userExplicitlyPaused = true;
                            return origPauseVideo.apply(this, arguments);
                        };
                    }
                }
            }

            if ('mediaSession' in navigator) {
                try {
                    navigator.mediaSession.playbackState = 'playing';
                    navigator.mediaSession.setActionHandler('pause', function() {
                        userExplicitlyPaused = true;
                        var v = document.querySelector('video');
                        if (v) origPause.call(v);
                    });
                    navigator.mediaSession.setActionHandler('play', function() {
                        userExplicitlyPaused = false;
                        var v = document.querySelector('video');
                        if (v) origPlay.call(v);
                    });
                } catch(e) {}
            }

            // Stalni nadzornik za neprekinjeno predvajanje v ozadju
            setInterval(function() {
                if ((location.hostname || '').indexOf('xploretv') !== -1) return;
                if ((location.hostname || '').toLowerCase().indexOf('24ur') !== -1) return;
                if ((location.hostname || '').toLowerCase().indexOf('hydrahd') !== -1) return;
                if (window._safeer_app_bg || document.hidden) return;
                hookPlayerObject();
                var video = document.querySelector('video');
                if (video && video.paused && !video.ended && !userExplicitlyPaused && video.readyState >= 2) {
                    video.play().catch(function() {});
                }
            }, 500);
        })();
    """

    const val GPC_AND_DNT_JS = """
        /* 🔒 Safeer Global Privacy Control (GPC) & Do Not Track (DNT) W3C Engine */
        (function() {
            if (window._safeer_gpc_active) return;
            window._safeer_gpc_active = true;
            var gpcProp = { value: true, writable: false, configurable: false, enumerable: true };
            var dntProp = { value: '1', writable: false, configurable: false, enumerable: true };
            try {
                Object.defineProperty(navigator, 'globalPrivacyControl', gpcProp);
                Object.defineProperty(navigator, 'doNotTrack', dntProp);
                if (window.Navigator && window.Navigator.prototype) {
                    Object.defineProperty(window.Navigator.prototype, 'globalPrivacyControl', gpcProp);
                    Object.defineProperty(window.Navigator.prototype, 'doNotTrack', dntProp);
                }
            } catch(e) {}
        })();
    """

    const val FORCE_UNMUTE_JS = """
        (function() {
            if (window._safeer_force_unmute) return;
            var host = (location.hostname || '').toLowerCase();
            var href = (location.href || '').toLowerCase();
            if (host.indexOf('xploretv') !== -1 || host.indexOf('a1xploretv') !== -1) return;
            if (host.indexOf('24ur') !== -1) return;
            if (href.indexOf('youtube.com/tv') !== -1) return;
            if (href.indexOf('brave_home') !== -1) return;
            window._safeer_force_unmute = true;

            function lockEl(v) {
                if (!v || v._safeer_audio_lock) return;
                if (v.readyState < 2) return;
                v._safeer_audio_lock = true;
                try { v.defaultMuted = false; } catch (e0) {}
                try { v.removeAttribute('muted'); } catch (e1) {}
                try {
                    var desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'muted');
                    try { if (desc && desc.set) desc.set.call(v, false); else v.muted = false; } catch (e2) {}
                    Object.defineProperty(v, 'muted', {
                        configurable: true,
                        get: function() { return false; },
                        set: function() {
                            try { if (desc && desc.set) desc.set.call(v, false); } catch (e3) {}
                        }
                    });
                } catch (e4) {
                    try { v.muted = false; } catch (e5) {}
                }
                try { if (v.volume < 0.15) v.volume = 1.0; } catch (e6) {}
            }

            function sweep() {
                try {
                    var vids = document.querySelectorAll('video, audio');
                    for (var i = 0; i < vids.length; i++) {
                        var v = vids[i];
                        if (!v || v.ended || v.paused || v.readyState < 2) continue;
                        lockEl(v);
                    }
                } catch (eSw) {}
            }

            document.addEventListener('playing', function(ev) { lockEl(ev.target); }, true);
            setInterval(sweep, 1200);
        })();
    """

    private const val MOBILE_MEDIA_AUDIO_JS = """
        (function() {
            try {
                var vids = document.querySelectorAll('video, audio');
                for (var i = 0; i < vids.length; i++) {
                    var v = vids[i];
                    v.muted = false;
                    v.defaultMuted = false;
                    v.volume = 1.0;
                    v.setAttribute('playsinline', 'true');
                    v.setAttribute('webkit-playsinline', 'true');
                }
            } catch(e) {}
        })();
    """

    @Volatile
    private var cachedTvSpatialJs: String? = null
    @Volatile
    private var cachedSiteXploreJs: String? = null
    @Volatile
    private var cachedSiteHydraJs: String? = null
    @Volatile
    private var cachedSite24urJs: String? = null
    @Volatile
    private var cachedSiteAgentJs: String? = null
    @Volatile
    private var cachedXploreAuthJs: String? = null

    private fun assetJs(webView: WebView, name: String, cache: () -> String?, store: (String) -> Unit): String {
        cache()?.let { return it }
        val js = webView.context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        store(js)
        return js
    }

    private fun siteAgentJs(webView: WebView): String {
        return assetJs(webView, "site_agent.js", { cachedSiteAgentJs }, { cachedSiteAgentJs = it })
    }

    private fun tvSpatialJs(webView: WebView): String {
        return assetJs(webView, "tv_spatial.js", { cachedTvSpatialJs }, { cachedTvSpatialJs = it })
    }

    private fun siteXploreJs(webView: WebView): String {
        return assetJs(webView, "site_xplore.js", { cachedSiteXploreJs }, { cachedSiteXploreJs = it })
    }

    private fun siteHydraJs(webView: WebView): String {
        return assetJs(webView, "site_hydra.js", { cachedSiteHydraJs }, { cachedSiteHydraJs = it })
    }

    private fun site24urJs(webView: WebView): String {
        return assetJs(webView, "site_24ur.js", { cachedSite24urJs }, { cachedSite24urJs = it })
    }

    private fun xploreAuthJs(webView: WebView): String {
        return try {
            assetJs(webView, "xplore_auth.js", { cachedXploreAuthJs }, { cachedXploreAuthJs = it })
        } catch (_: Exception) {
            "window._safeerXploreAuth = null;"
        }
    }

    private const val YOUTUBE_TV_LEANBACK_JS = """
        (function initYouTubeTvLeanback() {
            if ((location.href || '').indexOf('youtube.com/tv') === -1) return;
            if (window._safeer_yt_tv_leanback) return;
            window._safeer_yt_tv_leanback = true;

            try {
                var preconnects = [
                    'https://googlevideo.com',
                    'https://i.ytimg.com',
                    'https://yt3.ggpht.com',
                    'https://www.youtube.com',
                    'https://youtubei.googleapis.com',
                    'https://jnn-pa.googleapis.com'
                ];
                preconnects.forEach(function(url) {
                    var link = document.createElement('link');
                    link.rel = 'preconnect';
                    link.href = url;
                    link.crossOrigin = 'anonymous';
                    document.head.appendChild(link);

                    var dnsLink = document.createElement('link');
                    dnsLink.rel = 'dns-prefetch';
                    dnsLink.href = url;
                    document.head.appendChild(dnsLink);
                });
            } catch(e) {}

            function isAdNode(item) {
                if (!item || typeof item !== 'object') return false;
                return !!(item.adSlotRenderer || item.promotedVideoRenderer || item.inFeedAdLayoutRenderer ||
                    item.promotedSparklesWebRenderer || item.promotedSparklesTextRenderer ||
                    item.promotedSparklesRenderer || item.displayAdRenderer || item.mastheadAdRenderer ||
                    item.houseAdRenderer || item.adVideoEndRenderer || item.promotedItemRenderer ||
                    item.bannerPromoRenderer || item.adInfoRenderer || item.instreamVideoAdRenderer ||
                    item.playerLegacyDesktopWatchAdsRenderer);
            }

            function tileLooksSponsored(tile) {
                if (!tile || typeof tile !== 'object') return false;
                try {
                    var style = (tile.style || '') + '';
                    if (style.toUpperCase().indexOf('SPONSOR') !== -1) return true;
                    var blob = JSON.stringify(tile.metadata || tile.header || {}).toLowerCase();
                    if (blob.indexOf('sponzorirano') !== -1 || blob.indexOf('sponsored') !== -1) return true;
                } catch (e) {}
                return false;
            }

            function stripAds(obj, depth) {
                if (!obj || typeof obj !== 'object' || depth > 36) return obj;
                if (Array.isArray(obj)) {
                    for (var i = obj.length - 1; i >= 0; i--) {
                        var item = obj[i];
                        if (item && typeof item === 'object') {
                            if (isAdNode(item) || (item.tileRenderer && tileLooksSponsored(item.tileRenderer))) {
                                obj.splice(i, 1);
                                continue;
                            }
                            stripAds(item, depth + 1);
                        }
                    }
                    return obj;
                }
                if (obj.adPlacements) obj.adPlacements = [];
                if (obj.adSlots) obj.adSlots = [];
                if (obj.playerAds) obj.playerAds = [];
                if (obj.adBreaks) obj.adBreaks = [];
                try { delete obj.adBreakHeartbeatParams; } catch (e) {}
                if (obj.playbackTracking && typeof obj.playbackTracking === 'object') {
                    try {
                        delete obj.playbackTracking.videostatsPlaybackUrl;
                        delete obj.playbackTracking.videostatsDelayplayUrl;
                        delete obj.playbackTracking.videostatsWatchtimeUrl;
                        delete obj.playbackTracking.ptrackingUrl;
                        delete obj.playbackTracking.qoeUrl;
                        delete obj.playbackTracking.atrUrl;
                    } catch(eTr) {}
                }
                var keys = Object.keys(obj);
                for (var k = 0; k < keys.length; k++) {
                    var v = obj[keys[k]];
                    if (v && typeof v === 'object') stripAds(v, depth + 1);
                }
                return obj;
            }

            function looksLikeYt(obj) {
                return !!(obj && (obj.adPlacements || obj.adSlots || obj.playerAds || obj.videoDetails ||
                    obj.contents || obj.responseContext || obj.streamingData || obj.playabilityStatus ||
                    obj.onResponseReceivedEndpoints));
            }

            try {
                var origParse = JSON.parse;
                JSON.parse = function(text) {
                    var data = origParse.apply(this, arguments);
                    try {
                        if (data && typeof data === 'object' && looksLikeYt(data)) stripAds(data, 0);
                    } catch (e) {}
                    return data;
                };
            } catch (e) {}

            function compactText(el) {
                if (!el) return '';
                var t = ((el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('title'))) || el.innerText || '').replace(/\s+/g, ' ').trim();
                if (t.length > 80) t = t.substring(0, 80);
                return t.toLowerCase();
            }

            function clickEl(el) {
                if (!el) return false;
                try {
                    if (typeof el.click === 'function') el.click();
                    else el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    return true;
                } catch (e) {
                    return false;
                }
            }

            window._safeer_yt_tv_search = function(q) {
                try {
                    var query = (q || '').toString();
                    location.hash = query ? ('#/search?q=' + encodeURIComponent(query)) : '#/search';
                } catch (e) {}
            };

            var guestDone = false;
            var lastSkip = 0;

            function skipVideoAd() {
                var nodes = document.querySelectorAll('button, [role="button"]');
                var skipBtn = null;
                var countdownAd = false;
                for (var i = 0; i < nodes.length; i++) {
                    var t = compactText(nodes[i]);
                    if (t.indexOf('preskočite čez') !== -1 || t.indexOf('skip in') !== -1 || t.indexOf('skip after') !== -1) {
                        countdownAd = true;
                        continue;
                    }
                    if (t === 'preskoči' || t === 'preskoci' || t === 'skip' || t === 'skip ad' || t === 'skip ads' ||
                        t.indexOf('preskoči oglas') !== -1) {
                        skipBtn = nodes[i];
                        break;
                    }
                }
                if (!skipBtn && !countdownAd) {
                    var bodyText = ((document.body && document.body.innerText) || '').toLowerCase();
                    countdownAd = bodyText.indexOf('preskočite čez') !== -1 || bodyText.indexOf('skip in') !== -1;
                }
                if (!skipBtn && !countdownAd) return;

                var now = Date.now();
                if (skipBtn && now - lastSkip > 400) {
                    if (clickEl(skipBtn)) lastSkip = now;
                }

                var videos = document.querySelectorAll('video');
                for (var v = 0; v < videos.length; v++) {
                    try {
                        if (isFinite(videos[v].duration) && videos[v].duration > 0) {
                            videos[v].currentTime = videos[v].duration;
                        }
                        videos[v].playbackRate = 16;
                        videos[v].muted = true;
                    } catch (e) {}
                }
            }

            function hideSponsoredTiles() {
                var hash = (location.hash || '').toLowerCase();
                if (hash.indexOf('/watch') !== -1) return;
                var labels = document.querySelectorAll('yt-formatted-string, span, p');
                var max = Math.min(labels.length, 80);
                for (var i = 0; i < max; i++) {
                    var el = labels[i];
                    var t = ((el.textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                    if (t !== 'sponzorirano' && t !== 'sponsored') continue;
                    var p = el;
                    for (var u = 0; u < 10 && p; u++) {
                        try {
                            var r = p.getBoundingClientRect();
                            if (r.width > 160 && r.width < window.innerWidth * 0.7 && r.height > 80 && r.height < window.innerHeight * 0.7) {
                                p.style.display = 'none';
                                break;
                            }
                        } catch (e) {}
                        p = p.parentElement;
                    }
                }
            }

            function guestAssist() {
                var hash = (location.hash || '').toLowerCase();
                if (hash.indexOf('/search') !== -1 || hash.indexOf('/watch') !== -1) return;
                if (guestDone) return;
                var nodes = document.querySelectorAll('button, [role="button"]');
                var i, el, t;
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    t = compactText(el);
                    if (t.indexOf('glejte kot gost') !== -1 || t.indexOf('watch as guest') !== -1 ||
                        t.indexOf('continue as guest') !== -1) {
                        if (clickEl(el)) { guestDone = true; return; }
                    }
                }
                for (i = 0; i < nodes.length; i++) {
                    el = nodes[i];
                    t = compactText(el);
                    if (t === 'začnite' || t === 'zacnite' || t === 'get started') {
                        clickEl(el);
                        return;
                    }
                }
            }

            function stripGlobals() {
                try {
                    if (window.ytInitialPlayerResponse) stripAds(window.ytInitialPlayerResponse, 0);
                    if (window.ytInitialData) stripAds(window.ytInitialData, 0);
                } catch (e) {}
            }

            stripGlobals();
            guestAssist();
            skipVideoAd();
            hideSponsoredTiles();
            setInterval(function() {
                guestAssist();
                skipVideoAd();
                hideSponsoredTiles();
                stripGlobals();
            }, 700);
        })();
    """

    private const val XPLORE_LIVE_JS = """
        (function() {
            if ((location.hostname || '').indexOf('xploretv') === -1) return;
            if (window._safeer_xplore_live_helpers) return;
            window._safeer_xplore_live_helpers = true;
            function paintXploreDark() {
                try {
                    if (window._safeer_xplore_want_play || window._safeer_xplore_playing) return;
                    document.documentElement.classList.add('safeer-xplore-dark');
                    try { document.documentElement.style.colorScheme = 'dark'; } catch (_) {}
                    var painted = 0;
                    var samples = [];
                    var els = document.querySelectorAll('html, body, #root, #app, main, section, article, div');
                    var i, el, bg, r, cls, m, lum, skip;
                    for (i = 0; i < els.length; i++) {
                        el = els[i];
                        cls = ((el.className || '') + '').toString().toLowerCase();
                        skip = cls.indexOf('clpp') !== -1 || cls.indexOf('player') !== -1 || cls.indexOf('poster') !== -1 ||
                            cls.indexOf('thumb') !== -1 || cls.indexOf('logo') !== -1 || cls.indexOf('overlay') !== -1 ||
                            el.tagName === 'VIDEO' || el.tagName === 'IMG';
                        if (skip) continue;
                        try { if (el.closest && el.closest('video, [class*="clpp"], [class*="player"], [class*="overlays-layer"]')) continue; } catch (_) {}
                        r = el.getBoundingClientRect();
                        if (r.width < 360 || r.height < 40) continue;
                        bg = (window.getComputedStyle(el).backgroundColor || '');
                        m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
                        if (!m) continue;
                        lum = (parseInt(m[1], 10) + parseInt(m[2], 10) + parseInt(m[3], 10)) / 3;
                        if (lum < 188) continue;
                        el.style.setProperty('background-color', '#07090d', 'important');
                        el.style.setProperty('background-image', 'none', 'important');
                        if (cls.indexOf('options-wrapper') !== -1 || cls.indexOf('menu') !== -1) {
                            el.style.setProperty('color', '#e8eef5', 'important');
                        }
                        painted++;
                        if (samples.length < 8) samples.push({ cls: cls.slice(0, 70), bg: bg, w: Math.round(r.width), h: Math.round(r.height) });
                    }
                    // #region agent log
                    try {
                        if (window._safeerDbg && (!window._safeer_xplore_dark_logged || painted > (window._safeer_xplore_dark_n || 0))) {
                            window._safeer_xplore_dark_logged = true;
                            window._safeer_xplore_dark_n = painted;
                            window._safeerDbg('H80', 'UserScriptManager.kt:paint_dark', 'painted', {
                                painted: painted,
                                samples: samples,
                                body: getComputedStyle(document.body).backgroundColor,
                                path: (location.pathname || '').slice(0, 40)
                            });
                        }
                    } catch (_) {}
                    // #endregion
                } catch (_) {}
            }
            paintXploreDark();
            window._safeer_xplore_list_live_tiles = function() {
                var out = [];
                var seen = {};
                var els = document.querySelectorAll('a, [role="link"], [class*="card"], [class*="Card"], [class*="tile"], [class*="item"]');
                var i, el, t, tl, r, key;
                for (i = 0; i < els.length; i++) {
                    el = els[i];
                    t = ((el.innerText || el.textContent || '') + '').replace(/\\s+/g, ' ').trim();
                    tl = t.toLowerCase();
                    if (tl.indexOf('v živo') === -1 && tl.indexOf('v zivo') === -1) continue;
                    r = el.getBoundingClientRect();
                    if (r.width < 90 || r.height < 50) continue;
                    key = t.slice(0, 48);
                    if (seen[key]) continue;
                    seen[key] = 1;
                    el.setAttribute('data-safeer-live-idx', String(out.length));
                    out.push({ i: out.length, t: t.slice(0, 80), w: Math.round(r.width), h: Math.round(r.height) });
                }
                try { if (window._safeerDbg) window._safeerDbg('H9', 'UserScriptManager.kt:live_tiles', 'live tiles', { n: out.length, tiles: out.slice(0, 12) }); } catch (_) {}
                return out;
            };
            window._safeer_xplore_list_programs = function() {
                var out = [];
                var seen = {};
                var els = document.querySelectorAll('.item.item--event, .item--event');
                var i, el, t, r, key;
                for (i = 0; i < els.length; i++) {
                    el = els[i];
                    r = el.getBoundingClientRect();
                    if (r.width < 120 || r.height < 70) continue;
                    if (r.bottom < 80 || r.top > ((window.innerHeight || 1080) - 20)) continue;
                    t = ((el.innerText || el.textContent || '') + '').replace(/\\s+/g, ' ').trim();
                    key = t.slice(0, 36);
                    if (!key || seen[key]) continue;
                    seen[key] = 1;
                    el.setAttribute('data-safeer-prog-idx', String(out.length));
                    out.push({ i: out.length, t: t.slice(0, 70), w: Math.round(r.width), h: Math.round(r.height) });
                }
                try { if (window._safeerDbg) window._safeerDbg('H9', 'UserScriptManager.kt:programs', 'program tiles', { n: out.length, tiles: out.slice(0, 12) }); } catch (_) {}
                return out;
            };
            window._safeer_xplore_play_program = function(idx) {
                try {
                    var list = window._safeer_xplore_list_programs();
                    var el = document.querySelector('[data-safeer-prog-idx="' + idx + '"]');
                    var title = (list[idx] && list[idx].t) || '';
                    try { if (window._safeerDbg) window._safeerDbg('H13', 'UserScriptManager.kt:play_program', 'play program', { idx: idx, found: !!el, t: title, n: list.length }); } catch (_) {}
                    if (!el) return false;
                    try { sessionStorage.setItem('safeer_xplore_autoplay', '1'); } catch (_) {}
                    window._safeer_xplore_want_play = true;
                    window._safeer_xplore_video_boosted = false;
                    window._safeer_xplore_playing = false;
                    window._safeer_xplore_playbtn_n = 0;
                    try {
                        var prevs = document.querySelectorAll('.safeer-active-card');
                        var pi;
                        for (pi = 0; pi < prevs.length; pi++) prevs[pi].classList.remove('safeer-active-card');
                        el.classList.add('safeer-active-card');
                    } catch (_) {}
                    if (window._safeer_click_focused_card) {
                        return window._safeer_click_focused_card();
                    }
                    try { el.click(); } catch (_) {}
                    return true;
                } catch (_) {}
                return false;
            };
            window._safeer_xplore_play_live_tile = function(idx) {
                try {
                    var list = window._safeer_xplore_list_live_tiles();
                    var el = document.querySelector('[data-safeer-live-idx="' + idx + '"]');
                    var title = (list[idx] && list[idx].t) || '';
                    try { if (window._safeerDbg) window._safeerDbg('H9', 'UserScriptManager.kt:play_live', 'play live tile', { idx: idx, found: !!el, t: title, n: list.length }); } catch (_) {}
                    if (!el) return false;
                    try { sessionStorage.setItem('safeer_xplore_autoplay', '1'); } catch (_) {}
                    window._safeer_xplore_want_play = true;
                    window._safeer_xplore_video_boosted = false;
                    window._safeer_xplore_playing = false;
                    try { el.click(); } catch (_) {}
                    return true;
                } catch (_) {}
                return false;
            };
            window._safeer_xplore_exit_player = function() {
                try {
                    window._safeer_xplore_playing = false;
                    window._safeer_xplore_want_play = false;
                    window._safeer_xplore_video_boosted = false;
                    window._safeer_xplore_fs_clicked = false;
                    window._safeer_xplore_replay_clicked = false;
                    window._safeer_xplore_player_el = null;
                    try { document.documentElement.classList.remove('safeer-xplore-fs'); } catch (_) {}
                    try { if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) window.SafeerBridge.setChromeHidden(true); } catch (_) {}
                    location.href = 'https://www.xploretv.si/home';
                } catch (_) {}
            };

            window._safeer_xplore_search = function(query) {
                query = (query || '').toString();
                function findInput() {
                    return document.querySelector('input[placeholder*="tipkanjem"], input[placeholder*="Isk"], input[placeholder*="iskanj"]')
                        || document.querySelector('input[type="text"]:not([readonly])');
                }
                function openIcon() {
                    var el = document.querySelector('.icon-p24_search') || document.querySelector('li.search');
                    if (!el) return false;
                    try { el.click(); } catch (_) {}
                    try { if (el.parentElement) el.parentElement.click(); } catch (_) {}
                    return true;
                }
                function fill() {
                    var input = findInput();
                    if (!input) return false;
                    try {
                        var native = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        native.call(input, query);
                    } catch (e) { input.value = query; }
                    try {
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        input.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'a' }));
                    } catch (_) {}
                    try {
                        if (window._safeerDbg) window._safeerDbg('H10', 'UserScriptManager.kt:xplore_search', 'filled', {
                            q: query.slice(0, 40),
                            val: (input.value || '').slice(0, 40),
                            href: (location.href || '').slice(0, 180)
                        });
                    } catch (_) {}
                    return true;
                }
                openIcon();
                if (!query) {
                    try { if (window._safeerDbg) window._safeerDbg('H10', 'UserScriptManager.kt:xplore_search', 'opened', { hasInput: !!findInput() }); } catch (_) {}
                    return true;
                }
                if (fill()) return true;
                if (!window._safeer_xplore_search_mo) {
                    window._safeer_xplore_search_mo = new MutationObserver(function() {
                        if (fill()) {
                            try { window._safeer_xplore_search_mo.disconnect(); } catch (_) {}
                            window._safeer_xplore_search_mo = null;
                        }
                    });
                    try { window._safeer_xplore_search_mo.observe(document.documentElement, { childList: true, subtree: true }); } catch (_) {}
                }
                return true;
            };
        })();
    """

    private fun isXploreUrl(url: String?): Boolean {
        val u = (url ?: "").lowercase()
        return u.contains("xploretv") || u.contains("a1xploretv")
    }

    private fun isBrowserHome(url: String?): Boolean {
        return (url ?: "").contains("brave_home", ignoreCase = true)
    }

    private fun is24urUrl(url: String?): Boolean {
        return (url ?: "").contains("24ur", ignoreCase = true)
    }

    private fun isHydraUrl(url: String?): Boolean {
        return (url ?: "").contains("hydrahd", ignoreCase = true)
    }

    fun isGoogleAuthUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase()
        return lower.contains("accounts.google") ||
               lower.contains("accounts.youtube") ||
               lower.contains("myaccount.google") ||
               lower.contains("google.com/accounts") ||
               lower.contains("signin/v2") ||
               lower.contains("signin/challenge") ||
               lower.contains("signin/identifier") ||
               lower.contains("v3/signin")
    }

    fun isGoogleDomain(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase()
        val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        if (host.contains("youtube") || host.contains("googlevideo") || host.contains("ytimg")) return false
        return host == "google.com" || host.endsWith(".google.com") ||
               host == "google.si" || host.endsWith(".google.si") ||
               host.contains(".google.") || host.startsWith("google.") ||
               host.contains("recaptcha") ||
               host.contains("gstatic.com") ||
               host.contains("googleapis.com") ||
               isGoogleAuthUrl(url) ||
               lower.contains("google.com/search") ||
               lower.contains("google.si/search") ||
               lower.contains("/recaptcha")
    }

    private const val WINDOWS_CHROME_DESKTOP_JS = """
        (function() {
            var host = (location.hostname || '').toLowerCase();
            if (host.indexOf('24ur') === -1 && host.indexOf('hydrahd') === -1) return;
            var ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36';
            function fake(key, val) {
                try {
                    Object.defineProperty(navigator, key, { configurable: true, enumerable: true, get: function() { return val; } });
                } catch (e) {}
            }
            fake('userAgent', ua);
            fake('appVersion', '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36');
            fake('platform', 'Win32');
            fake('vendor', 'Google Inc.');
            fake('maxTouchPoints', 0);
            try {
                fake('userAgentData', {
                    brands: [{ brand: 'Google Chrome', version: '133' }, { brand: 'Chromium', version: '133' }, { brand: 'Not_A Brand', version: '24' }],
                    mobile: false,
                    platform: 'Windows',
                    getHighEntropyValues: function() {
                        return Promise.resolve({ architecture: 'x86', bitness: '64', mobile: false, model: '', platform: 'Windows', platformVersion: '15.0.0', uaFullVersion: '133.0.0.0' });
                    }
                });
            } catch (e2) {}
            try { window.chrome = window.chrome || { runtime: {} }; } catch (e3) {}
            try {
                var vp = document.querySelector('meta[name="viewport"]');
                if (!vp) {
                    vp = document.createElement('meta');
                    vp.setAttribute('name', 'viewport');
                    (document.head || document.documentElement).appendChild(vp);
                }
                vp.setAttribute('content', 'width=1280, initial-scale=1');
            } catch (e4) {}
        })();
    """

    fun injectWindowsDesktopSpoof(webView: WebView) {
        if (isGoogleDomain(webView.url)) return
        webView.evaluateJavascript(WINDOWS_CHROME_DESKTOP_JS, null)
    }

    private fun injectSiteScripts(webView: WebView, pageUrl: String?, isDarkMode: Boolean, finished: Boolean) {
        val target = pageUrl ?: webView.url ?: ""
        if (isGoogleDomain(target)) {
            // NEVER inject any scripts or CSS into Google authentication, Google Search or reCAPTCHA to preserve 100% native environment
            return
        }
        val xplore = isXploreUrl(pageUrl) || isXploreUrl(webView.url)
        val home = isBrowserHome(pageUrl) || isBrowserHome(webView.url)
        val news24 = is24urUrl(pageUrl) || is24urUrl(webView.url)
        val hydra = isHydraUrl(pageUrl) || isHydraUrl(webView.url)
        if (xplore) {
            injectCss(webView, XPLORE_DARK_CSS, "tv-remote-xplore-dark")
            webView.evaluateJavascript(
                xploreAuthJs(webView) + "\n" + siteAgentJs(webView) + "\n" +
                    tvSpatialJs(webView) + "\n" + siteXploreJs(webView),
                null
            )
            webView.evaluateJavascript(XPLORE_LIVE_JS, null)
            if (finished) {
                webView.evaluateJavascript("try{if(window._safeerSiteAgent)window._safeerSiteAgent.onPageReady()}catch(e){}", null)
            }
            return
        }
        if (!home && !news24 && !hydra) {
            injectCss(webView, CosmeticFilterEngine.buildCosmeticCss(), "safeer-cosmetic-filter")
            if (isDarkMode) {
                injectCss(webView, DARK_MODE_AMOLED_CSS, "safeer-dark-mode-style")
            } else if (finished) {
                removeCss(webView, "safeer-dark-mode-style")
            }
        } else if (news24 || hydra) {
            removeCss(webView, "safeer-dark-mode-style")
            removeCss(webView, "safeer-cosmetic-filter")
            webView.evaluateJavascript(WINDOWS_CHROME_DESKTOP_JS, null)
        }
        if (hydra) {
            webView.evaluateJavascript(FORCE_UNMUTE_JS, null)
        }
        webView.evaluateJavascript(GPC_AND_DNT_JS, null)
        webView.evaluateJavascript(ANTI_POPUNDER_SHIELD_JS, null)
        webView.evaluateJavascript(BACKGROUND_PLAYBACK_JS, null)
        webView.evaluateJavascript(YOUTUBE_FREEDOM_MOBILE_JS, null)
        webView.evaluateJavascript(YOUTUBE_TV_LEANBACK_JS, null)
        webView.evaluateJavascript(siteAgentJs(webView), null)
        webView.evaluateJavascript(tvSpatialJs(webView) + "\n" + siteHydraJs(webView) + "\n" + site24urJs(webView), null)
        if (finished) {
            if (!news24) webView.evaluateJavascript(MOBILE_MEDIA_AUDIO_JS, null)
            webView.evaluateJavascript("try{if(window._safeerSiteAgent)window._safeerSiteAgent.onPageReady()}catch(e){}", null)
        }
    }

    fun injectEarlyScript(webView: WebView, pageUrl: String? = null) {
        val target = pageUrl ?: webView.url ?: ""
        if (isGoogleDomain(target)) return
        val dark = (webView as? ChromiumEngineView)?.isDarkMode ?: true
        injectSiteScripts(webView, pageUrl, dark, finished = false)
    }

    fun injectOnPageFinished(webView: WebView, isDarkMode: Boolean, pageUrl: String? = null) {
        val target = pageUrl ?: webView.url ?: ""
        if (isGoogleDomain(target)) return
        val ping = """
            (function(){
                try {
                    var host = (location.hostname || '').toLowerCase();
                    if (host.indexOf('xploretv') !== -1 && !window._safeer_xplore_helpers_ready) return 'need';
                    if (window._safeer_tv_remote_installed) {
                        try { if (window._safeerSiteAgent) window._safeerSiteAgent.onPageReady(); } catch (e) {}
                        return 'ok';
                    }
                } catch (e2) {}
                return 'need';
            })();
        """.trimIndent()
        webView.evaluateJavascript(ping) { result ->
            if (result != null && result.contains("ok")) {
                val xplore = isXploreUrl(pageUrl) || isXploreUrl(webView.url)
                val news24 = is24urUrl(pageUrl) || is24urUrl(webView.url)
                val hydra = isHydraUrl(pageUrl) || isHydraUrl(webView.url)
                if (!xplore && !news24 && !hydra) {
                    webView.evaluateJavascript(MOBILE_MEDIA_AUDIO_JS, null)
                    if (!isDarkMode) removeCss(webView, "safeer-dark-mode-style")
                } else if (news24 || hydra) {
                    removeCss(webView, "safeer-dark-mode-style")
                    removeCss(webView, "safeer-cosmetic-filter")
                    webView.evaluateJavascript(WINDOWS_CHROME_DESKTOP_JS, null)
                    if (hydra) webView.evaluateJavascript(FORCE_UNMUTE_JS, null)
                }
                return@evaluateJavascript
            }
            injectSiteScripts(webView, pageUrl, isDarkMode, finished = true)
        }
    }

    fun injectDarkModeToggle(webView: WebView, enable: Boolean) {
        if (enable) {
            injectCss(webView, DARK_MODE_AMOLED_CSS, "safeer-dark-mode-style", replace = true)
        } else {
            removeCss(webView, "safeer-dark-mode-style")
        }
    }

    private fun injectCss(webView: WebView, css: String, elementId: String? = null, replace: Boolean = false) {
        val base64 = android.util.Base64.encodeToString(css.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val idStr = elementId ?: "custom-css"
        val force = if (replace) "true" else "false"
        val js = """
            (function() {
                try {
                    var parent = document.head || document.documentElement;
                    if (!parent) return;
                    if ('$idStr' === 'safeer-dark-mode-style' || '$idStr' === 'safeer-cosmetic-filter') {
                        var href = (location.href || '').toLowerCase();
                        var host = (location.hostname || '').toLowerCase();
                        if (href.indexOf('youtube.com/tv') !== -1 || host.indexOf('youtube.') !== -1 || host.indexOf('youtu.be') !== -1 || host.indexOf('xploretv.si') !== -1 || host.indexOf('24ur') !== -1 || host.indexOf('hydrahd') !== -1 || href.indexOf('brave_home') !== -1) {
                            var existing = document.getElementById('$idStr');
                            if (existing) existing.remove();
                            return;
                        }
                    }
                    if ('$idStr' === 'tv-remote-xplore-dark') {
                        var xhost = (location.hostname || '').toLowerCase();
                        if (xhost.indexOf('xploretv') === -1) return;
                    }
                    var old = document.getElementById('$idStr');
                    if (old && !$force) return;
                    if (old) old.remove();
                    var style = document.createElement('style');
                    style.id = '$idStr';
                    style.type = 'text/css';
                    style.textContent = atob('$base64');
                    parent.appendChild(style);
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun removeCss(webView: WebView, elementId: String) {
        val js = """
            (function() {
                var el = document.getElementById('$elementId');
                if (el) el.remove();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
