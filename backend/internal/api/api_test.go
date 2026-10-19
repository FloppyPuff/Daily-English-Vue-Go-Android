package api

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"strings"
	"testing"

	"dailyenglish/backend/internal/store"
)

const testState = `{"version":3,"settings":{"exam":"cet6","dailyGoal":20,"sentenceGoal":3,"focusMinutes":25},"records":{},"sentenceRecords":{},"papers":{},"drafts":{},"savedWords":[],"savedSentences":[],"events":[]}`

func newTestServer(t *testing.T) (*httptest.Server, *store.Store) {
	t.Helper()
	st, err := store.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = st.Close() })
	handler := New(st, Config{
		AccountsOpen: true,
		CookieSecure: false,
		SessionDays:  60,
		MaxBodyBytes: 6 * 1024 * 1024,
	})
	server := httptest.NewServer(handler.Handler())
	t.Cleanup(server.Close)
	return server, st
}

func clientWithJar(t *testing.T) *http.Client {
	t.Helper()
	jar, err := cookiejar.New(nil)
	if err != nil {
		t.Fatal(err)
	}
	return &http.Client{Jar: jar}
}

func request(t *testing.T, client *http.Client, method, url string, body any, headers map[string]string) (*http.Response, map[string]any) {
	t.Helper()
	var reader io.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
		reader = bytes.NewReader(raw)
	}
	req, err := http.NewRequest(method, url, reader)
	if err != nil {
		t.Fatal(err)
	}
	if body != nil {
		req.Header.Set("content-type", "application/json")
	}
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	resp, err := client.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	raw, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	data := map[string]any{}
	if len(raw) > 0 {
		_ = json.Unmarshal(raw, &data)
	}
	return resp, data
}

func TestHealthRegisterAndState(t *testing.T) {
	server, _ := newTestServer(t)
	client := clientWithJar(t)

	resp, body := request(t, client, http.MethodGet, server.URL+"/api/health", nil, nil)
	if resp.StatusCode != 200 || body["ok"] != true {
		t.Fatalf("health: %d %+v", resp.StatusCode, body)
	}

	resp, body = request(t, client, http.MethodPost, server.URL+"/api/register", map[string]string{
		"email": "learner@example.com", "password": "Corr3ct-Horse",
	}, nil)
	if resp.StatusCode != 201 || body["user"].(map[string]any)["email"] != "learner@example.com" {
		t.Fatalf("register: %d %+v", resp.StatusCode, body)
	}
	if len(resp.Cookies()) == 0 {
		t.Fatal("register did not set a session cookie")
	}

	resp, body = request(t, client, http.MethodGet, server.URL+"/api/state", nil, nil)
	if resp.StatusCode != 200 || body["revision"].(float64) != 0 {
		t.Fatalf("initial state: %d %+v", resp.StatusCode, body)
	}

	payload := json.RawMessage(testState)
	resp, body = request(t, client, http.MethodPut, server.URL+"/api/state", map[string]any{
		"revision": 0, "payload": payload,
	}, nil)
	if resp.StatusCode != 200 || body["revision"].(float64) != 1 {
		t.Fatalf("write state: %d %+v", resp.StatusCode, body)
	}

	resp, body = request(t, client, http.MethodPut, server.URL+"/api/state", map[string]any{
		"revision": 0, "payload": payload,
	}, nil)
	if resp.StatusCode != 409 || body["revision"].(float64) != 1 {
		t.Fatalf("conflict state: %d %+v", resp.StatusCode, body)
	}
}

func TestMobileTokenAndSameOrigin(t *testing.T) {
	server, _ := newTestServer(t)
	client := &http.Client{}
	resp, body := request(t, client, http.MethodPost, server.URL+"/api/register", map[string]string{
		"email": "mobile@example.com", "password": "Corr3ct-Horse",
	}, map[string]string{"x-client": "daily-english-mobile", "origin": "https://localhost"})
	if resp.StatusCode != 201 || body["token"] == nil {
		t.Fatalf("mobile register: %d %+v", resp.StatusCode, body)
	}

	resp, _ = request(t, client, http.MethodPost, server.URL+"/api/register", map[string]string{
		"email": "phish@example.com", "password": "Corr3ct-Horse",
	}, map[string]string{"origin": "https://evil.example"})
	if resp.StatusCode != 403 {
		t.Fatalf("cross-site write should be 403, got %d", resp.StatusCode)
	}
}

func TestStateValidationAndLockout(t *testing.T) {
	server, _ := newTestServer(t)
	client := clientWithJar(t)
	resp, _ := request(t, client, http.MethodPost, server.URL+"/api/register", map[string]string{
		"email": "locked@example.com", "password": "Corr3ct-Horse",
	}, nil)
	if resp.StatusCode != 201 {
		t.Fatal("register failed")
	}
	resp, _ = request(t, client, http.MethodPut, server.URL+"/api/state", map[string]any{
		"revision": 0, "payload": map[string]any{"version": 99},
	}, nil)
	if resp.StatusCode != 400 {
		t.Fatalf("invalid state should be 400, got %d", resp.StatusCode)
	}

	for i := 0; i < 8; i++ {
		resp, _ = request(t, client, http.MethodPost, server.URL+"/api/login", map[string]string{
			"email": "locked@example.com", "password": "wrong-password1",
		}, nil)
		if resp.StatusCode != 401 {
			t.Fatalf("failure %d: %d", i+1, resp.StatusCode)
		}
	}
	resp, _ = request(t, client, http.MethodPost, server.URL+"/api/login", map[string]string{
		"email": "locked@example.com", "password": "wrong-password1",
	}, nil)
	if resp.StatusCode != 429 {
		t.Fatalf("ninth failure should be 429, got %d", resp.StatusCode)
	}
}

func TestLogoutAndPasswordChange(t *testing.T) {
	server, _ := newTestServer(t)
	client := clientWithJar(t)
	resp, _ := request(t, client, http.MethodPost, server.URL+"/api/register", map[string]string{
		"email": "password@example.com", "password": "Corr3ct-Horse",
	}, nil)
	if resp.StatusCode != 201 {
		t.Fatal("register failed")
	}
	resp, body := request(t, client, http.MethodPost, server.URL+"/api/password", map[string]string{
		"current": "Corr3ct-Horse", "next": "New-Horse2",
	}, nil)
	if resp.StatusCode != 200 || body["user"].(map[string]any)["email"] != "password@example.com" {
		t.Fatalf("password change: %d %+v", resp.StatusCode, body)
	}
	resp, _ = request(t, client, http.MethodPost, server.URL+"/api/logout", nil, nil)
	if resp.StatusCode != 204 {
		t.Fatalf("logout: %d", resp.StatusCode)
	}
	resp, _ = request(t, client, http.MethodGet, server.URL+"/api/me", nil, nil)
	if resp.StatusCode != 401 {
		t.Fatalf("me after logout: %d", resp.StatusCode)
	}
}

func TestBodyLimitAndUnknownRoute(t *testing.T) {
	server, _ := newTestServer(t)
	client := clientWithJar(t)
	resp, _ := request(t, client, http.MethodGet, server.URL+"/api/nope", nil, nil)
	if resp.StatusCode != 404 {
		t.Fatalf("unknown route: %d", resp.StatusCode)
	}
	// Six megabytes is the configured ceiling; a slightly larger JSON body must
	// be rejected before the route.
	huge := `{"email":"` + strings.Repeat("a", 6*1024*1024) + `@example.com","password":"Corr3ct-Horse"}`
	req, _ := http.NewRequest(http.MethodPost, server.URL+"/api/register", strings.NewReader(huge))
	req.Header.Set("content-type", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 413 {
		t.Fatalf("oversized body: %d", resp.StatusCode)
	}
}
