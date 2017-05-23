# GNI_Honours

For the ING Honours programme we had to write a software that represented a banking system that satisfied certain [requirements](https://github.com/Saulero/GNI_Honours/wiki/Initial-System-Requirements). We were completely free on how to implement this system as long as it satisfied the requirements.

# Prerequisites
The system uses a MySQL database to store all its data. Currently, for convenience, all the tables belonging to different microservices are by default put into the same database.
Make sure you have a MySQL database setup. Set the URL, username and password variables in database/Variables.java to the corresponding values for your MySQL server.
All required libraries are managed by Gradle, ensure that they are properly loaded.
Run util/TableCreator.java main method once to create all the tables in the database. Running this main method again will remake the database, deleting all existing data in the GNI tables.

# How to run the system
Each service package contains a main class which you can run to start up the service. Once all services have been started you can make requests to each service on their uri, by default this uri is services/<service>/<requestType>.
**This will probably be changed during the first extension when we will have to implement a new protocol.**
Our idea for the system was that only the UIService, PinService and TransactionReceive/DispatchService are reachable from outside the system so all requests should be sent to one of these services.

# How to test/demo the system
Both a SystemTest and several Junit tests have been provided in the test packages.
For a demo of all functionality in the system run the systemTest. The systemTest will start all services and then execute every request the system supports and print the output from each service in System.Out. This gives a nice overview of the flow of requests through the system.

A more detailed overview of the system can be found in the [wiki](https://github.com/Saulero/GNI_Honours/wiki/System-overview).
