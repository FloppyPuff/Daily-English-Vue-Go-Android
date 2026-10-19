package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"dailyenglish/backend/internal/api"
	"dailyenglish/backend/internal/store"
)

func main() {
	config := readConfig()
	st, err := store.Open(config.dataDir)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer st.Close()

	handler := api.New(st, api.Config{
		TrustProxy:   config.trustProxy,
		CookieSecure: config.cookieSecure,
		AccountsOpen: config.accountsOpen,
		SessionDays:  60,
		MaxBodyBytes: 6 * 1024 * 1024,
	}).Handler()

	server := &http.Server{
		Addr:              config.host + ":" + strconv.Itoa(config.port),
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		log.Printf("Daily English Go API listening on %s (data %s)", server.Addr, config.dataDir)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("serve: %v", err)
		}
	}()
	<-ctx.Done()
	shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdown); err != nil {
		log.Printf("shutdown: %v", err)
	}
}

type config struct {
	host         string
	port         int
	dataDir      string
	cookieSecure bool
	accountsOpen bool
	trustProxy   bool
}

func readConfig() config {
	return config{
		host:         env("HOST", "127.0.0.1"),
		port:         envInt("PORT", 8788),
		dataDir:      env("DATA_DIR", "./data"),
		cookieSecure: envBool("COOKIE_SECURE", false),
		accountsOpen: envBool("ACCOUNTS_OPEN", true),
		trustProxy:   envBool("TRUST_PROXY", false),
	}
}

func env(name, fallback string) string {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	return value
}

func envInt(name string, fallback int) int {
	value, err := strconv.Atoi(env(name, ""))
	if err != nil || value < 1 || value > 65535 {
		return fallback
	}
	return value
}

func envBool(name string, fallback bool) bool {
	value := strings.ToLower(strings.TrimSpace(os.Getenv(name)))
	if value == "" {
		return fallback
	}
	return value == "1" || value == "true" || value == "yes" || value == "on"
}
