# Bundled library

- `bcprov-ed25519-1.78.1.jar`: Bouncy Castle 1.78.1 (`org.bouncycastle:bcprov-jdk18on`, MIT license, text inside the JAR) reduced by R8 tree shaking to `org.bouncycastle.math.ec.rfc8032.Ed25519.verify` and the classes it needs (45 classes, about 50 KB instead of 8 MB). No optimization or obfuscation passes. Input JAR SHA-256 `add5915e6acfc6ab5836e1fd8a5e21c6488536a8c1f21f386eeb3bf280b702d7`, JAR-signed by "Legion of the Bouncy Castle Inc.". Reproducible with `clients/kotlin/bouncycastle/shrink.sh` in https://github.com/memelandfaner/safeer-threat-intel (R8 8.2.42). Checksum: `SHA256SUMS`.

It verifies the Ed25519 signatures of the Safeer Threat Intelligence feed. `java.security` Ed25519 exists only on Android 13+, while this app supports Android 9+. `tests/run_signed_feed_tests.sh` runs the feed tests against this exact JAR: RFC 8032 vectors, a cross-check with the JDK Ed25519 implementation on random and mutated signatures, and the shared conformance corpus.
