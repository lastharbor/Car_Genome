import os
import sqlite3
import json
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict, Any

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr
from passlib.context import CryptContext
from jose import JWTError, jwt

# Configuration
SECRET_KEY = os.getenv("SECRET_KEY", "cargenome_secret_jwt_key_please_change_in_production_998877")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 365
DB_PATH = os.getenv("DB_PATH", os.path.join(os.path.dirname(__file__), "data", "cargenome.db"))

os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/token")

app = FastAPI(
    title="CarGenome Cloud Sync Server",
    description="Sync server for CarGenome Android App to synchronize vehicles, maintenance and fuel logs between devices.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db():
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with get_db() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                hashed_password TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sync_data (
                user_id INTEGER PRIMARY KEY,
                revision INTEGER NOT NULL DEFAULT 1,
                device_id TEXT,
                payload_json TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sync_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                device_id TEXT,
                payload_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """)
        conn.commit()

init_db()

# --- Schemas ---

class AuthRequest(BaseModel):
    email: EmailStr
    password: str

class AuthResponse(BaseModel):
    token: str
    token_type: str = "bearer"
    email: str
    user_id: int

class SyncPushRequest(BaseModel):
    device_id: Optional[str] = "android-device"
    payload: Dict[str, Any]

class SyncPullResponse(BaseModel):
    revision: int
    updated_at: str
    payload: Optional[Dict[str, Any]] = None

class SyncStatusResponse(BaseModel):
    has_data: bool
    revision: int
    updated_at: Optional[str] = None
    email: str

# --- Helpers ---

def create_access_token(data: dict) -> str:
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def get_current_user(token: str = Depends(oauth2_scheme)) -> sqlite3.Row:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Неверные учетные данные авторизации",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id: int = payload.get("sub")
        if user_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    with get_db() as conn:
        user = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        if user is None:
            raise credentials_exception
        return user

# --- Routes ---

@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "CarGenome Cloud Sync",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

@app.post("/api/v1/auth/register", response_model=AuthResponse)
def register(req: AuthRequest):
    email = req.email.lower().strip()
    if len(req.password) < 6:
        raise HTTPException(status_code=400, detail="Пароль должен содержать не менее 6 символов")

    hashed = pwd_context.hash(req.password)
    now = datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        existing = conn.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
        if existing:
            raise HTTPException(status_code=400, detail="Пользователь с таким email уже зарегистрирован")

        cursor = conn.execute(
            "INSERT INTO users (email, hashed_password, created_at) VALUES (?, ?, ?)",
            (email, hashed, now),
        )
        user_id = cursor.lastrowid
        conn.commit()

    token = create_access_token({"sub": user_id, "email": email})
    return AuthResponse(token=token, email=email, user_id=user_id)

@app.post("/api/v1/auth/login", response_model=AuthResponse)
def login(req: AuthRequest):
    email = req.email.lower().strip()
    with get_db() as conn:
        user = conn.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()
        if not user or not pwd_context.verify(req.password, user["hashed_password"]):
            raise HTTPException(status_code=401, detail="Неверный email или пароль")

        token = create_access_token({"sub": user["id"], "email": email})
        return AuthResponse(token=token, email=email, user_id=user["id"])

@app.get("/api/v1/sync/status", response_model=SyncStatusResponse)
def sync_status(user: sqlite3.Row = Depends(get_current_user)):
    with get_db() as conn:
        row = conn.execute("SELECT revision, updated_at FROM sync_data WHERE user_id = ?", (user["id"],)).fetchone()
        if not row:
            return SyncStatusResponse(has_data=False, revision=0, updated_at=None, email=user["email"])
        return SyncStatusResponse(
            has_data=True,
            revision=row["revision"],
            updated_at=row["updated_at"],
            email=user["email"],
        )

@app.post("/api/v1/sync/push")
def sync_push(req: SyncPushRequest, user: sqlite3.Row = Depends(get_current_user)):
    payload_str = json.dumps(req.payload, ensure_ascii=False)
    now = datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        current = conn.execute("SELECT revision FROM sync_data WHERE user_id = ?", (user["id"],)).fetchone()
        next_rev = (current["revision"] + 1) if current else 1

        conn.execute("""
            INSERT INTO sync_data (user_id, revision, device_id, payload_json, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                revision = excluded.revision,
                device_id = excluded.device_id,
                payload_json = excluded.payload_json,
                updated_at = excluded.updated_at;
        """, (user["id"], next_rev, req.device_id, payload_str, now))

        # Keep sync history audit log
        conn.execute("""
            INSERT INTO sync_history (user_id, revision, device_id, payload_json, created_at)
            VALUES (?, ?, ?, ?, ?)
        """, (user["id"], next_rev, req.device_id, payload_str, now))
        conn.commit()

    return {
        "status": "success",
        "revision": next_rev,
        "updated_at": now,
    }

@app.get("/api/v1/sync/pull", response_model=SyncPullResponse)
def sync_pull(user: sqlite3.Row = Depends(get_current_user)):
    with get_db() as conn:
        row = conn.execute("SELECT revision, updated_at, payload_json FROM sync_data WHERE user_id = ?", (user["id"],)).fetchone()
        if not row:
            return SyncPullResponse(revision=0, updated_at="", payload=None)

        payload_obj = json.loads(row["payload_json"])
        return SyncPullResponse(
            revision=row["revision"],
            updated_at=row["updated_at"],
            payload=payload_obj,
        )
