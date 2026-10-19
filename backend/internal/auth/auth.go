// Package auth mirrors the browser server's credential rules. Existing scrypt
// hashes and token hashes must remain verifiable after the runtime changes.
package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf16"

	"golang.org/x/crypto/scrypt"
)

const (
	MaxPassword  = 128
	SessionDays  = 60
	AllowedFails = 8
	WindowMillis = 15 * 60 * 1000
	LockMillis   = 15 * 60 * 1000
)

var (
	EmailPattern = regexp.MustCompile(`^[^\s@]+@[^\s@]+\.[^\s@]{2,}$`)
	letter       = regexp.MustCompile(`[a-zA-Z]`)
	digit        = regexp.MustCompile(`[0-9]`)
)

func NormalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

func EmailProblem(email string) string {
	value := NormalizeEmail(email)
	if value == "" {
		return "请填写邮箱"
	}
	if utf16Len(value) > 254 || !EmailPattern.MatchString(value) {
		return "邮箱格式不正确"
	}
	return ""
}

func PasswordProblem(password string) string {
	length := utf16Len(password)
	if length < 8 {
		return "密码至少 8 位"
	}
	if length > MaxPassword {
		return "密码最长 128 位"
	}
	if !letter.MatchString(password) || !digit.MatchString(password) {
		return "密码需同时包含字母和数字"
	}
	return ""
}

func credential(password string) string {
	if utf16Len(password) <= MaxPassword {
		return password
	}
	// Match String#slice(0, 128) in the browser implementation.
	units := utf16.Encode([]rune(password))
	return string(utf16.Decode(units[:MaxPassword]))
}

func HashPassword(password string) (string, error) {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	key, err := scrypt.Key([]byte(credential(password)), salt, 1<<14, 8, 1, 64)
	if err != nil {
		return "", err
	}
	return "scrypt:16384:8:1:" + base64.RawURLEncoding.EncodeToString(salt) + ":" + base64.RawURLEncoding.EncodeToString(key), nil
}

func VerifyPassword(password, stored string) bool {
	parts := strings.Split(stored, ":")
	if len(parts) != 6 || parts[0] != "scrypt" {
		return false
	}
	n, errN := strconv.Atoi(parts[1])
	r, errR := strconv.Atoi(parts[2])
	p, errP := strconv.Atoi(parts[3])
	if errN != nil || errR != nil || errP != nil || n <= 1 || r <= 0 || p <= 0 {
		return false
	}
	salt, err := base64.RawURLEncoding.DecodeString(parts[4])
	if err != nil {
		return false
	}
	expected, err := base64.RawURLEncoding.DecodeString(parts[5])
	if err != nil || len(expected) == 0 {
		return false
	}
	key, err := scrypt.Key([]byte(credential(password)), salt, n, r, p, len(expected))
	if err != nil || len(key) != len(expected) {
		return false
	}
	return subtle.ConstantTimeCompare(key, expected) == 1
}

func CreateToken() (token, tokenHash string, err error) {
	raw := make([]byte, 32)
	if _, err = rand.Read(raw); err != nil {
		return "", "", err
	}
	token = base64.RawURLEncoding.EncodeToString(raw)
	return token, HashToken(token), nil
}

func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func AttemptKeys(ip, email string) []string {
	return []string{"ip:" + ip, "account:" + NormalizeEmail(email)}
}

func OneOf(value string, options ...string) bool {
	for _, option := range options {
		if value == option {
			return true
		}
	}
	return false
}

var ErrInvalidCredentials = errors.New("invalid credentials")

func utf16Len(value string) int {
	return len(utf16.Encode([]rune(value)))
}
