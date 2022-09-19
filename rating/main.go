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
	"time"

	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"go.opentelemetry.io/contrib/instrumentation/github.com/labstack/echo/otelecho"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/jaeger"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
	oteltrace "go.opentelemetry.io/otel/trace"
)

var tracer = otel.Tracer("echo-server")

var errRatingNotFound = errors.New("There is no rating for this product")

// Rating ...
type Rating struct {
	ProductID int `json:"product_id"`
	Value     int `json:"value"`
}

// RatingRepository ....
type RatingRepository interface {
	RatingByProductID(ctx context.Context, productID int) (*Rating, error)
}

type ratingRepository struct {
	ratings map[int]*Rating
}

// RatingByProductID ...
func (r *ratingRepository) RatingByProductID(ctx context.Context, productID int) (*Rating, error) {
	_, span := tracer.Start(ctx, "repository.RatingByProductID", oteltrace.WithAttributes(attribute.String("productID", strconv.Itoa(productID))))
	defer span.End()

	rating, ok := r.ratings[productID]
	if !ok {
		return nil, errRatingNotFound
	}
	return rating, nil
}

// NewRatingRepository ...
func NewRatingRepository() RatingRepository {
	return &ratingRepository{
		ratings: map[int]*Rating{
			1: &Rating{
				ProductID: 1,
				Value:     4,
			},
			2: &Rating{
				ProductID: 2,
				Value:     3,
			},
			3: &Rating{
				ProductID: 3,
				Value:     5,
			},
		},
	}
}

// RatingService ....
type RatingService interface {
	RatingByProductID(ctx context.Context, productID int) (*Rating, error)
}

type ratingService struct {
	repository RatingRepository
}

// RatingByProductID ...
func (s *ratingService) RatingByProductID(ctx context.Context, productID int) (*Rating, error) {
	_, span := tracer.Start(ctx, "service.RatingByProductID", oteltrace.WithAttributes(attribute.String("productID", strconv.Itoa(productID))))
	defer span.End()

	rating, err := s.repository.RatingByProductID(ctx, productID)
	if err != nil {
		return nil, err
	}
	return rating, nil
}

// NewRatingService ...
func NewRatingService(repository RatingRepository) RatingService {
	return &ratingService{
		repository: repository,
	}
}

func main() {
	tp, err := initTracer()
	if err != nil {
		log.Fatal(err)
	}

	defer func() {
		if err := tp.Shutdown(context.Background()); err != nil {
			log.Printf("Error shutting down tracer provider: %v", err)
		}
	}()

	service := NewRatingService(NewRatingRepository())

	e := echo.New()
	e.Use(middleware.Logger())
	e.Use(otelecho.Middleware("rating"))

	e.GET("/product/:id", func(c echo.Context) error {
		ctx := c.Request().Context()
		idParam := c.Param("id")
		id, err := strconv.Atoi(idParam)
		if err != nil {
			return c.String(http.StatusBadRequest, "invalid parameter")
		}
		rating, err := service.RatingByProductID(ctx, id)
		if err != nil {
			if strings.Contains(err.Error(), "There is no rating for this product") {
				return c.String(http.StatusNotFound, "rating not found")
			}
			return c.String(http.StatusBadRequest, "error search rating")
		}
		return c.JSON(http.StatusOK, rating)
	})

	// Start server
	go func() {
		if err := e.Start(":1323"); err != nil && err != http.ErrServerClosed {
			e.Logger.Fatal("shuting down the server")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt)
	<-quit
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := e.Shutdown(ctx); err != nil {
		e.Logger.Fatal(err)
	}
}

func initTracer() (*sdktrace.TracerProvider, error) {
	// create the Jaeger exporter
	exp, err := jaeger.New(jaeger.WithCollectorEndpoint(jaeger.WithEndpoint("http://jaeger:14268/api/traces")))
	if err != nil {
		return nil, err
	}
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceNameKey.String("rating"),
			attribute.Int64("ID", 1),
		)),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))
	return tp, nil
}
