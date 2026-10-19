package store

import "testing"

func openTestStore(t *testing.T) *Store {
	t.Helper()
	st, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = st.Close() })
	return st
}

func TestUsersSessionsAndStateRevision(t *testing.T) {
	st := openTestStore(t)
	id, conflict, err := st.InsertUser("a@example.com", "hash", 100)
	if err != nil || conflict || id == 0 {
		t.Fatalf("insert: id=%d conflict=%v err=%v", id, conflict, err)
	}
	if _, conflict, err = st.InsertUser("a@example.com", "hash2", 101); err != nil || !conflict {
		t.Fatalf("duplicate should conflict: conflict=%v err=%v", conflict, err)
	}
	if err := st.CreateSession("tokenhash", id, 10000, 100); err != nil {
		t.Fatal(err)
	}
	session, err := st.FindSession("tokenhash", 999)
	if err != nil || session == nil || session.Email != "a@example.com" {
		t.Fatalf("session lookup: %+v err=%v", session, err)
	}
	if session, _ = st.FindSession("tokenhash", 10001); session != nil {
		t.Fatal("expired session should not be returned")
	}

	ok, revision, _, err := st.PutState(id, `{"version":3}`, 0, 200)
	if err != nil || !ok || revision != 1 {
		t.Fatalf("first state write: ok=%v rev=%d err=%v", ok, revision, err)
	}
	ok, revision, _, err = st.PutState(id, `{"version":3,"second":true}`, 0, 201)
	if err != nil || ok || revision != 0 {
		t.Fatalf("stale revision should conflict: ok=%v rev=%d err=%v", ok, revision, err)
	}
	row, err := st.GetState(id)
	if err != nil || row == nil || row.Revision != 1 {
		t.Fatalf("state row: %+v err=%v", row, err)
	}
}

func TestAttemptsAndRevocation(t *testing.T) {
	st := openTestStore(t)
	if err := st.NoteFailure("account:a", 8, 5000, 100); err != nil {
		t.Fatal(err)
	}
	row, err := st.AttemptRow("account:a")
	if err != nil || row == nil || row.Failures != 8 || row.LockedUntil != 5000 {
		t.Fatalf("attempt row: %+v err=%v", row, err)
	}
	if err := st.ClearAttempts([]string{"account:a"}); err != nil {
		t.Fatal(err)
	}
	if row, _ = st.AttemptRow("account:a"); row != nil {
		t.Fatal("attempt row should be cleared")
	}

	id, _, _ := st.InsertUser("b@example.com", "hash", 100)
	_ = st.CreateSession("keep", id, 10000, 100)
	_ = st.CreateSession("drop", id, 10000, 100)
	if err := st.RevokeOtherSessions(id, "keep", 100); err != nil {
		t.Fatal(err)
	}
	if session, _ := st.FindSession("drop", 100); session != nil {
		t.Fatal("other session was not revoked")
	}
	if session, _ := st.FindSession("keep", 100); session == nil {
		t.Fatal("current session was revoked")
	}
}
