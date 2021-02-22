![las2peer](img/logo/bitmap/las2peer-logo-128x128.png)

# [![Java CI with Gradle](https://github.com/rwth-acis/las2peer/workflows/Java%20CI%20with%20Gradle/badge.svg?branch=master)](https://github.com/rwth-acis/las2peer/actions?query=workflow%3A%22Java+CI+with+Gradle%22+branch%3Amaster) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/rwth-acis/las2peer)

las2peer is a Java-based server framework for developing and deploying microservices in a distributed Peer-to-Peer (P2P) environment.
It is developed by the Advanced Community Information Systems (ACIS) group at the Chair of Computer Science 5 (Information Systems & Databases), RWTH Aachen University, Germany.
It's main focus lies on providing developers with a tool to easily develop and test their microservices and deploy them in a P2P network without having to rely on a centralized infrastructure.
Communication between nodes is realized using the [FreePastry](http://www.freepastry.org/ "FreePastry") library.

For more information on the core concepts of las2peer, please visit [las2peer.org](https://las2peer.org "las2peer.org") or read the [las2peer Primer](https://dx.doi.org/10.13140/RG.2.2.31456.48645 "las2peer Primer").

## Service Development

This project contains las2peer itself.
To develop a service for las2peer, please use the [las2peer Template Project](https://github.com/rwth-acis/las2peer-template-project/) and follow the instructions of the project's ReadMe.

If you want to learn more about las2peer, please visit the [las2peer Template Project's Wiki Page](https://github.com/rwth-acis/las2peer-template-project/wiki).

## Preparations

### Java

las2peer uses **Java 14**.

### Build Dependencies

* Gradle 6.8
* Node 8 / npm

## Project Structure

### Modules

This repository contains three las2peer modules:

* Core (`/core`)
* REST Mapper (`/restmapper`)
* Web Connector (`/webconnector`)

Each of them resides in its own subfolder, containing a build file providing the following tasks:

* `gradle jar` will build the respective jar of the submodule. The jars will be stored in `/export/jars`.
* `gradle test` will run the respective JUnit tests. Test reports will be stored in `/build/reports`.
* `gradle javadoc` will create the JavaDocs for the respective submodule. The results will be stored in `/export/javadoc`.
* `gradle build` runs all the tasks from above.
* `gradle publish<submodule>PublicationToMavenLocal` publishes the respective submodule to the local maven repository. If you have set up a project to resolve dependencies from the local repository, you can conveniently test your local changes without pushing and publishing them.

### Bundle

las2peer has a modular structure and many dependencies.
However, most installations use all modules together, this is why we provide a bundle of all submodules and their dependencies in one single jar.
The build script for the bundle can be found in `/bundle`.

The build script provides the following tasks:

* `gradle shadowJar` will build the fat jar. The jar will be stored in `/export/jars`.
* `gradle publishBundlePublicationToMavenLocal` publishes the bundle to the local maven repository.

### Super Build Script

To make life easier for developers, this repository contains a build script for conveniently building multiple submodules.

* `gradle buildOnly` will run `publish<submodule>PublicationToMavenLocal` on each submodule
* `gradle buildOnlyNoBundle` will run `publish<submodule>PublicationToMavenLocal` on each submodule except for the bundle

## Documentation

JavaDocs of the latest release can be found online:

* [Core](http://rwth-acis.github.io/las2peer/core/ "Core")
* [Web Connector](http://rwth-acis.github.io/las2peer/webconnector/ "Web Connector")
* [Rest Mapper](http://rwth-acis.github.io/las2peer/restmapper/ "Rest Mapper")

## CI

Every commit to the develop branch is built by GitHub actions and will be published as a snapshot to our Archiva.
Releases are drafted from the master branch.
