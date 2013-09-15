Welcome to LAS2peer!
=================

PREPARATIONS
-----------------------

If you use an Oracle Java version, you have to enable strong encryption for this software.

Just put the file matching your java version to
    [...]/lib/security/local-policy.jar
of your used java runtime. (You have to replace the existing one.)

The policy files are downloadable via the Oracle webpage:

[JCE for Java 6](http://www.oracle.com/technetwork/java/javase/downloads/jce-6-download-429243.html "JCE-6")
or
[JCE for Java 7](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html "JCE-7")

(If the unit-test "i5.las2peer.communication.MessageTest" runs successfully, you have enabled strong encryption correctly)

Building instructions: [![Build Status](https://travis-ci.org/rwth-acis/las2peer.png?branch=master)](https://travis-ci.org/rwth-acis/las2peer)
----------------------
For building simply run
    ant compile_all

Unit tests:
-----------
All JUnit tests are started with
    ant junit_tests

Reports can be found in ../tmp/reports afterwards.

Java-Docs:
----------
Simply build the standard java docs with
    ant java_doc


How to develop a Service for las2peer
-------------------------------------
1. Get the SampleService Project frome here: [LAS2Peer-Sample-Project](https://github.com/rwth-acis/LAS2peer-Sample-Service/archive/master.zip)
2. If you use Eclipse, import the project or just create a new Project in the same folder.
3. Change conf/service.properties according to the service you want to build
4. Run "ant get_deps" once to pull all dependencies. (You can skip this but Eclipse will complain about missing libraries until you build the first time)
5. Implement a Service by inheriting from i5.las2peer.api.Service (Or refactor from the existing i5.las2peer.ServicePackage.ServiceClass)
6. run "ant" to just build the service jar or "ant run" to directly run your service

A jar file with your service will be in export/ and a generated service agent XML file in startup/

Using a different build system/directory structure
-------------------------------------------------
If you really want to use las2peer in a different project structure/ with a different build system you can point your build-System to this
Maven repository: http://role.dbis.rwth-aachen.de:9911/archiva/repository/internal/ and add the las2peer as dependency for your project:
```
<dependency>
    <groupId>i5</groupId>
    <artifactId>las2peer</artifactId>
    <version>0.0.1</version>
</dependency>
```
For information about the build process look at the "jar" and "generate_configs" tasks in build.xml

Simple Node-Starter for Testing purposes
----------------------------------------
The class i5.las2peer.testing.L2pNodeLaucher provides a simple way to start a node and launch some testing methods.

All you have to know is a port, which you can open at your local machine.
If you want to join an existing network, you will need to know address and port of at least one participant.

For simplicity, you can just use the helper script located at bin/start_node.sh.


So to start a new network, follow this steps:

1. build everything with
    ant compile_all

2. run the starter script with
    bin/start_node.sh -s 9001 -

3. a) add an additional node to the net with
      bin/start_node.sh -s 9002 127.0.0.1:9001

3. b) add an additional node hosted at another machine with
      bin/start_node.sh -s 9001 IP_OF_THE_FIRST_MACHINE:9001


If you want to execute test methods at the nodes just put their names as additional command line parameters to the start_node.sh script like
    bin/start_node.sh -s 9001 - uploadAgents waitALittle waitALittle searchEve
and for the second node
    bin/start_node.sh -s 9002 127.0.0.1:9001 waitALittle fetchAgent registerEve

More informations about existing test methods can be found in the Java Docs in the documentation of the i5.las2peer.testing.L2pNodeLauncher class.
Basically you can use all public (non-static) methods of the class.

You can find detailed log files for each node in the directory testing/log afterwards.


Start a complete network of testing nodes
-----------------------------------------

As an alternative to starting single nodes for testing purposes (see last section) you can use the start_node.sh script to start a complete network configured by a directory containing one [name].node file.

Each node configuration file follows a simple syntax:

    line    content
    1:      port
    2:      bootstrap
    3-x:    test method

In lines 3-x instructions starting with // or # will be left out. You can use all public methods of the L2pNodeLauncher class (see api doc) as testing methods. Those expecting a String parameter can be called simply as
    testMethod(myStringValue)
This may be useful e.g. in combination with uploadStartupDirectory to set up a previously defined set of artifacts and/or agents to be stored in the global storage at startup.
You can find a short documentation of all possible commands in the Java Doc of the L2pNodeLauncher class.
One method points out however: with the method
    interactive
you can start a simple interactive command prompt, where you can given simple commands to one of the nodes.

The nodes will be launched in alphabetical order of the configuration file names. So make sure, that the first node will contain a "NEW" bootstrap statement.

All nodes will be launched with just a little pause between startup and in separate threads. If one of the first nodes provides data (e.g. for testing purposes) it could be necessary to add a waitALittle statement at the start of all depending nodes. There are no dependency checks implemented at this point of development.

You can find a sample configuration directory at testing/sample_nodes*.

For logging, the log subdirectory directory of the current working directory will be used. A fresh directory named with the current date and a consecutive number suffix will be created. Inside this new directory one log file per started node will be filled.

The uploadStartupDirectory method can be used to upload a set of artifacts and agents to the network. Each .xml-file in the given directory ('startup' by default) will be used as artifact or agent (if the file name starts with 'agent-'). For uploading agents make sure that a file called passphrases.txt exists and contains the passphrases for the service or user agents to be uploaded. Each line of the passphrase file consists of the agent's xml filename and the corresponding passphrase separated by a ';'.

You can use the command line tools EnvelopeGenerator, ServiceAgentGenerator and UserAgentGenerator from the i5.las2peer.tools package to create such xml files.

To start the the network, use the following command:
    bin/start_node.sh -d [config_dir]



The End of a run
----------------
Each started node will be kept running if the last command executed either via command line or configuration file does not give a /shutdown/ command.
In a network of nodes, the simulation (or test run or whatever) will stop only, if all participating nodes have been closed.

You can stop the complete run using Strg-C at any point, of course.

