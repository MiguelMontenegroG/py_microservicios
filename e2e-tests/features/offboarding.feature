Feature: Employee offboarding
  As an HR administrator
  I want to remove employees from the system
  So that they no longer have access after leaving the company

  Background:
    Given I am authenticated as ADMIN
    And a fully onboarded employee exists with unique email ending with "offboarding.test"

  Scenario: Deleting an employee disables their login
    When I delete the last created employee
    Then the response status should be 204
    And the offboarded employee cannot login

  Scenario: Deleted employee returns 404 on lookup
    Given I am authenticated as ADMIN
    And a fully onboarded employee exists with unique email ending with "offboarding.lookup"
    When I delete the last created employee
    Then the response status should be 204
    When I look up the last created employee
    Then the response status should be 404
