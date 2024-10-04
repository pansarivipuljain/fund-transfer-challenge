# fund-transfer-challenge

# Features achieved 
  1. Added functionality of transferring fund from one account to another
  2. This functionality is thread safe by using synchronization feature of Java
  3. Tested basic validation and positive scenerio using junit test cases in AccountsControllerTest class
  4. Tested Thread safety using concurrent requests in AccountsServiceTest class
  5. In all the test cases we have mocked NotificationService using Mockito framework
  6. Added loggers to understand data flow

# Added extra Functionality
  1. Fixed existing getAccount() test case which was failing in AccountsControllerTest
  2. Added proper looging for all newly implemented requirements
  3. Added updateAccount feature at repository service


