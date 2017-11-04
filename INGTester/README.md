# ING Honours project - Functionality test suite round two
## About
This test covers the extensions 8 up to and including 13 and assumes a correct implementation of the preceding extensions. 

## Setup
- Clone project
- Import the project as maven project
- Change in the TestSuite.java (located in package main) the implementation of client to use (socket or HTTP) 
- Make sure the dependencies are all imported correctly
- Make sure your database is empty (a fresh start of the server). The test is designed in such way that if it is run completely the database is restored to the original starting point. This means you do not have to reset your database after each test attempt. 
- Make sure your server is running
- Run TestSuite.java (located in the main package) to run the entire testsuite. The result in the console should be clear enough to understand if you passed or failed the tests. 

# Remarks
- If you are using the HTTP client, fully specify the url (http://...)
- You can also execute each sub test separately. The sub tests are located in package "main.test.extension". You can run each of these classes (handy if one particular test fails).
- The test assumes some error codes that seemed fit to me. If you feel that your error code is more fit, you can change it in the code. All the error codes are located in ErrorCodes.java (located in package main.util). The check on error codes are as follow: checkError(result, errorCode);. This function checks on the given error, here result is the result message of the server and the errorCode the specified error code to check on.


