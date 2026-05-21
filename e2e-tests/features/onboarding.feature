Feature: Employee onboarding
  As an HR administrator
  I want to register new employees
  So that they can access the system with their credentials

  Background:
    Given I am authenticated as ADMIN

  Scenario: Successfully create a new employee
    When I create an employee with name "New Employee" and unique email ending with "onboard.success"
    Then the response status should be 201
    And the response body should contain the created employee email

  Scenario: Auth service eventually creates credentials for new employee
    When I create an employee with name "Credential Test" and unique email ending with "onboard.credential"
    Then the response status should be 201
    And eventually the auth service should have a user for the created employee

  Scenario: Cannot create employee with missing required fields
    When I try to create an employee without required fields
    Then the response status should be 400

  Scenario: Cannot create employee with duplicate email
    Given an employee with unique email ending with "onboard.duplicate" already exists
    When I try to create an employee with name "Duplicate" and the same email as before
    Then the response status should be 409
