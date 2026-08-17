"""
Resolve a Medium post id (or any /p/<id> URL) to the canonical article URL.

A companion utility to the Android app, not part of the APK. Freedium recovers a post id
from a Medium notification's PendingIntent; this turns that id into the pretty article URL
so the result can be checked from a desktop without a device in the loop.

Why the naive one-hop version fails:

    https://www.medium.com/p/826ebf9ad9fb  -> 301 -> https://medium.com/p/826ebf9ad9fb
    https://medium.com/p/826ebf9ad9fb      -> 302 -> https://netflixtechblog.medium.com/...

The "www." costs an extra hop, so returning at the first Location header hands back a /p/
URL rather than the article. The redirects have to be followed as a chain.

Usage as a library:

    from tools.resolve_medium import resolve
    resolve("826ebf9ad9fb")
    resolve("https://medium.com/p/826ebf9ad9fb")

Requires: requests
"""

import re
from urllib.parse import urljoin, urlparse, urlunparse

import requests

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

MAX_HOPS = 5
TIMEOUT = 15
REDIRECT_CODES = {301, 302, 303, 307, 308}

_CANONICAL = re.compile(r'<link[^>]+rel=["\']canonical["\'][^>]*>', re.IGNORECASE)
_HREF = re.compile(r'href=["\']([^"\']+)["\']', re.IGNORECASE)


def _normalise(url: str) -> str:
    """Drop the pointless 'www.' hop, and expand a bare post id into a /p/ URL."""
    if not url.startswith(("http://", "https://")):
        # A bare post id such as "826ebf9ad9fb".
        if re.fullmatch(r"[0-9a-f]{6,}", url):
            return f"https://medium.com/p/{url}"
        url = "https://" + url

    parts = urlparse(url)
    host = parts.netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    return urlunparse(parts._replace(netloc=host))


def _canonical_from_body(session, url):
    """Some posts answer 200 and name the pretty URL in a <link rel="canonical"> tag."""
    try:
        response = session.get(url, timeout=TIMEOUT)
    except requests.RequestException:
        return None

    tag = _CANONICAL.search(response.text or "")
    if not tag:
        return None
    href = _HREF.search(tag.group(0))
    if not href:
        return None

    candidate = href.group(1)
    return candidate if candidate.startswith("http") and "/p/" not in candidate else None


def resolve(url, session=None):
    """
    Follow the redirect chain to the canonical article URL.

    Returns the best URL found. Never raises for an ordinary network or HTTP failure - an
    unresolved link is still a usable link, and the caller can spot the difference by
    checking whether "/p/" is still in the result.
    """
    owns_session = session is None
    session = session or requests.Session()
    session.headers.setdefault("User-Agent", USER_AGENT)

    current = _normalise(url)

    try:
        for _ in range(MAX_HOPS):
            try:
                # stream=True so the body is not downloaded just to read a header.
                response = session.get(
                    current, allow_redirects=False, timeout=TIMEOUT, stream=True
                )
                response.close()
            except requests.RequestException:
                return current

            if response.status_code not in REDIRECT_CODES:
                break

            location = response.headers.get("Location")
            if not location:
                break

            # urljoin handles a relative Location such as "/foo".
            nxt = urljoin(current, location)
            if nxt == current:
                # A redirect pointing at itself; stop rather than spin.
                break
            current = nxt

        # Fallback for posts served at 200 with only a canonical tag.
        if "/p/" in current:
            canonical = _canonical_from_body(session, current)
            if canonical:
                current = canonical

        return current
    finally:
        if owns_session:
            session.close()


# ---------------------------------------------------------------------------
# Script entry point.
#
# Commented out deliberately: this file is a utility meant to be imported, so importing it
# must not perform network calls. Uncomment the block below to run it directly as a script
# for a quick manual check:
#
#     python tools/resolve_medium.py
#
# if __name__ == "__main__":
#     for candidate in [
#         "https://www.medium.com/p/826ebf9ad9fb",   # the original, with the www hop
#         "https://medium.com/p/826ebf9ad9fb",
#         "826ebf9ad9fb",                            # a bare post id
#         "https://medium.com/p/25a5afe2b71c",
#         "https://medium.com/p/000000000000",       # nonexistent id, returns the stub
#     ]:
#         print(f"{candidate}\n  -> {resolve(candidate)}\n")
# ---------------------------------------------------------------------------
