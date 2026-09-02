# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Cloud Pipeline SMB Server

Exposes per-user storage directories (/dav-serve/<USERNAME>/) over SMB/CIFS
with Cloud Pipeline JWT authentication via a two-step flow:

  1. POST /auth  (Authorization: Bearer <CP_JWT>)
     → {"smb_username": "JDOE", "smb_password": "<session_key>",
        "smb_share": "JDOE", "expires_in": 86400}

  2. Connect Windows Explorer to \\\\<host>\\JDOE with the returned credentials.
"""

import json
import logging
import os
import signal
import sys
import tempfile
import threading
import time
import uuid

import jwt as pyjwt
from flask import Flask, jsonify, request
from impacket.smbserver import SimpleSMBServer
import impacket.smbserver as _impacket_smbserver
from werkzeug.serving import make_server

# impacket's findFirst2 calls os.stat() which raises FileNotFoundError on
# broken symlinks, dropping the entire SMB connection. Patch os.stat inside
# the impacket module to fall back to os.lstat() so broken symlinks appear in
# directory listings rather than crashing them.
_orig_stat = _impacket_smbserver.os.stat

def _safe_stat(path):
    try:
        return _orig_stat(path)
    except OSError:
        return _impacket_smbserver.os.lstat(path)

_impacket_smbserver.os.stat = _safe_stat

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CP_DAV_SERVE_DIR      = os.environ.get('CP_DAV_SERVE_DIR', '/dav-serve')
CP_API_SRV_CERT_DIR   = os.environ.get('CP_API_SRV_CERT_DIR', '/opt/api/pki')
CP_CAP_UID_SEED       = int(os.environ.get('CP_CAP_UID_SEED', '70000'))

CP_SMB_INTERNAL_PORT  = int(os.environ.get('CP_SMB_INTERNAL_PORT', '445'))
CP_SMB_AUTH_PORT      = int(os.environ.get('CP_SMB_AUTH_PORT', '31087'))
CP_SMB_SESSION_DURATION  = int(os.environ.get('CP_SMB_SESSION_DURATION', '86400'))
CP_SMB_CLEANUP_INTERVAL  = int(os.environ.get('CP_SMB_CLEANUP_INTERVAL', '300'))

_smb_log_dir = os.environ.get('SMB_LOG_DIR', '/var/log/dav/smb')
CP_SMB_SESSIONS_FILE  = os.environ.get(
    'CP_SMB_SESSIONS_FILE',
    os.path.join(_smb_log_dir, 'sessions.json'),
)

JWT_PUBLIC_KEY_PATH = os.path.join(CP_API_SRV_CERT_DIR, 'jwt.key.public')

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=getattr(logging, os.environ.get('CP_LOGGING_LEVEL', 'INFO').upper(), logging.INFO),
    format='%(asctime)s %(name)-14s %(levelname)-8s %(message)s',
    stream=sys.stdout,
)
log = logging.getLogger('cp-smb')

# ---------------------------------------------------------------------------
# NTLM helpers
# ---------------------------------------------------------------------------

def _nt_hash(password: str) -> str:
    """Compute NT hash: MD4(UTF-16LE(password)), returned as lowercase hex."""
    from Cryptodome.Hash import MD4
    h = MD4.new()
    h.update(password.encode('utf-16-le'))
    return h.hexdigest()

# ---------------------------------------------------------------------------
# JWT validation
# ---------------------------------------------------------------------------

def _load_public_key() -> str:
    with open(JWT_PUBLIC_KEY_PATH) as fh:
        key = fh.read().strip()
    if not key.startswith('-----BEGIN'):
        key = f'-----BEGIN PUBLIC KEY-----\n{key}\n-----END PUBLIC KEY-----'
    return key


def validate_jwt(token: str) -> tuple[str, int]:
    """Decode and verify a CP JWT (RS512). Returns (username, user_id)."""
    public_key = _load_public_key()
    payload = pyjwt.decode(
        token,
        public_key,
        algorithms=['RS512'],
        options={'verify_aud': False},
    )
    sub = (payload.get('sub') or '').strip()
    user_id = int(payload.get('user_id', 0))
    username = sub.upper().split('@')[0]
    if not username:
        raise ValueError('Empty username in JWT sub claim')
    return username, user_id


# ---------------------------------------------------------------------------
# Session store (in-memory + file-backed)
# ---------------------------------------------------------------------------

class SessionStore:
    """
    Tracks active SMB sessions keyed by uppercase username.

    Persistence: every mutating operation atomically rewrites
    CP_SMB_SESSIONS_FILE so that sessions survive smb_server.py restarts.
    """

    def __init__(self, sessions_file: str, duration: int):
        self._file = sessions_file
        self._duration = duration
        self._lock = threading.Lock()
        self._sessions: dict = {}
        self._load()

    # -- public API ----------------------------------------------------------

    def create(self, username: str, user_id: int) -> str:
        session_key = uuid.uuid4().hex
        entry = {
            'key': session_key,
            'uid': CP_CAP_UID_SEED + user_id,
            'expires': time.time() + self._duration,
        }
        with self._lock:
            self._sessions[username] = entry
            self._persist_locked()
        log.debug('Session created for %s', username)
        return session_key

    def get(self, username: str) -> dict | None:
        with self._lock:
            entry = self._sessions.get(username)
            if entry is None:
                return None
            if entry['expires'] <= time.time():
                del self._sessions[username]
                self._persist_locked()
                return None
            return dict(entry)

    def revoke(self, username: str) -> bool:
        with self._lock:
            if username not in self._sessions:
                return False
            del self._sessions[username]
            self._persist_locked()
        log.info('Session revoked for %s', username)
        return True

    def expire_stale(self) -> list[str]:
        now = time.time()
        with self._lock:
            expired = [u for u, s in self._sessions.items() if s['expires'] <= now]
            for u in expired:
                del self._sessions[u]
            if expired:
                self._persist_locked()
        return expired

    def all_active(self) -> dict:
        now = time.time()
        with self._lock:
            return {u: dict(s) for u, s in self._sessions.items() if s['expires'] > now}

    # -- private helpers -----------------------------------------------------

    def _load(self):
        if not os.path.exists(self._file):
            return
        try:
            with open(self._file) as fh:
                raw = json.load(fh)
            now = time.time()
            self._sessions = {
                u: s for u, s in raw.items()
                if isinstance(s, dict) and s.get('expires', 0) > now
            }
            log.info('Loaded %d active session(s) from %s', len(self._sessions), self._file)
        except Exception as exc:
            log.warning('Could not load sessions from %s: %s', self._file, exc)

    def _persist_locked(self):
        """Must be called with self._lock held."""
        os.makedirs(os.path.dirname(self._file), exist_ok=True)
        tmp = self._file + '.tmp'
        try:
            with open(tmp, 'w') as fh:
                json.dump(self._sessions, fh)
            os.replace(tmp, self._file)
        except Exception as exc:
            log.warning('Failed to persist sessions: %s', exc)


# ---------------------------------------------------------------------------
# Cloud Pipeline SMB Server
# ---------------------------------------------------------------------------

class CloudPipelineSMBServer:

    def __init__(self):
        self._sessions = SessionStore(CP_SMB_SESSIONS_FILE, CP_SMB_SESSION_DURATION)
        self._smb = SimpleSMBServer(listenAddress='0.0.0.0', listenPort=CP_SMB_INTERNAL_PORT)
        # impacket defaults to SMBv1-only; modern Windows has SMBv1 disabled
        self._smb._SimpleSMBServer__smbConfig.set('global', 'SMB2Support', 'True')
        self._smb.setSMBChallenge('')   # random 8-byte challenge per connection
        self._registered_shares: set[str] = set()
        self._shares_lock = threading.Lock()

        self._flask = Flask('cp-smb-auth')
        self._flask.logger.setLevel(logging.WARNING)
        self._register_routes()

    # -- startup -------------------------------------------------------------

    def start(self):
        self._restore_sessions()

        threading.Thread(target=self._cleanup_loop, daemon=True, name='smb-cleanup').start()

        http_srv = make_server('0.0.0.0', CP_SMB_AUTH_PORT, self._flask)
        threading.Thread(target=http_srv.serve_forever, daemon=True, name='smb-http').start()
        log.info('SMB auth endpoint listening on http://0.0.0.0:%d', CP_SMB_AUTH_PORT)

        log.info('SMB server listening on smb://0.0.0.0:%d', CP_SMB_INTERNAL_PORT)
        self._smb.start()   # blocks until process exits

    # -- HTTP routes ---------------------------------------------------------

    def _register_routes(self):
        app = self._flask

        @app.route('/auth', methods=['POST'], strict_slashes=False)
        def issue():
            token = _extract_bearer(request)
            if not token:
                return jsonify(error='Missing Bearer token'), 401
            try:
                username, user_id = validate_jwt(token)
            except Exception as exc:
                log.warning('JWT validation failed from %s: %s', request.remote_addr, exc)
                return jsonify(error='Invalid or expired token'), 401

            user_dir = os.path.join(CP_DAV_SERVE_DIR, username)
            if not os.path.isdir(user_dir):
                log.warning('User directory not found: %s', user_dir)
                return jsonify(error='User storage not ready; the sync may still be running'), 503

            session_key = self._sessions.create(username, user_id)
            self._apply_session(username, user_dir, user_id, session_key)

            log.info('SMB session issued for %s (expires in %ds)', username, CP_SMB_SESSION_DURATION)
            return jsonify(
                smb_username=username,
                smb_password=session_key,
                smb_share=username,
                expires_in=CP_SMB_SESSION_DURATION,
            )

        @app.route('/auth', methods=['DELETE'], strict_slashes=False)
        def revoke():
            token = _extract_bearer(request)
            if not token:
                return jsonify(error='Missing Bearer token'), 401
            try:
                username, _ = validate_jwt(token)
            except Exception as exc:
                log.warning('JWT validation failed from %s: %s', request.remote_addr, exc)
                return jsonify(error='Invalid or expired token'), 401

            if self._sessions.revoke(username):
                # Overwrite stored credential so ongoing NTLM handshakes fail
                self._smb.addCredential(username, 0, '', _nt_hash('__REVOKED__'))
            return jsonify(status='revoked')

        @app.route('/health', methods=['GET'])
        def health():
            return jsonify(status='ok')

    # -- SMB helpers ---------------------------------------------------------

    def _apply_session(self, username: str, user_dir: str, user_id: int, session_key: str):
        uid = CP_CAP_UID_SEED + user_id
        # Register/update NTLM credential: addCredential(name, uid, lmhash, nthash)
        self._smb.addCredential(username, uid, '', _nt_hash(session_key))

        with self._shares_lock:
            if username not in self._registered_shares:
                self._smb.addShare(
                    username,
                    user_dir,
                    f'Cloud Pipeline storage for {username}',
                    readOnly='no',
                )
                self._registered_shares.add(username)
                log.info('SMB share registered: \\\\host\\%s -> %s', username, user_dir)

    def _restore_sessions(self):
        """Re-register credentials and shares for sessions that survived a restart."""
        for username, entry in self._sessions.all_active().items():
            user_dir = os.path.join(CP_DAV_SERVE_DIR, username)
            if os.path.isdir(user_dir):
                self._apply_session(username, user_dir, entry['uid'] - CP_CAP_UID_SEED, entry['key'])
                log.info('Restored SMB session for %s', username)
            else:
                log.warning('Skipping session restore for %s: directory missing', username)

    def _cleanup_loop(self):
        while True:
            time.sleep(CP_SMB_CLEANUP_INTERVAL)
            expired = self._sessions.expire_stale()
            for username in expired:
                log.info('Session expired for %s, invalidating credential', username)
                self._smb.addCredential(username, 0, '', _nt_hash('__EXPIRED__'))


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _extract_bearer(req) -> str | None:
    header = req.headers.get('Authorization', '')
    if header.startswith('Bearer '):
        return header[7:].strip() or None
    return None


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    if not os.path.exists(JWT_PUBLIC_KEY_PATH):
        log.error('JWT public key not found: %s', JWT_PUBLIC_KEY_PATH)
        sys.exit(1)

    server = CloudPipelineSMBServer()

    def _shutdown(sig, _frame):
        log.info('Received signal %d, shutting down', sig)
        sys.exit(0)

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    server.start()
