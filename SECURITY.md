# Security Policy

Safeer is a privacy and security product. If something in it puts users at risk, we
want to hear about it before anyone else does.

## Reporting a vulnerability

Please report privately, not in a public issue.

Use GitHub's private vulnerability reporting: open the **Security** tab of this
repository and choose **Report a vulnerability**. The report is visible only to the
maintainers.

If that option is not available to you, open a public issue that says only that you
have a security report and asks for a private channel. Do not include details,
proof-of-concept code, or affected versions in the public issue.

You can also write to **varnost@safeer.si** (or security@safeer.si, which reaches the
same mailbox). Keep the first email free of details as well: say that you have a
security report and we will reply with a private channel.

Helpful things to include in the private report:

- what an attacker can do, and what they need in order to do it
- the exact version (2.1.x) and platform
- steps to reproduce, ideally the smallest case that still works
- anything you already know about the fix

## What happens next

We are a small team. We aim to confirm that we received your report within a week,
tell you whether we consider it a vulnerability, and keep you updated while we work
on a fix. When the fix ships, we credit you in the release notes unless you prefer
otherwise.

## Scope

In scope: the Safeer browser for Android TV, its ad and threat blocking, the built-in
Safeer Link hub and its local network protocol, and anything that handles pages
or keys on the device.

Out of scope: vulnerabilities in third-party websites the browser merely visits;
upstream issues in the public blocklists we consume; findings that require physical
access to an unlocked device; and reports produced by a scanner without a working
example.

## Verifying what you downloaded

Every release publishes a `SHA256SUMS` file next to the packages. Check the file you
downloaded against it before installing:

```
sha256sum -c SHA256SUMS --ignore-missing
```

Android packages are signed; the signing key does not change between versions, so an
update that will not install over an existing one is a signal worth reporting.

## Good-faith research

We will not pursue or support legal action against anyone who reports a vulnerability
in good faith, keeps it private until a fix is available, and does not access, modify
or destroy data belonging to other people.
