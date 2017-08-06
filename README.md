# GNI_Honours

For the ING Honours programme we had to write a software that represented a banking system that satisfied certain [requirements](https://github.com/Saulero/GNI_Honours/wiki/Initial-System-Requirements). We were completely free on how to implement this system as long as it satisfied the requirements.

# Prerequisites
The system uses a MySQL database to store all its data. Currently, for convenience, all the tables belonging to different microservices are by default put into the same database.
Make sure you have a MySQL database setup. Set the URL, username and password variables in database/Variables.java to the corresponding values for your MySQL server.
All required libraries are managed by Gradle, ensure that they are properly loaded.
Run the util/TableCreator.java main method once to create all the tables in the database. Running this main method again will remake the database, deleting all existing data in the GNI tables.

# How to run the system
Each service package contains a main class which you can run to start up the service. Once all services have been started you can make requests to each service on their uri, by default this uri is services/<service>/<requestType>.
The system can easily be started by running the util/BootSystem.java main method.
When communicating according to the API protocol, the address is by default http://<IP or localhost>:9997/services/api/request

# How to test/demo the system
Several Junit tests have been provided in the test packages.
A modified version of the test suite is also present in the repository, when running this project on the same machine as the system, it works out of the box.

A more detailed overview of the system can be found in the [wiki](https://github.com/Saulero/GNI_Honours/wiki/System-overview).
