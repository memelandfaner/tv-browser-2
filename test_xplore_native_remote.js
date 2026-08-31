#!/usr/bin/env node
'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');

function fakeDom() {
    const styles = [];
    const html = {
        classList: {
            _c: {},
            add(n) { this._c[n] = true; },
            remove(n) { delete this._c[n]; },
            contains(n) { return !!this._c[n]; }
        },
        style: {},
        appendChild() {}
    };
    return {
        styles,
        html,
        document: {
            documentElement: html,
            head: { appendChild(el) { styles.push(el); } },
            body: html,
            createElement() {
                return { id: '', style: {}, setAttribute() {}, appendChild() {}, textContent: '' };
            },
            querySelector() { return null; },
            querySelectorAll() { return []; },
            getElementById() { return null; },
            addEventListener() {}
        }
    };
}

function runScript(filename, hostname) {
    const src = fs.readFileSync(path.join(__dirname, filename), 'utf8');
    const dom = fakeDom();
    const location = {
        href: 'https://' + hostname + '/home',
        hostname: hostname,
        pathname: '/home',
        hash: ''
    };
    const windowObj = {
        location,
        document: dom.document,
        SafeerBridge: { setChromeHidden(v) { windowObj._chromeHidden = v; } },
        addEventListener() {},
        _chromeHidden: undefined
    };
    windowObj.window = windowObj;
    const ctx = Object.assign(Object.create(null), {
        window: windowObj,
        document: dom.document,
        location,
        console,
        setInterval() { return 0; },
        setTimeout() { return 0; },
        clearTimeout() {},
        MutationObserver: function () { this.observe = function () {}; this.disconnect = function () {}; },
        fetch() { return Promise.resolve(); },
        URL,
        navigator: {},
        sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} }
    });
    vm.runInNewContext(src, ctx, { filename });
    return {
        hostname,
        chromeHidden: windowObj._chromeHidden,
        spatial: typeof windowObj._safeer_navigate_spatial,
        smash: typeof windowObj._safeer_xplore_unsmash,
        playFromStart: typeof windowObj._safeer_xplore_play_from_start,
        siteAgent: windowObj._safeerSiteAgent || null,
        styleIds: dom.styles.map((s) => s.id).filter(Boolean)
    };
}

let failed = 0;
function assert(cond, msg) {
    if (!cond) {
        failed += 1;
        console.error('FAIL', msg);
    } else {
        console.log('OK  ', msg);
    }
}

const xploreNav = runScript('assets/tv_remote_nav.js', 'www.xploretv.si');
assert(xploreNav.chromeHidden === true, 'xplore hides browser chrome');
assert(xploreNav.spatial === 'undefined', 'xplore does not install spatial nav');
assert(xploreNav.smash === 'undefined', 'xplore does not install player smash');
assert(xploreNav.playFromStart === 'undefined', 'xplore does not install autoplay helper');
assert(!xploreNav.styleIds.includes('tv-remote-cinema-style'), 'xplore does not inject cinema CSS');
assert(!xploreNav.styleIds.includes('tv-remote-xplore-layout-fix'), 'xplore does not inject layout-smash CSS');

const googleNav = runScript('assets/tv_remote_nav.js', 'www.google.com');
assert(googleNav.spatial === 'function', 'other sites still get spatial nav');

const xploreAgent = runScript('assets/site_agent.js', 'www.xploretv.si');
assert(xploreAgent.siteAgent && xploreAgent.siteAgent.allowBoost() === false, 'xplore site agent is a no-op stub');
assert(typeof xploreAgent.siteAgent.markWantPlay === 'function', 'xplore stub still exposes markWantPlay');

if (failed) {
    console.error('\n' + failed + ' assertion(s) failed');
    process.exit(1);
}
console.log('\nall xplore native-remote checks passed');
