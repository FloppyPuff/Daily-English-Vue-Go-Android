// Package store owns the SQLite schema and the only queries that touch user
// rows. It intentionally uses one connection: the server is single-node and
// SQLite writes must not race each other.
package store

import (
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	_ "modernc.org/sqlite"
)

const schema = `
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions(user_id);
CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);
CREATE TABLE IF NOT EXISTS states (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  payload TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS login_attempts (
  key TEXT PRIMARY KEY,
  failures INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
`

type Store struct {
	db *sql.DB
}

type User struct {
	ID       int64
	Email    string
	Password string
}

type Session struct {
	TokenHash string
	UserID    int64
	Email     string
	ExpiresAt int64
}

type Attempt struct {
	Failures    int
	LockedUntil int64
}

func Open(dataDir string) (*Store, error) {
	if err := os.MkdirAll(dataDir, 0o700); err != nil {
		return nil, err
	}
	path := filepath.Join(dataDir, "progress.db")
	dsn := fmt.Sprintf("file:%s?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)", path)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	// One connection makes SQLite's WAL/locking behaviour predictable for the
	// small traffic this service handles.
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	if _, err := db.Exec(schema); err != nil {
		db.Close()
		return nil, err
	}
	return &Store{db: db}, nil
}

func (s *Store) Close() error {
	if s == nil || s.db == nil {
		return nil
	}
	_, _ = s.db.Exec(`PRAGMA wal_checkpoint(TRUNCATE)`)
	return s.db.Close()
}

func (s *Store) CountUsers() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM users`).Scan(&n)
	return n, err
}

func (s *Store) InsertUser(email, password string, at int64) (int64, bool, error) {
	result, err := s.db.Exec(
		`INSERT INTO users (email, password, created_at, updated_at) VALUES (?, ?, ?, ?)`,
		email, password, at, at,
	)
	if err != nil {
		if strings.Contains(strings.ToLower(err.Error()), "unique") {
			return 0, true, nil
		}
		return 0, false, err
	}
	id, err := result.LastInsertId()
	return id, false, err
}

func (s *Store) FindUserByEmail(email string) (*User, error) {
	row := s.db.QueryRow(`SELECT id, email, password FROM users WHERE email = ?`, email)
	var user User
	if err := row.Scan(&user.ID, &user.Email, &user.Password); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (s *Store) FindUserByID(id int64) (*User, error) {
	row := s.db.QueryRow(`SELECT id, email FROM users WHERE id = ?`, id)
	var user User
	if err := row.Scan(&user.ID, &user.Email); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (s *Store) SetPassword(userID int64, password string, at int64) error {
	_, err := s.db.Exec(`UPDATE users SET password = ?, updated_at = ? WHERE id = ?`, password, at, userID)
	return err
}

func (s *Store) CreateSession(tokenHash string, userID, expiresAt, createdAt int64) error {
	_, err := s.db.Exec(
		`INSERT INTO sessions (token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)`,
		tokenHash, userID, expiresAt, createdAt,
	)
	return err
}

func (s *Store) FindSession(tokenHash string, now int64) (*Session, error) {
	row := s.db.QueryRow(
		`SELECT s.token_hash, s.user_id, s.expires_at, u.email
		 FROM sessions s JOIN users u ON u.id = s.user_id
		 WHERE s.token_hash = ? AND s.expires_at > ?`,
		tokenHash, now,
	)
	var session Session
	if err := row.Scan(&session.TokenHash, &session.UserID, &session.ExpiresAt, &session.Email); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &session, nil
}

func (s *Store) DeleteSession(tokenHash string) error {
	_, err := s.db.Exec(`DELETE FROM sessions WHERE token_hash = ?`, tokenHash)
	return err
}

func (s *Store) RevokeOtherSessions(userID int64, keepHash string, now int64) error {
	if _, err := s.db.Exec(`DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?`, userID, keepHash); err != nil {
		return err
	}
	_, err := s.db.Exec(`DELETE FROM sessions WHERE expires_at <= ?`, now)
	return err
}

type StateRow struct {
	Payload  string
	Revision int64
}

func (s *Store) GetState(userID int64) (*StateRow, error) {
	row := s.db.QueryRow(`SELECT payload, revision FROM states WHERE user_id = ?`, userID)
	var stateRow StateRow
	if err := row.Scan(&stateRow.Payload, &stateRow.Revision); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &stateRow, nil
}

// PutState performs the same revision-guarded UPSERT as the Node server.
func (s *Store) PutState(userID int64, payload string, expectedRevision, at int64) (bool, int64, *StateRow, error) {
	result, err := s.db.Exec(
		`INSERT INTO states (user_id, payload, revision, updated_at) VALUES (?, ?, 1, ?)
		 ON CONFLICT(user_id) DO UPDATE SET
		   payload = excluded.payload,
		   revision = states.revision + 1,
		   updated_at = excluded.updated_at
		 WHERE states.revision = ?`,
		userID, payload, at, expectedRevision,
	)
	if err != nil {
		return false, 0, nil, err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return false, 0, nil, err
	}
	if changed == 1 {
		var revision int64
		if err := s.db.QueryRow(`SELECT revision FROM states WHERE user_id = ?`, userID).Scan(&revision); err != nil {
			return false, 0, nil, err
		}
		return true, revision, nil, nil
	}
	current, err := s.GetState(userID)
	return false, 0, current, err
}

func (s *Store) AttemptRow(key string) (*Attempt, error) {
	row := s.db.QueryRow(`SELECT failures, locked_until FROM login_attempts WHERE key = ?`, key)
	var attempt Attempt
	if err := row.Scan(&attempt.Failures, &attempt.LockedUntil); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &attempt, nil
}

func (s *Store) NoteFailure(key string, failures int, lockedUntil, at int64) error {
	_, err := s.db.Exec(
		`INSERT INTO login_attempts (key, failures, locked_until, updated_at) VALUES (?, ?, ?, ?)
		 ON CONFLICT(key) DO UPDATE SET
		   failures = excluded.failures,
		   locked_until = excluded.locked_until,
		   updated_at = excluded.updated_at`,
		key, failures, lockedUntil, at,
	)
	return err
}

func (s *Store) ClearAttempts(keys []string) error {
	for _, key := range keys {
		if _, err := s.db.Exec(`DELETE FROM login_attempts WHERE key = ?`, key); err != nil {
			return err
		}
	}
	return nil
}
