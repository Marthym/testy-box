# testy-box [![](https://img.shields.io/github/release/Marthym/testy-box.svg)](https://GitHub.com/Marthym/testy-box/releases/) [![GitHub license](https://img.shields.io/github/license/Marthym/testy-box.svg)](https://github.com/Marthym/testy-box/blob/master/LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=alert_status)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=coverage)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)


## Description

`testy-box` is a module providing many extensions for **JUnit 5** tests:

* **[testy-beat-box](testy-beat-box/README.md)** provides extensions to run an in-memory [Qpid](https://qpid.apache.org/) AMQP broker and provide reactive RabbitMQ connections.
* **[testy-core-box](testy-core-box/README.md)** provides core extensions. All the other projects depend on it.
* **[testy-jooq-box](testy-jooq-box/README.md)** provides extensions to run an in-memory H2 database. Test data can be inserted using [JOOQ](https://www.jooq.org/).
* **[testy-mongo-box](testy-mongo-box/README.md)** provides extensions to run an embedded [MongoDB](https://www.mongodb.com/) database. Test data can be inserted.
* **[testy-params-box](testy-params-box/README.md)** provides aggregators for ParameterizedTest.
* **[testy-redis-box](testy-redis-box/README.md)** provides extensions to run an embedded [Redis](https://redis.io/) database.
* **[testy-search-box](testy-search-box/README.md)** provides extensions to run an embedded [OpenSearch](https://opensearch.org/) node. Test data can be indexed.

## testy-core-box

This project provides common extensions

-> **[See module documentation](testy-core-box/README.md)**

## testy-jooq-box

This project is used to test SQL repositories.

-> **[See module documentation](testy-jooq-box/README.md)**

## testy-beat-box

This project is used to test classes using RabbitMQ. It provides an extension to run an embedded AMQP broker.

-> **[See module documentation](testy-beat-box/README.md)**
