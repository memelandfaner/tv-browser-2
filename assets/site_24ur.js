/* 24ur.com — Windows Chrome namizni način (brez CSS prestavljanja menija) */
(function() {
    var host = (location.hostname || '').toLowerCase();
    if (host.indexOf('24ur') === -1) return;

    var DESKTOP_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36';

    function fakeNav(key, val) {
        try {
            Object.defineProperty(navigator, key, {
                configurable: true,
                enumerable: true,
                get: function() { return val; }
            });
        } catch (_) {
            try { navigator[key] = val; } catch (_2) {}
        }
    }

    function spoofWindowsChrome() {
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
                        architecture: 'x86',
                        bitness: '64',
                        mobile: false,
                        model: '',
                        platform: 'Windows',
                        platformVersion: '15.0.0',
                        uaFullVersion: '133.0.0.0'
                    });
                }
            });
        } catch (_) {}
        try { window.chrome = window.chrome || { runtime: {} }; } catch (_2) {}
    }

    function forceDesktopViewport() {
        try {
            var vp = document.querySelector('meta[name="viewport"]');
            if (!vp) {
                vp = document.createElement('meta');
                vp.setAttribute('name', 'viewport');
                (document.head || document.documentElement).appendChild(vp);
            }
            vp.setAttribute('content', 'width=1280, initial-scale=1');
        } catch (_) {}
    }

    function acceptDidomi() {
        try {
            var b = document.getElementById('didomi-notice-agree-button');
            if (b) {
                b.click();
                return;
            }
            var btns = document.querySelectorAll('button, [role="button"]');
            var i, t;
            for (i = 0; i < btns.length; i++) {
                t = ((btns[i].innerText || btns[i].textContent || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (t === 'sprejmi in zapri' || t.indexOf('sprejmi in zapri') === 0) {
                    btns[i].click();
                    return;
                }
            }
        } catch (_) {}
    }

    window._safeer_24ur_cards = function() {
        var out = [];
        var cards = document.querySelectorAll('a.card');
        var i, el, r;
        for (i = 0; i < cards.length; i++) {
            el = cards[i];
            try {
                r = el.getBoundingClientRect();
                if (r.width < 90 || r.height < 48) continue;
                out.push(el);
            } catch (_) {}
        }
        var nav = document.querySelectorAll('.submenu a, .menu__item a, .menu a');
        for (i = 0; i < nav.length; i++) {
            el = nav[i];
            try {
                r = el.getBoundingClientRect();
                if (r.width < 28 || r.height < 18) continue;
                if (out.indexOf(el) === -1) out.push(el);
            } catch (_2) {}
        }
        return out;
    };

    spoofWindowsChrome();
    forceDesktopViewport();
    if (window._safeer_24ur_tv) return;
    window._safeer_24ur_tv = true;

    acceptDidomi();
    setInterval(function() {
        spoofWindowsChrome();
        forceDesktopViewport();
        acceptDidomi();
    }, 800);
})();
