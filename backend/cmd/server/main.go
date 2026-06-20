package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	firebase "firebase.google.com/go/v4"
	"github.com/boriay/cld-anki/backend/internal/auth"
	"github.com/boriay/cld-anki/backend/internal/config"
	"github.com/boriay/cld-anki/backend/internal/db"
	"github.com/boriay/cld-anki/backend/internal/handler"
	"github.com/boriay/cld-anki/backend/internal/repository"
	"github.com/boriay/cld-anki/backend/internal/weather"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"google.golang.org/api/option"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(log) // handlers log internal errors via slog.Default()

	cfg, err := config.Load()
	if err != nil {
		log.Error("load config", "err", err)
		os.Exit(1)
	}

	ctx := context.Background()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Error("connect db", "err", err)
		os.Exit(1)
	}
	defer pool.Close()

	// Firebase Admin SDK — uses GOOGLE_APPLICATION_CREDENTIALS if set,
	// otherwise falls back to GKE Workload Identity via ADC.
	var fbOpts []option.ClientOption
	if creds := os.Getenv("GOOGLE_APPLICATION_CREDENTIALS"); creds != "" {
		fbOpts = append(fbOpts, option.WithCredentialsFile(creds))
	}
	fbApp, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: cfg.FirebaseProjectID}, fbOpts...)
	if err != nil {
		log.Error("init firebase", "err", err)
		os.Exit(1)
	}
	authClient, err := fbApp.Auth(ctx)
	if err != nil {
		log.Error("init firebase auth", "err", err)
		os.Exit(1)
	}

	deckRepo := repository.NewDeckRepo(pool)
	cardRepo := repository.NewCardRepo(pool)

	weatherSvc := weather.NewService(cfg.WeatherGeoIPURL, cfg.WeatherForecastURL, time.Hour)

	deckH := handler.NewDeckHandler(deckRepo)
	cardH := handler.NewCardHandler(cardRepo)
	syncH := handler.NewSyncHandler(pool, deckRepo, cardRepo)
	weatherH := handler.NewWeatherHandler(weatherSvc)

	r := chi.NewRouter()
	r.Use(middleware.RealIP)
	r.Use(middleware.RequestID)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(30 * time.Second))

	r.Get("/health", handler.Health)

	r.Route("/api/v1", func(r chi.Router) {
		r.Use(auth.Middleware(authClient))

		r.Get("/decks", deckH.List)
		r.Post("/decks", deckH.Create)
		r.Get("/decks/{id}", deckH.Get)
		r.Put("/decks/{id}", deckH.Update)
		r.Delete("/decks/{id}", deckH.Delete)

		r.Get("/decks/{deckID}/cards", cardH.ListByDeck)
		r.Post("/decks/{deckID}/cards", cardH.Create)
		r.Get("/cards/{id}", cardH.Get)
		r.Put("/cards/{id}", cardH.Update)
		r.Delete("/cards/{id}", cardH.Delete)

		r.Post("/sync", syncH.Sync)

		r.Get("/weather", weatherH.Current)
	})

	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go func() {
		log.Info("server started", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Error("server error", "err", err)
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	log.Info("shutting down")
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Error("shutdown error", "err", err)
	}
}
