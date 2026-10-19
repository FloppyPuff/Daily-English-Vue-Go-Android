package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"dailyenglish/backend/internal/auth"
	"dailyenglish/backend/internal/state"
	"dailyenglish/backend/internal/store"
)

type Config struct {
	TrustProxy   bool
	CookieSecure bool
	AccountsOpen bool
	SessionDays  int
	MaxBodyBytes int64
}

type Server struct {
	store     *store.Store
	config    Config
	startedAt time.Time
}

func New(st *store.Store, config Config) *Server {
	if config.SessionDays <= 0 {
		config.SessionDays = auth.SessionDays
	}
	if config.MaxBodyBytes <= 0 {
		config.MaxBodyBytes = 6 * 1024 * 1024
	}
	return &Server{store: st, config: config, startedAt: time.Now()}
}

type requestContext struct {
	request *http.Request
	body    map[string]any
	session *store.Session
}

type response struct {
	status  int
	body    any
	headers http.Header
	empty   bool
}

func (s *Server) Handler() http.Handler {
	return http.HandlerFunc(s.serveHTTP)
}

func (s *Server) serveHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("x-content-type-options", "nosniff")
	w.Header().Set("referrer-policy", "same-origin")
	w.Header().Set("x-frame-options", "SAMEORIGIN")
	w.Header().Set("cross-origin-opener-policy", "same-origin")

	if !isAPIPath(r.URL.Path) {
		s.write(w, jsonError(http.StatusNotFound, "接口不存在"))
		return
	}
	if !sameOrigin(r) {
		s.write(w, jsonError(http.StatusForbidden, "跨站请求已被拒绝"))
		return
	}

	ctx := &requestContext{request: r}
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		body, tooLarge, err := readBody(w, r, s.config.MaxBodyBytes)
		if tooLarge {
			s.write(w, jsonError(http.StatusRequestEntityTooLarge, "数据过大，未保存"))
			return
		}
		if err != nil {
			s.write(w, jsonError(http.StatusBadRequest, "请求格式不正确"))
			return
		}
		object, err := bodyObject(body)
		if err != nil {
			s.write(w, jsonError(http.StatusBadRequest, "请求格式不正确"))
			return
		}
		ctx.body = object
	}

	now := time.Now().UnixMilli()
	var result response
	switch r.Method + " " + r.URL.Path {
	case "GET /api/health":
		result = s.health(now)
	case "POST /api/register":
		result = s.register(ctx, now)
	case "POST /api/login":
		result = s.login(ctx, now)
	case "POST /api/logout":
		result = s.logout(ctx, now)
	case "GET /api/me":
		result = s.me(ctx, now)
	case "GET /api/state":
		result = s.readState(ctx, now)
	case "PUT /api/state":
		result = s.writeState(ctx, now)
	case "POST /api/password":
		result = s.changePassword(ctx, now)
	default:
		result = jsonError(http.StatusNotFound, "接口不存在")
	}
	s.write(w, result)
}

func (s *Server) health(now int64) response {
	users, _ := s.store.CountUsers()
	return jsonResponse(http.StatusOK, map[string]any{
		"ok":            true,
		"users":         users,
		"uptimeSeconds": (now - s.startedAt.UnixMilli()) / 1000,
	})
}

func (s *Server) register(ctx *requestContext, now int64) response {
	if !s.config.AccountsOpen {
		return jsonError(http.StatusServiceUnavailable, "注册功能已暂停，请稍后再试")
	}
	email := auth.NormalizeEmail(stringValue(ctx.body["email"]))
	if problem := auth.EmailProblem(email); problem != "" {
		return jsonError(http.StatusBadRequest, problem)
	}
	password, problem := passwordValue(ctx.body["password"])
	if problem != "" {
		return jsonError(http.StatusBadRequest, problem)
	}
	hashed, err := auth.HashPassword(password)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	userID, conflict, err := s.store.InsertUser(email, hashed, now)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	if conflict {
		return jsonError(http.StatusConflict, "这个邮箱已经注册过了")
	}
	return s.sessionResponse(ctx, http.StatusCreated, email, userID, now)
}

func (s *Server) login(ctx *requestContext, now int64) response {
	email := auth.NormalizeEmail(stringValue(ctx.body["email"]))
	keys := auth.AttemptKeys(s.clientIP(ctx.request), email)
	if locked := s.blockedFor(keys, now); locked > 0 {
		resp := jsonError(http.StatusTooManyRequests, "尝试次数过多，请 15 分钟后再试")
		resp.headers = http.Header{"Retry-After": []string{strconv.Itoa(locked)}}
		return resp
	}
	user, err := s.store.FindUserByEmail(email)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	password, _ := ctx.body["password"].(string)
	if user == nil || !auth.VerifyPassword(password, user.Password) {
		s.recordFailure(keys, now)
		return jsonError(http.StatusUnauthorized, "邮箱或密码不正确")
	}
	_ = s.store.ClearAttempts(keys)
	return s.sessionResponse(ctx, http.StatusOK, user.Email, user.ID, now)
}

func (s *Server) logout(ctx *requestContext, now int64) response {
	if session := s.currentSession(ctx, now); session != nil {
		_ = s.store.DeleteSession(session.TokenHash)
	}
	resp := response{status: http.StatusNoContent, empty: true}
	resp.headers = http.Header{"Set-Cookie": []string{s.clearCookie().String()}}
	return resp
}

func (s *Server) me(ctx *requestContext, now int64) response {
	session := s.currentSession(ctx, now)
	if session == nil {
		return jsonError(http.StatusUnauthorized, "请先登录")
	}
	return jsonResponse(http.StatusOK, map[string]any{"user": map[string]string{"email": session.Email}})
}

func (s *Server) readState(ctx *requestContext, now int64) response {
	session := s.currentSession(ctx, now)
	if session == nil {
		return jsonError(http.StatusUnauthorized, "请先登录")
	}
	row, err := s.store.GetState(session.UserID)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	if row == nil {
		return jsonResponse(http.StatusOK, map[string]any{"revision": 0, "payload": state.CreateState()})
	}
	parsed, err := state.ParseBackup(row.Payload)
	if err != nil {
		parsed = state.CreateState()
	}
	return jsonResponse(http.StatusOK, map[string]any{"revision": row.Revision, "payload": parsed})
}

func (s *Server) writeState(ctx *requestContext, now int64) response {
	session := s.currentSession(ctx, now)
	if session == nil {
		return jsonError(http.StatusUnauthorized, "请先登录")
	}
	rawRevision := ctx.body["revision"]
	revision, ok := integerValue(rawRevision)
	if !ok || revision < 0 {
		return jsonError(http.StatusBadRequest, "缺少数据版本号")
	}
	payloadValue, exists := ctx.body["payload"]
	if !exists {
		return jsonError(http.StatusBadRequest, "学习数据格式不正确，未保存")
	}
	payloadBytes, err := json.Marshal(payloadValue)
	if err != nil {
		return jsonError(http.StatusBadRequest, "学习数据格式不正确，未保存")
	}
	parsed, err := state.ParsePayload(payloadBytes)
	if err != nil {
		return jsonError(http.StatusBadRequest, "学习数据格式不正确，未保存")
	}
	normalized, err := json.Marshal(parsed)
	if err != nil {
		return jsonError(http.StatusBadRequest, "学习数据格式不正确，未保存")
	}
	changed, newRevision, current, err := s.store.PutState(session.UserID, string(normalized), revision, now)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	if changed {
		return jsonResponse(http.StatusOK, map[string]any{"revision": newRevision})
	}
	var payload *state.AppState
	if current == nil {
		payload = state.CreateState()
	} else if parsedCurrent, err := state.ParseBackup(current.Payload); err == nil {
		payload = parsedCurrent
	} else {
		payload = state.CreateState()
	}
	currentRevision := int64(0)
	if current != nil {
		currentRevision = current.Revision
	}
	return jsonResponse(http.StatusConflict, map[string]any{"revision": currentRevision, "payload": payload})
}

func (s *Server) changePassword(ctx *requestContext, now int64) response {
	session := s.currentSession(ctx, now)
	if session == nil {
		return jsonError(http.StatusUnauthorized, "请先登录")
	}
	user, err := s.store.FindUserByEmail(session.Email)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	current, _ := ctx.body["current"].(string)
	if user == nil || !auth.VerifyPassword(current, user.Password) {
		return jsonError(http.StatusUnauthorized, "当前密码不正确")
	}
	next, problem := passwordValue(ctx.body["next"])
	if problem != "" {
		return jsonError(http.StatusBadRequest, problem)
	}
	hashed, err := auth.HashPassword(next)
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	if err := s.store.SetPassword(user.ID, hashed, now); err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	_ = s.store.RevokeOtherSessions(user.ID, session.TokenHash, now)
	return jsonResponse(http.StatusOK, map[string]any{"user": map[string]string{"email": user.Email}})
}

func (s *Server) sessionResponse(ctx *requestContext, status int, email string, userID, now int64) response {
	token, tokenHash, err := auth.CreateToken()
	if err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	if err := s.store.CreateSession(tokenHash, userID, now+int64(s.config.SessionDays)*86400000, now); err != nil {
		return jsonError(http.StatusInternalServerError, "服务暂时不可用，请稍后再试")
	}
	body := map[string]any{"user": map[string]string{"email": email}}
	if isMobile(ctx.request) {
		body["token"] = token
	}
	resp := jsonResponse(status, body)
	resp.headers = http.Header{
		"Set-Cookie": []string{s.cookie("de_session", token).String()},
	}
	return resp
}

func (s *Server) currentSession(ctx *requestContext, now int64) *store.Session {
	if ctx.session != nil {
		return ctx.session
	}
	token := ""
	if header := ctx.request.Header.Get("Authorization"); strings.HasPrefix(header, "Bearer ") {
		token = strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
	}
	if token == "" {
		if cookie, err := ctx.request.Cookie("de_session"); err == nil {
			token = cookie.Value
		}
	}
	if token == "" {
		return nil
	}
	session, err := s.store.FindSession(auth.HashToken(token), now)
	if err != nil {
		return nil
	}
	ctx.session = session
	return session
}

func (s *Server) blockedFor(keys []string, now int64) int {
	soonest := int64(0)
	for _, key := range keys {
		row, err := s.store.AttemptRow(key)
		if err == nil && row != nil && row.LockedUntil > now && row.LockedUntil > soonest {
			soonest = row.LockedUntil
		}
	}
	if soonest <= now {
		return 0
	}
	return int(math.Ceil(float64(soonest-now) / 1000))
}

func (s *Server) recordFailure(keys []string, now int64) {
	for _, key := range keys {
		row, _ := s.store.AttemptRow(key)
		failures := 1
		lockedUntil := int64(0)
		if row != nil {
			failures = row.Failures + 1
			lockedUntil = row.LockedUntil
		}
		if failures >= auth.AllowedFails {
			lockedUntil = now + auth.LockMillis
		}
		_ = s.store.NoteFailure(key, failures, lockedUntil, now)
	}
}

func (s *Server) clientIP(r *http.Request) string {
	if s.config.TrustProxy {
		forwarded := strings.TrimSpace(strings.Split(r.Header.Get("X-Forwarded-For"), ",")[0])
		if forwarded != "" {
			if len(forwarded) > 100 {
				forwarded = forwarded[:100]
			}
			return forwarded
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil && host != "" {
		return host
	}
	if len(r.RemoteAddr) > 100 {
		return r.RemoteAddr[:100]
	}
	return r.RemoteAddr
}

func (s *Server) cookie(name, value string) *http.Cookie {
	return &http.Cookie{
		Name:     name,
		Value:    value,
		Path:     "/",
		MaxAge:   s.config.SessionDays * 86400,
		HttpOnly: true,
		Secure:   s.config.CookieSecure,
		SameSite: http.SameSiteLaxMode,
	}
}

func (s *Server) clearCookie() *http.Cookie {
	return &http.Cookie{
		Name:     "de_session",
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		Secure:   s.config.CookieSecure,
		SameSite: http.SameSiteLaxMode,
	}
}

func (s *Server) write(w http.ResponseWriter, resp response) {
	w.Header().Set("cache-control", "no-store")
	for key, values := range resp.headers {
		for _, value := range values {
			w.Header().Add(key, value)
		}
	}
	if resp.empty {
		w.WriteHeader(resp.status)
		return
	}
	w.Header().Set("content-type", "application/json; charset=utf-8")
	w.WriteHeader(resp.status)
	_ = json.NewEncoder(w).Encode(resp.body)
}

func jsonResponse(status int, body any) response {
	return response{status: status, body: body}
}

func jsonError(status int, message string) response {
	return jsonResponse(status, map[string]string{"error": message})
}

func isAPIPath(path string) bool {
	return path == "/api" || strings.HasPrefix(path, "/api/")
}

func isMobile(r *http.Request) bool {
	return r.Header.Get("x-client") == "daily-english-mobile"
}

func sameOrigin(r *http.Request) bool {
	if r.Method == http.MethodGet || r.Method == http.MethodHead || r.Method == http.MethodOptions {
		return true
	}
	origin := r.Header.Get("Origin")
	if origin == "" {
		return true
	}
	if origin == "null" {
		return false
	}
	if origin == "https://localhost" || origin == "capacitor://localhost" {
		return true
	}
	parsed, err := url.Parse(origin)
	if err != nil {
		return false
	}
	expected := strings.ToLower(strings.TrimSpace(strings.Split(r.Host, ",")[0]))
	return strings.ToLower(parsed.Host) == expected
}

func readBody(w http.ResponseWriter, r *http.Request, limit int64) ([]byte, bool, error) {
	reader := http.MaxBytesReader(w, r.Body, limit)
	body, err := io.ReadAll(reader)
	if err != nil {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			return nil, true, err
		}
		return nil, false, err
	}
	return body, false, nil
}

func bodyObject(raw []byte) (map[string]any, error) {
	if len(bytes.TrimSpace(raw)) == 0 {
		return map[string]any{}, nil
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return nil, err
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		return nil, errors.New("trailing data")
	}
	obj, ok := value.(map[string]any)
	if !ok {
		return nil, errors.New("not an object")
	}
	return obj, nil
}

func stringValue(value any) string {
	text, _ := value.(string)
	return text
}

func passwordValue(value any) (string, string) {
	password, ok := value.(string)
	if !ok {
		return "", "请填写密码"
	}
	return password, auth.PasswordProblem(password)
}

func integerValue(value any) (int64, bool) {
	switch v := value.(type) {
	case json.Number:
		f, err := v.Float64()
		if err != nil || f < 0 || f != math.Trunc(f) || f > math.MaxInt32 {
			return 0, false
		}
		return int64(f), true
	case float64:
		if v < 0 || v != math.Trunc(v) || v > math.MaxInt32 {
			return 0, false
		}
		return int64(v), true
	default:
		return 0, false
	}
}
