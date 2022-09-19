.PHONY:

run-all:
	@echo Executando todos os servicos
	DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1 docker-compose up

stop-all:
	@echo Parando todos os servicos
	docker-compose stop

clean-all:
	@echo Removendo todos os containers
	docker-compose rm -f

