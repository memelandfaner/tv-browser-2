/* HydraHD smash/unsmash — injected after tv_spatial.js */
(function() {
    if ((location.href || '').indexOf('youtube.com/tv') !== -1) return;
    if ((location.hostname || '').toLowerCase().indexOf('hydrahd') === -1) return;

    var DESKTOP_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36';
    function fakeNav(key, val) {
        try {
            Object.defineProperty(navigator, key, {
                configurable: true,
                enumerable: true,
                get: function() { return val; }
            });
        } catch (_) {}
    }
    fakeNav('userAgent', DESKTOP_UA);
    fakeNav('appVersion', '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36');
    fakeNav('platform', 'Win32');
    fakeNav('vendor', 'Google Inc.');
    fakeNav('maxTouchPoints', 0);
    try {
        fakeNav('userAgentData', {
            brands: [
                { brand: 'Google Chrome', version: '133' },
                { brand: 'Chromium', version: '133' },
                { brand: 'Not_A Brand', version: '24' }
            ],
            mobile: false,
            platform: 'Windows',
            getHighEntropyValues: function() {
                return Promise.resolve({
                    architecture: 'x86', bitness: '64', mobile: false, model: '',
                    platform: 'Windows', platformVersion: '15.0.0', uaFullVersion: '133.0.0.0'
                });
            }
        });
    } catch (_) {}
    try { window.chrome = window.chrome || { runtime: {} }; } catch (_2) {}
    try {
        var vp = document.querySelector('meta[name="viewport"]');
        if (!vp) {
            vp = document.createElement('meta');
            vp.setAttribute('name', 'viewport');
            (document.head || document.documentElement).appendChild(vp);
        }
        vp.setAttribute('content', 'width=1280, initial-scale=1');
    } catch (_3) {}

    if (window._safeer_hydra_smash) return;
    try {
                function isHydraWatchPage() {
                    var h = (location.hostname || '').toLowerCase();
                    if (h.indexOf('hydrahd') === -1) return false;
                    var p = (location.pathname || '').toLowerCase();
                    return p.indexOf('/movie/') !== -1 || p.indexOf('/tv/') !== -1 || p.indexOf('/watch') !== -1;
                }
                function hydraPlayerFrame() {
                    var ifr = document.getElementById('iframePlayer');
                    if (ifr) {
                        var r = ifr.getBoundingClientRect();
                        if (r.width >= 80 && r.height >= 80) return ifr;
                    }
                    var nodes = document.querySelectorAll('iframe.img-responsive, iframe[src*="ythd"], iframe[src*="embed"], iframe[src*="vidlink"], iframe[src*="vidsrc"], iframe[src*="megacloud"]');
                    var i, n, nr;
                    for (i = 0; i < nodes.length; i++) {
                        n = nodes[i];
                        if ((n.id || '') === 'trailerIframeUnique') continue;
                        nr = n.getBoundingClientRect();
                        if (nr.width >= 80 && nr.height >= 80) return n;
                    }
                    return null;
                }
                function ensureHydraFsStyle() {
                    if (document.getElementById('safeer-hydra-fs-style')) return;
                    var st = document.createElement('style');
                    st.id = 'safeer-hydra-fs-style';
                    st.textContent = 'html.safeer-hydra-fs,html.safeer-hydra-fs body{overflow:hidden!important;background:#000!important;margin:0!important;padding:0!important;}' +
                        'html.safeer-hydra-fs .mynav,html.safeer-hydra-fs .logo,html.safeer-hydra-fs header,html.safeer-hydra-fs footer,' +
                        'html.safeer-hydra-fs .footer,html.safeer-hydra-fs aside,html.safeer-hydra-fs #trailerIframeUnique,' +
                        'html.safeer-hydra-fs [class*="recommend"],html.safeer-hydra-fs [class*="related"],' +
                        'html.safeer-hydra-fs .col-xl-3,html.safeer-hydra-fs .col-lg-3,html.safeer-hydra-fs .col-md-3,' +
                        'html.safeer-hydra-fs [id*="server"],html.safeer-hydra-fs .server-list,html.safeer-hydra-fs .select-server,' +
                        'html.safeer-hydra-fs .push-footer-wrapper > *:not(.movie){display:none!important;visibility:hidden!important;pointer-events:none!important;}' +
                        'html.safeer-hydra-fs .browse,html.safeer-hydra-fs .movie,html.safeer-hydra-fs #wdthcontrol,' +
                        'html.safeer-hydra-fs .videomp4,html.safeer-hydra-fs .loader,html.safeer-hydra-fs .iframe-body{' +
                        'position:static!important;transform:none!important;overflow:visible!important;width:100%!important;height:100%!important;max-width:none!important;margin:0!important;padding:0!important;}' +
                        'html.safeer-hydra-fs #iframePlayer,html.safeer-hydra-fs iframe.img-responsive,' +
                        'html.safeer-hydra-fs iframe[src*="embed"],html.safeer-hydra-fs iframe[src*="ythd"]{' +
                        'position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
                        'max-width:none!important;max-height:none!important;z-index:2147483646!important;border:0!important;margin:0!important;padding:0!important;' +
                        'display:block!important;visibility:visible!important;opacity:1!important;background:#000!important;}' +
                        'html.safeer-hydra-fs #safeer-focus-target-ring,html.safeer-hydra-fs .safeer-focus-badge,' +
                        'html.safeer-hydra-fs .safeer-active-card,html.safeer-hydra-fs .tv-remote-focused{' +
                        'outline:none!important;box-shadow:none!important;display:none!important;opacity:0!important;transform:none!important;}';
                    (document.head || document.documentElement).appendChild(st);
                }
                function smashHydraPlayer() {
                    try {
                        if (!isHydraWatchPage()) {
                            window._safeer_hydra_leave = false;
                            return false;
                        }
                        if (window._safeer_hydra_leave) return false;
                        var ifr = hydraPlayerFrame();
                        if (!ifr) return false;
                        ensureHydraFsStyle();
                        document.documentElement.classList.add('safeer-hydra-fs');
                        try {
                            var ring = document.getElementById('safeer-focus-target-ring');
                            if (ring) {
                                ring.classList.remove('active');
                                ring.style.display = 'none';
                            }
                        } catch (_) {}
                        try {
                            if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) {
                                window.SafeerBridge.setChromeHidden(true);
                            }
                        } catch (_) {}
                        try { ifr.setAttribute('allowfullscreen', 'true'); } catch (_) {}
                        try { ifr.setAttribute('allow', 'autoplay; fullscreen; encrypted-media; picture-in-picture'); } catch (_) {}
                        return true;
                    } catch (_) { return false; }
                }
                function unsmashHydraPlayer() {
                    try {
                        document.documentElement.classList.remove('safeer-hydra-fs');
                        try {
                            if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) {
                                window.SafeerBridge.setChromeHidden(false);
                            }
                        } catch (_) {}
                        return true;
                    } catch (_) { return false; }
                }
                window._safeer_hydra_smash = smashHydraPlayer;
                window._safeer_hydra_unsmash = unsmashHydraPlayer;
                smashHydraPlayer();
                setInterval(function() { smashHydraPlayer(); }, 900);

            } catch(e) {}
})();
