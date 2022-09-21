FROM golang:1.19-alpine as build

RUN apk add --no-cache --update git

WORKDIR /src

ADD . /src

RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o api main.go

# ========================================================================

FROM alpine

RUN apk --no-cache add ca-certificates

WORKDIR /app

COPY --from=build /src/api /app/

EXPOSE 1323

ENTRYPOINT ./api
